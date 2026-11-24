package com.greenaltambe.vela.core.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }
/** The colour schemes on offer (Customize > Color scheme). The palettes themselves live in `ui/theme/Palettes.kt`. */
enum class ColorPalette { VELA_RED, CLASSIC, SAKURA, OCEAN, FOREST, TWILIGHT, SUNSET }
/** How the seek bar is drawn: a hairline or a bold track, each with or without a round marker, a ribbon (bold with a dot, thin silky ribbons lifting off the watched part), or a wave that moves up to the current position. */
enum class ProgressStyle { EXPRESSIVE, HAIRLINE, HAIRLINE_DOT, BOLD, BOLD_DOT, RIBBON, WAVE }
enum class NavTab { HOME, SHORTS, SUBSCRIPTIONS, LIBRARY, SEARCH, DOWNLOADS, EXPLORE }

/** The tabs of the navigation, in order. Shorts only while Shorts are on and Explore only while its switch is on, see [com.greenaltambe.vela.ui.nav.NavConfig]. Home, Subs and Library are always there. */
object NavTabs {
    val default = listOf(NavTab.HOME, NavTab.SHORTS, NavTab.SUBSCRIPTIONS, NavTab.EXPLORE, NavTab.LIBRARY)
}

/**
 * The appearance settings (task 23). Every key starts with `look.`: that prefix is what makes a setting part of a Look
 * ([LookKeys]); behaviour (account safety, tier order, downloads, SponsorBlock...) never starts with it, so a Look cannot change it.
 */
object LookSettings {
    private fun <E : Enum<E>> pick(key: String, default: E, entries: List<E>, label: String, labels: Map<E, String>, keywords: List<String>, info: String = "") = Setting(
        key = key, default = default, scope = SettingScope.GLOBAL, label = label, keywords = keywords, codec = SettingCodecs.enum(entries),
        section = SettingSection.APPEARANCE, choices = entries.map { SettingChoice(it.name, labels[it] ?: it.name.lowercase().replaceFirstChar(Char::uppercase)) }, info = info,
    )

    private fun flag(key: String, default: Boolean, label: String, keywords: List<String>, info: String = "") = Setting(
        key = key, default = default, scope = SettingScope.GLOBAL, label = label, keywords = keywords, codec = SettingCodecs.boolean, section = SettingSection.APPEARANCE, info = info,
    )

