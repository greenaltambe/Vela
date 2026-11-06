package com.greenaltambe.vela.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.VelaMotion
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.player.HapticKind
import com.greenaltambe.vela.ui.player.rememberPlayerHaptics

/**
 * Subscribe / Subscribed as one button that changes state in place: the palette gradient fades out into a quiet tonal fill, the icon and label
 * swap with a pop, it squeezes on press and gives a haptic (a firm one when you subscribe, a soft one when you leave).
 */
@Composable
fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = Vela.motion
    val haptics = rememberPlayerHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !motion.reduced) 0.93f else 1f, VelaMotion.bouncy(motion), label = "subscribeScale")
    val gradient by animateFloatAsState(if (subscribed) 0f else 1f, VelaMotion.tween(motion, VelaMotion.Level.STANDARD), label = "subscribeGradient")
    val tonal by animateColorAsState(if (subscribed) Vela.colors.surfaceVariant.toColor() else Color.Transparent, VelaMotion.tween(motion, VelaMotion.Level.STANDARD), label = "subscribeTonal")
    val label by animateColorAsState(if (subscribed) Vela.colors.onSurface.toColor() else Vela.onButton, VelaMotion.tween(motion, VelaMotion.Level.QUICK), label = "subscribeLabel")
    val fill = Vela.buttonFill
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Vela.pillShape)
            .background(tonal)
            .drawBehind { if (gradient > 0.01f) drawRect(fill, alpha = gradient) }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptics.perform(if (subscribed) HapticKind.TOGGLE_OFF else HapticKind.CONFIRM)
                onClick()
            }
            .heightIn(min = 44.dp).padding(horizontal = Vela.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.xs, Alignment.CenterHorizontally),
    ) {
        AnimatedContent(
            targetState = subscribed,
            transitionSpec = { (fadeIn(VelaMotion.tween(motion, VelaMotion.Level.QUICK)) + scaleIn(VelaMotion.bouncy(motion), initialScale = 0.6f)) togetherWith (fadeOut(VelaMotion.tween(motion, VelaMotion.Level.PRESS)) + scaleOut(targetScale = 0.6f)) },
            label = "subscribeContent",
        ) { on ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.xs)) {
                Icon(if (on) Vela.icons.check else Vela.icons.notifications, null, Modifier.size(18.dp), tint = label)
                Text(if (on) "Subscribed" else "Subscribe", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = label, maxLines = 1, softWrap = false)
            }
        }
    }
}
