package com.greenaltambe.vela.ui.player

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
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.VelaMotion
import com.greenaltambe.vela.ui.look.pressable
import com.greenaltambe.vela.ui.look.toColor
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
@Composable
fun PlayerIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    haptic: HapticKind = HapticKind.CLICK,
    pressShift: Dp = 0.dp,
    halo: Color = Vela.colors.onMedia.toColor(),
    /** A filled disc behind the icon (the player's buttons wear a translucent scrim so they read over any picture); it deepens while pressed. */
    background: Color = Color.Transparent,
    /** A press-and-hold action (a second, heavier gesture; the buzz is stronger when it fires). */
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = Vela.motion
    val haptics = rememberPlayerHaptics()
    val squeeze = pressed && !motion.reduced
    val scale by animateFloatAsState(if (squeeze) 0.82f else 1f, VelaMotion.bouncy(motion), label = "playerButtonScale")
    val shift by animateDpAsState(if (squeeze) pressShift else 0.dp, VelaMotion.snappy(motion), label = "playerButtonShift")
    val glow by animateFloatAsState(if (pressed) 0.28f else 0f, VelaMotion.tween(motion, VelaMotion.Level.QUICK), label = "playerButtonGlow")
    LaunchedEffect(pressed) { if (pressed && enabled) haptics.perform(haptic) }
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
            .drawBehind {
                if (background != Color.Transparent) drawCircle(background.copy(alpha = (background.alpha + 0.22f * glow / 0.28f).coerceAtMost(1f)), radius = this.size.minDimension / 2f * 0.88f)
                if (glow > 0f) drawCircle(halo.copy(alpha = glow))
            }
            .combinedClickable(
                interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick,
                onLongClick = onLongClick?.let { l -> { haptics.perform(HapticKind.HEAVY); l() } },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // On a disc the glyph is drawn smaller than the button so it has room around it inside the disc.
        val glyph = if (background != Color.Transparent) Modifier.size(size * 0.42f) else Modifier
        Box(glyph.graphicsLayer { translationX = shift.toPx() }, contentAlignment = Alignment.Center) { content() }
    }
}

/** An icon that swaps with a short pop (play becomes pause, mute becomes volume) instead of cutting. */
@Composable
fun SwapIcon(icon: ImageVector, description: String?, tint: Color, modifier: Modifier = Modifier) {
    val motion = Vela.motion
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Opens at its full height: a half-height first stop hid the bottom of long menus (the Download button) until dragged.
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = Vela.look.radii.largeDp.dp, topEnd = Vela.look.radii.largeDp.dp),
        containerColor = Vela.colors.surface.toColor(),
        contentColor = Vela.colors.onSurface.toColor(),
        modifier = modifier.testTag("player.menu.sheet"),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = Vela.lg), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
            Column(Modifier.padding(horizontal = Vela.gutter).padding(bottom = Vela.sm), verticalArrangement = Arrangement.spacedBy(Vela.xs)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            content()
        }
    }
}

