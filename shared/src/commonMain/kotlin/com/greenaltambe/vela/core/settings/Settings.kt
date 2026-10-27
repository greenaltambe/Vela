package com.greenaltambe.vela.core.settings

enum class AccountFallbackMode { ONLY_ON_FAILURE, ALWAYS, NEVER }

enum class ShortsMode { HIDE, SHOW, SEPARATE_TAB }

/** Registry of every setting the app knows about. */
object Settings {
    val accountFallbackMode = Setting(
        key = "playback.accountFallbackMode",
        default = AccountFallbackMode.ONLY_ON_FAILURE,
        scope = SettingScope.GLOBAL,
        label = "Account fallback",
        keywords = listOf("login", "account", "blocked", "sign in", "fallback"),
        codec = SettingCodecs.enum(AccountFallbackMode.entries),
        section = SettingSection.PLAYBACK,
        choices = listOf(
            SettingChoice(AccountFallbackMode.ONLY_ON_FAILURE.name, "Only on failure"),
            SettingChoice(AccountFallbackMode.ALWAYS.name, "Always use account"),
            SettingChoice(AccountFallbackMode.NEVER.name, "Never"),
        ),
    )

    val shortsMode = Setting(
        key = "feed.shortsMode",
        default = ShortsMode.SHOW,
        scope = SettingScope.CHANNEL,
        label = "Shorts",
        keywords = listOf("shorts", "hide", "feed", "tab"),
        codec = SettingCodecs.enum(ShortsMode.entries),
        section = SettingSection.FEED,
        choices = listOf(
            SettingChoice(ShortsMode.HIDE.name, "Hide"),
            SettingChoice(ShortsMode.SHOW.name, "Show"),
            SettingChoice(ShortsMode.SEPARATE_TAB.name, "Separate tab"),
        ),
    )

    val defaultPlaybackSpeed = Setting(
        key = "playback.speed",
        default = 1f,
        scope = SettingScope.VIDEO,
        label = "Playback speed",
        keywords = listOf("speed", "rate", "faster", "slower"),
        codec = SettingCodecs.float,
        section = SettingSection.PLAYBACK,
        range = SettingRange(min = 0.25, max = 4.0, step = 0.25),
    )

    /** Resolved from PATH when it is a bare name. Desktop only; Android has no yt-dlp tier. */
    val ytDlpPath = Setting(
        key = "ytdlp.path",
        default = "yt-dlp",
        scope = SettingScope.GLOBAL,
        label = "yt-dlp location",
        keywords = listOf("yt-dlp", "youtube-dl", "binary", "path", "extractor", "fallback"),
        codec = SettingCodecs.string,
        section = SettingSection.ADVANCED,
    )

    val sponsorBlockEnabled = moduleToggle("sponsorblock", "SponsorBlock", "sponsor", "skip", "segments")
    val returnYouTubeDislikeEnabled = moduleToggle("ryd", "Return YouTube Dislike", "dislike", "ryd", "likes")
    val deArrowEnabled = moduleToggle("dearrow", "DeArrow", "titles", "thumbnails", "clickbait")

    val all: List<Setting<*>> = listOf(
        accountFallbackMode, shortsMode, defaultPlaybackSpeed, ytDlpPath,
        sponsorBlockEnabled, returnYouTubeDislikeEnabled, deArrowEnabled,
    )

    /**
     * Settings whose label or keywords match every word of [query] (case-insensitive substring match),
     * label matches first, then registry order. A blank query matches nothing.
     */
    fun search(query: String, settings: List<Setting<*>> = all): List<Setting<*>> {
        val words = query.lowercase().split(' ', '\t').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return settings
            .filter { setting ->
                val haystack = listOf(setting.label) + setting.keywords
                words.all { word -> haystack.any { it.contains(word, ignoreCase = true) } }
            }
            .sortedBy { setting -> if (words.all { setting.label.contains(it, ignoreCase = true) }) 0 else 1 }
    }

    private fun moduleToggle(id: String, label: String, vararg keywords: String) = Setting(
        key = "modules.$id.enabled",
        default = true,
        scope = SettingScope.GLOBAL,
        label = label,
        keywords = keywords.toList(),
        codec = SettingCodecs.boolean,
        section = SettingSection.MODULES,
    )
}
