package com.greenaltambe.vela.ui.look

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp

/** [IconNames] mapped to the closest MaterialOutlined icon. */
internal object MaterialOutlined {
    val icons: Map<String, ImageVector> by lazy {
        mapOf(
            "home" to Icons.Outlined.Home,
            "subscriptions" to Icons.Outlined.Subscriptions,
            "library" to Icons.Outlined.VideoLibrary,
            "settings" to Icons.Outlined.Settings,
            "search" to Icons.Outlined.Search,
            "back" to Icons.AutoMirrored.Outlined.ArrowBack,
            "close" to Icons.Outlined.Close,
            "more" to Icons.Outlined.MoreHoriz,
            "watchLater" to Icons.Outlined.WatchLater,
            "comment" to Icons.Outlined.Comment,
            "audioTrack" to Icons.Outlined.Audiotrack,
            "play" to Icons.Outlined.PlayArrow,
            "pause" to Icons.Outlined.Pause,
            "rewind" to Icons.Outlined.FastRewind,
            "forward" to Icons.Outlined.FastForward,
            "fullscreen" to Icons.Outlined.Fullscreen,
            "fullscreenExit" to Icons.Outlined.FullscreenExit,
            "volume" to Icons.Outlined.VolumeUp,
            "mute" to Icons.Outlined.VolumeOff,
            "download" to Icons.Outlined.Download,
            "downloaded" to Icons.Outlined.DownloadDone,
            "save" to Icons.Outlined.Bookmark,
            "share" to Icons.Outlined.Share,
            "speed" to Icons.Outlined.Speed,
            "timer" to Icons.Outlined.Timer,
            "quality" to Icons.Outlined.HighQuality,
            "captions" to Icons.Outlined.ClosedCaption,
            "like" to Icons.Outlined.ThumbUp,
            "refresh" to Icons.Outlined.Refresh,
            "info" to Icons.Outlined.Info,
            "chevronRight" to Icons.Outlined.ChevronRight,
            "chevronLeft" to Icons.Outlined.ChevronLeft,
            "chevronDown" to Icons.Outlined.ExpandMore,
            "chevronUp" to Icons.Outlined.ExpandLess,
            "check" to Icons.Outlined.Check,
            "add" to Icons.Outlined.Add,
            "delete" to Icons.Outlined.Delete,
            "edit" to Icons.Outlined.Edit,
            "history" to Icons.Outlined.History,
            "playlist" to Icons.AutoMirrored.Outlined.PlaylistPlay,
            "notifications" to Icons.Outlined.Notifications,
            "account" to Icons.Outlined.AccountCircle,
            "palette" to Icons.Outlined.Palette,
            "lock" to Icons.Outlined.Lock,
            "shield" to Icons.Outlined.Shield,
            "folder" to Icons.Outlined.Folder,
            "tune" to Icons.Outlined.Tune,
            "arrowUp" to Icons.Outlined.ArrowUpward,
            "arrowDown" to Icons.Outlined.ArrowDownward,
            "dislike" to Icons.Outlined.ThumbDown,
            "link" to Icons.Outlined.Link,
            "cloud" to Icons.Outlined.Cloud,
            "visibility" to Icons.Outlined.Visibility,
            "bolt" to Icons.Outlined.Bolt,
        )
    }
}
