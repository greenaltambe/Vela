package com.greenaltambe.vela.ui.player

import com.greenaltambe.vela.ui.theme.AppIcons
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.shape.CircleShape
import com.greenaltambe.vela.ui.theme.ExtraColors
import com.greenaltambe.vela.ui.theme.Spacing
import com.greenaltambe.vela.ui.theme.LocalMotion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.look.VelaMotion
import com.greenaltambe.vela.ui.look.pressable
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** What a button press should feel like. The system's own "touch feedback" setting still decides whether anything is felt. */
enum class HapticKind { TICK, CLICK, CONFIRM, HEAVY, TOGGLE_ON, TOGGLE_OFF }

/** The player's haptics in one place, so every button and gesture feels from the same small vocabulary. */
@Stable
class PlayerHaptics(private val feedback: HapticFeedback) {
    fun perform(kind: HapticKind) {
        feedback.performHapticFeedback(
            when (kind) {
                HapticKind.TICK -> HapticFeedbackType.SegmentTick
                HapticKind.CLICK -> HapticFeedbackType.ContextClick
                HapticKind.CONFIRM -> HapticFeedbackType.Confirm
                HapticKind.HEAVY -> HapticFeedbackType.LongPress
                HapticKind.TOGGLE_ON -> HapticFeedbackType.ToggleOn
                HapticKind.TOGGLE_OFF -> HapticFeedbackType.ToggleOff
            },
        )
    }
    fun tick() = perform(HapticKind.TICK)
    fun click() = perform(HapticKind.CLICK)
    fun confirm() = perform(HapticKind.CONFIRM)
    fun heavy() = perform(HapticKind.HEAVY)
    fun toggle(on: Boolean) = perform(if (on) HapticKind.TOGGLE_ON else HapticKind.TOGGLE_OFF)
}

@Composable
fun rememberPlayerHaptics(): PlayerHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { PlayerHaptics(feedback) }
}

/**
 * A round player button with feedback you can see and feel: it squeezes on press and springs back past its size, a soft halo blooms behind
 * the icon, [pressShift] nudges the icon sideways (previous / next lean towards where they go), and a haptic fires the moment the finger lands.
 * With Reduce motion or Performance mode only the halo (and the haptic) remain.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    haptic: HapticKind = HapticKind.CLICK,
    pressShift: Dp = 0.dp,
    halo: Color = ExtraColors.onMedia,
    /** A filled disc behind the icon (the player's buttons wear a translucent scrim so they read over any picture); it deepens while pressed. */
    background: Color = Color.Transparent,
    /** A press-and-hold action (a second, heavier gesture; the buzz is stronger when it fires). */
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = rememberPlayerHaptics()
    // ONE implementation for every round player button: Material 3 Expressive's own icon button (it morphs from a circle to a rounded square while
    // pressed), on the scrim disc when there is one. A press-and-hold action is added on top of the same button, so a button with one (captions)
    // looks exactly like the others: same disc, same glyph size, same press shape.
    val longFired = remember { androidx.compose.runtime.mutableStateOf(false) }
    val longTimeout = androidx.compose.ui.platform.LocalViewConfiguration.current.longPressTimeoutMillis
    val latestLong by androidx.compose.runtime.rememberUpdatedState(onLongClick)
    LaunchedEffect(pressed) {
        if (pressed && enabled) {
            longFired.value = false
            haptics.perform(haptic)
            if (latestLong != null) { delay(longTimeout); longFired.value = true; haptics.perform(HapticKind.HEAVY); latestLong?.invoke() }
        }
    }
    // The release that ends a long press must not also count as a click.
    val click: () -> Unit = { if (longFired.value) { longFired.value = false } else { onClick() } }
    val body: @Composable () -> Unit = { Box(Modifier.graphicsLayer { translationX = if (pressed) pressShift.toPx() else 0f }, contentAlignment = Alignment.Center) { content() } }
    if (background != Color.Transparent) androidx.compose.material3.FilledIconButton(
        onClick = click, modifier = modifier.size(size), enabled = enabled, interactionSource = source,
        shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = background, contentColor = ExtraColors.onMedia, disabledContainerColor = background.copy(alpha = 0.12f)),
        content = body,
    ) else androidx.compose.material3.IconButton(
        onClick = click, modifier = modifier.size(size), enabled = enabled, interactionSource = source,
        shapes = androidx.compose.material3.IconButtonDefaults.shapes(), content = body,
    )
}

/** The sizes of the player's round buttons, in one place. Every utility button (back, captions, mute, quality, ...) is the same. */
object PlayerButtonSizes {
    val utility = 48.dp
    /** Previous / next, and play / pause, in the small player and in fullscreen. */
    val transportCompact = 48.dp
    val playCompact = 56.dp
    val transportFullscreen = 56.dp
    val playFullscreen = 64.dp
    /** The fullscreen toggle in the top-right of the small player's time row only. */
    val compactFullscreenToggle = 40.dp
}