/** One row of a menu: slides and fades in by [index], tints and shows a check that pops when it is the chosen one, and gives a haptic on tap. */
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
) {
    val motion = Vela.motion
    val haptics = rememberPlayerHaptics()
    val enter = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!motion.reduced) { delay(VelaMotion.staggerDelayMs(motion, index).toLong()); enter.animateTo(1f, VelaMotion.tween(motion, VelaMotion.Level.STANDARD)) }
    }
    val tint by animateColorAsState(
        if (selected) Vela.colors.primary.toColor().copy(alpha = 0.14f) else Color.Transparent,
        VelaMotion.tween(motion, VelaMotion.Level.QUICK), label = "menuRowTint",
    )
    val accent = if (selected) Vela.colors.primary.toColor() else Vela.colors.onSurface.toColor()
    Row(
        modifier.fillMaxWidth().padding(horizontal = Vela.sm)
            .graphicsLayer { alpha = enter.value * (if (enabled) 1f else 0.5f); translationY = (1f - enter.value) * 18.dp.toPx() }
            .clip(Vela.mediumShape).background(tint)
            .pressable(enabled = enabled, role = Role.RadioButton) { haptics.perform(if (selected) HapticKind.TICK else HapticKind.CONFIRM); onClick() }
            .heightIn(min = if (supporting != null) 64.dp else 56.dp).padding(horizontal = Vela.md, vertical = Vela.sm)
            .testTag("player.menu.row"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.md),
    ) {
        if (leading != null) Box(
            Modifier.size(40.dp).clip(Vela.pillShape).background(if (selected) Vela.colors.primary.toColor().copy(alpha = 0.18f) else Vela.colors.surfaceVariant.toColor()),
            contentAlignment = Alignment.Center,
        ) { Icon(leading, null, Modifier.size(22.dp), tint = accent) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = Vela.colors.onSurfaceVariant.toColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AnimatedVisibility(selected, enter = scaleIn(VelaMotion.bouncy(motion), initialScale = 0.3f) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(Vela.icons.check, "Selected", Modifier.size(22.dp), tint = Vela.colors.primary.toColor())
        }
    }
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
    val motion = Vela.motion
    val haptics = rememberPlayerHaptics()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: current
    val primary = Vela.colors.primary.toColor()
    val onSurface = Vela.colors.onSurface.toColor()
    val sliderColors = SliderDefaults.colors(thumbColor = primary, activeTrackColor = primary, inactiveTrackColor = Vela.colors.surfaceVariant.toColor())
    val caption = when {
        kotlin.math.abs(shown - 1f) < 0.005f -> "Normal speed"
        shown < 1f -> "Slower than normal"
        else -> "Faster than normal"
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = Vela.gutter), verticalArrangement = Arrangement.spacedBy(Vela.md)) {
        Column(Modifier.fillMaxWidth().testTag("player.speed.readout"), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = formatSpeed(shown),
                transitionSpec = { (slideInVertically(VelaMotion.snappy(motion)) { it / 3 } + fadeIn()) togetherWith (slideOutVertically(VelaMotion.snappy(motion)) { -it / 3 } + fadeOut()) },
                label = "speedReadout",
            ) { text -> Text(text, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = primary, textAlign = TextAlign.Center) }
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = Vela.colors.onSurfaceVariant.toColor())
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
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
                thumb = { Box(Modifier.size(28.dp).clip(Vela.pillShape).background(primary)) },
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
        onClick, enabled = enabled, size = 44.dp, haptic = HapticKind.TICK, halo = Vela.colors.primary.toColor(),
        background = Vela.colors.surfaceVariant.toColor(), modifier = Modifier.testTag(tag),
    ) { Text(label, style = MaterialTheme.typography.titleLarge, color = Vela.colors.onSurface.toColor()) }
}

/** Options as a grid of equal chips, [columns] to a row, so labels of any length line up. One is chosen; they pop in one after another. */
@Composable
fun <T> ChoiceChipGrid(
    options: List<T>, isSelected: (T) -> Boolean, label: (T) -> String, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, columns: Int = 4, tag: (T) -> String = { "" },
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Vela.sm)) {
        options.chunked(columns).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Vela.sm)) {
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
    val motion = Vela.motion
    val haptics = rememberPlayerHaptics()
    val enter = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!motion.reduced) { delay(VelaMotion.staggerDelayMs(motion, index / 2).toLong()); enter.animateTo(1f, VelaMotion.bouncy(motion)) } }
    val bg by animateColorAsState(if (selected) Vela.colors.primary.toColor() else Vela.colors.surfaceVariant.toColor(), VelaMotion.tween(motion, VelaMotion.Level.QUICK), label = "chipBg")
    val fg by animateColorAsState(if (selected) Vela.colors.onPrimary.toColor() else Vela.colors.onSurface.toColor(), VelaMotion.tween(motion, VelaMotion.Level.QUICK), label = "chipFg")
    Box(
        modifier
            .graphicsLayer { val sc = 0.6f + 0.4f * enter.value; scaleX = sc; scaleY = sc; alpha = enter.value.coerceIn(0f, 1f) }
            .clip(Vela.mediumShape).background(bg)
            .pressable(role = Role.RadioButton) { haptics.perform(HapticKind.CONFIRM); onClick() }
            .heightIn(min = Vela.minTouch),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1) }
}

/** Slides up and fades in, [index] places after the first, so the parts of a sheet arrive one by one. Instant with Reduce motion. */
@Composable
fun Modifier.reveal(index: Int): Modifier {
    val motion = Vela.motion
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
    size: Dp = 48.dp,
    haptic: HapticKind = HapticKind.CLICK,
    pressShift: Dp = 0.dp,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = PlayerIconButton(onClick, modifier, enabled, size, haptic, pressShift, background = Vela.colors.mediaScrim.toColor().copy(alpha = 0.42f), onLongClick = onLongClick, content = content)

/** A small caption between groups of rows in a menu sheet ("Send", "Copy"). */
@Composable
fun PlayerMenuSection(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.padding(horizontal = Vela.gutter).padding(top = Vela.sm), style = MaterialTheme.typography.labelLarge, color = Vela.colors.onSurfaceVariant.toColor())
}
