package com.greenaltambe.vela.core.settings

/**
 * What a setting is called and explained as on screen. The schema's [Setting.label] stays the long, searchable sentence; the UI shows a short [title],
 * one line of [description] under it, and for sliders a [unit] after the number. A setting without an entry shows its label and its info text.
 */
object SettingCopy {
    private class Copy(val title: String, val description: String = "", val unit: String = "")

    private val table: Map<String, Copy> = mapOf(
        "playback.accountFallbackMode" to Copy("Use the account for playback", "When playback may use your signed-in account."),
        "feed.shortsMode" to Copy("Shorts", "Hide Shorts, show them in feeds, or give them their own tab."),
        "playback.speed" to Copy("Playback speed", "The speed new videos start at.", "x"),
        "playback.autoplayNext" to Copy("Autoplay next video", "When a video ends, play the next one in the queue or the first related video."),
        "playback.seekSeconds" to Copy("Double-tap seek", "How far double-tapping the left or right of the video jumps.", " s"),
        "playback.holdSpeed" to Copy("Hold for fast playback", "The speed while you hold the video or the space bar.", "x"),
        "playback.maxQualityWifi" to Copy("Maximum quality on Wi-Fi", "The highest resolution on an unmetered connection."),
        "playback.maxQualityCellular" to Copy("Maximum quality on mobile data", "The highest resolution on a metered connection."),
        "playback.preferredCodec" to Copy("Preferred video codec", "VP9 and AV1 save data; AVC (H.264) plays everywhere."),
        "playback.breakReminder" to Copy("Take a break reminder", "After this long of watching without a pause of 5 minutes, a reminder appears. Only time spent playing counts."),
        "feed.shortsDailyLimit" to Copy("Daily Shorts limit", "After this much time in Shorts in one day, Vela stops and asks whether to leave. You can take 5 more minutes. The day ends at midnight."),
        "playback.volumeBoost" to Copy("Volume boost", "Makes every video louder than your phone's maximum, for quiet recordings. Loud videos can distort; lower it if the sound turns harsh. Android only."),
        "playback.captionSize" to Copy("Caption size", "How big captions are. Large and Huge help on a small screen or in bright light."),
        "playback.captionBackground" to Copy("Caption background", "Outline draws the words with a dark edge and nothing behind them; Shade is a soft dark box; Solid is a black box."),
        "playback.captionCreatorStyle" to Copy("Use the creator's caption style", "Some channels colour their captions or put them somewhere other than the bottom (a chat overlay, say). Off shows every caption plainly at the bottom."),
        "playback.audioOnly" to Copy("Audio only", "Play only the sound, without the picture."),
        "downloads.subtitles" to Copy("Save subtitles", "Keep the available subtitles with each download so they work offline."),
        "notify.background" to Copy("Check for new videos", "Look for new uploads of your subscriptions in the background, a few times a day."),
        "notify.system" to Copy("System notifications", "Show a notification for new videos of channels set to All."),
        "notify.downloads" to Copy("Download results", "Add finished and failed downloads to the notification feed."),
        "playback.slowStreamFailover" to Copy("Switch source when buffering", "Try another source when a stream keeps buffering."),
        "playback.allowAgeRestricted" to Copy("Allow age-restricted videos", "Off by default. They play through yt-dlp, which can take about 20 seconds to start."),
        "playback.allowControversial" to Copy("Allow videos behind a content warning", "Off by default. The alternate client then answers YouTube's viewer-discretion prompt."),
        "ytdlp.path" to Copy("yt-dlp location", "Leave empty to use the bundled or managed copy."),
        "ytdlp.autoUpdate" to Copy("Update yt-dlp", "Keep the app's own copy of yt-dlp up to date."),
        "history.localOnly" to Copy("Keep watch history on this device", "Stops Vela sending what you watch to your account. Reading your account's history is a separate setting and still works."),
        "history.accountSync" to Copy("Read my YouTube history", "Bring what you watched on other devices into Vela's History and use it for hiding watched videos."),
        "feed.hideWatched" to Copy("Hide watched videos", "Leave videos you have already watched out of lists."),
        "feed.watchedThresholdPercent" to Copy("Counts as watched at", "A video is watched once you got this far, here or on your account. 1 means played at all.", " %"),
        "feed.minLengthSeconds" to Copy("Minimum video length", "Hide videos shorter than this. 0 shows everything.", " s"),
        "feed.blockKeywords" to Copy("Hide titles with these words", "Separate words with commas."),
        "feed.allowKeywords" to Copy("Only show titles with these words", "Separate words with commas. Empty shows everything."),
        "feed.keywordsAreRegex" to Copy("Keywords are regular expressions", "Treat the word lists above as patterns."),
        "modules.sponsorblock.enabled" to Copy("SponsorBlock", "Skip sponsor, intro and other segments people have marked."),
        "modules.ryd.enabled" to Copy("Return YouTube Dislike", "Show dislike counts."),
        "modules.dearrow.enabled" to Copy("DeArrow", "Replace clickbait titles and thumbnails with community ones."),
        "modules.showContribute" to Copy("Show Contribute under videos", "A card for sending SponsorBlock segments and DeArrow thumbnails."),
        "sponsorblock.minSegmentSeconds" to Copy("Ignore short segments", "Segments shorter than this are not skipped.", " s"),
        "sponsorblock.notifications" to Copy("Notice when a segment is skipped", "Show a notice with an Undo button."),
        "sponsorblock.server" to Copy("SponsorBlock server", "The address segments are read from."),
        "sponsorblock.userId" to Copy("SponsorBlock user id", "Private. It identifies your submissions."),
        "ryd.inFeed" to Copy("Likes and dislikes on video cards", "Show Return YouTube Dislike counts in lists."),
        "ryd.server" to Copy("Return YouTube Dislike server", "The address counts are read from."),
        "dearrow.titleCasing" to Copy("Title casing", "How DeArrow writes titles."),
        "dearrow.minVotes" to Copy("Votes needed to trust a submission", "Locked submissions always count.", " votes"),
        "dearrow.generatedThumbnails" to Copy("Generated thumbnails", "Use a frame from the video when nobody submitted a thumbnail."),
        "dearrow.thumbnailServer" to Copy("DeArrow thumbnail server", "The address thumbnails are read from."),
        "downloads.folder" to Copy("Download folder", "Leave empty for the default folder."),
        "downloads.defaultQuality" to Copy("Default download quality", "The resolution a download starts with."),
        "downloads.audioOnlyDefault" to Copy("Audio only by default", "Download just the sound."),
        "downloads.preferAvc" to Copy("Prefer H.264", "Plays on every device; VP9 and AV1 files are smaller."),
        "downloads.keepOriginalAudio" to Copy("Keep the original audio format", "Save m4a or webm as it is instead of converting Opus to .opus."),
        "downloads.wifiOnly" to Copy("Wi-Fi only", "Download only on Wi-Fi or another unmetered connection."),
        "downloads.concurrent" to Copy("Simultaneous downloads", "How many downloads run at once."),
        "downloads.deleteAfterWatch" to Copy("Delete after watching", "Remove a download when you watch it to the end."),
        "downloads.storageCapGb" to Copy("Storage cap for automatic downloads", "0 means no cap. The oldest watched downloads go first to stay under it.", " GB"),
        "downloads.auto.deleteAfterDays" to Copy("Delete automatic downloads after", "Days after you watched them. 0 keeps them.", " days"),
        "downloads.commentPages" to Copy("Comment pages to save", "Saved with each download.", " pages"),
        "account.profilesExperimental" to Copy("Choose another profile", "Experimental. Unverified with more than one profile; it only reads."),
        "account.useForChannels" to Copy("Use the account for channel pages", "Channels load through your account."),
        "account.useForDetails" to Copy("Use the account for details and comments", "Video details and comments load through your account."),
        "account.downloadsMayUseAccount" to Copy("Downloads may use the account", "Only when an anonymous download fails."),
        "account.killSwitch" to Copy("Stop all account traffic", "Blocks every request that carries account credentials."),
        "account.tierOrder" to Copy("Order sources are tried in", "Comma-separated: ANONYMOUS, ALT_CLIENT, YT_DLP, AUTHENTICATED."),
        "account.budget.playbackPerHour" to Copy("Playback requests per hour", "Account playback resolves.", " per hour"),
        "account.budget.browsePerMinute" to Copy("Browse and sync calls per minute", "Account browse and sync requests.", " per min"),
        "account.budget.writesPerHour" to Copy("Account writes per hour", "Likes, subscriptions and playlist changes.", " per hour"),
        "remoteConfig.url" to Copy("Remote configuration address", "Where settings updates are fetched from."),
        "debug.frameStats" to Copy("Show frame times", "A small readout in a corner, for measuring slow screens."),
        "home.source" to Copy("Home shows", "Recommended needs a signed-in account; without one Home shows Trending."),
        "content.region" to Copy("Region", "Whose trending and recommendations you see when signed out."),
        "dearrow.feed" to Copy("In the feed and Home", "Use DeArrow titles and thumbnails here."),
        "dearrow.search" to Copy("In search results", "Use DeArrow titles and thumbnails here."),
        "dearrow.videoPage" to Copy("On the video page", "Use DeArrow titles and thumbnails here."),
        "dearrow.playerTitle" to Copy("In the player and notification", "Use the DeArrow title here."),
        "look.theme" to Copy("Theme", "Follow the system, or choose light or dark."),
        "look.progressStyle" to Copy("Progress bar", "How the player's seek bar looks."),
        "look.home.density" to Copy("Shelves between videos", "How often a shelf appears among Home's videos."),
        "look.home.chips" to Copy("Filter chips on Home", "A row of quick filters above Home."),
        "look.nav.railCollapsed" to Copy("Collapse the navigation rail", "On wide windows, show the rail as icons only."),
    )

    fun title(s: Setting<*>): String = table[s.key]?.title ?: s.label.removePrefix("SponsorBlock: ")
    fun description(s: Setting<*>): String = table[s.key]?.description ?: s.info
    /** The text after a slider's number ("s", " GB"); empty when the number says it all. */
    fun unit(s: Setting<*>): String = table[s.key]?.unit.orEmpty()
}

val Setting<*>.title: String get() = SettingCopy.title(this)
val Setting<*>.description: String get() = SettingCopy.description(this)
val Setting<*>.unit: String get() = SettingCopy.unit(this)
