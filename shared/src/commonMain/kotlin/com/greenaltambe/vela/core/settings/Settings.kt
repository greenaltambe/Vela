package com.greenaltambe.vela.core.settings

enum class AccountFallbackMode { ONLY_ON_FAILURE, ALWAYS, NEVER }

/** What to do when a newer yt-dlp exists: nothing, offer it on Health (default), or install it (and roll back if the yt-dlp source regresses). */
enum class YtDlpAutoUpdate { OFF, ASK, AUTO }

enum class ShortsMode { HIDE, SHOW, SEPARATE_TAB }

/** Highest resolution to play; [height] is the cap in pixels of video height, null for no cap. */
enum class MaxQuality(val height: Int?) {
    P360(360), P480(480), P720(720), P1080(1080), P1440(1440), P2160(2160), BEST(null)
}

/** In order of how modern the codec is; the selector falls back down this list from the preferred one. */
enum class PreferredCodec { AV1, VP9, AVC }

/** The saved appearance to use; both follow nothing but their own palette except [DEFAULT], which follows system light/dark. */
enum class LookChoice { DEFAULT, PURE_BLACK }

/** How DeArrow titles are cased. DeArrow titles are community-written; many are fine as submitted. */
/** Mirrors the SponsorBlock engine's actions; kept here so the schema does not depend on the module. */
enum class SegmentActionSetting { SKIP, MUTE, PROMPT, IGNORE }

enum class TitleCasing { AS_SUBMITTED, SENTENCE_CASE, TITLE_CASE }