    val theme = pick("look.theme", ThemeMode.SYSTEM, ThemeMode.entries, "Theme", mapOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), listOf("dark", "light", "mode", "appearance"))

    val colorScheme = pick(
        "look.colorScheme", ColorPalette.VELA_RED, ColorPalette.entries, "Color scheme",
        mapOf(ColorPalette.VELA_RED to "Vela red", ColorPalette.CLASSIC to "Classic", ColorPalette.SAKURA to "Sakura pink", ColorPalette.OCEAN to "Ocean", ColorPalette.FOREST to "Forest", ColorPalette.TWILIGHT to "Twilight", ColorPalette.SUNSET to "Sunset"),
        listOf("color", "colour", "scheme", "palette", "accent", "pink", "red", "blue", "green", "purple", "orange", "cute", "theme"),
        "The accent and the tint of the surfaces, in both the light and the dark theme.",
    )

    val amoled = flag("look.amoled", false, "Pure black in dark theme", listOf("amoled", "black", "oled", "dark", "battery"), "Backgrounds become true black (cards stay a hair lighter so they still show). Saves battery on OLED screens; only applies while the dark theme is on.")

    val progressStyle = pick(
        "look.progressStyle", ProgressStyle.EXPRESSIVE, ProgressStyle.entries, "Progress bar",
        mapOf(ProgressStyle.EXPRESSIVE to "Wavy (Material)", ProgressStyle.HAIRLINE to "Hairline", ProgressStyle.HAIRLINE_DOT to "Hairline + dot", ProgressStyle.BOLD to "Bold", ProgressStyle.BOLD_DOT to "Bold + dot", ProgressStyle.RIBBON to "Ribbon", ProgressStyle.WAVE to "Wave (classic)"),
        listOf("progress", "seek", "bar", "scrubber", "timeline", "spikes", "waves", "hairline", "thumb"),
        "The player's seek bar. Every style is filled with the app's accent colour.",
    )
    val homeShelves = Setting(
        key = "look.home.shelves", default = HomeShelves.encode(HomeShelves.default), scope = SettingScope.GLOBAL, label = "Home shelves", keywords = listOf("home", "shelves", "rows", "sections", "order", "continue", "subscriptions", "because", "shorts", "interests"),
        codec = SettingCodecs.string, section = SettingSection.APPEARANCE, info = "Which shelves Home shows above its list, and in what order. A shelf with nothing to show is left out.",
    )
    val homeDensity = pick("look.home.density", HomeDensity.NORMAL, HomeDensity.entries, "Shelves between videos", mapOf(HomeDensity.FEW to "Few (every 8 videos)", HomeDensity.NORMAL to "Normal (every 5)", HomeDensity.MANY to "Many (every 3)"), listOf("home", "shelves", "rows", "often", "density", "carousel"), "How often a shelf comes between the videos of Home's list.")
    val homeChips = flag("look.home.chips", true, "Chips on Home", listOf("home", "filter", "chips", "unwatched", "music", "live", "short", "custom feed"), "The row above Home: a Filter button (Unwatched, Under 10 min) and, when signed in, YouTube's own chips for your feed (All, your custom feeds, topics, Live, ...).")
    val hideChipsOnScroll = flag("look.home.hideChipsOnScroll", true, "Hide the filter chips while scrolling", listOf("home", "chips", "filter", "hide", "scroll", "auto", "immersive"), "Scrolling down slides Home's filter chips away so the videos get the room; scrolling up brings them back.")
    val hideBarOnScroll = flag("look.nav.hideBarOnScroll", false, "Hide the bottom bar while scrolling", listOf("bottom", "bar", "navigation", "nav", "hide", "scroll", "auto", "immersive"), "Scrolling down slides the bottom navigation bar away; scrolling up brings it back. Phones and narrow windows.")
    val hideChannelTabsOnScroll = flag("look.nav.hideChannelTabsOnScroll", false, "Hide channel tabs while scrolling", listOf("channel", "tabs", "videos", "shorts", "playlists", "hide", "scroll", "auto", "immersive"), "On a channel page, scrolling down slides the tab row (Home, Videos, Shorts...) away; scrolling up brings it back.")
    val showExplore = flag("look.nav.showExplore", true, "Explore in the navigation", listOf("explore", "tab", "navigation", "nav", "bar", "rail", "hide", "show", "topics", "discover"), "Shows Explore (topics and live streams) as a tab in the bottom bar and the side bar. Off: it is still reachable from the buttons that say Browse Explore.")
    val showNavLabels = flag("look.nav.showLabels", true, "Names under the icons", listOf("labels", "names", "text", "navigation", "nav", "bar", "icons", "hide", "minimal"), "Shows each tab's name under its icon in the bottom bar and the folded side bar. Off: icons only (screen readers still announce the names). The unfolded desktop side bar always shows names.")
    val translucentNavBar = flag("look.nav.translucent", false, "Translucent bars", listOf("translucent", "transparent", "blur", "blurred", "frosted", "glass", "see-through", "bottom", "top", "bar", "navigation", "nav", "chips", "mini player"), "The page scrolls under the top bar, Home's chip row, the bottom bar and the mini player and shows through them, blurred, like YouTube's. Phones and narrow windows. On Android 11 and older they are only tinted (no blur).")
    val animateNavIcons = flag("look.nav.animateIcons", true, "Animate the tab icons", listOf("animation", "animate", "icons", "bounce", "pop", "navigation", "nav", "bar", "motion"), "The tab you tap pops and settles, and the one you leave shrinks back. Off: the icons just change.")
    val railCollapsed = flag("look.nav.railCollapsed", false, "Collapse the side rail to icons", listOf("rail", "sidebar", "collapse", "icons"), "Wide windows and Linux: the left rail shows icons only.")

    val all: List<Setting<*>> = listOf(theme, colorScheme, amoled, progressStyle, homeShelves, homeDensity, homeChips, hideChipsOnScroll, hideBarOnScroll, hideChannelTabsOnScroll, showExplore, showNavLabels, translucentNavBar, animateNavIcons, railCollapsed)
}

/**
 * Which settings belong to the Customize page: the `look.` keys. Behaviour (account safety, tier order, downloads, SponsorBlock...) never starts
 * with it, so customizing cannot change it.
 */
object LookKeys {
    fun isLookKey(key: String) = key.startsWith("look.")
    val all: Set<String> get() = LookSettings.all.map { it.key }.toSet()
}

/**
 * Keys of settings that no longer exist (palette, fonts, icon packs, glass, the floating bar, the mini-player looks ...). Old backups and old
 * databases may still carry them; they are left alone, unused, and a restore does not warn about them.
 */
object LegacySettingKeys {
    private val exact = setOf("appearance.look", "appearance.performanceMode", "home.source", "look.nav.barOpacity")
    private val prefixes = listOf(
        "look.palette", "look.colorMode", "look.customColor", "look.corners", "look.density", "look.textScale", "look.font", "look.titleWeight",
        "look.iconStyle", "look.iconWeight", "look.iconRounded", "look.glass", "look.reduceMotion", "look.nav.tabs", "look.nav.openOn", "look.nav.labels",
        "look.nav.barStyle", "look.nav.shrinkOnScroll", "look.nav.marker", "look.mini.",
    )
    fun isLegacy(key: String) = key in exact || prefixes.any { key.startsWith(it) }
}
