@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.data.NotificationEntry
import com.greenaltambe.vela.data.NotificationKind
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.components.EmptyState
import com.greenaltambe.vela.ui.components.formatAge
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.settings.SettingsScaffold
import com.greenaltambe.vela.ui.settings.segmentedItemColors
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.Spacing

/** "Today", "Yesterday" or the date, for grouping the feed. */
internal fun dayLabel(at: kotlin.time.Instant, now: kotlin.time.Instant, offsetMinutes: Int): String {
    val day = (at.toEpochMilliseconds() + offsetMinutes * 60_000L).floorDiv(86_400_000L)
    val today = (now.toEpochMilliseconds() + offsetMinutes * 60_000L).floorDiv(86_400_000L)
    return when (today - day) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> java.time.LocalDate.ofEpochDay(day).toString()
    }
}

/** The notification feed: new uploads, downloads, health alerts, newest first, grouped by day; unread rows are bold with a dot. */
@Composable
fun NotificationsScreen(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    val revision by services.notificationsRevision.collectAsState()
    val items = remember(revision, services.activeId) { services.notifications.recent() }
    val now = services.clock.now()
    val offset = services.utcOffsetMinutes()
    val groups = remember(items, offset) { items.groupBy { dayLabel(it.createdAt, now, offset) } }

    SettingsScaffold(
        "Notifications", modifier, onBack = { navigator.back() }, listTag = "notifications.list",
        actions = {
            IconButton(onClick = { services.notifications.markAllRead(services.clock.now()) }, enabled = items.any { !it.read }, modifier = Modifier.testTag("notifications.markAll")) { Icon(AppIcons.check, "Mark all as read") }
            IconButton(onClick = { navigator.push(Route.SettingsPage("notifications")) }, modifier = Modifier.testTag("notifications.settings")) { Icon(AppIcons.settings, "Notification settings") }
        },
    ) {
        if (items.isEmpty()) item(key = "empty") { EmptyState("Nothing yet. New videos from your subscriptions, finished downloads and alerts show up here.") }
        groups.forEach { (label, rows) ->
            item(key = "h-$label", contentType = "header") {
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Spacing.md, top = Spacing.md, bottom = Spacing.xs))
            }
            itemsIndexed(rows, key = { _, e -> "n-${e.id}" }, contentType = { _, _ -> "row" }) { i, e ->
                NotificationRow(services, navigator, e, i, rows.size)
            }
        }
    }
}

@Composable
private fun NotificationRow(services: AppServices, navigator: Navigator, e: NotificationEntry, index: Int, count: Int) {
    var menu by remember { mutableStateOf(false) }
    fun open() {
        services.notifications.markRead(e.id, services.clock.now())
        when (e.kind) {
            NotificationKind.UPLOAD -> navigator.push(Route.Video(VideoSummary(e.videoId, e.title, e.channelId, e.channelName)))
            NotificationKind.DOWNLOAD_DONE, NotificationKind.DOWNLOAD_FAILED -> navigator.push(Route.Downloads)
            NotificationKind.HEALTH -> navigator.push(Route.Health)
            NotificationKind.ACCOUNT -> if (e.videoId.isNotBlank()) navigator.push(Route.Video(VideoSummary(e.videoId, e.title, e.channelId, e.channelName)))
        }
    }
    SegmentedListItem(
        onClick = ::open,
        shapes = ListItemDefaults.segmentedShapes(index, count), colors = segmentedItemColors(),
        leadingContent = {
            Box(Modifier.size(width = 96.dp, height = 54.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                val url = e.thumbnailUrl ?: if (e.kind == NotificationKind.UPLOAD && e.videoId.isNotBlank()) "https://i.ytimg.com/vi/${e.videoId}/mqdefault.jpg" else null
                if (url != null) AsyncImage(url, null, Modifier.fillMaxWidth().size(width = 96.dp, height = 54.dp), contentScale = ContentScale.Crop)
                else Icon(
                    when (e.kind) { NotificationKind.DOWNLOAD_DONE -> AppIcons.downloaded; NotificationKind.DOWNLOAD_FAILED, NotificationKind.HEALTH -> AppIcons.info; else -> AppIcons.notifications },
                    null, tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        trailingContent = {
            Box {
                if (!e.read) Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape).testTag("notification.unread"))
                else IconButton(onClick = { menu = true }, modifier = Modifier.testTag("notification.menu")) { Icon(AppIcons.more, "Options") }
                DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; services.notifications.delete(e.id) }) }
            }
        },
        supportingContent = {
            Text(listOf(e.channelName.takeIf { it.isNotBlank() } ?: e.body.takeIf { it.isNotBlank() }, formatAge(e.createdAt, services.clock.now())).filterNotNull().joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = Modifier.testTag("notification.row"),
    ) { Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (e.read) FontWeight.Normal else FontWeight.Bold) }
}
