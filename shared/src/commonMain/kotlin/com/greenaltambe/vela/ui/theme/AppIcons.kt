package com.greenaltambe.vela.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The icons of one look, by name: [icons] are the ordinary (outlined) forms, [filledIcons] the solid forms of what can be selected or switched
 * on (tabs, like, save, ...), which fall back to the ordinary form. [MaterialIconSet.Default] is the Material set; a later look brings its own.
 */
class AppIconSet(private val icons: Map<String, ImageVector>, private val filledIcons: Map<String, ImageVector> = emptyMap()) {
    operator fun get(name: String): ImageVector = icons.getValue(name)
    fun filled(name: String): ImageVector = filledIcons[name] ?: icons.getValue(name)
    /** The filled form when [active] (selected / on), the ordinary one otherwise. */
    operator fun get(name: String, active: Boolean): ImageVector = if (active) filled(name) else this[name]
    val names: Set<String> get() = icons.keys
    val filledNames: Set<String> get() = filledIcons.keys
}

object MaterialIconSet {
    val Default: AppIconSet by lazy { AppIconSet(MaterialOutlined.icons, MaterialFilled.icons.filterKeys { it in IconNames.withFilledForm }) }
}

val LocalIconSet = staticCompositionLocalOf { MaterialIconSet.Default }

/** Every icon slot the app uses. Each icon set must cover all of them (a test checks it). */
object IconNames {
    val all: List<String> = listOf("home", "subscriptions", "library", "settings", "search", "back", "close", "more", "play", "pause", "rewind", "forward", "fullscreen", "fullscreenExit", "volume", "mute", "download", "downloaded", "save", "share", "speed", "timer", "quality", "captions", "like", "refresh", "info", "chevronRight", "chevronLeft", "chevronDown", "chevronUp", "check", "add", "delete", "edit", "history", "playlist", "notifications", "account", "palette", "lock", "shield", "folder", "tune", "arrowUp", "arrowDown", "dislike", "link", "cloud", "visibility", "bolt", "explore", "skipNext", "skipPrevious", "watchLater", "comment", "audioTrack", "menu", "notificationsActive", "notificationsOff")
    /** The slots that have a filled form: what can be selected or switched on. */
    val withFilledForm: List<String> = listOf("home", "subscriptions", "library", "explore", "bolt", "download", "save", "like", "dislike", "notifications", "watchLater", "comment")
}

/**
 * `AppIcons.home`, `AppIcons.homeFilled`: the icons of the active theme. Outlined for everything; Filled for the selected state of tabs and
 * for like / dislike / save / download / notifications / watch later / comment when they are on. One-weight icons (search, close, ...) have no filled form.
 */
object AppIcons {
    val home: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["home"]
    val subscriptions: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["subscriptions"]
    val library: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["library"]
    val settings: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["settings"]
    val search: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["search"]
    val back: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["back"]
    val close: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["close"]
    val more: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["more"]
    val play: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["play"]
    val pause: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["pause"]
    val rewind: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["rewind"]
    val forward: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["forward"]
    val fullscreen: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["fullscreen"]
    val fullscreenExit: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["fullscreenExit"]
    val volume: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["volume"]
    val mute: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["mute"]
    val download: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["download"]
    val downloaded: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["downloaded"]
    val save: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["save"]
    val share: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["share"]
    val speed: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["speed"]
    val timer: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["timer"]
    val quality: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["quality"]
    val captions: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["captions"]
    val like: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["like"]
    val refresh: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["refresh"]
    val info: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["info"]
    val chevronRight: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["chevronRight"]
    val chevronLeft: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["chevronLeft"]
    val chevronDown: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["chevronDown"]
    val chevronUp: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["chevronUp"]
    val check: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["check"]
    val add: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["add"]
    val delete: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["delete"]
    val edit: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["edit"]
    val history: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["history"]
    val playlist: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["playlist"]
    val notifications: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["notifications"]
    val notificationsActive: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["notificationsActive"]
    val notificationsOff: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["notificationsOff"]
    val account: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["account"]
    val palette: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["palette"]
    val lock: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["lock"]
    val shield: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["shield"]
    val folder: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["folder"]
    val tune: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["tune"]
    val arrowUp: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["arrowUp"]
    val arrowDown: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["arrowDown"]
    val dislike: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["dislike"]
    val link: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["link"]
    val cloud: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["cloud"]
    val visibility: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["visibility"]
    val bolt: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["bolt"]
    val explore: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["explore"]
    val skipNext: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["skipNext"]
    val skipPrevious: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["skipPrevious"]
    val watchLater: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["watchLater"]
    val comment: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["comment"]
    val audioTrack: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["audioTrack"]
    val menu: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current["menu"]

    // Filled forms
    val homeFilled: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current.filled("home")
    val subscriptionsFilled: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current.filled("subscriptions")
    val libraryFilled: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current.filled("library")
    val exploreFilled: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current.filled("explore")
    val boltFilled: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current.filled("bolt")
    val downloadFilled: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current.filled("download")
    val saveFilled: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current.filled("save")
    val likeFilled: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current.filled("like")
    val dislikeFilled: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current.filled("dislike")
    val notificationsFilled: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current.filled("notifications")
    val watchLaterFilled: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current.filled("watchLater")
    val commentFilled: ImageVector @Composable @ReadOnlyComposable get() = LocalIconSet.current.filled("comment")

    /** The icon [name] in the form for [active]. */
    @Composable @ReadOnlyComposable fun named(name: String, active: Boolean = false): ImageVector = LocalIconSet.current.get(name, active)
}
