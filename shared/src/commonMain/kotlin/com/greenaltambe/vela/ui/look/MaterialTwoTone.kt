package com.greenaltambe.vela.ui.look

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.PlaylistPlay
import androidx.compose.material.icons.twotone.AccountCircle
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.ArrowDownward
import androidx.compose.material.icons.twotone.ArrowUpward
import androidx.compose.material.icons.twotone.Bolt
import androidx.compose.material.icons.twotone.Explore
import androidx.compose.material.icons.twotone.SkipNext
import androidx.compose.material.icons.twotone.SkipPrevious
import androidx.compose.material.icons.twotone.Bookmark
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.ChevronLeft
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.ClosedCaption
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.DownloadDone
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.ExpandLess
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.FastForward
import androidx.compose.material.icons.twotone.FastRewind
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Fullscreen
import androidx.compose.material.icons.twotone.FullscreenExit
import androidx.compose.material.icons.twotone.HighQuality
import androidx.compose.material.icons.twotone.History
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Audiotrack
import androidx.compose.material.icons.twotone.Comment
import androidx.compose.material.icons.twotone.MoreHoriz
import androidx.compose.material.icons.twotone.WatchLater
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Pause
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.material.icons.twotone.Subscriptions
import androidx.compose.material.icons.twotone.ThumbDown
import androidx.compose.material.icons.twotone.ThumbUp
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.VideoLibrary
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.VolumeOff
import androidx.compose.material.icons.twotone.VolumeUp

/** [IconNames] mapped to the closest MaterialTwoTone icon. */
internal object MaterialTwoTone {
    val icons: Map<String, ImageVector> by lazy {
        mapOf(
            "home" to Icons.TwoTone.Home,
            "subscriptions" to Icons.TwoTone.Subscriptions,
            "library" to Icons.TwoTone.VideoLibrary,
            "settings" to Icons.TwoTone.Settings,
            "search" to Icons.TwoTone.Search,
            "back" to Icons.AutoMirrored.TwoTone.ArrowBack,
            "close" to Icons.TwoTone.Close,
            "more" to Icons.TwoTone.MoreHoriz,
            "watchLater" to Icons.TwoTone.WatchLater,
            "comment" to Icons.TwoTone.Comment,
            "audioTrack" to Icons.TwoTone.Audiotrack,
            "play" to Icons.TwoTone.PlayArrow,
            "pause" to Icons.TwoTone.Pause,
            "rewind" to Icons.TwoTone.FastRewind,
            "forward" to Icons.TwoTone.FastForward,
            "fullscreen" to Icons.TwoTone.Fullscreen,
            "fullscreenExit" to Icons.TwoTone.FullscreenExit,
            "volume" to Icons.TwoTone.VolumeUp,
            "mute" to Icons.TwoTone.VolumeOff,
            "download" to Icons.TwoTone.Download,
            "downloaded" to Icons.TwoTone.DownloadDone,
            "save" to Icons.TwoTone.Bookmark,
            "share" to Icons.TwoTone.Share,
            "speed" to Icons.TwoTone.Speed,
            "timer" to Icons.TwoTone.Timer,
            "quality" to Icons.TwoTone.HighQuality,
            "captions" to Icons.TwoTone.ClosedCaption,
            "like" to Icons.TwoTone.ThumbUp,
            "refresh" to Icons.TwoTone.Refresh,
            "info" to Icons.TwoTone.Info,
            "chevronRight" to Icons.TwoTone.ChevronRight,
            "chevronLeft" to Icons.TwoTone.ChevronLeft,
            "chevronDown" to Icons.TwoTone.ExpandMore,
            "chevronUp" to Icons.TwoTone.ExpandLess,
            "check" to Icons.TwoTone.Check,
            "add" to Icons.TwoTone.Add,
            "delete" to Icons.TwoTone.Delete,
            "edit" to Icons.TwoTone.Edit,
            "history" to Icons.TwoTone.History,
            "playlist" to Icons.AutoMirrored.TwoTone.PlaylistPlay,
            "notifications" to Icons.TwoTone.Notifications,
            "account" to Icons.TwoTone.AccountCircle,
            "palette" to Icons.TwoTone.Palette,
            "lock" to Icons.TwoTone.Lock,
            "shield" to Icons.TwoTone.Shield,
            "folder" to Icons.TwoTone.Folder,
            "tune" to Icons.TwoTone.Tune,
            "arrowUp" to Icons.TwoTone.ArrowUpward,
            "arrowDown" to Icons.TwoTone.ArrowDownward,
            "dislike" to Icons.TwoTone.ThumbDown,
            "link" to Icons.TwoTone.Link,
            "cloud" to Icons.TwoTone.Cloud,
            "visibility" to Icons.TwoTone.Visibility,
            "bolt" to Icons.TwoTone.Bolt,
            "explore" to Icons.TwoTone.Explore,
            "skipNext" to Icons.TwoTone.SkipNext,
            "skipPrevious" to Icons.TwoTone.SkipPrevious,
        )
    }
}
