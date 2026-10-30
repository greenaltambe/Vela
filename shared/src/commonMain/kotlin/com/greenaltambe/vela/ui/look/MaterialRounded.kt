package com.greenaltambe.vela.ui.look

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp

/** [IconNames] mapped to the closest MaterialRounded icon. */
internal object MaterialRounded {
    val icons: Map<String, ImageVector> by lazy {
        mapOf(
            "home" to Icons.Rounded.Home,
            "subscriptions" to Icons.Rounded.Subscriptions,
            "library" to Icons.Rounded.VideoLibrary,
            "settings" to Icons.Rounded.Settings,
            "search" to Icons.Rounded.Search,
            "back" to Icons.AutoMirrored.Rounded.ArrowBack,
            "close" to Icons.Rounded.Close,
            "more" to Icons.Rounded.MoreHoriz,
            "watchLater" to Icons.Rounded.WatchLater,
            "comment" to Icons.Rounded.Comment,
            "audioTrack" to Icons.Rounded.Audiotrack,
            "play" to Icons.Rounded.PlayArrow,
            "pause" to Icons.Rounded.Pause,
            "rewind" to Icons.Rounded.FastRewind,
            "forward" to Icons.Rounded.FastForward,
            "fullscreen" to Icons.Rounded.Fullscreen,
            "fullscreenExit" to Icons.Rounded.FullscreenExit,
            "volume" to Icons.Rounded.VolumeUp,
            "mute" to Icons.Rounded.VolumeOff,
            "download" to Icons.Rounded.Download,
            "downloaded" to Icons.Rounded.DownloadDone,
            "save" to Icons.Rounded.Bookmark,
            "share" to Icons.Rounded.Share,
            "speed" to Icons.Rounded.Speed,
            "timer" to Icons.Rounded.Timer,
            "quality" to Icons.Rounded.HighQuality,
            "captions" to Icons.Rounded.ClosedCaption,
            "like" to Icons.Rounded.ThumbUp,
            "refresh" to Icons.Rounded.Refresh,
            "info" to Icons.Rounded.Info,
            "chevronRight" to Icons.Rounded.ChevronRight,
            "chevronLeft" to Icons.Rounded.ChevronLeft,
            "chevronDown" to Icons.Rounded.ExpandMore,
            "chevronUp" to Icons.Rounded.ExpandLess,
            "check" to Icons.Rounded.Check,
            "add" to Icons.Rounded.Add,
            "delete" to Icons.Rounded.Delete,
            "edit" to Icons.Rounded.Edit,
            "history" to Icons.Rounded.History,
            "playlist" to Icons.AutoMirrored.Rounded.PlaylistPlay,
            "notifications" to Icons.Rounded.Notifications,
            "account" to Icons.Rounded.AccountCircle,
            "palette" to Icons.Rounded.Palette,
            "lock" to Icons.Rounded.Lock,
            "shield" to Icons.Rounded.Shield,
            "folder" to Icons.Rounded.Folder,
            "tune" to Icons.Rounded.Tune,
            "arrowUp" to Icons.Rounded.ArrowUpward,
            "arrowDown" to Icons.Rounded.ArrowDownward,
            "dislike" to Icons.Rounded.ThumbDown,
            "link" to Icons.Rounded.Link,
            "cloud" to Icons.Rounded.Cloud,
            "visibility" to Icons.Rounded.Visibility,
            "bolt" to Icons.Rounded.Bolt,
            "explore" to Icons.Rounded.Explore,
            "skipNext" to Icons.Rounded.SkipNext,
            "skipPrevious" to Icons.Rounded.SkipPrevious,
        )
    }
}
