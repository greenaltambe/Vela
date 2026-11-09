@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    val blockedRevision by services.blocklist.revision.collectAsState()
    val items = remember(revision, services.activeId, blockedRevision) { services.notifications.recent().filterNot { it.channelId.isNotBlank() && services.blocklist.isBlocked(it.channelId) } }
    val now = services.clock.now()
    val offset = services.utcOffsetMinutes()
    val groups = remember(items, offset) { items.groupBy { dayLabel(it.createdAt, now, offset) } }
    // The pictures of the channels you subscribe to, to put on the rows of their uploads.
    val avatars = remember(revision, services.activeId) { services.subscriptions.all().associate { it.channelId to it.avatarUrl } }

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
                NotificationRow(services, navigator, e, i, rows.size, avatars[e.channelId])
            }
        }
    }
}

@Composable
private fun NotificationRow(services: AppServices, navigator: Navigator, e: NotificationEntry, index: Int, count: Int, channelAvatarUrl: String?) {
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
    // The way the official app lays a notification out, in the app's expressive lists: who it is from (their round picture, with the unread dot
    // before it), what they did in words ("Uploaded: ..."), how long ago, and the video's picture at the end. An unread row sits on a warmer tonal
    // surface than a read one, so the list reads at a glance without anything laid over a picture.
    val isUpload = e.kind == NotificationKind.UPLOAD || (e.kind == NotificationKind.ACCOUNT && e.channelName.isNotBlank())
    val thumb = e.thumbnailUrl ?: if (e.videoId.isNotBlank()) "https://i.ytimg.com/vi/${e.videoId}/mqdefault.jpg" else null
    val scheme = MaterialTheme.colorScheme
    // Laid out by hand rather than as a list item, so the words get the room: the item's own padding and slot gaps left them about 100 dp.
    androidx.compose.material3.Surface(
        onClick = ::open, shape = com.greenaltambe.vela.ui.settings.segmentedShape(index, count),
        color = if (e.read) scheme.surfaceContainerLow else scheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).testTag("notification.row"),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            // The unread dot always takes its place, so read and unread rows line up.
            Box(Modifier.padding(top = 20.dp).size(8.dp), contentAlignment = Alignment.Center) {
                if (!e.read) Box(Modifier.size(8.dp).background(scheme.primary, CircleShape).testTag("notification.unread"))
            }
            Spacer(Modifier.width(8.dp))
            if (isUpload) Box(Modifier.testTag("notification.channelAvatar")) { com.greenaltambe.vela.ui.components.Avatar(e.channelName, size = 44.dp, url = channelAvatarUrl) }
            else Box(Modifier.size(44.dp).clip(CircleShape).background(if (e.kind == NotificationKind.DOWNLOAD_FAILED) scheme.errorContainer else scheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(
                    when (e.kind) { NotificationKind.DOWNLOAD_DONE -> AppIcons.downloaded; NotificationKind.DOWNLOAD_FAILED, NotificationKind.HEALTH -> AppIcons.info; else -> AppIcons.notifications },
                    null, tint = if (e.kind == NotificationKind.DOWNLOAD_FAILED) scheme.onErrorContainer else scheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // A notification saved without a title (a download of a video that had none) leads with its message instead of an empty line.
                val headline = if (isUpload) e.channelName else e.title.ifBlank { e.body.ifBlank { "Notification" } }
                Text(headline, style = MaterialTheme.typography.titleSmall, maxLines = if (isUpload) 1 else 2, overflow = TextOverflow.Ellipsis, fontWeight = if (e.read) FontWeight.Medium else FontWeight.Bold)
                val body = if (isUpload) "Uploaded: ${e.title}" else e.body.takeIf { it.isNotBlank() && it != headline }
                if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = scheme.onSurface)
                formatAge(e.createdAt, services.clock.now())?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1) }
            }
            if (isUpload && thumb != null) {
                Spacer(Modifier.width(10.dp))
                AsyncImage(thumb, null, Modifier.size(width = 96.dp, height = 54.dp).clip(MaterialTheme.shapes.medium).background(scheme.surfaceContainerHigh), contentScale = ContentScale.Crop)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp).testTag("notification.menu")) { Icon(AppIcons.more, "Options", Modifier.size(20.dp)) }
                DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; services.notifications.delete(e.id) }) }
            }
        }
    }
}