/** An icon that swaps with a short pop (play becomes pause, mute becomes volume) instead of cutting. */
@Composable
fun SwapIcon(icon: ImageVector, description: String?, tint: Color, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val color by animateColorAsState(tint, VelaMotion.tween(motion, VelaMotion.Level.QUICK), label = "swapIconTint")
    AnimatedContent(
        targetState = icon, modifier = modifier,
        transitionSpec = {
            (fadeIn(VelaMotion.tween(motion, VelaMotion.Level.QUICK)) + scaleIn(VelaMotion.bouncy(motion), initialScale = 0.45f)) togetherWith
                (fadeOut(VelaMotion.tween(motion, VelaMotion.Level.PRESS)) + scaleOut(VelaMotion.tween(motion, VelaMotion.Level.PRESS), targetScale = 0.45f))
        },
        label = "swapIcon",
    ) { Icon(it, description, tint = color) }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------
// Menus as modal bottom sheets
// ---------------------------------------------------------------------------------------------------------------------------------------------

/** The shell every player menu (quality, speed, captions, audio, sleep timer) opens in: a modal bottom sheet with a title; the rows slide in one after another. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenuSheet(title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    com.greenaltambe.vela.ui.components.VelaSheet(onDismiss, modifier.testTag("player.menu.sheet")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Column(Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            content()
        }
    }
}

/** One row of a menu: slides and fades in by [index], tints and shows a check that pops when it is the chosen one, and gives a haptic on tap. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    index: Int,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: ImageVector? = null,
    enabled: Boolean = true,
    /** Something at the row's end instead of the check mark (a switch). */
    trailing: (@Composable () -> Unit)? = null,
    /** How many lines the supporting text may take (a switch row explains itself, so it gets room). */
    supportingLines: Int = 1,
) {
    val motion = LocalMotion.current
    val haptics = rememberPlayerHaptics()
    val enter = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!motion.reduced) { delay(VelaMotion.staggerDelayMs(motion, index).toLong()); enter.animateTo(1f, VelaMotion.tween(motion, VelaMotion.Level.STANDARD)) }
    }
    // Material's selectable list item: the chosen row fills with the primary container; entrance stagger and haptic stay.
    val shape = MaterialTheme.shapes.large
    androidx.compose.material3.SegmentedListItem(
        selected = selected,
        onClick = { haptics.perform(if (selected) HapticKind.TICK else HapticKind.CONFIRM); onClick() },
        shapes = androidx.compose.material3.ListItemShapes(shape, shape, shape, shape, shape, shape),
        enabled = enabled,
        colors = androidx.compose.material3.ListItemDefaults.segmentedColors(
            containerColor = Color.Transparent, selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedSupportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        leadingContent = leading?.let { icon -> { Icon(icon, null, Modifier.size(24.dp)) } },
        supportingContent = supporting?.let { sup -> { Text(sup, maxLines = supportingLines, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            if (trailing != null) trailing()
            else AnimatedVisibility(selected, enter = scaleIn(VelaMotion.bouncy(motion), initialScale = 0.3f) + fadeIn(), exit = scaleOut() + fadeOut()) {
                Icon(AppIcons.check, "Selected", Modifier.size(22.dp))
            }
        },
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.sm)
            .graphicsLayer { alpha = enter.value * (if (enabled) 1f else 0.5f); translationY = (1f - enter.value) * 18.dp.toPx() }
            .testTag("player.menu.row"),
    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------
// Speed
// ---------------------------------------------------------------------------------------------------------------------------------------------

/** The speeds offered as one-tap chips; the slider below them reaches everything in between. */
val SpeedChoices = listOf(0.25f, 0.5f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 3.5f, 4f)
const val SPEED_MIN = 0.25f
const val SPEED_MAX = 4f
private const val SPEED_STEP = 0.05f

/** "1×", "1.25×", "0.9×": no trailing zeros. */
fun formatSpeed(speed: Float): String {
    val hundredths = (speed * 100).roundToInt()
    val whole = hundredths / 100
    val frac = hundredths % 100
    return when {
        frac == 0 -> "$whole×"
        frac % 10 == 0 -> "$whole.${frac / 10}×"
        else -> "$whole.${frac.toString().padStart(2, '0')}×"
    }
}

private fun snapSpeed(raw: Float): Float = ((raw / SPEED_STEP).roundToInt() * SPEED_STEP).coerceIn(SPEED_MIN, SPEED_MAX).let { (it * 100).roundToInt() / 100f }

/**
 * The speed menu's body: a large readout (with what it means), a slider with - / + for fine steps (0.05), and a tidy grid of the common speeds
 * up to 4x. Everything lines up on the same columns, whatever the label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedMenuContent(current: Float, onPick: (Float) -> Unit) {
    val motion = LocalMotion.current
    val haptics = rememberPlayerHaptics()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: current
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val sliderColors = SliderDefaults.colors(thumbColor = primary, activeTrackColor = primary, inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val caption = when {
        kotlin.math.abs(shown - 1f) < 0.005f -> "Normal speed"
        shown < 1f -> "Slower than normal"
        else -> "Faster than normal"
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(Modifier.fillMaxWidth().testTag("player.speed.readout"), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = formatSpeed(shown),
                transitionSpec = { (slideInVertically(VelaMotion.snappy(motion)) { it / 3 } + fadeIn()) togetherWith (slideOutVertically(VelaMotion.snappy(motion)) { -it / 3 } + fadeOut()) },
                label = "speedReadout",
            ) { text -> Text(text, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = primary, textAlign = TextAlign.Center) }
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StepButton("−", enabled = shown > SPEED_MIN + 0.001f, tag = "player.speed.slower") { haptics.tick(); onPick(snapSpeed(shown - SPEED_STEP)) }
            Slider(
                value = shown,
                onValueChange = { raw ->
                    val snapped = snapSpeed(raw)
                    if (snapped != shown) { haptics.tick(); onPick(snapped) }
                    dragging = snapped
                },
                onValueChangeFinished = { dragging = null; haptics.confirm() },
                valueRange = SPEED_MIN..SPEED_MAX, modifier = Modifier.weight(1f).testTag("player.speed.slider"), colors = sliderColors,
                thumb = { Box(Modifier.size(28.dp).clip(CircleShape).background(primary)) },
                track = { state -> SliderDefaults.Track(sliderState = state, modifier = Modifier.height(8.dp), colors = sliderColors, drawStopIndicator = null, thumbTrackGapSize = 0.dp) },
            )
            StepButton("+", enabled = shown < SPEED_MAX - 0.001f, tag = "player.speed.faster") { haptics.tick(); onPick(snapSpeed(shown + SPEED_STEP)) }
        }

        ChoiceChipGrid(
            options = SpeedChoices, isSelected = { kotlin.math.abs(shown - it) < 0.005f }, label = ::formatSpeed, onSelect = onPick,
            columns = 4, tag = { "player.speed.chip" },
        )
    }
}

/** A round - / + button for the speed slider. */
@Composable
private fun StepButton(label: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    PlayerIconButton(
        onClick, enabled = enabled, size = 44.dp, haptic = HapticKind.TICK, halo = MaterialTheme.colorScheme.primary,
        background = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.testTag(tag),
    ) { Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) }
}

/** Options as a grid of equal chips, [columns] to a row, so labels of any length line up. One is chosen; they pop in one after another. */
@Composable
fun <T> ChoiceChipGrid(
    options: List<T>, isSelected: (T) -> Boolean, label: (T) -> String, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, columns: Int = 4, tag: (T) -> String = { "" },
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.chunked(columns).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEachIndexed { c, option ->
                    ChoiceChip(label(option), isSelected(option), r * columns + c, Modifier.weight(1f).testTag(tag(option))) { onSelect(option) }
                }
                repeat(columns - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val motion = LocalMotion.current
    val haptics = rememberPlayerHaptics()
    val enter = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!motion.reduced) { delay(VelaMotion.staggerDelayMs(motion, index / 2).toLong()); enter.animateTo(1f, VelaMotion.bouncy(motion)) } }
    // The app's expressive toggle button (it morphs between round and square, shows a check), arriving with the sheet's stagger.
    Box(modifier.graphicsLayer { val sc = 0.6f + 0.4f * enter.value; scaleX = sc; scaleY = sc; alpha = enter.value.coerceIn(0f, 1f) }, contentAlignment = Alignment.Center) {
        com.greenaltambe.vela.ui.components.ExpressiveChip(selected, text, { haptics.perform(HapticKind.CONFIRM); onClick() }, Modifier.fillMaxWidth())
    }
}

/** Slides up and fades in, [index] places after the first, so the parts of a sheet arrive one by one. Instant with Reduce motion. */
@Composable
fun Modifier.reveal(index: Int): Modifier {
    val motion = LocalMotion.current
    val enter = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!motion.reduced) { delay(VelaMotion.staggerDelayMs(motion, index).toLong()); enter.animateTo(1f, VelaMotion.tween(motion, VelaMotion.Level.STANDARD)) }
    }
    return this.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 24.dp.toPx() }
}

/** The player's own button: a [PlayerIconButton] on a translucent scrim disc, so it reads over any picture and still answers touch. */
@Composable
fun ControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = PlayerButtonSizes.utility,
    haptic: HapticKind = HapticKind.CLICK,
    pressShift: Dp = 0.dp,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = PlayerIconButton(onClick, modifier, enabled, size, haptic, pressShift, background = ExtraColors.mediaScrim.copy(alpha = 0.30f), onLongClick = onLongClick, content = content)

/** A small caption between groups of rows in a menu sheet ("Send", "Copy"). */
@Composable
fun PlayerMenuSection(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.sm), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
