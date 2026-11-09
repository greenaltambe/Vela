package com.greenaltambe.vela.core.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }
/** How the seek bar is drawn: a hairline or a bold track, each with or without a round marker, a ribbon (bold with a dot, thin silky ribbons lifting off the watched part), or a wave that moves up to the current position. */
enum class ProgressStyle { EXPRESSIVE, HAIRLINE, HAIRLINE_DOT, BOLD, BOLD_DOT, RIBBON, WAVE }
enum class NavTab { HOME, SHORTS, SUBSCRIPTIONS, LIBRARY, SEARCH, DOWNLOADS, EXPLORE }

/** The tabs of the navigation: fixed (no setting). Shorts only while Shorts are on, see [com.greenaltambe.vela.ui.nav.NavConfig]. */
object NavTabs {
    val default = listOf(NavTab.HOME, NavTab.SHORTS, NavTab.SUBSCRIPTIONS, NavTab.LIBRARY)
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
    val homeChips = flag("look.home.chips", true, "Quick filters on Home", listOf("home", "filter", "chips", "unwatched", "music", "live", "short"), "A row of chips above Home (All, Unwatched, Under 10 min, Live, Music, and your own topics). They filter what is already loaded.")
    val railCollapsed = flag("look.nav.railCollapsed", false, "Collapse the side rail to icons", listOf("rail", "sidebar", "collapse", "icons"), "Wide windows and Linux: the left rail shows icons only.")

    val all: List<Setting<*>> = listOf(theme, amoled, progressStyle, homeShelves, homeDensity, homeChips, railCollapsed)
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
    private val exact = setOf("appearance.look", "appearance.performanceMode")
    private val prefixes = listOf(
        "look.palette", "look.colorMode", "look.customColor", "look.corners", "look.density", "look.textScale", "look.font", "look.titleWeight",
        "look.iconStyle", "look.iconWeight", "look.iconRounded", "look.glass", "look.reduceMotion", "look.nav.tabs", "look.nav.openOn", "look.nav.labels",
        "look.nav.barStyle", "look.nav.shrinkOnScroll", "look.nav.marker", "look.mini.",
    )
    fun isLegacy(key: String) = key in exact || prefixes.any { key.startsWith(it) }
}
