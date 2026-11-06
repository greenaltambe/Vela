package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greenaltambe.vela.ui.player.HapticKind
import com.greenaltambe.vela.ui.player.rememberPlayerHaptics
import com.greenaltambe.vela.ui.theme.AppIcons

/**
 * Subscribe / Subscribed as an expressive toggle button: filled to subscribe, tonal once subscribed, with a haptic (a firm one when you subscribe,
 * a soft one when you leave). Next to the bell it is the left half of a connected pair ([position] LEADING), like the like and dislike buttons.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, position: ActionPosition = ActionPosition.ALONE) {
    val haptics = rememberPlayerHaptics()
    val click = { haptics.perform(if (subscribed) HapticKind.TOGGLE_OFF else HapticKind.CONFIRM); onClick() }
    androidx.compose.material3.ToggleButton(
        checked = subscribed, onCheckedChange = { click() }, modifier = modifier,
        shapes = when (position) {
            ActionPosition.LEADING -> androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonShapes()
            ActionPosition.TRAILING -> androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonShapes()
            ActionPosition.ALONE -> androidx.compose.material3.ToggleButtonDefaults.shapes()
        },
        colors = androidx.compose.material3.ToggleButtonDefaults.toggleButtonColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
            checkedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer, checkedContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Icon(if (subscribed) AppIcons.check else AppIcons.notifications, null, Modifier.size(androidx.compose.material3.ToggleButtonDefaults.IconSize))
        Spacer(Modifier.width(androidx.compose.material3.ToggleButtonDefaults.IconSpacing))
        Text(if (subscribed) "Subscribed" else "Subscribe", maxLines = 1, softWrap = false)
    }
}
