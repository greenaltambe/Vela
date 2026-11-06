package com.greenaltambe.vela.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.extractor.CaptionTrack
import com.greenaltambe.vela.extractor.Chapter
import com.greenaltambe.vela.extractor.VideoInfo
import com.greenaltambe.vela.library.Cue
import com.greenaltambe.vela.library.Vtt
import com.greenaltambe.vela.library.currentChapter
import com.greenaltambe.vela.library.formatTimestamp
import com.greenaltambe.vela.ui.components.Avatar
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.VelaMotion
import com.greenaltambe.vela.ui.look.pressable
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.platform.LocalTextFetcher
import com.greenaltambe.vela.ui.player.HapticKind
import com.greenaltambe.vela.ui.player.PlayerIconButton
import com.greenaltambe.vela.ui.player.SwapIcon
import com.greenaltambe.vela.ui.player.rememberPlayerHaptics
import com.greenaltambe.vela.ui.player.reveal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.greenaltambe.vela.ui.player.playerVerticalSwipe
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.time.Instant

private enum class DescriptionPage { MAIN, TRANSCRIPT }

/**
 * Everything about the video in one panel under the picture (never over it, like the comments), opened from its title: numbers (likes, views, published), the description with working links and
 * timestamps, the chapters, a transcript to follow along with, the channel, and the uploader's tags. The parts arrive one after another.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DescriptionPanel(
    title: String,
    info: VideoInfo?,
    viewCount: Long?,
    likeCount: Long?,
    uploadedAt: Instant?,
    channelName: String,
    channelAvatarUrl: String?,
    chapters: List<Chapter>,
    captions: List<CaptionTrack>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onOpenChapters: () -> Unit,
    onOpenChannel: () -> Unit,
    onSearchTag: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = Vela.motion
    val haptics = rememberPlayerHaptics()
    var page by remember { mutableStateOf(DescriptionPage.MAIN) }
    val description = info?.description.orEmpty()
    val tags = info?.tags.orEmpty()
    val onSurface = Vela.colors.onSurface.toColor()
    val muted = Vela.colors.onSurfaceVariant.toColor()
    val card = Vela.colors.primary.toColor().copy(alpha = 0.10f)

    // Pulling the handle down follows the finger and, past a short distance or a flick, closes the panel; otherwise it springs back.
    var drag by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val dismissAt = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    // The handle and the header row (title, copy, close) are all grab points: dragging any of them down follows the finger and closes the panel past a short distance.
    val dismissSwipe = Modifier.playerVerticalSwipe(
        onDelta = { dy -> drag = (drag + dy).coerceAtLeast(0f) },
        onEnd = { _, v -> if (drag > dismissAt || v > 900f) onDismiss() else scope.launch { androidx.compose.animation.core.animate(drag, 0f) { x, _ -> drag = x } } },
    )
    Column(
        modifier.graphicsLayer { translationY = drag }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = Vela.look.radii.largeDp.dp, topEnd = Vela.look.radii.largeDp.dp))
            .background(Vela.colors.surface.toColor()).testTag("video.descriptionSheet"),
    ) {
        Box(
            Modifier.fillMaxWidth().then(dismissSwipe).padding(vertical = Vela.xs),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.width(36.dp).height(4.dp).clip(Vela.pillShape).background(Vela.colors.outline.toColor())) }
        AnimatedContent(
            targetState = page, modifier = Modifier.fillMaxWidth().weight(1f),
            transitionSpec = {
                val towards = if (targetState == DescriptionPage.TRANSCRIPT) 1 else -1
                (slideInHorizontally(VelaMotion.standard(motion)) { towards * it / 3 } + fadeIn()) togetherWith (slideOutHorizontally(VelaMotion.standard(motion)) { -towards * it / 3 } + fadeOut())
            },
            label = "descriptionPage",
        ) { current ->
            when (current) {
                DescriptionPage.MAIN -> Column(Modifier.fillMaxSize()) {
                    // Header: title, copy, close.
                    Row(Modifier.fillMaxWidth().then(dismissSwipe).padding(start = Vela.gutter, end = Vela.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text("Description", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (description.isNotBlank()) CopyButton(description)
                        PlayerIconButton(onDismiss, halo = onSurface, modifier = Modifier.testTag("video.descriptionClose")) { Icon(Vela.icons.close, "Close", tint = onSurface) }
                    }
                    Box(Modifier.fillMaxWidth().size(1.dp).background(Vela.colors.outline.toColor().copy(alpha = 0.5f)))
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Vela.gutter, vertical = Vela.md),
                        verticalArrangement = Arrangement.spacedBy(Vela.md),
                    ) {
                        var n = 0
                        Text(title, Modifier.reveal(n++), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

                        // Numbers.
                        val stats = listOfNotNull(
                            likeCount?.let { com.greenaltambe.vela.modules.ryd.compact(it) to "Likes" },
                            viewCount?.let { groupedCount(it) to "Views" },
                            uploadedAt?.let { publishedParts(it) },
                        )
                        if (stats.isNotEmpty()) Row(Modifier.fillMaxWidth().reveal(n++), horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
                            stats.forEach { (value, label) ->
                                Column(Modifier.weight(1f).clip(Vela.largeShape).background(card).padding(vertical = Vela.md, horizontal = Vela.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                    Text(label, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
                                }
                            }
                        }

                        // The description, with links and timestamps that work; long ones fold.
                        if (description.isNotBlank()) {
                            var expanded by remember { mutableStateOf(false) }
                            val linkColor = Vela.colors.primary.toColor()
                            val text = remember(description, linkColor) { linkedText(description, linkColor) { seconds -> onSeek(seconds * 1000); onDismiss() } }
                            Column(Modifier.fillMaxWidth().reveal(n++).clip(Vela.largeShape).background(card).animateContentSize(VelaMotion.standard(motion)).padding(Vela.md), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                                Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = if (expanded) Int.MAX_VALUE else 8, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("video.description"))
                                if (description.count { it == '\n' } > 6 || description.length > 380) {
                                    Box(
                                        Modifier.fillMaxWidth().clip(Vela.pillShape).pressable(role = Role.Button) { haptics.click(); expanded = !expanded }.heightIn(min = Vela.minTouch)
                                            .testTag("video.descriptionToggle"),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(if (expanded) "See less" else "See more", style = MaterialTheme.typography.labelLarge, color = Vela.colors.primary.toColor()) }
                                }
                            }
                        }

                        if (chapters.isNotEmpty()) {
                            val now = currentChapter(chapters, positionMs)
                            NavCard("Chapters", "${chapters.size} chapter${if (chapters.size == 1) "" else "s"}${now?.let { " · now: ${it.title}" }.orEmpty()}", card, Modifier.reveal(n++).testTag("video.chaptersCard")) { haptics.click(); onOpenChapters(); onDismiss() }
                        }
                        if (captions.isNotEmpty()) {
                            NavCard("Transcript", "Follow along using the transcript.", card, Modifier.reveal(n++).testTag("video.transcriptCard")) { haptics.click(); page = DescriptionPage.TRANSCRIPT }
                        }

                        // The channel.
                        Row(
                            Modifier.fillMaxWidth().reveal(n++).clip(Vela.largeShape).background(card).pressable(role = Role.Button) { haptics.click(); onOpenChannel(); onDismiss() }.padding(Vela.md).testTag("video.channelCard"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md),
                        ) {
                            Avatar(channelName, url = channelAvatarUrl, size = 48.dp)
                            Column(Modifier.weight(1f)) {
                                Text(channelName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Open channel", style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
                            }
                            Icon(Vela.icons.chevronRight, null, tint = muted)
                        }

                        if (tags.isNotEmpty()) Column(Modifier.fillMaxWidth().reveal(n++), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                            Text("Tags", style = MaterialTheme.typography.titleSmall, color = muted)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Vela.sm), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
                                tags.forEach { tag ->
                                    Text(
                                        tag, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Vela.colors.primary.toColor(),
                                        modifier = Modifier.clip(Vela.pillShape).background(card).pressable(role = Role.Button) { haptics.click(); onSearchTag(tag); onDismiss() }
                                            .heightIn(min = Vela.minTouch).padding(horizontal = Vela.md, vertical = Vela.sm).testTag("video.tag"),
                                    )
                                }
                            }
                        }
                    }
                }
                DescriptionPage.TRANSCRIPT -> TranscriptPage(captions, positionMs, onSeek = { onSeek(it) }, onBack = { page = DescriptionPage.MAIN }, onClose = onDismiss, swipe = dismissSwipe)
            }
        }
    }
}

@Composable
private fun CopyButton(text: String) {
    val clipboard = LocalClipboardManager.current
    val haptics = rememberPlayerHaptics()
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) { delay(1_400); done = false } }
    PlayerIconButton({ clipboard.setText(AnnotatedString(text)); haptics.confirm(); done = true }, halo = Vela.colors.onSurface.toColor(), modifier = Modifier.testTag("video.descriptionCopy")) {
        SwapIcon(if (done) Vela.icons.check else Vela.icons.link, if (done) "Copied" else "Copy description", if (done) Vela.colors.primary.toColor() else Vela.colors.onSurface.toColor())
    }
}

@Composable
private fun NavCard(title: String, subtitle: String, background: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(Vela.largeShape).background(background).pressable(role = Role.Button, onClick = onClick).padding(Vela.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Vela.colors.primary.toColor())
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Vela.colors.onSurfaceVariant.toColor(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Vela.icons.chevronRight, null, tint = Vela.colors.primary.toColor())
    }
}

/** The captions as a list of lines with their times: the line being spoken is marked and followed, a tap jumps there. */
@Composable
private fun TranscriptPage(captions: List<CaptionTrack>, positionMs: Long, onSeek: (Long) -> Unit, onBack: () -> Unit, onClose: () -> Unit, swipe: Modifier) {
    val fetch = LocalTextFetcher.current
    val haptics = rememberPlayerHaptics()
    // English first (hand-made before auto-generated), then any hand-made track, then whatever there is.
    var chosen by remember {
        mutableStateOf(
            captions.firstOrNull { it.languageCode.startsWith("en") && !it.autoGenerated } ?: captions.firstOrNull { it.languageCode.startsWith("en") }
                ?: captions.firstOrNull { !it.autoGenerated } ?: captions.first(),
        )
    }
    // null while loading; empty when the track could not be read.
    val cues by produceState<List<Cue>?>(null, chosen, fetch) {
        value = null
        // Auto-generated captions repeat each line as the next one scrolls in: keep the first of any run of identical lines.
        value = if (fetch == null) emptyList() else fetch(chosen.url)?.let { Vtt.parse(it) }.orEmpty().let(::dedupeCues)
    }
    val listState = rememberLazyListState()
    val current = cues?.indexOfLast { it.startMs <= positionMs }?.takeIf { it >= 0 }
    LaunchedEffect(current) { if (current != null) runCatching { listState.animateScrollToItem((current - 2).coerceAtLeast(0)) } }
    val muted = Vela.colors.onSurfaceVariant.toColor()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().then(swipe).padding(start = Vela.sm, end = Vela.sm), verticalAlignment = Alignment.CenterVertically) {
            PlayerIconButton(onBack, halo = Vela.colors.onSurface.toColor(), modifier = Modifier.testTag("video.transcriptBack")) { Icon(Vela.icons.back, "Back", tint = Vela.colors.onSurface.toColor()) }
            Text("Transcript", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            PlayerIconButton(onClose, halo = Vela.colors.onSurface.toColor()) { Icon(Vela.icons.close, "Close", tint = Vela.colors.onSurface.toColor()) }
        }
        if (captions.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Vela.gutter, vertical = Vela.xs), horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
            captions.forEach { c ->
                val on = c == chosen
                Text(
                    c.label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    color = if (on) Vela.colors.onPrimary.toColor() else Vela.colors.onSurface.toColor(),
                    modifier = Modifier.clip(Vela.pillShape).background(if (on) Vela.colors.primary.toColor() else Vela.colors.surfaceVariant.toColor())
                        .pressable(role = Role.RadioButton) { haptics.confirm(); chosen = c }.heightIn(min = Vela.minTouch).padding(horizontal = Vela.md).wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
        when {
            cues == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(color = Vela.colors.primary.toColor()) }
            cues!!.isEmpty() -> Box(Modifier.fillMaxSize().padding(Vela.gutter), contentAlignment = Alignment.Center) { Text("This transcript could not be loaded.", color = muted, textAlign = TextAlign.Center) }
            else -> LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Vela.sm, vertical = Vela.sm)) {
                itemsIndexed(cues!!, key = { i, c -> "${c.startMs}-$i" }) { i, cue ->
                    val now = i == current
                    Row(
                        Modifier.fillMaxWidth().clip(Vela.mediumShape).background(if (now) Vela.colors.primary.toColor().copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { haptics.click(); onSeek(cue.startMs) }.padding(horizontal = Vela.md, vertical = Vela.sm).testTag("video.transcriptLine"),
                        horizontalArrangement = Arrangement.spacedBy(Vela.md),
                    ) {
                        Text(formatTimestamp(cue.startMs / 1000), style = MaterialTheme.typography.labelMedium, color = Vela.colors.primary.toColor(), modifier = Modifier.padding(top = 2.dp))
                        Text(cue.text, style = MaterialTheme.typography.bodyLarge, fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Oct 30" over "2026": the two lines of the published card (UTC date). */
fun publishedParts(at: Instant): Pair<String, String> {
    // Days since 1970-01-01 to a civil date (Howard Hinnant's algorithm), so no date library is needed.
    val z = at.epochSeconds.floorDiv(86_400L) + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0
    return "${MONTHS[(month - 1).toInt()]} $day" to year.toString()
}

/** 340925 -> "340,925". */
fun groupedCount(n: Long): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/** Auto-generated captions repeat each line as the next one scrolls in: keeps the first of any run of identical lines. */
fun dedupeCues(cues: List<Cue>): List<Cue> = cues.filterIndexed { i, c -> i == 0 || c.text.trim() != cues[i - 1].text.trim() }
