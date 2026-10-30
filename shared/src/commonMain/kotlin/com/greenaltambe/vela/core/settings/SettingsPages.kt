package com.greenaltambe.vela.core.settings

/** The small-caps headers of the settings home (brief section 4). */
enum class SettingsGroup(val title: String) {
    MAKE_IT_YOURS("MAKE IT YOURS"), WATCHING("WATCHING"), YOUR_FEED("YOUR FEED"), ACCOUNT_AND_SAFETY("ACCOUNT & SAFETY"), OFFLINE("OFFLINE"), MAINTENANCE("MAINTENANCE"),
}

/** Where a page row goes when it is not a list of settings: another screen of the app. */
enum class SettingsLink { NONE, CUSTOMIZE, ACCOUNTS, DOWNLOAD_RULES, BACKUP, HEALTH }

/**
 * One row of the settings home. A page shows the settings it [owns]; a row with a [link] opens another screen instead (and may own
 * settings too, shown on top of the page it leads to). [tile] is the icon tile's colour slot, drawn from the Look's tokens by the UI.
 */
class SettingsPage(
    val id: String, val title: String, val group: SettingsGroup, val tile: Int, val link: SettingsLink = SettingsLink.NONE,
    val owns: (Setting<*>) -> Boolean = { false },
)

object SettingsPages {
    private fun keys(vararg k: String): (Setting<*>) -> Boolean = { it.key in k }
    private fun prefix(vararg p: String): (Setting<*>) -> Boolean = { s -> p.any { s.key.startsWith(it) } }

    private val safetyKeys = setOf("playback.accountFallbackMode", "playback.allowAgeRestricted", "playback.allowControversial")
    private val storageKeys = setOf("downloads.folder", "downloads.storageCapGb", "downloads.deleteAfterWatch")

    val all: List<SettingsPage> = listOf(
        SettingsPage("customize", "Customize Vela", SettingsGroup.MAKE_IT_YOURS, 0, SettingsLink.CUSTOMIZE) { LookKeys.isLookKey(it.key) },
        SettingsPage("playback", "Playback & quality", SettingsGroup.WATCHING, 1) { it.key.startsWith("playback.") && it.key !in safetyKeys },
        SettingsPage("extras", "SponsorBlock & extras", SettingsGroup.WATCHING, 2, owns = prefix("sponsorblock.", "ryd.", "dearrow.", "modules.")),
        SettingsPage("home", "Home", SettingsGroup.YOUR_FEED, 3, owns = keys("home.source")),
        SettingsPage("feed", "Subscriptions & feed filters", SettingsGroup.YOUR_FEED, 4, owns = prefix("feed.hide", "feed.min", "feed.block", "feed.allow", "feed.keywords")),
        SettingsPage("shorts", "Shorts", SettingsGroup.YOUR_FEED, 5, owns = keys("feed.shortsMode")),
        SettingsPage("accounts", "Accounts", SettingsGroup.ACCOUNT_AND_SAFETY, 6, SettingsLink.ACCOUNTS),
        SettingsPage("safety", "Account safety", SettingsGroup.ACCOUNT_AND_SAFETY, 7) { it.key.startsWith("account.") || it.key in safetyKeys },
        SettingsPage("privacy", "Privacy", SettingsGroup.ACCOUNT_AND_SAFETY, 8, owns = keys("history.localOnly")),
        SettingsPage("downloads", "Downloads", SettingsGroup.OFFLINE, 9) { it.key.startsWith("downloads.") && it.key !in storageKeys && !it.key.startsWith("downloads.auto.") },
        SettingsPage("auto", "Automatic downloads", SettingsGroup.OFFLINE, 10, SettingsLink.DOWNLOAD_RULES, owns = prefix("downloads.auto.")),
        SettingsPage("storage", "Storage", SettingsGroup.OFFLINE, 11, owns = { it.key in storageKeys }),
        SettingsPage("backup", "Backup & restore", SettingsGroup.MAINTENANCE, 12, SettingsLink.BACKUP),
        SettingsPage("updates", "Updates", SettingsGroup.MAINTENANCE, 13, owns = prefix("ytdlp.", "remoteConfig.")),
        SettingsPage("health", "Health & diagnostics", SettingsGroup.MAINTENANCE, 14, SettingsLink.HEALTH, owns = prefix("debug.")),
    )

    fun byId(id: String): SettingsPage? = all.firstOrNull { it.id == id }

    /** The settings a page shows, in registry order. Each setting belongs to the FIRST page that owns it. */
    fun settingsOf(page: SettingsPage, registry: List<Setting<*>> = Settings.all): List<Setting<*>> =
        registry.filter { s -> all.firstOrNull { it.owns(s) } === page }

    /** One titled block of a page: related settings together, so a long page reads as a few small cards instead of one list. */
    class Section(val title: String, val settings: List<Setting<*>>)