/** Registry of every setting the app knows about. */
object Settings {
    val accountFallbackMode = Setting(
        key = "playback.accountFallbackMode",
        default = AccountFallbackMode.ONLY_ON_FAILURE,
        scope = SettingScope.GLOBAL,
        label = "Use the account for playback",
        keywords = listOf("login", "account", "blocked", "sign in", "fallback"),
        codec = SettingCodecs.enum(AccountFallbackMode.entries),
        section = SettingSection.ACCOUNT,
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

    val maxQualityUnmetered = maxQualitySetting(
        key = "playback.maxQualityWifi", default = MaxQuality.P1080, label = "Maximum quality on Wi-Fi",
        keywords = listOf("quality", "resolution", "wifi", "wi-fi", "unmetered", "1080p", "4k", "cap"),
    )

    val maxQualityMetered = maxQualitySetting(
        key = "playback.maxQualityCellular", default = MaxQuality.P720, label = "Maximum quality on mobile data",
        keywords = listOf("quality", "resolution", "cellular", "mobile", "metered", "data", "720p", "cap"),
    )

    val preferredCodec = Setting(
        key = "playback.preferredCodec",
        default = PreferredCodec.VP9,
        scope = SettingScope.CHANNEL,
        label = "Preferred video codec",
        keywords = listOf("codec", "av1", "vp9", "avc", "h264", "decoder", "battery"),
        codec = SettingCodecs.enum(PreferredCodec.entries),
        section = SettingSection.PLAYBACK,
        choices = PreferredCodec.entries.map { SettingChoice(it.name, it.name) },
    )

    val audioOnly = Setting(
        key = "playback.audioOnly",
        default = false,
        scope = SettingScope.CHANNEL,
        label = "Audio only",
        keywords = listOf("audio", "music", "background", "data", "video off"),
        codec = SettingCodecs.boolean,
        section = SettingSection.PLAYBACK,
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

    val ytDlpAutoUpdate = Setting(
        key = "ytdlp.autoUpdate", default = YtDlpAutoUpdate.ASK, scope = SettingScope.GLOBAL,
        label = "Update the app's own yt-dlp copy",
        keywords = listOf("yt-dlp", "update", "auto", "upgrade", "extractor"),
        codec = SettingCodecs.enum(YtDlpAutoUpdate.entries), section = SettingSection.ADVANCED,
        choices = listOf(
            SettingChoice(YtDlpAutoUpdate.OFF.name, "Off"),
            SettingChoice(YtDlpAutoUpdate.ASK.name, "Ask me"),
            SettingChoice(YtDlpAutoUpdate.AUTO.name, "Automatically"),
        ),
    )

    val look = Setting(
        key = "appearance.look",
        default = LookChoice.DEFAULT,
        scope = SettingScope.GLOBAL,
        label = "Look",
        keywords = listOf("theme", "dark", "light", "black", "amoled", "appearance", "colour", "color"),
        codec = SettingCodecs.enum(LookChoice.entries),
        section = SettingSection.APPEARANCE,
        choices = listOf(
            SettingChoice(LookChoice.DEFAULT.name, "Default (follows system)"),
            SettingChoice(LookChoice.PURE_BLACK.name, "Pure black"),
        ),
    )

    val performanceMode = Setting(
        key = "appearance.performanceMode",
        default = false,
        scope = SettingScope.GLOBAL,
        label = "Performance mode",
        keywords = listOf("blur", "glass", "effects", "slow", "battery", "fast"),
        codec = SettingCodecs.boolean,
        section = SettingSection.APPEARANCE,
    )

    val localOnlyHistory = Setting(
        key = "history.localOnly",
        default = false,
        scope = SettingScope.GLOBAL,
        label = "Keep watch history on this device only",
        keywords = listOf("history", "local", "private", "sync", "youtube"),
        codec = SettingCodecs.boolean,
        section = SettingSection.LIBRARY,
    )

    val hideWatched = Setting(
        key = "feed.hideWatched",
        default = false,
        scope = SettingScope.CHANNEL,
        label = "Hide watched videos",
        keywords = listOf("watched", "seen", "feed", "hide", "history"),
        codec = SettingCodecs.boolean,
        section = SettingSection.FEED,
    )

    val minLengthSeconds = Setting(
        key = "feed.minLengthSeconds",
        default = 0f,
        scope = SettingScope.CHANNEL,
        label = "Minimum video length (seconds)",
        keywords = listOf("length", "duration", "short", "minimum", "feed", "filter"),
        codec = SettingCodecs.float,
        section = SettingSection.FEED,
        range = SettingRange(min = 0.0, max = 3600.0, step = 30.0),
    )

    val blockKeywords = Setting(
        key = "feed.blockKeywords",
        default = "",
        scope = SettingScope.CHANNEL,
        label = "Hide videos with these words in the title",
        keywords = listOf("keyword", "block", "filter", "mute", "words", "feed", "regex"),
        codec = SettingCodecs.string,
        section = SettingSection.FEED,
    )

    val allowKeywords = Setting(
        key = "feed.allowKeywords",
        default = "",
        scope = SettingScope.CHANNEL,
        label = "Only show videos with these words in the title",
        keywords = listOf("keyword", "allow", "only", "filter", "words", "feed", "regex"),
        codec = SettingCodecs.string,
        section = SettingSection.FEED,
    )

    val keywordsAreRegex = Setting(
        key = "feed.keywordsAreRegex",
        default = false,
        scope = SettingScope.GLOBAL,
        label = "Treat keywords as regular expressions",
        keywords = listOf("regex", "regular expression", "keyword", "pattern", "feed"),
        codec = SettingCodecs.boolean,
        section = SettingSection.FEED,
    )

    val sponsorBlockEnabled = moduleToggle("sponsorblock", "SponsorBlock", "sponsor", "skip", "segments")
    val returnYouTubeDislikeEnabled = moduleToggle("ryd", "Return YouTube Dislike", "dislike", "ryd", "likes")
    val deArrowEnabled = moduleToggle("dearrow", "DeArrow", "titles", "thumbnails", "clickbait")

    // ---- SponsorBlock ----

    val sponsorBlockMinSegmentSeconds = Setting(
        key = "sponsorblock.minSegmentSeconds", default = 1f, scope = SettingScope.GLOBAL,
        label = "Ignore segments shorter than (seconds)",
        keywords = listOf("sponsorblock", "segment", "minimum", "short", "duration"),
        codec = SettingCodecs.float, section = SettingSection.MODULES,
        range = SettingRange(min = 0.0, max = 30.0, step = 0.5),
    )

    val sponsorBlockNotifications = Setting(
        key = "sponsorblock.notifications", default = true, scope = SettingScope.GLOBAL,
        label = "Show a notice with Undo when a segment is skipped",
        keywords = listOf("sponsorblock", "notification", "toast", "undo", "snackbar"),
        codec = SettingCodecs.boolean, section = SettingSection.MODULES,
    )

    val sponsorBlockServer = Setting(
        key = "sponsorblock.server", default = "https://sponsor.ajay.app", scope = SettingScope.GLOBAL,
        label = "SponsorBlock server",
        keywords = listOf("sponsorblock", "server", "url", "mirror", "dearrow"),
        codec = SettingCodecs.string, section = SettingSection.ADVANCED,
    )

    /** A random id that identifies this install to the SponsorBlock server when submitting; created on first use. */
    val sponsorBlockUserId = Setting(
        key = "sponsorblock.userId", default = "", scope = SettingScope.GLOBAL,
        label = "SponsorBlock user id (private)",
        keywords = listOf("sponsorblock", "user id", "submit", "private"),
        codec = SettingCodecs.string, section = SettingSection.ADVANCED,
    )

    /** One action setting per category, overridable per channel. Keys are `sponsorblock.action.<category id>`. */
    val sponsorBlockActions: Map<String, Setting<SegmentActionSetting>> = com.greenaltambe.vela.modules.sponsorblock.SegmentCategory.entries.associate { c ->
        c.id to Setting(
            key = "sponsorblock.action.${c.id}",
            default = defaultActionFor(c),
            scope = SettingScope.CHANNEL,
            label = "SponsorBlock: ${c.label}",
            keywords = listOf("sponsorblock", "segment", "skip", "mute", "prompt", c.id, c.label.lowercase()),
            codec = SettingCodecs.enum(SegmentActionSetting.entries),
            section = SettingSection.MODULES,
            choices = listOf(
                SettingChoice(SegmentActionSetting.SKIP.name, "Skip"),
                SettingChoice(SegmentActionSetting.MUTE.name, "Mute"),
                SettingChoice(SegmentActionSetting.PROMPT.name, "Ask (show a Skip button)"),
                SettingChoice(SegmentActionSetting.IGNORE.name, "Do nothing"),
            ),
        )
    }

    // ---- Return YouTube Dislike ----

    val rydInFeed = Setting(
        key = "ryd.inFeed", default = false, scope = SettingScope.GLOBAL,
        label = "Show likes and dislikes on video cards",
        keywords = listOf("dislike", "ryd", "likes", "feed", "cards", "rating"),
        codec = SettingCodecs.boolean, section = SettingSection.MODULES,
    )

    val rydServer = Setting(
        key = "ryd.server", default = "https://returnyoutubedislikeapi.com", scope = SettingScope.GLOBAL,
        label = "Return YouTube Dislike server",
        keywords = listOf("dislike", "ryd", "server", "url"),
        codec = SettingCodecs.string, section = SettingSection.ADVANCED,
    )

    // ---- DeArrow ----

    val deArrowFeed = deArrowSurface("feed", "the subscription feed and Home")
    val deArrowSearch = deArrowSurface("search", "search results")
    val deArrowVideoPage = deArrowSurface("videoPage", "the video page")
    val deArrowPlayerTitle = deArrowSurface("playerTitle", "the player title (and media notification)")

    val deArrowTitleCasing = Setting(
        key = "dearrow.titleCasing", default = TitleCasing.AS_SUBMITTED, scope = SettingScope.GLOBAL,
        label = "DeArrow title casing",
        keywords = listOf("dearrow", "title", "case", "capital", "sentence"),
        codec = SettingCodecs.enum(TitleCasing.entries), section = SettingSection.MODULES,
        choices = listOf(
            SettingChoice(TitleCasing.AS_SUBMITTED.name, "As submitted"),
            SettingChoice(TitleCasing.SENTENCE_CASE.name, "Sentence case"),
            SettingChoice(TitleCasing.TITLE_CASE.name, "Title Case"),
        ),
    )

    val deArrowMinVotes = Setting(
        key = "dearrow.minVotes", default = 0f, scope = SettingScope.GLOBAL,
        label = "DeArrow: minimum votes to trust a submission (locked ones always count)",
        keywords = listOf("dearrow", "votes", "confidence", "trust", "threshold"),
        codec = SettingCodecs.float, section = SettingSection.MODULES,
        range = SettingRange(min = -2.0, max = 10.0, step = 1.0),
    )

    val deArrowGeneratedThumbnails = Setting(
        key = "dearrow.generatedThumbnails", default = false, scope = SettingScope.GLOBAL,
        label = "DeArrow: use a generated frame when there is no submitted thumbnail",
        keywords = listOf("dearrow", "thumbnail", "generated", "frame", "random"),
        codec = SettingCodecs.boolean, section = SettingSection.MODULES,
    )

    val deArrowThumbnailServer = Setting(
        key = "dearrow.thumbnailServer", default = "https://dearrow-thumb.ajay.app", scope = SettingScope.GLOBAL,
        label = "DeArrow thumbnail server",
        keywords = listOf("dearrow", "thumbnail", "server", "url"),
        codec = SettingCodecs.string, section = SettingSection.ADVANCED,
    )

    private fun defaultActionFor(c: com.greenaltambe.vela.modules.sponsorblock.SegmentCategory) = when (c) {
        com.greenaltambe.vela.modules.sponsorblock.SegmentCategory.SPONSOR,
        com.greenaltambe.vela.modules.sponsorblock.SegmentCategory.SELFPROMO,
        com.greenaltambe.vela.modules.sponsorblock.SegmentCategory.INTERACTION -> SegmentActionSetting.SKIP
        com.greenaltambe.vela.modules.sponsorblock.SegmentCategory.FILLER -> SegmentActionSetting.IGNORE
        else -> SegmentActionSetting.PROMPT
    }

    private fun deArrowSurface(key: String, where: String) = Setting(
        key = "dearrow.$key", default = true, scope = SettingScope.GLOBAL,
        label = "DeArrow titles and thumbnails in $where",
        keywords = listOf("dearrow", "title", "thumbnail", "clickbait", key.lowercase()),
        codec = SettingCodecs.boolean, section = SettingSection.MODULES,
    )

    // ---- Downloads ----

    /** Empty means the platform default: the app's own folder on Android, the XDG videos folder on Linux. */
    val downloadsFolder = Setting(
        key = "downloads.folder", default = "", scope = SettingScope.GLOBAL,
        label = "Download folder (empty = default)",
        keywords = listOf("download", "folder", "directory", "sd card", "storage", "location", "path"),
        codec = SettingCodecs.string, section = SettingSection.DOWNLOADS,
    )

    val downloadsDefaultQuality = Setting(
        key = "downloads.defaultQuality", default = MaxQuality.P1080, scope = SettingScope.GLOBAL,
        label = "Default download quality",
        keywords = listOf("download", "quality", "resolution", "1080p", "720p", "default"),
        codec = SettingCodecs.enum(MaxQuality.entries), section = SettingSection.DOWNLOADS,
        choices = MaxQuality.entries.map { SettingChoice(it.name, if (it.height == null) "Best available" else "${it.height}p") },
    )

    val downloadsAudioOnlyDefault = Setting(
        key = "downloads.audioOnlyDefault", default = false, scope = SettingScope.GLOBAL,
        label = "Download audio only by default",
        keywords = listOf("download", "audio", "music", "podcast", "default"),
        codec = SettingCodecs.boolean, section = SettingSection.DOWNLOADS,
    )

    val downloadsPreferAvc = Setting(
        key = "downloads.preferAvc", default = false, scope = SettingScope.GLOBAL,
        label = "Prefer H.264 (plays on every device) over VP9/AV1",
        keywords = listOf("download", "avc", "h264", "codec", "compatibility", "mp4"),
        codec = SettingCodecs.boolean, section = SettingSection.DOWNLOADS,
    )

    val downloadsKeepOriginalAudio = Setting(
        key = "downloads.keepOriginalAudio", default = true, scope = SettingScope.GLOBAL,
        label = "Audio-only: keep the original container (m4a/webm) instead of remuxing Opus to .opus",
        keywords = listOf("download", "audio", "container", "m4a", "webm", "opus", "ogg"),
        codec = SettingCodecs.boolean, section = SettingSection.DOWNLOADS,
    )

    val downloadsWifiOnly = Setting(
        key = "downloads.wifiOnly", default = true, scope = SettingScope.GLOBAL,
        label = "Download on Wi-Fi / unmetered connections only",
        keywords = listOf("download", "wifi", "wi-fi", "mobile data", "metered", "cellular"),
        codec = SettingCodecs.boolean, section = SettingSection.DOWNLOADS,
    )

    val downloadsConcurrent = Setting(
        key = "downloads.concurrent", default = 2f, scope = SettingScope.GLOBAL,
        label = "Simultaneous downloads",
        keywords = listOf("download", "concurrent", "parallel", "queue", "simultaneous"),
        codec = SettingCodecs.float, section = SettingSection.DOWNLOADS,
        range = SettingRange(min = 1.0, max = 4.0, step = 1.0),
    )

    val downloadsDeleteAfterWatch = Setting(
        key = "downloads.deleteAfterWatch", default = false, scope = SettingScope.GLOBAL,
        label = "Delete a download after watching it to the end",
        keywords = listOf("download", "delete", "watched", "auto", "cleanup", "space"),
        codec = SettingCodecs.boolean, section = SettingSection.DOWNLOADS,
    )

    /** Display only for now: the storage screen shows usage against it; nothing is deleted when it is exceeded. */
    val downloadsStorageCapGb = Setting(
        key = "downloads.storageCapGb", default = 0f, scope = SettingScope.GLOBAL,
        label = "Storage cap in GB (0 = none; shown only, not enforced)",
        keywords = listOf("download", "storage", "cap", "limit", "gb", "space"),
        codec = SettingCodecs.float, section = SettingSection.DOWNLOADS,
        range = SettingRange(min = 0.0, max = 500.0, step = 5.0),
    )

    val downloadsCommentPages = Setting(
        key = "downloads.commentPages", default = 2f, scope = SettingScope.GLOBAL,
        label = "Comment pages to save with a download",
        keywords = listOf("download", "comments", "offline", "pages", "save"),
        codec = SettingCodecs.float, section = SettingSection.DOWNLOADS,
        range = SettingRange(min = 0.0, max = 10.0, step = 1.0),
    )

    val accountReportActivity = Setting(
        key = "account.reportActivity", default = false, scope = SettingScope.GLOBAL,
        label = "Tell YouTube what I watch (adds to the account's history)",
        keywords = listOf("account", "history", "activity", "report", "watch"),
        codec = SettingCodecs.boolean, section = SettingSection.ACCOUNT,
    )

    val accountUseForChannels = Setting(
        key = "account.useForChannels", default = false, scope = SettingScope.GLOBAL,
        label = "Use the account for channel pages",
        keywords = listOf("account", "channel", "login"),
        codec = SettingCodecs.boolean, section = SettingSection.ACCOUNT,
    )

    /** The mini player's video, kept so it comes back after the OS kills the process (not user-facing, so not in [all]). */
    val restoreNowPlaying = Setting(
        key = "session.nowPlaying", default = "", scope = SettingScope.GLOBAL, label = "Last playing video",
        keywords = emptyList(), codec = SettingCodecs.string, section = SettingSection.ADVANCED,
    )

    val slowStreamFailover = Setting(
        key = "playback.slowStreamFailover", default = true, scope = SettingScope.GLOBAL,
        label = "Switch source when a stream keeps buffering",
        keywords = listOf("buffering", "slow", "failover", "stall", "source", "tier"),
        codec = SettingCodecs.boolean, section = SettingSection.PLAYBACK,
    )

    val accountUseForDetails = Setting(
        key = "account.useForDetails", default = false, scope = SettingScope.GLOBAL,
        label = "Use the account for video details and comments",
        keywords = listOf("account", "details", "comments", "login"),
        codec = SettingCodecs.boolean, section = SettingSection.ACCOUNT,
    )

    val accountDownloadsMayUse = Setting(
        key = "account.downloadsMayUseAccount", default = false, scope = SettingScope.GLOBAL,
        label = "Downloads may use the account when anonymous fails",
        keywords = listOf("account", "download", "login"),
        codec = SettingCodecs.boolean, section = SettingSection.ACCOUNT,
    )

    val accountKillSwitch = Setting(
        key = "account.killSwitch", default = false, scope = SettingScope.GLOBAL,
        label = "Stop ALL traffic that carries account credentials",
        keywords = listOf("account", "kill", "stop", "disable", "emergency"),
        codec = SettingCodecs.boolean, section = SettingSection.ACCOUNT,
    )

    val accountTierOrder = Setting(
        key = "account.tierOrder", default = "ANONYMOUS,ALT_CLIENT,YT_DLP,AUTHENTICATED", scope = SettingScope.GLOBAL,
        label = "Order sources are tried in",
        keywords = listOf("account", "order", "tier", "fallback", "sources"),
        codec = SettingCodecs.string, section = SettingSection.ACCOUNT,
    )

    /** Empty = bundled configuration only. Must be https; the file is only accepted with a valid Ed25519 signature (docs/remote-config.md). */
    val remoteConfigUrl = Setting(
        key = "remoteConfig.url", default = "", scope = SettingScope.GLOBAL,
        label = "Remote configuration URL",
        keywords = listOf("remote", "config", "update", "profiles", "kill switch", "signed"),
        codec = SettingCodecs.string, section = SettingSection.ADVANCED,
    )

    val accountPlaybackPerHour = Setting(
        key = "account.budget.playbackPerHour", default = 20f, scope = SettingScope.GLOBAL,
        label = "Account playback resolves per hour",
        keywords = listOf("account", "budget", "limit", "playback"),
        codec = SettingCodecs.float, section = SettingSection.ACCOUNT,
        range = SettingRange(min = 1.0, max = 120.0, step = 1.0),
    )

    val accountBrowsePerMinute = Setting(
        key = "account.budget.browsePerMinute", default = 6f, scope = SettingScope.GLOBAL,
        label = "Account browse/sync calls per minute",
        keywords = listOf("account", "budget", "limit", "browse", "sync"),
        codec = SettingCodecs.float, section = SettingSection.ACCOUNT,
        range = SettingRange(min = 1.0, max = 30.0, step = 1.0),
    )

    val accountWritesPerHour = Setting(
        key = "account.budget.writesPerHour", default = 10f, scope = SettingScope.GLOBAL,
        label = "Account writes per hour",
        keywords = listOf("account", "budget", "limit", "write"),
        codec = SettingCodecs.float, section = SettingSection.ACCOUNT,
        range = SettingRange(min = 0.0, max = 60.0, step = 1.0),
    )

    val all: List<Setting<*>> = listOf(
        look, performanceMode, localOnlyHistory, hideWatched, minLengthSeconds, blockKeywords, allowKeywords, keywordsAreRegex,
        slowStreamFailover, accountFallbackMode, accountReportActivity, accountUseForChannels, accountUseForDetails, accountDownloadsMayUse, accountKillSwitch, accountTierOrder, remoteConfigUrl, ytDlpAutoUpdate,
        accountPlaybackPerHour, accountBrowsePerMinute, accountWritesPerHour, shortsMode, defaultPlaybackSpeed, maxQualityUnmetered, maxQualityMetered, preferredCodec, audioOnly, ytDlpPath,
        sponsorBlockEnabled, returnYouTubeDislikeEnabled, deArrowEnabled,
        sponsorBlockMinSegmentSeconds, sponsorBlockNotifications, sponsorBlockServer, sponsorBlockUserId, rydInFeed, rydServer,
        deArrowFeed, deArrowSearch, deArrowVideoPage, deArrowPlayerTitle, deArrowTitleCasing, deArrowMinVotes, deArrowGeneratedThumbnails, deArrowThumbnailServer,
        downloadsFolder, downloadsDefaultQuality, downloadsAudioOnlyDefault, downloadsPreferAvc, downloadsKeepOriginalAudio,
        downloadsWifiOnly, downloadsConcurrent, downloadsDeleteAfterWatch, downloadsStorageCapGb, downloadsCommentPages,
    ) + sponsorBlockActions.values

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

    private fun maxQualitySetting(key: String, default: MaxQuality, label: String, keywords: List<String>) = Setting(
        key = key,
        default = default,
        scope = SettingScope.CHANNEL,
        label = label,
        keywords = keywords,
        codec = SettingCodecs.enum(MaxQuality.entries),
        section = SettingSection.PLAYBACK,
        choices = MaxQuality.entries.map { SettingChoice(it.name, if (it.height == null) "Best available" else "${it.height}p") },
    )

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
