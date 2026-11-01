package com.greenaltambe.vela.ui.look

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp

/** [IconNames] mapped to the closest MaterialFilled icon. */
internal object MaterialFilled {
    val icons: Map<String, ImageVector> by lazy {
        mapOf(
            "home" to Icons.Filled.Home,
            "subscriptions" to Icons.Filled.Subscriptions,
            "library" to Icons.Filled.VideoLibrary,
            "settings" to Icons.Filled.Settings,
            "search" to Icons.Filled.Search,
            "back" to Icons.AutoMirrored.Filled.ArrowBack,
            "close" to Icons.Filled.Close,
            "more" to Icons.Filled.MoreHoriz,
            "watchLater" to Icons.Filled.WatchLater,
            "comment" to Icons.AutoMirrored.Filled.Comment,
            "audioTrack" to Icons.Filled.Audiotrack,
            "play" to Icons.Filled.PlayArrow,
            "pause" to Icons.Filled.Pause,
            "rewind" to Icons.Filled.FastRewind,
            "forward" to Icons.Filled.FastForward,
            "fullscreen" to Icons.Filled.Fullscreen,
            "fullscreenExit" to Icons.Filled.FullscreenExit,
            "volume" to Icons.AutoMirrored.Filled.VolumeUp,
            "mute" to Icons.AutoMirrored.Filled.VolumeOff,
            "download" to Icons.Filled.Download,
            "downloaded" to Icons.Filled.DownloadDone,
            "save" to Icons.Filled.Bookmark,
            "share" to Icons.Filled.Share,
            "speed" to Icons.Filled.Speed,
            "timer" to Icons.Filled.Timer,
            "quality" to Icons.Filled.HighQuality,
            "captions" to Icons.Filled.ClosedCaption,
            "like" to Icons.Filled.ThumbUp,
            "refresh" to Icons.Filled.Refresh,
            "info" to Icons.Filled.Info,
            "chevronRight" to Icons.Filled.ChevronRight,
            "chevronLeft" to Icons.Filled.ChevronLeft,
            "chevronDown" to Icons.Filled.ExpandMore,
            "chevronUp" to Icons.Filled.ExpandLess,
            "check" to Icons.Filled.Check,
            "add" to Icons.Filled.Add,
            "delete" to Icons.Filled.Delete,
            "edit" to Icons.Filled.Edit,
            "history" to Icons.Filled.History,
            "playlist" to Icons.AutoMirrored.Filled.PlaylistPlay,
            "notifications" to Icons.Filled.Notifications,
            "account" to Icons.Filled.AccountCircle,
            "palette" to Icons.Filled.Palette,
            "lock" to Icons.Filled.Lock,
            "shield" to Icons.Filled.Shield,
            "folder" to Icons.Filled.Folder,
            "tune" to Icons.Filled.Tune,
            "arrowUp" to Icons.Filled.ArrowUpward,
            "arrowDown" to Icons.Filled.ArrowDownward,
            "dislike" to Icons.Filled.ThumbDown,
            "link" to Icons.Filled.Link,
            "cloud" to Icons.Filled.Cloud,
            "visibility" to Icons.Filled.Visibility,
            "bolt" to Icons.Filled.Bolt,
            "explore" to Icons.Filled.Explore,
            "skipNext" to Icons.Filled.SkipNext,
            "skipPrevious" to Icons.Filled.SkipPrevious,
        )
    }
}
