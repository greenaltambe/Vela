package com.greenaltambe.vela.modules.sponsorblock

import com.greenaltambe.vela.core.http.ApiResult
import com.greenaltambe.vela.core.http.ModuleEvent
import com.greenaltambe.vela.core.http.ModuleLog
import com.greenaltambe.vela.core.settings.SegmentActionSetting
import com.greenaltambe.vela.core.settings.Setting
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.modules.FeatureModule
import com.greenaltambe.vela.player.AddonHost
import com.greenaltambe.vela.player.PlaybackAddon
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

/** "Skipped a sponsor": what the UI shows, with Undo returning to [fromMs]. */
class SkipNotice(val segments: List<Segment>, val fromMs: Long, val toMs: Long) {
    val label: String get() = segments.map { SegmentCategory.fromId(it.category)?.label ?: it.category }.distinct().joinToString(", ")
}

/** Where a submission stands, for the player's submit flow. */
sealed interface SubmitOutcome {
    data object Sent : SubmitOutcome
    data class Failed(val message: String) : SubmitOutcome
}

/**
 * SponsorBlock as a [FeatureModule] and a [PlaybackAddon]: looks the segments up when a video starts (never blocking
 * playback; a failure only leaves the feature inactive for that video, visible in Diagnostics), then skips / mutes /
 * prompts as the position moves. Everything it does is settable per category and per channel.
 */
