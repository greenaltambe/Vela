package com.greenaltambe.vela.core.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }
/** The one colour choice: a gradient of three stops that tints the whole app (stops live in ui/look/Palettes). */
enum class Palette { VELA, AURORA, OCEAN, SUNSET, EMBER, ORCHID, MEADOW, SLATE }
enum class DensityChoice { COMPACT, COMFORTABLE, ROOMY }
enum class FontChoice { VELA, GROTESQUE, SERIF, MONO, SYSTEM }
enum class TitleWeight { LIGHT, NORMAL, MEDIUM }
/**
 * The icon pack. Vela's own: Filled (solid shapes), Line (fine strokes drawn as strokes, not outlined solids) and Duotone (line icons with a soft fill).
 * Ready-made: Material Rounded / Outlined / Sharp / Two-tone and Lucide. Every pack covers every icon in the app.
 */
enum class IconStyle { FILLED, LINE, DUOTONE, ROUNDED, OUTLINED, SHARP, TWO_TONE, LUCIDE }
/** How the seek bar is drawn: a hairline or a bold track, each with or without a round marker, a ribbon (bold with a dot, thin silky ribbons lifting off the watched part), or a wave that moves up to the current position. */
enum class ProgressStyle { HAIRLINE, HAIRLINE_DOT, BOLD, BOLD_DOT, RIBBON, WAVE }
enum class IconWeight { THIN, REGULAR, BOLD }
enum class NavTab { HOME, SHORTS, SUBSCRIPTIONS, LIBRARY, SEARCH, DOWNLOADS, EXPLORE }
enum class NavLabels { HIDDEN, ALWAYS }
enum class BarStyle { FLOATING, DOCKED, MINIMAL }
/** The mini player: a bar-wide card, or a small picture-in-picture style card. */
enum class MiniLook { CARD, PIP }
/** Whether the mini player touches the bar or floats a set distance above it. */
enum class MiniPlacement { ATTACHED, SEPARATE }

/** The tab list as one setting value: tab names in order, comma separated. Unknown names and repeats are dropped; fewer than two usable tabs gives the default. */
object NavTabs {
    val default = listOf(NavTab.HOME, NavTab.SHORTS, NavTab.SUBSCRIPTIONS, NavTab.LIBRARY)
    const val MAX = 5

    fun parse(raw: String): List<NavTab> {
        val tabs = raw.split(',').mapNotNull { n -> NavTab.entries.firstOrNull { it.name.equals(n.trim(), ignoreCase = true) } }.distinct().take(MAX)
        return if (tabs.size >= 2) tabs else default
    }
    fun encode(tabs: List<NavTab>) = tabs.joinToString(",") { it.name }
}

