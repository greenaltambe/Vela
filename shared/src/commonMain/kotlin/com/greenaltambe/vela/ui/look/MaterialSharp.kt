package com.greenaltambe.vela.ui.look

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.automirrored.sharp.PlaylistPlay
import androidx.compose.material.icons.sharp.AccountCircle
import androidx.compose.material.icons.sharp.Add
import androidx.compose.material.icons.sharp.ArrowDownward
import androidx.compose.material.icons.sharp.ArrowUpward
import androidx.compose.material.icons.sharp.Bolt
import androidx.compose.material.icons.sharp.Bookmark
import androidx.compose.material.icons.sharp.Check
import androidx.compose.material.icons.sharp.ChevronLeft
import androidx.compose.material.icons.sharp.ChevronRight
import androidx.compose.material.icons.sharp.Close
import androidx.compose.material.icons.sharp.ClosedCaption
import androidx.compose.material.icons.sharp.Cloud
import androidx.compose.material.icons.sharp.Delete
import androidx.compose.material.icons.sharp.Download
import androidx.compose.material.icons.sharp.DownloadDone
import androidx.compose.material.icons.sharp.Edit
import androidx.compose.material.icons.sharp.ExpandLess
import androidx.compose.material.icons.sharp.ExpandMore
import androidx.compose.material.icons.sharp.FastForward
import androidx.compose.material.icons.sharp.FastRewind
import androidx.compose.material.icons.sharp.Folder
import androidx.compose.material.icons.sharp.Fullscreen
import androidx.compose.material.icons.sharp.FullscreenExit
import androidx.compose.material.icons.sharp.HighQuality
import androidx.compose.material.icons.sharp.History
import androidx.compose.material.icons.sharp.Home
import androidx.compose.material.icons.sharp.Info
import androidx.compose.material.icons.sharp.Link
import androidx.compose.material.icons.sharp.Lock
import androidx.compose.material.icons.sharp.MoreVert
import androidx.compose.material.icons.sharp.Notifications
import androidx.compose.material.icons.sharp.Palette
import androidx.compose.material.icons.sharp.Pause
import androidx.compose.material.icons.sharp.PlayArrow
import androidx.compose.material.icons.sharp.Refresh
import androidx.compose.material.icons.sharp.Search
import androidx.compose.material.icons.sharp.Settings
import androidx.compose.material.icons.sharp.Share
import androidx.compose.material.icons.sharp.Shield
import androidx.compose.material.icons.sharp.Speed
import androidx.compose.material.icons.sharp.Subscriptions
import androidx.compose.material.icons.sharp.ThumbDown
import androidx.compose.material.icons.sharp.ThumbUp
import androidx.compose.material.icons.sharp.Timer
import androidx.compose.material.icons.sharp.Tune
import androidx.compose.material.icons.sharp.VideoLibrary
import androidx.compose.material.icons.sharp.Visibility
import androidx.compose.material.icons.sharp.VolumeOff
import androidx.compose.material.icons.sharp.VolumeUp

/** [IconNames] mapped to the closest MaterialSharp icon. */
internal object MaterialSharp {
    val icons: Map<String, ImageVector> by lazy {
        mapOf(
            "home" to Icons.Sharp.Home,
            "subscriptions" to Icons.Sharp.Subscriptions,
            "library" to Icons.Sharp.VideoLibrary,
            "settings" to Icons.Sharp.Settings,
            "search" to Icons.Sharp.Search,
            "back" to Icons.AutoMirrored.Sharp.ArrowBack,
            "close" to Icons.Sharp.Close,
            "more" to Icons.Sharp.MoreVert,
            "play" to Icons.Sharp.PlayArrow,
            "pause" to Icons.Sharp.Pause,
            "rewind" to Icons.Sharp.FastRewind,
            "forward" to Icons.Sharp.FastForward,
            "fullscreen" to Icons.Sharp.Fullscreen,
            "fullscreenExit" to Icons.Sharp.FullscreenExit,
            "volume" to Icons.Sharp.VolumeUp,
            "mute" to Icons.Sharp.VolumeOff,
            "download" to Icons.Sharp.Download,
            "downloaded" to Icons.Sharp.DownloadDone,
            "save" to Icons.Sharp.Bookmark,
            "share" to Icons.Sharp.Share,
            "speed" to Icons.Sharp.Speed,
            "timer" to Icons.Sharp.Timer,
            "quality" to Icons.Sharp.HighQuality,
            "captions" to Icons.Sharp.ClosedCaption,
            "like" to Icons.Sharp.ThumbUp,
            "refresh" to Icons.Sharp.Refresh,
            "info" to Icons.Sharp.Info,
            "chevronRight" to Icons.Sharp.ChevronRight,
            "chevronLeft" to Icons.Sharp.ChevronLeft,
            "chevronDown" to Icons.Sharp.ExpandMore,
            "chevronUp" to Icons.Sharp.ExpandLess,
            "check" to Icons.Sharp.Check,
            "add" to Icons.Sharp.Add,
            "delete" to Icons.Sharp.Delete,
            "edit" to Icons.Sharp.Edit,
            "history" to Icons.Sharp.History,
            "playlist" to Icons.AutoMirrored.Sharp.PlaylistPlay,
            "notifications" to Icons.Sharp.Notifications,
            "account" to Icons.Sharp.AccountCircle,
            "palette" to Icons.Sharp.Palette,
            "lock" to Icons.Sharp.Lock,
            "shield" to Icons.Sharp.Shield,
            "folder" to Icons.Sharp.Folder,
            "tune" to Icons.Sharp.Tune,
            "arrowUp" to Icons.Sharp.ArrowUpward,
            "arrowDown" to Icons.Sharp.ArrowDownward,
            "dislike" to Icons.Sharp.ThumbDown,
            "link" to Icons.Sharp.Link,
            "cloud" to Icons.Sharp.Cloud,
            "visibility" to Icons.Sharp.Visibility,
            "bolt" to Icons.Sharp.Bolt,
        )
    }
}
