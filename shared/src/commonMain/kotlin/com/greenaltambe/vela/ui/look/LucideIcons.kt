package com.greenaltambe.vela.ui.look

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.Captions
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.SkipBack
import com.composables.icons.lucide.SkipForward
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleUser
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.FastForward
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Hd
import com.composables.icons.lucide.History
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Library
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.ListVideo
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize
import com.composables.icons.lucide.Minimize
import com.composables.icons.lucide.MonitorPlay
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Rewind
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.ThumbsDown
import com.composables.icons.lucide.ThumbsUp
import com.composables.icons.lucide.Timer
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.VolumeX
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap

/** [IconNames] mapped to the closest LucideIcons icon. */
internal object LucideIcons {
    val icons: Map<String, ImageVector> by lazy {
        mapOf(
            "home" to Lucide.House,
            "subscriptions" to Lucide.MonitorPlay,
            "library" to Lucide.Library,
            "settings" to Lucide.Settings,
            "search" to Lucide.Search,
            "back" to Lucide.ArrowLeft,
            "close" to Lucide.X,
            "more" to Lucide.Ellipsis,
            "watchLater" to Lucide.Clock,
            "comment" to Lucide.MessageSquare,
            "audioTrack" to Lucide.Languages,
            "play" to Lucide.Play,
            "pause" to Lucide.Pause,
            "rewind" to Lucide.Rewind,
            "forward" to Lucide.FastForward,
            "fullscreen" to Lucide.Maximize,
            "fullscreenExit" to Lucide.Minimize,
            "volume" to Lucide.Volume2,
            "mute" to Lucide.VolumeX,
            "download" to Lucide.Download,
            "downloaded" to Lucide.CircleCheck,
            "save" to Lucide.Bookmark,
            "share" to Lucide.Share2,
            "speed" to Lucide.Gauge,
            "timer" to Lucide.Timer,
            "quality" to Lucide.Hd,
            "captions" to Lucide.Captions,
            "like" to Lucide.ThumbsUp,
            "refresh" to Lucide.RefreshCw,
            "info" to Lucide.Info,
            "chevronRight" to Lucide.ChevronRight,
            "chevronLeft" to Lucide.ChevronLeft,
            "chevronDown" to Lucide.ChevronDown,
            "chevronUp" to Lucide.ChevronUp,
            "check" to Lucide.Check,
            "add" to Lucide.Plus,
            "delete" to Lucide.Trash2,
            "edit" to Lucide.Pencil,
            "history" to Lucide.History,
            "playlist" to Lucide.ListVideo,
            "notifications" to Lucide.Bell,
            "account" to Lucide.CircleUser,
            "palette" to Lucide.Palette,
            "lock" to Lucide.Lock,
            "shield" to Lucide.Shield,
            "folder" to Lucide.Folder,
            "tune" to Lucide.SlidersHorizontal,
            "arrowUp" to Lucide.ArrowUp,
            "arrowDown" to Lucide.ArrowDown,
            "dislike" to Lucide.ThumbsDown,
            "link" to Lucide.Link,
            "cloud" to Lucide.Cloud,
            "visibility" to Lucide.Eye,
            "bolt" to Lucide.Zap,
            "explore" to Lucide.Compass,
            "skipNext" to Lucide.SkipForward,
            "skipPrevious" to Lucide.SkipBack,
        )
    }
}
