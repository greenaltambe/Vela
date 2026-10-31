package com.greenaltambe.vela.player

import com.greenaltambe.vela.core.network.FixedNetworkMonitor
import com.greenaltambe.vela.core.network.NetworkMonitor
import com.greenaltambe.vela.core.network.NetworkType
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.LOCAL_ACCOUNT
import com.greenaltambe.vela.data.WatchHistoryRepository
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorError
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.StreamSet
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.library.SleepTimerMode
import com.greenaltambe.vela.library.SleepTimerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The viewer's own choices for the video being played; null means "not chosen, follow the settings". */
/** What the OS media controls show for the current video (MPRIS on Linux). */
data class MediaInfo(val videoId: String, val title: String, val channelName: String, val artworkUrl: String?)

data class PlaybackOverrides(val speed: Float? = null, val maxHeight: Int? = null)

sealed interface SessionState {
    data object Idle : SessionState

    /** Fetching streams; [refresh] when this is a re-resolve of a video that was already playing. */
    data class Resolving(val videoId: String, val refresh: Boolean) : SessionState

    /**
     * Playing or ready to. [heights] are the resolutions on offer (best first) and [maxHeight] the cap picked from
     * the quality menu (null = automatic: the per-network setting applies), for a quality menu.
     */
    data class Ready(
        val videoId: String,
        val plan: PlaybackPlan,
        val heights: List<Int>,
        val maxHeight: Int?,
        val client: String?,
    ) : SessionState

    data class Failed(
        val videoId: String,
        val message: String,
        val extractorError: ExtractorError? = null,
        val playbackError: PlaybackError? = null,
    ) : SessionState
}

/**
 * Plays one video at a time: resolves its streams through [extractor] (the fallback chain), chooses a plan with
 * [StreamSelector], hands it to [controller], and keeps it playing.
 *
 * - **Expiry.** Stream URLs die. On [PlaybackErrorKind.StreamExpiredOrForbidden], or on any player error once
 *   [StreamSet.expiresAt] has passed, the streams are resolved again and playback resumes at the same position. That
 *   happens at most [maxReResolves] times in a row; the count resets once playback has moved on by a few seconds.
 *   Then the error is surfaced as [SessionState.Failed]. [play] and [selectQuality] also refresh first if the URLs
 *   have already expired.
 * - **History.** One [WatchHistoryRepository] row per session, created when something has been watched and then kept
 *   current: on pause, end, error, [stop], and every [historyInterval] while playing. A video that was left part
 *   way through resumes there.
 * - **Speed.** Resolved per video and channel from settings, unless the user has picked one this session.
 *
 * Not thread-safe: call from the thread [scope] runs on (the main thread in the apps).
 */
