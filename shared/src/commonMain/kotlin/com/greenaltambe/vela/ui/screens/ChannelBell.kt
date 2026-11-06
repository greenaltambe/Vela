@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.data.NotifyLevel
import com.greenaltambe.vela.ui.theme.AppIcons

/**
 * The bell next to Subscribe: how much this channel may notify (All, Personalized, None). Kept on this device; it decides which new uploads
 * reach the notification feed and which also raise a system notification. Shown only while subscribed.
 */
@Composable
fun ChannelBell(services: AppServices, channelId: String, modifier: Modifier = Modifier, connected: Boolean = true, onChanged: (NotifyLevel) -> Unit = {}) {
    var level by remember(channelId) { mutableStateOf(services.subscriptions.notifyLevel(channelId)) }
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        // The right half of the connected pair that starts with the Subscribe button.
        com.greenaltambe.vela.ui.components.ActionChip(
            when (level) { NotifyLevel.ALL -> AppIcons.notificationsActive; NotifyLevel.PERSONALIZED -> AppIcons.notifications; NotifyLevel.NONE -> AppIcons.notificationsOff },
            "", { open = true }, Modifier.testTag("channel.bell"), position = if (connected) com.greenaltambe.vela.ui.components.ActionPosition.TRAILING else com.greenaltambe.vela.ui.components.ActionPosition.ALONE,
        )
        DropdownMenu(open, { open = false }) {
            NotifyLevel.entries.forEach { l ->
                DropdownMenuItem(
                    text = { Text(l.label + if (l == level) "  ✓" else "") },
                    leadingIcon = { Icon(when (l) { NotifyLevel.ALL -> AppIcons.notificationsActive; NotifyLevel.PERSONALIZED -> AppIcons.notifications; NotifyLevel.NONE -> AppIcons.notificationsOff }, null) },
                    onClick = { open = false; level = l; services.subscriptions.setNotifyLevel(channelId, l); onChanged(l) },
                    modifier = Modifier.testTag("channel.bell.${l.name}"),
                )
            }
        }
    }
}