/**
 * The appearance settings (task 23). Every key starts with `look.`: that prefix is what makes a setting part of a Look
 * ([LookKeys]); behaviour (account safety, tier order, downloads, SponsorBlock...) never starts with it, so a Look cannot change it.
 * The pure-black switch keeps its old key (`appearance.look`) and the performance switch its own (`appearance.performanceMode`);
 * both are listed in [LookKeys.extra].
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

    val palette = pick(
        "look.palette", Palette.VELA, Palette.entries, "Colour palette",
        mapOf(Palette.VELA to "Vela", Palette.AURORA to "Aurora", Palette.OCEAN to "Ocean", Palette.SUNSET to "Sunset", Palette.EMBER to "Ember", Palette.ORCHID to "Orchid", Palette.MEADOW to "Meadow", Palette.SLATE to "Slate"),
        listOf("color", "colour", "accent", "gradient", "palette", "tint"),
        "One gradient for the whole app: buttons, switches, selected tabs, sliders and the profile ring.",
    )

    val corners = Setting(
        key = "look.corners", default = 12f, scope = SettingScope.GLOBAL, label = "Corner radius", keywords = listOf("round", "corner", "radius", "shape", "sharp"),
        codec = SettingCodecs.float, section = SettingSection.APPEARANCE, range = SettingRange(0.0, 16.0, 2.0), info = "How round everything is: thumbnails, cards, buttons, tabs, sheets and the bars. 0 is square.",
    )
    val density = pick("look.density", DensityChoice.COMFORTABLE, DensityChoice.entries, "Spacing", mapOf(DensityChoice.COMPACT to "Compact", DensityChoice.COMFORTABLE to "Comfortable", DensityChoice.ROOMY to "Roomy"), listOf("density", "spacing", "compact", "padding"))
    val textScale = Setting(
        key = "look.textScale", default = 100f, scope = SettingScope.GLOBAL, label = "Text size (%)", keywords = listOf("text", "font", "size", "bigger", "larger"),
        codec = SettingCodecs.float, section = SettingSection.APPEARANCE, range = SettingRange(80.0, 130.0, 5.0),
    )
    val font = pick("look.font", FontChoice.VELA, FontChoice.entries, "Font", mapOf(FontChoice.VELA to "Vela", FontChoice.GROTESQUE to "Grotesque", FontChoice.SERIF to "Serif", FontChoice.MONO to "Mono", FontChoice.SYSTEM to "System"), listOf("font", "typeface", "serif", "mono"))
    val titleWeight = pick("look.titleWeight", TitleWeight.LIGHT, TitleWeight.entries, "Title weight", mapOf(TitleWeight.LIGHT to "Light", TitleWeight.NORMAL to "Regular", TitleWeight.MEDIUM to "Medium"), listOf("title", "weight", "bold", "thin"))

    val iconStyle = pick("look.iconStyle", IconStyle.FILLED, IconStyle.entries, "Icon pack", mapOf(IconStyle.FILLED to "Filled", IconStyle.LINE to "Line", IconStyle.DUOTONE to "Duotone", IconStyle.ROUNDED to "Rounded", IconStyle.OUTLINED to "Outlined", IconStyle.SHARP to "Sharp", IconStyle.TWO_TONE to "Two-tone", IconStyle.LUCIDE to "Lucide"), listOf("icons", "pack", "style", "outline", "line", "filled", "duotone", "thin", "elegant", "rounded", "soft", "sharp", "material", "lucide"), "Every pack restyles every icon in the app. Filled, Line and Duotone are drawn for Vela (Line and Duotone follow the weight and rounded-ends options). Rounded, Outlined, Sharp and Two-tone are Material icons; Lucide is a fine, rounded line set. Rounded is the soft, friendly one.")
    val iconWeight = pick("look.iconWeight", IconWeight.REGULAR, IconWeight.entries, "Icon line weight", mapOf(IconWeight.THIN to "Thin", IconWeight.REGULAR to "Regular", IconWeight.BOLD to "Bold"), listOf("icons", "weight", "thin", "bold"))
    val iconRounded = flag("look.iconRounded", true, "Rounded icon ends", listOf("icons", "rounded", "ends"))

    val progressStyle = pick(
        "look.progressStyle", ProgressStyle.BOLD_DOT, ProgressStyle.entries, "Progress bar",
        mapOf(ProgressStyle.HAIRLINE to "Hairline", ProgressStyle.HAIRLINE_DOT to "Hairline + dot", ProgressStyle.BOLD to "Bold", ProgressStyle.BOLD_DOT to "Bold + dot", ProgressStyle.RIBBON to "Ribbon", ProgressStyle.WAVE to "Wave"),
        listOf("progress", "seek", "bar", "scrubber", "timeline", "spikes", "waves", "hairline", "thumb"),
        "The player's seek bar. Every style is filled with the colour palette.",
    )
    val navTabs = Setting(
        key = "look.nav.tabs", default = NavTabs.encode(NavTabs.default), scope = SettingScope.GLOBAL, label = "Tabs in the bar", keywords = listOf("tabs", "bar", "navigation", "order", "home", "shorts", "downloads"),
        codec = SettingCodecs.string, section = SettingSection.APPEARANCE, info = "Which pages get a tab, in order (2 to 5). Settings is never a tab: it lives in the profile menu.",
    )
    val navOpenOn = pick("look.nav.openOn", NavTab.HOME, NavTab.entries.filter { it != NavTab.SEARCH }, "Open on", mapOf(NavTab.HOME to "Home", NavTab.SHORTS to "Shorts", NavTab.SUBSCRIPTIONS to "Subscriptions", NavTab.LIBRARY to "Library", NavTab.DOWNLOADS to "Downloads", NavTab.EXPLORE to "Explore"), listOf("start", "open", "launch", "first"))
    val navLabels = pick("look.nav.labels", NavLabels.ALWAYS, NavLabels.entries, "Tab labels", mapOf(NavLabels.HIDDEN to "Hidden", NavLabels.ALWAYS to "Always visible"), listOf("labels", "tabs", "text", "hide"), "Labels also hide while the bar is shrunk (see Shrink the bar while scrolling).")
    val barStyle = pick("look.nav.barStyle", BarStyle.FLOATING, BarStyle.entries, "Bar style", mapOf(BarStyle.FLOATING to "Floating", BarStyle.DOCKED to "Docked", BarStyle.MINIMAL to "Minimal"), listOf("bar", "floating", "docked", "pill", "minimal", "dock"), "Floating runs over the page (the page scrolls behind it). Docked sits below the page. Minimal is a small icon-only pill that also floats.")
    val navShrink = flag("look.nav.shrinkOnScroll", false, "Shrink the bar while scrolling", listOf("shrink", "scroll", "collapse", "bar", "dock"), "Scrolling down contracts the bar into a small icon pill (the labels hide); scrolling up brings it back. The mini player moves with it.")
    val miniLook = pick("look.mini.look", MiniLook.CARD, MiniLook.entries, "Mini player look", mapOf(MiniLook.CARD to "Regular", MiniLook.PIP to "Picture-in-picture"), listOf("mini", "player", "pip", "picture", "look", "card"), "Regular is as wide as the bar. Picture-in-picture is a small card in the corner. Both follow the corner radius.")
    val miniPlacement = pick("look.mini.placement", MiniPlacement.ATTACHED, MiniPlacement.entries, "Mini player position", mapOf(MiniPlacement.ATTACHED to "Attached to the bar", MiniPlacement.SEPARATE to "Apart from the bar"), listOf("mini", "player", "attach", "dock", "gap", "distance", "position"))
    val miniGap = Setting(
        key = "look.mini.gap", default = 8f, scope = SettingScope.GLOBAL, label = "Mini player distance from the bar (dp)", keywords = listOf("mini", "player", "gap", "distance", "space"),
        codec = SettingCodecs.float, section = SettingSection.APPEARANCE, range = SettingRange(0.0, 32.0, 2.0), info = "Used when the mini player is apart from the bar.",
    )
    val navMarker = flag("look.nav.marker", true, "Gradient mark on the selected tab", listOf("marker", "indicator", "underline", "gradient", "selected", "tab", "bar"), "A small palette-gradient line marks the selected tab: under its icon in the bottom bar, at its left edge in the side rail.")
    val railCollapsed = flag("look.nav.railCollapsed", false, "Collapse the side rail to icons", listOf("rail", "sidebar", "collapse", "icons"), "Wide windows and Linux: the left rail shows icons only.")

    val glass = flag("look.glass", true, "Frosted glass bars", listOf("glass", "blur", "frosted", "translucent"), "Bars blur what is behind them, with enough tint that their labels stay readable. Performance mode turns this off.")
    val reduceMotion = flag("look.reduceMotion", false, "Reduce motion", listOf("motion", "animation", "reduce", "accessibility"), "Shorter, simpler animations; no staggered entrances.")

    val all: List<Setting<*>> = listOf(
        theme, palette, corners, density, textScale, font, titleWeight,
        iconStyle, iconWeight, iconRounded, progressStyle, navTabs, navOpenOn, navLabels, barStyle, navShrink, navMarker, miniLook, miniPlacement, miniGap, railCollapsed, glass, reduceMotion,
    )
}

/**
 * Which settings a Look may contain. A Look is a bundle of APPEARANCE choices only: the `look.` keys, the pure-black switch and the
 * performance switch. Anything else (account safety, tier order, downloads, SponsorBlock...) is behaviour and is refused,
 * whatever a shared file claims.
 */
object LookKeys {
    val extra = setOf("appearance.look", "appearance.performanceMode")
    fun isLookKey(key: String) = key.startsWith("look.") || key in extra
    val all: Set<String> get() = LookSettings.all.map { it.key }.toSet() + extra
}