class PlaybackSession(
    private val extractor: Extractor,
    private val controller: PlayerController,
    private val settings: SettingsResolver,
    private val history: WatchHistoryRepository,
    scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val accountId: () -> String = { LOCAL_ACCOUNT },
    private val network: NetworkMonitor = FixedNetworkMonitor(),
    private val constraints: () -> PlaybackConstraints = { PlaybackConstraints() },
    private val maxReResolves: Int = 2,
    /** Connection drops (Wi-Fi <-> mobile, brief offline) are re-resolved this many times in a row, with a growing pause. */
    private val maxNetworkRecoveries: Int = 3,
    private val networkBackoff: Duration = 2.seconds,
    /** Buffering this long after playback had started counts as a slow stream: re-resolved once per video, preferring another source (setting, default on). */
    private val stallAfter: Duration = 12.seconds,
    private val historyInterval: Duration = 15.seconds,
    /** URLs this close to expiring count as expired, so a refresh happens before the player is rejected. */
    expiryMargin: Duration = 30.seconds,
    /** Features that watch playback (SponsorBlock). */
    private val addons: List<PlaybackAddon> = emptyList(),
) : AddonHost {
    private val sessionScope = scope
    private val leases = StreamLeases(extractor, clock, expiryMargin)

    init { addons.forEach { runCatching { it.attach(this) } } }

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state

    /**
     * The connection type, for display. A change mid-playback is deliberately NOT acted on: quality is chosen when a
     * plan is made (start, refresh, quality menu), never switched under the viewer.
     */
    val networkType: StateFlow<NetworkType> get() = network.type

    private val _volume = MutableStateFlow(1f)

    /** Output volume 0..1 as the user set it, whether or not it is currently [muted]. */
    val volume: StateFlow<Float> = _volume
    private val _heldSpeed = MutableStateFlow<Float?>(null)
    /** The speed being held right now (screen hold or space bar), or null: the player shows "2× speed" while it is set. */
    val heldSpeed: StateFlow<Float?> = _heldSpeed
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted

    private var addonMuted = false

    private fun applyVolume() = controller.setVolume(if (_muted.value || addonMuted) 0f else _volume.value)

    fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0f, 1f)
        _muted.value = false
        applyVolume()
    }

    fun toggleMute() {
        _muted.value = !_muted.value
        applyVolume()
    }

    override val scope: CoroutineScope get() = this.sessionScope

    override fun setAddonMuted(muted: Boolean) {
        if (addonMuted == muted) return
        addonMuted = muted
        applyVolume()
    }

    private fun forAddons(block: (PlaybackAddon) -> Unit) = addons.forEach { runCatching { block(it) } }

    private val _sleepTimer = MutableStateFlow<SleepTimerState?>(null)

    /** The running sleep timer, or null. At the deadline (or the end of the video) playback is paused and this clears. */
    val sleepTimer: StateFlow<SleepTimerState?> = _sleepTimer
    private var sleepJob: Job? = null

    // ---- per-video state, reset by start() ----
    private var videoId: String? = null
    private var title = ""
    private var channelNameShown = ""
    private var artworkUrl: String? = null
    private val _media = MutableStateFlow<MediaInfo?>(null)
    /** Title, channel and artwork of what is loaded, for the OS media controls; null when nothing is. */
    val media: StateFlow<MediaInfo?> = _media
    private fun publishMedia() { _media.value = videoId?.let { MediaInfo(it, title, channelNameShown, artworkUrl) } }
    private var channelId = ""
    private var streams: StreamSet? = null
    private val _captions = MutableStateFlow<List<com.greenaltambe.vela.extractor.CaptionTrack>>(emptyList())
    /** The subtitle tracks the current video offers (empty when it has none, or while it is resolving). */
    val captions: StateFlow<List<com.greenaltambe.vela.extractor.CaptionTrack>> = _captions
    private val _audioTracks = MutableStateFlow<List<AudioTrackChoice>>(emptyList())
    /** The audio tracks the video offers when it has more than one (languages / dubs); empty otherwise. */
    val audioTracks: StateFlow<List<AudioTrackChoice>> = _audioTracks
    private var audioKeyOverride: String? = null
    private val _audioKey = MutableStateFlow<String?>(null)
    /** The key of the audio track in use among [audioTracks], or null for the video's own (original) one. */
    val audioTrack: StateFlow<String?> = _audioKey
    /** Switches the soundtrack: the same picture re-planned with that language's audio, resuming where it was. [key] null: the original. */
    fun selectAudioTrack(key: String?) {
        if (localPlan != null) return
        val id = videoId ?: return
        audioKeyOverride = key; _audioKey.value = key
        val wasPlaying = controller.state.value.status != PlaybackStatus.PAUSED
        val position = controller.state.value.positionMs
        work?.cancel()
        work = sessionScope.launch { if (needsRefresh() || streams == null) refresh(id, position, wasPlaying) else { persist(); loadPlan(position, wasPlaying) } }
    }
    private val _caption = MutableStateFlow<com.greenaltambe.vela.extractor.CaptionTrack?>(null)
    /** The subtitle track on, or null. Kept across quality changes and re-resolves of the same video. */
    val activeCaption: StateFlow<com.greenaltambe.vela.extractor.CaptionTrack?> = _caption
    /** Turns a subtitle track on (null: off). The player screen fetches and draws it; the choice survives quality changes of the same video. */
    fun selectCaption(track: com.greenaltambe.vela.extractor.CaptionTrack?) { _caption.value = track }
    private var localPlan: PlaybackPlan.Local? = null
    private var maxHeightOverride: Int? = null
    private var speedOverride: Float? = null
    private val _overrides = MutableStateFlow(PlaybackOverrides())
    /** The viewer's own picks for the current video (speed from the speed menu, a quality cap from the quality menu), so they can be kept across a restart. */
    val overrides: StateFlow<PlaybackOverrides> = _overrides
    private var pendingRestore: Pair<String, PlaybackOverrides>? = null
    /** After a restart: the next start of [videoId] (from the restored mini player or its page) begins with these picks. */
    fun restoreOverrides(videoId: String, overrides: PlaybackOverrides) { pendingRestore = videoId to overrides }
    private fun publishOverrides() { _overrides.value = PlaybackOverrides(speedOverride, maxHeightOverride) }
    private var reResolves = 0
    private var networkRecoveries = 0
    /** The last position/duration the player reported while it was really playing, buffering or paused. */
    private var lastGood = PlayerState()
    private var slowFailovers = 0
    private var stallJob: Job? = null
    private val _recoveryNote = MutableStateFlow<String?>(null)
    /** Why playback was re-resolved last ("network error, retry 1/3 ..."), for Diagnostics; null when nothing happened. */
    val recoveryNote: StateFlow<String?> = _recoveryNote
    private var loadedAtMs = 0L
    private var settled = false // false from load() until the player reports it is ready; position is not trustworthy meanwhile

    // ---- history bookkeeping ----
    private var recordId: Long? = null
    private var watchedMs = 0L
    private var playingSince: kotlin.time.Instant? = null
    private var lastStatus = PlaybackStatus.IDLE

    private var work: Job? = null
    private var observers: List<Job> = emptyList()

    /**
     * Starts [videoId], replacing whatever played before. [hint] (from the search result being opened) provides the
     * title and channel without another request; without it they are looked up.
     */
    /** [restored] re-applies the picks saved before the process died (speed, quality cap); a normal start passes none. */
    fun start(videoId: String, hint: VideoSummary? = null, restored: PlaybackOverrides? = null) {
        finishCurrent()
        clearPreviousVideo()
        if (_sleepTimer.value?.mode == SleepTimerMode.EndOfVideo) setSleepTimer(null)
        this.videoId = videoId
        title = hint?.title.orEmpty()
        channelId = hint?.channelId.orEmpty()
        channelNameShown = hint?.channelName.orEmpty(); artworkUrl = hint?.thumbnailUrl
        streams = null; _captions.value = emptyList(); _caption.value = null; _audioTracks.value = emptyList(); audioKeyOverride = null; _audioKey.value = null
        localPlan = null
        maxHeightOverride = null
        speedOverride = null
        publishOverrides()
        val pick = restored ?: pendingRestore?.takeIf { it.first == videoId }?.second
        pendingRestore = null
        if (pick != null) { speedOverride = pick.speed; maxHeightOverride = pick.maxHeight; publishOverrides() }
        lastGood = PlayerState()
        reResolves = 0; networkRecoveries = 0; slowFailovers = 0
        recordId = null
        watchedMs = 0
        playingSince = null
        settled = false
        publishMedia()
        forAddons { it.onVideo(videoId, channelId, offline = false) } // before the observers, so an add-on knows the video before any state of it arrives
        ensureObservers()
        work = sessionScope.launch { resolveAndLoad(videoId, resume = true, needsInfo = hint == null) }
    }

    /**
     * Plays a downloaded file. No stream is resolved and no network is touched: the file is the whole source, so there
     * is no URL expiry and no quality menu. The title and channel come from the stored download.
     */
    fun startLocal(videoId: String, plan: PlaybackPlan.Local, title: String, channelId: String) {
        finishCurrent()
        clearPreviousVideo()
        this.videoId = videoId
        this.title = title; channelNameShown = ""; artworkUrl = null
        this.channelId = channelId
        streams = null; _captions.value = emptyList(); _caption.value = null; _audioTracks.value = emptyList(); audioKeyOverride = null; _audioKey.value = null
        localPlan = plan
        maxHeightOverride = null
        speedOverride = null
        publishOverrides()
        reResolves = 0; networkRecoveries = 0
        recordId = null
        watchedMs = 0
        playingSince = null
        settled = false
        forAddons { it.onVideo(videoId, channelId, offline = true) }
        ensureObservers()
        work = sessionScope.launch {
            val startMs = resumePosition(videoId)
            settled = false
            loadedAtMs = startMs
            controller.load(plan, startMs, true, PlaybackMetadata(title = title.ifEmpty { null }))
            controller.setSpeed(settings.resolve(Settings.defaultPlaybackSpeed, channelId.ifEmpty { null }, videoId))
            _state.value = SessionState.Ready(videoId, plan, emptyList(), null, OFFLINE_CLIENT)
        }
    }

    /**
     * The player still holds the last video (its frame, position and duration) until something new is loaded, which is
     * only after the streams are resolved. Unload it now so a new page never shows the previous video's state.
     */
    private fun clearPreviousVideo() {
        if (controller.state.value.status != PlaybackStatus.IDLE) controller.stop()
        lastStatus = PlaybackStatus.IDLE
    }

    fun play() {
        if (needsRefresh()) {
            val id = videoId ?: return
            work?.cancel()
            work = sessionScope.launch { refresh(id, controller.state.value.positionMs, playWhenReady = true) }
        } else {
            controller.play()
        }
    }

    fun pause() = controller.pause()

    /**
     * Pauses playback after [mode] (null cancels). The countdown runs on wall-clock time whether or not the video is
     * playing. [SleepTimerMode.EndOfVideo] applies to the current video only: starting another one cancels it.
     */
    fun setSleepTimer(mode: SleepTimerMode?) {
        sleepJob?.cancel()
        sleepJob = null
        when (mode) {
            null -> _sleepTimer.value = null
            SleepTimerMode.EndOfVideo -> _sleepTimer.value = SleepTimerState(mode)
            is SleepTimerMode.After -> {
                _sleepTimer.value = SleepTimerState(mode, deadline = clock.now() + mode.duration)
                sleepJob = sessionScope.launch {
                    delay(mode.duration)
                    controller.pause()
                    _sleepTimer.value = null
                }
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        reResolves = 0; networkRecoveries = 0
        controller.seekTo(positionMs)
    }

    /** Applies [speed] now and for the rest of this session, over what settings say. */
    fun goLive() = controller.goLive()

    /** A temporary speed (holding the screen or space bar): not remembered, and null puts back the viewer's pick or the setting. */
    fun holdSpeed(speed: Float?) {
        val id = videoId ?: return
        _heldSpeed.value = speed
        controller.setSpeed(speed ?: speedOverride ?: settings.resolve(Settings.defaultPlaybackSpeed, channelId.ifEmpty { null }, id))
    }

    fun setSpeed(speed: Float) {
        speedOverride = speed
        publishOverrides()
        controller.setSpeed(speed)
    }

    /** Caps the resolution at [maxHeight] (null = automatic, per the network settings) and re-plans at the current position. */
    fun selectQuality(maxHeight: Int?) {
        if (localPlan != null) return // one file, one quality
        val id = videoId ?: return
        maxHeightOverride = maxHeight
        publishOverrides()
        val wasPlaying = controller.state.value.status != PlaybackStatus.PAUSED
        val position = controller.state.value.positionMs
        work?.cancel()
        work = sessionScope.launch {
            if (needsRefresh() || streams == null) refresh(id, position, wasPlaying)
            else {
                persist()
                loadPlan(position, wasPlaying)
            }
        }
    }

    /** Ends the session: writes history, stops playback. The session can be [start]ed again. */
    fun stop() {
        forAddons { it.onStop() }
        setSleepTimer(null)
        finishCurrent()
        controller.stop()
        videoId = null
        _state.value = SessionState.Idle
        publishMedia()
    }

    /** Stops everything this session launched; call when it will not be used again. */
    fun release() {
        stop()
        observers.forEach(Job::cancel)
        observers = emptyList()
        ticker.cancel()
    }

    // ---- resolving ----

    private suspend fun resolveAndLoad(id: String, resume: Boolean, needsInfo: Boolean) {
        _state.value = SessionState.Resolving(id, refresh = false)
        val (result, info) = coroutineScope {
            val s = async { extractor.streams(id) }
            // Only when there is no hint: videoInfo is a second full request on some tiers.
            val i = if (needsInfo) async { extractor.videoInfo(id) } else null
            s.await() to i?.await()
        }
        (info as? ExtractorResult.Success)?.value?.let { title = it.title; channelId = it.channelId; channelNameShown = it.channelName; artworkUrl = it.thumbnailUrl ?: artworkUrl }
        publishMedia()
        val set = when (result) {
            is ExtractorResult.Success -> result.value
            is ExtractorResult.Failure -> return fail(id, result.error)
        }
        streams = set; _captions.value = set.captions; _audioTracks.value = set.audioChoices()
        val startMs = if (resume) resumePosition(id) else 0L
        leases.awaitAvailable(set)
        loadPlan(startMs, playWhenReady = true)
    }

    /** Re-resolves the streams of the current video and resumes at [positionMs]. */
    private suspend fun refresh(id: String, positionMs: Long, playWhenReady: Boolean, preferOther: Boolean = false) {
        persist()
        _state.value = SessionState.Resolving(id, refresh = true)
        when (val result = if (preferOther) extractor.streamsPreferringOther(id) else extractor.streams(id)) {
            is ExtractorResult.Success -> {
                streams = result.value; _captions.value = result.value.captions; _audioTracks.value = result.value.audioChoices()
                leases.awaitAvailable(result.value)
                loadPlan(positionMs, playWhenReady)
            }
            is ExtractorResult.Failure -> fail(id, result.error)
        }
    }

    private fun loadPlan(positionMs: Long, playWhenReady: Boolean) {
        val id = videoId ?: return
        val set = streams ?: return
        val device = constraints().copy(network = network.type.value)
        val base = PlaybackPrefs.from(settings, channelId.ifEmpty { null }, id)
        val prefs = base.copy(maxHeight = maxHeightOverride, audioLanguage = audioKeyOverride ?: base.audioLanguage)
        val plan = StreamSelector.plan(set, prefs, device)
        if (plan is PlaybackPlan.NothingPlayable) {
            _state.value = SessionState.Failed(id, plan.reason)
            return
        }
        settled = false
        loadedAtMs = positionMs
        controller.load(plan, positionMs, playWhenReady, PlaybackMetadata(title = title.ifEmpty { null }))
        controller.setSpeed(speedOverride ?: settings.resolve(Settings.defaultPlaybackSpeed, channelId.ifEmpty { null }, id))
        _state.value = SessionState.Ready(id, plan, StreamSelector.availableHeights(set, device), maxHeightOverride, set.client)
    }

    private fun fail(id: String, error: ExtractorError) {
        _state.value = SessionState.Failed(id, "${error.reason}: ${error.message}", extractorError = error)
    }

    private fun needsRefresh(): Boolean = leases.isExpired(streams)

    private fun resumePosition(id: String): Long {
        val last = history.lastForVideo(accountId(), id) ?: return 0
        val nearEnd = last.durationMs > 0 && last.positionMs >= last.durationMs - RESUME_END_SLACK_MS
        return if (last.positionMs < RESUME_MIN_MS || nearEnd) 0 else last.positionMs
    }

    // ---- player feedback ----

    private fun ensureObservers() {
        if (observers.any { it.isActive }) return
        observers = listOf(
            sessionScope.launch(start = CoroutineStart.UNDISPATCHED) { controller.state.collect(::onPlayerState) },
            sessionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                controller.events.collect { if (it is PlayerEvent.Error) onPlayerError(it.error) }
            },
        )
    }

    private fun onPlayerState(st: PlayerState) {
        forAddons { it.onPlayerState(st) }
        val previous = lastStatus
        lastStatus = st.status
        if (st.status != PlaybackStatus.IDLE && st.positionMs > 0) lastGood = st
        if (st.status == PlaybackStatus.BUFFERING && settled && localPlan == null) {
            if (stallJob?.isActive != true) stallJob = sessionScope.launch { delay(stallAfter); onStall() }
        } else if (st.status != PlaybackStatus.BUFFERING) { stallJob?.cancel(); stallJob = null }
        if (st.status == PlaybackStatus.PLAYING || st.status == PlaybackStatus.PAUSED || st.status == PlaybackStatus.ENDED) settled = true
        if (previous == PlaybackStatus.PLAYING && st.status != PlaybackStatus.PLAYING) {
            persist() // accrues the time played so far
            playingSince = null
        } else if (previous != PlaybackStatus.PLAYING && st.status == PlaybackStatus.PLAYING) {
            playingSince = clock.now()
        }
        if (st.status == PlaybackStatus.PLAYING && st.positionMs >= loadedAtMs + PROGRESS_MS) { reResolves = 0; networkRecoveries = 0 }
        if (st.status == PlaybackStatus.ENDED) {
            persist()
            if (_sleepTimer.value?.mode == SleepTimerMode.EndOfVideo) setSleepTimer(null)
        }
    }

    private suspend fun onStall() {
        val id = videoId ?: return
        if (controller.state.value.status != PlaybackStatus.BUFFERING || slowFailovers >= 1 || !settings.resolve(Settings.slowStreamFailover)) return
        slowFailovers++
        val position = controller.state.value.positionMs
        _recoveryNote.value = "stream buffering for ${stallAfter.inWholeSeconds}s at ${position / 1000}s; re-resolved once preferring another source"
        work?.cancel()
        refresh(id, position, playWhenReady = true, preferOther = true)
    }

    private suspend fun onPlayerError(error: PlaybackError) {
        val id = videoId ?: return
        persist()
        if (localPlan == null && error.kind == PlaybackErrorKind.Network && networkRecoveries < maxNetworkRecoveries) {
            // A handoff changes the client's IP, which also invalidates URLs bound to it, so the streams are resolved again, not just resumed.
            networkRecoveries++
            _recoveryNote.value = "network error (${error.message.take(80)}); re-resolving, try $networkRecoveries/$maxNetworkRecoveries"
            val position = controller.state.value.positionMs
            work?.cancel()
            delay(networkBackoff * networkRecoveries)
            // Offline: wait (bounded) for a connection before spending a try on the network.
            var waited = 0
            while (network.type.value == NetworkType.OFFLINE && waited < 30) { delay(1.seconds); waited++ }
            _state.value = SessionState.Resolving(id, refresh = true)
            refresh(id, position, playWhenReady = true)
            return
        }
        val urlsDead = localPlan == null && (error.kind == PlaybackErrorKind.StreamExpiredOrForbidden || needsRefresh())
        if (!urlsDead || reResolves >= maxReResolves) {
            _state.value = SessionState.Failed(id, "${error.kind}: ${error.message}", playbackError = error)
            return
        }
        reResolves++
        work?.cancel()
        val position = controller.state.value.positionMs
        refresh(id, position, playWhenReady = true)
    }

    // ---- history ----

    private fun accrue() {
        val since = playingSince ?: return
        val now = clock.now()
        val speed = controller.state.value.speed
        watchedMs += ((now - since).inWholeMilliseconds * speed).toLong()
        playingSince = now
    }

    private fun persist() {
        val id = videoId ?: return
        if (!settled) return
        accrue()
        // A player that was stopped or reset (IDLE) reports position 0; that must never overwrite the position the viewer had reached.
        val raw = controller.state.value
        val st = if (raw.status == PlaybackStatus.IDLE && raw.positionMs == 0L && lastGood.positionMs > 0) raw.copy(positionMs = lastGood.positionMs, durationMs = lastGood.durationMs) else raw
        if (recordId == null && watchedMs == 0L && st.positionMs == 0L) return
        val now = clock.now()
        val existing = recordId
        if (existing == null) {
            recordId = history.begin(accountId(), id, channelId, title, now, st.positionMs, st.durationMs, watchedMs)
        } else {
            history.update(existing, now, st.positionMs, st.durationMs, watchedMs)
        }
    }

    private fun finishCurrent() {
        persist()
        work?.cancel()
    }

    // The periodic writer lives as long as the session; it only acts while something is playing.
    private val ticker: Job = sessionScope.launch {
        while (true) {
            delay(historyInterval)
            if (controller.state.value.status == PlaybackStatus.PLAYING) persist()
        }
    }

    companion object {
        /** [SessionState.Ready.client] of a downloaded file. */
        const val OFFLINE_CLIENT = "offline"

        /** Positions before this are treated as "not started" and not resumed. */
        private const val RESUME_MIN_MS = 5_000L

        /** A video left within this of its end is treated as finished and restarts from the beginning. */
        private const val RESUME_END_SLACK_MS = 10_000L

        /** Playback this far past the (re)load position counts as the reload having worked. */
        private const val PROGRESS_MS = 3_000L
    }
}

/** One soundtrack the viewer can pick: [key] is its language code (what the stream selector matches on), [label] what the menu shows. */
data class AudioTrackChoice(val key: String, val label: String, val original: Boolean)

/** The distinct language tracks of a stream set, original first; empty unless there are at least two (a single track is no choice). */
internal fun StreamSet.audioChoices(): List<AudioTrackChoice> {
    val tracks = audioStreams.filter { !it.languageCode.isNullOrBlank() }.distinctBy { it.languageCode }
        .map { AudioTrackChoice(it.languageCode!!, it.trackName ?: it.languageCode!!, it.isOriginalTrack) }
    return if (tracks.size >= 2) tracks.sortedByDescending { it.original } else emptyList()
}