    /** The title of the block [s] sits in on [page]; blank for a page that is one block. */
    fun sectionOf(page: SettingsPage, s: Setting<*>): String = when (page.id) {
        "extras" -> when {
            s.key.startsWith("sponsorblock.") -> "SponsorBlock"
            s.key.startsWith("ryd.") -> "Return YouTube Dislike"
            s.key.startsWith("dearrow.") -> "DeArrow"
            else -> "Modules"
        }
        "playback" -> when (s.key) {
            "playback.speed", "playback.autoplayNext", "playback.seekSeconds", "playback.holdSpeed" -> "Playing"
            "playback.maxQualityWifi", "playback.maxQualityCellular", "playback.preferredCodec", "playback.audioOnly" -> "Quality and data"
            else -> "Reliability"
        }
        "downloads" -> when (s.key) {
            "downloads.defaultQuality", "downloads.audioOnlyDefault", "downloads.preferAvc", "downloads.keepOriginalAudio" -> "What to download"
            "downloads.wifiOnly", "downloads.concurrent" -> "Network and queue"
            else -> "Saved with a download"
        }
        "safety" -> when {
            s.key.startsWith("account.budget.") -> "Request limits"
            s.key.startsWith("playback.allow") -> "Content"
            s.key in setOf("account.reportActivity", "account.profilesExperimental", "session.nowPlaying") -> "Reporting"
            else -> "When the account is used"
        }
        "feed" -> if (s.key.startsWith("feed.hide") || s.key.startsWith("feed.min")) "Hide videos" else "Keyword rules"
        "updates" -> if (s.key.startsWith("ytdlp.")) "yt-dlp" else "Remote settings"
        else -> ""
    }

    /** [settingsOf] split into the page's blocks, in the order they first appear. */
    fun sectionsOf(page: SettingsPage, registry: List<Setting<*>> = Settings.all): List<Section> =
        settingsOf(page, registry).groupBy { sectionOf(page, it) }.map { (t, list) -> Section(t, list) }

    /** The page that shows [setting], or null if none does (a test makes sure that never happens). */
    fun pageOf(setting: Setting<*>): SettingsPage? = all.firstOrNull { it.owns(setting) }

    /** Settings the pages do not show, for the "none is orphaned" test. Appearance settings are shown by the Customize pages instead. */
    fun orphans(registry: List<Setting<*>> = Settings.all): List<String> = registry.filter { pageOf(it) == null }.map { it.key }
}

/**
 * The one-line live summary under a page title, generated from the current values (never hand-written strings that go stale):
 * "720p on Wi-Fi · 1.25x", "Floating bar · 4 tabs", "Hide watched · min 5 min".
 */
object SettingsSummaries {
    private fun <T> SettingsResolver.v(s: Setting<T>) = resolve(s)
    private fun SettingsResolver.label(s: Setting<*>): String = rawValue(s).let { raw -> s.choices.firstOrNull { it.raw == raw }?.label ?: raw }
    private fun onOff(b: Boolean) = if (b) "on" else "off"
    private fun num(f: Float) = if (f == f.toLong().toFloat()) f.toLong().toString() else f.toString()

    fun of(page: SettingsPage, s: SettingsResolver): String = when (page.id) {
        "customize" -> "${s.label(LookSettings.theme)}${if (s.v(Settings.look) == LookChoice.PURE_BLACK) " · pure black" else ""} · ${s.label(LookSettings.barStyle)} · ${NavTabs.parse(s.v(LookSettings.navTabs)).size} tabs"
        "playback" -> "${s.label(Settings.maxQualityUnmetered)} on Wi-Fi · ${num(s.v(Settings.defaultPlaybackSpeed))}x${if (s.v(Settings.audioOnly)) " · audio only" else ""}"
        "extras" -> "SponsorBlock ${onOff(s.v(Settings.sponsorBlockEnabled))} · RYD ${onOff(s.v(Settings.returnYouTubeDislikeEnabled))} · DeArrow ${onOff(s.v(Settings.deArrowEnabled))}"
        "home" -> s.label(Settings.homeSource)
        "feed" -> buildList {
            add(if (s.v(Settings.hideWatched)) "hide watched" else "show watched")
            s.v(Settings.minLengthSeconds).takeIf { it > 0 }?.let { add("min ${(it / 60).toInt().coerceAtLeast(1)} min") }
            if (s.v(Settings.blockKeywords).isNotBlank() || s.v(Settings.allowKeywords).isNotBlank()) add("keyword rules")
        }.joinToString(" · ")
        "shorts" -> s.label(Settings.shortsMode)
        "accounts" -> "Sign in, switch, add"
        "safety" -> "Account ${s.label(Settings.accountFallbackMode).lowercase()} · age-restricted ${onOff(s.v(Settings.allowAgeRestricted))}${if (s.v(Settings.accountKillSwitch)) " · PAUSED" else ""}"
        "privacy" -> if (s.v(Settings.localOnlyHistory)) "History stays on this device" else "History may be shared with your account"
        "downloads" -> "${s.label(Settings.downloadsDefaultQuality)}${if (s.v(Settings.downloadsWifiOnly)) " · Wi-Fi only" else ""} · ${num(s.v(Settings.downloadsConcurrent))} at once"
        "auto" -> "Rules for new videos"
        "storage" -> "Cap ${num(s.v(Settings.downloadsStorageCapGb))} GB${if (s.v(Settings.downloadsDeleteAfterWatch)) " · delete after watching" else ""}"
        "backup" -> "Save or restore your data"
        "updates" -> "yt-dlp: ${s.label(Settings.ytDlpAutoUpdate).lowercase()}"
        "health" -> "Source checks and the tier log${if (s.resolve(Settings.debugFrameStats)) " · frame times shown" else ""}"
        else -> ""
    }
}