class SponsorBlockAddon(
    private val repo: SegmentRepository,
    private val api: SponsorBlockApi,
    private val settings: SettingsResolver,
    private val log: ModuleLog,
    private val clock: Clock = Clock.System,
    /** The active account: each account submits under its own private id, so two accounts can never be linked through SponsorBlock. */
    private val accountId: () -> String = { com.greenaltambe.vela.data.LOCAL_ACCOUNT },
) : PlaybackAddon, FeatureModule {
    override val id = "sponsorblock"
    override val enabledSetting = Settings.sponsorBlockEnabled

    private var host: AddonHost? = null
    private var engine = newEngine("")
    private var videoId: String? = null
    private var loading: Job? = null

    private val _segments = MutableStateFlow<List<Segment>>(emptyList())

    /** Everything that does something (not "do nothing"), for the seek-bar markers. */
    val segments: StateFlow<List<Segment>> = _segments

    private val _prompt = MutableStateFlow<Segment?>(null)

    /** The segment a "Skip" button is on offer for right now. */
    val prompt: StateFlow<Segment?> = _prompt

    private val _notices = MutableSharedFlow<SkipNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<SkipNotice> = _notices

    override fun attach(host: AddonHost) { this.host = host }

    override fun onVideo(videoId: String, channelId: String, offline: Boolean) {
        reset()
        if (!isEnabled(settings)) return
        this.videoId = videoId
        engine = newEngine(channelId)
        val h = host ?: return
        loading = h.scope.launch {
            when (val r = repo.get(videoId, SegmentCategory.entries, allowNetwork = !offline)) {
                is SegmentsResult.Loaded -> {
                    engine.load(r.segments)
                    _segments.value = engine.visible()
                    log.record(ModuleEvent(id, true, "$videoId: ${r.segments.size} segment(s) (${r.source.name.lowercase()})", clock.now()))
                }
                is SegmentsResult.Unavailable -> log.record(ModuleEvent(id, false, "$videoId: inactive, ${r.kind.name.lowercase()}: ${r.message}", clock.now()))
            }
        }
    }

    override fun onPlayerState(state: PlayerState) {
        val h = host ?: return
        if (videoId == null) return
        if (state.status != PlaybackStatus.PLAYING && state.status != PlaybackStatus.PAUSED && state.status != PlaybackStatus.BUFFERING) return
        for (effect in engine.onPosition(state.positionMs)) when (effect) {
            is SegmentEffect.Skip -> {
                h.seekTo(effect.toMs)
                if (settings.resolve(Settings.sponsorBlockNotifications)) _notices.tryEmit(SkipNotice(effect.segments, effect.fromMs, effect.toMs))
            }
            is SegmentEffect.Mute -> h.setAddonMuted(effect.muted)
            is SegmentEffect.Prompt -> _prompt.value = effect.segment
        }
    }

    override fun onStop() {
        reset()
        videoId = null
    }

    /** Takes the viewer back to where the skipped segment started, and keeps it from being skipped again. */
    fun undo(notice: SkipNotice) {
        engine.markHandled(notice.segments.map { it.uuid })
        host?.seekTo(notice.fromMs)
    }

    /** Acts on the current prompt: jumps past the segment (or to the highlight). */
    fun acceptPrompt() {
        val segment = _prompt.value ?: return
        engine.markHandled(listOf(segment.uuid))
        host?.seekTo(if (segment.isPoint) segment.startMs else segment.endMs)
        _prompt.value = null
    }

    /**
     * Sends a segment for review. Only called after the viewer has reviewed and confirmed it; nothing here is ever
     * triggered by playback.
     */
    suspend fun submit(category: SegmentCategory, startMs: Long, endMs: Long, videoDurationMs: Long): SubmitOutcome {
        val id = videoId ?: return SubmitOutcome.Failed("No video is playing")
        if (category != SegmentCategory.POI_HIGHLIGHT && endMs <= startMs) return SubmitOutcome.Failed("The end must be after the start")
        val result = api.submit(id, startMs, if (category == SegmentCategory.POI_HIGHLIGHT) startMs else endMs, category, userId(), videoDurationMs)
        val outcome = when (result) {
            is ApiResult.Ok -> SubmitOutcome.Sent
            is ApiResult.Failed -> SubmitOutcome.Failed(result.message)
        }
        log.record(ModuleEvent(this.id, outcome is SubmitOutcome.Sent, "submit $id ${category.id} ${startMs}-${endMs}: ${if (outcome is SubmitOutcome.Sent) "sent" else (outcome as SubmitOutcome.Failed).message}", clock.now()))
        return outcome
    }

    /** The private id this install submits under: generated once and kept in settings. */
    fun userId(): String {
        val acct = accountId()
        // The local account keeps the original setting (existing installs keep their id); every other account has its own.
        val setting = if (acct == com.greenaltambe.vela.data.LOCAL_ACCOUNT) Settings.sponsorBlockUserId else Setting(
            key = "sponsorblock.userId.$acct", default = "", scope = com.greenaltambe.vela.core.settings.SettingScope.GLOBAL, label = "SponsorBlock user id for $acct (private)",
            keywords = emptyList(), codec = com.greenaltambe.vela.core.settings.SettingCodecs.string, section = com.greenaltambe.vela.core.settings.SettingSection.ADVANCED,
        )
        settings.resolve(setting).takeIf { it.length >= 30 }?.let { return it }
        val fresh = buildString { repeat(36) { append("0123456789abcdef"[Random.nextInt(16)]) } }
        settings.set(setting, fresh)
        return fresh
    }

    private fun reset() {
        loading?.cancel(); loading = null
        host?.setAddonMuted(false)
        _segments.value = emptyList()
        _prompt.value = null
    }

    private fun newEngine(channelId: String) = SegmentEngine(
        actionFor = { category ->
            val setting = Settings.sponsorBlockActions.getValue(category.id)
            when (settings.resolve(setting, channelId.ifEmpty { null })) {
                SegmentActionSetting.SKIP -> SegmentAction.SKIP
                SegmentActionSetting.MUTE -> SegmentAction.MUTE
                SegmentActionSetting.PROMPT -> SegmentAction.PROMPT
                SegmentActionSetting.IGNORE -> SegmentAction.IGNORE
            }
        },
        minDurationMs = (settings.resolve(Settings.sponsorBlockMinSegmentSeconds) * 1000).toLong(),
    )
}
