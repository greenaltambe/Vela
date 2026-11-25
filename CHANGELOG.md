# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/)
(pre-releases are `-alpha.N`, `-beta.N`, `-rc.N`).

## [Unreleased]

### Fixed
- The icons of the bottom bar and the side rail were drawn black, so they could not be seen in the dark theme.
- Syncing with YouTube read no videos from Liked videos and from other playlists in YouTube's newer page layout; both layouts are read now, with their further pages. A page Vela cannot read is reported as a failed sync instead of an empty playlist.

## [0.6.1-beta.6]

### Added
- Home shows YouTube's own groups (rows about a game, rows of Shorts and the like) as titled rows where YouTube puts them, in All and in every chip. Settings > Home > "YouTube's groups on Home" hides them (synced and backed up); rows of Shorts follow the Shorts setting instead.
- Customize > Progress bar > Lightning: a seek bar drawn as an electric arc from the playhead.
- A "Gold" colour scheme.
- The health screen checks YouTube's Shorts feed.
- The chosen "Region for content" shows as a small code beside the app's name (on Home; on every page in the desktop bar).

### Changed
- The Shorts tab shows YouTube's recommended Shorts: your account's when signed in, the general ones when not, and Shorts from channels if neither can be read. A Short tapped in a row on Home plays the rest of the row and carries on from there. Blocked channels, words and keyword rules are applied, and blocked Shorts are passed over.
- A Short starts without waiting for its channel name, which appears a moment later.
- Signed in, Vela's own Shorts shelf on Home gives way to YouTube's rows of Shorts.

### Fixed
- Videos in YouTube's groups are no longer mixed, untitled, into your Home feed.
- Signed in, Home follows "Region for content"; it always asked for the United States. Changing the region reloads Home and Shorts.
- Videos in your history that had a title but no channel get their channel.
- The colour scheme cards showed light colours in a dark theme on Linux.

## [0.6.1-beta.5]

### Added
- Explore is a tab (Home, Shorts, Subs, Explore, Library). Its pills have icons: Gaming, Podcasts, Music, News, Sports, Movies, Learning, Cooking, Tech and Live (last). Gaming, Podcasts and Live show YouTube's own lists; the rest are searches.
- Home shows YouTube's own chips for your feed (All, your custom feeds, topics, Live, Recently uploaded, Watched, New to you, ...), each with its own endless list. A Filter button (Unwatched, Under 10 min) narrows whichever list is open. Settings > Home can turn YouTube's chips off.
- Customize > Navigation bar: a switch for Explore in the bar, a link to the Shorts setting, names under the icons on or off, animated tab icons (the tapped icon pops and settles), and "Translucent bars": the page scrolls under the top bar, Home's chips, the mini player and the bottom bar, blurred (Android 12+ and desktop; older Android gets a tint).
- Signed out, Home shows the shelves that work without an account and a sign-in card.

### Changed
- Signed in, Home is your account's feed only and scrolls without end; Trending is no longer added to it. The "Home shows" setting is gone.
- Ctrl+1..9 follow the visible tabs, so with Explore on Ctrl+4 is Explore and Ctrl+5 Library (turn Explore off to get the old numbers).
- Vela now includes the Haze library (Apache-2.0) for the blur, and Compose Multiplatform is 1.12.2.

### Fixed
- Videos from your subscriptions (Home's "New from your subscriptions" and the Subs page) show their length.
- "Trending" only ever showed live streams, because YouTube no longer has a Trending page; that list is gone and Live now shows real live streams with their LIVE badge.

## [0.6.1-beta.4]

### Added
- Playlists are one thing now: each is either on this device only or synced with your YouTube account, chosen by one box ("Sync with my YouTube account") when you create a playlist and in its "Sync settings". Synced playlists work both ways: saving or removing a video here is sent to YouTube, and changes made on YouTube come here. Changes the hourly limit holds back are kept and sent later, even after a restart.
- One Watch later. It stays on this device until you tick the box on it; then both lists are merged and everything ends up in both places. The separate "Watch later (YouTube)" copy is gone.
- YouTube holds 5000 videos per playlist. Videos that do not fit stay here, marked "Not on YouTube", and one button adds them to another synced playlist or to a new one.
- Settings > Playlists & YouTube: when synced playlists are read from your account (off, on request, at start, every 6 hours) and whether new playlists start synced.
- Adding or removing a video from a playlist shows a toast with Undo.
- Liking or un-liking a video updates your Liked videos list straight away.
- Desktop: on a results page the top bar's search box shows what you searched; clicking it or pressing `/` opens the search page with that text ready to edit.

### Changed
- "Sync account playlists" is now "Sync with YouTube". Playlists synced with your account are no longer part of Sync between devices (they come from YouTube), and the backup keeps only the videos that never reached YouTube from them.
- Old copies of account playlists become synced playlists. Nothing is deleted when updating; videos saved here that YouTube may not have are sent on the first sync. A backup of the database is kept next to it before it is updated.

## [0.6.1-beta.3]

### Added
- A floating video window as an alternative to the mini bar (Settings > Playback > Minimised video: Automatic, Mini bar, Floating window). It can be dragged (it settles into the nearest corner) and resized, shows its buttons on hover, and opens into the video page with the same animation as the mini bar. Automatic is the bar on a phone and the floating window on a computer. The choice is kept on each device and is never synced or backed up.
- Desktop: Ctrl+X closes the video (playing or paused, in the mini player or on the video page) with an Undo, and the video page's top bar has a cross beside the back arrow that does the same.

### Fixed
- Playlist pictures in Search were far larger than on the channel page on a wide window; both are now capped at the same size.
- A channel's Playlists tab shows its sort choices as pills (like Videos) instead of a dropdown.

## [0.6.1-beta.2]

### Added
- Continue watching: a card's "..." menu has "Remove from Continue watching" (with Undo). The video stays in your history and comes back to the shelf when you watch it again and leave it unfinished.

### Fixed
- Windows: downloads that merge video and audio failed because ffmpeg was missing. The Windows package now includes ffmpeg, and the "ffmpeg not found" message names the right install command for your system.

## [0.6.0-beta.2]

### Changed
- Desktop: Ctrl+1-9 open the navigation items (and minimise a playing video); jumping to a window tab moved to Alt+1-9. The plain number keys jump to a tenth of the video again.

### Fixed
- Video keys: Ctrl, Alt or Meta combinations (Ctrl+K, Ctrl+F, Ctrl+Left...) no longer trigger the player's plain-key shortcuts.
- Release workflow: a failure of GitHub's attestation service no longer stops the release (it now only warns).

## [0.6.0-beta.1]

### Added
- Sync between your devices (Settings > Sync between devices): subscriptions and groups, playlists and Watch later, watch and search history, the blocklist, settings and look, and (optional, off by default) your Google sign-in with each account's own data. Connect over the same Wi-Fi (no extra app), through a shared folder (Syncthing, Dropbox, Nextcloud...) or through online storage (WebDAV: Koofr, Nextcloud, Synology...). Everything is encrypted with a sync code before it leaves a device. Choose how often it syncs and "Only on Wi-Fi"; a failed sync retries by itself.
- Automatic backups (Settings > Backup & restore): a daily copy, one before the first sync and one before a large sync change, optionally also to a folder of your choice, with a list to restore from.
- Cast to a TV (DLNA/UPnP) from the player.
- Desktop shortcuts: Ctrl+M minimises the video to the mini player and brings it back, Ctrl+H opens History, Ctrl+1-9 open the matching item of the navigation bar (and minimise a playing video; Alt+1-9 jump to a tab). A "/" key hint in the top search bar.

### Changed
- Shorts has two choices, Hide or Show (the old "Separate tab" became Show).
- Desktop: the channel search result and the channel page use YouTube-like wide layouts; the search page is a centred column that opens with a short cross-fade; the video page picture has softly rounded corners.
- The "on" state of the caption, cast and sleep-timer buttons over the video is red in Classic and readable in every colour scheme.
- Desktop file pickers (sync folder, cookie file, download folder, backup) use the system's native dialog when available.
- Ctrl+1-9 used to jump to a window tab; that is now Alt+1-9, so Ctrl+1-9 can open the navigation items. The plain number keys still jump to a tenth of the video.

### Fixed
- Backups no longer contain this device's own sync and backup folder settings.

## [0.5.0-beta.4]

### Fixed
- Flatpak: videos did not play ("command loadfile failed: invalid parameter"): the bundled mpv is older than the one on Fedora; loading a video now also works with mpv before 0.38.

## [0.5.0-beta.3]

### Added
- Linux: a Flatpak bundle (`Vela-<version>-linux-x86_64.flatpak`) in every release.
- Desktop: every tab has its own player, like a browser; tabs show what is open in them, and a tab keeps its pages (no refetch) when you come back.
- Desktop: a top bar on every page (menu, Vela logo to Home, search, notifications, profile), a rebuilt side bar, and a Classic (YouTube-like) colour scheme.
- Desktop: real full screen for videos, Ctrl+R to refresh any page, "/" to search, number keys 0-9 and Home/End to jump in a video, round scroll arrows on every horizontal list.
- Tell YouTube what I watch (Settings > Privacy, off by default): adds what you watch to your YouTube history. Not yet tried against a real account.
- Social links in video descriptions with icons, and selectable description text.

### Changed
- Sakura and the other pink colour schemes are softer. Creator-styled captions (a chat shown at the side) are placed on the right, as YouTube does.
- Horizontal lists no longer take the vertical scroll away from the page.
- Choosing Home or a channel that is already open under the video page now minimises the video.

### Fixed
- Keyboard shortcuts keep working after the player's controls fade away.
- A black strip at the bottom of a window restored as maximised.

## [0.4.0-beta.1]

### Added
- Color schemes under Customize: Vela red, Sakura pink, Ocean, Forest, Twilight and Sunset, each in light, dark and pure black.
- Hide-on-scroll options: Home's filter chips (on by default), the bottom bar and a channel's tab row slide away while scrolling down and return on scrolling up.
- Members-only videos: a "Members only" badge, and a setting (per channel too) to hide them.
- Channel pages: videos and playlists as YouTube-style rows with a three-dot menu, a full-width Subscribe button, and refresh that keeps the chosen sort.
- Subscriptions: "Sync from your account" on the Channels page.
- Keep the screen awake while a video plays (Android and Linux).
- Search start page shows only recent searches, each with an arrow that puts it in the search field.

### Changed
- The dark theme is livelier: surfaces carry the scheme's tint, and Vela red's dark theme has a brighter accent.
- Channel and profile pictures are round again; filter pills no longer show a check mark (no more width jumps).
- "N hidden by your filters or blocklist" on Home is now a short toast after a refresh.
- After a restart the last video stays in the mini player, paused, instead of playing by itself. Toasts (such as Undo for a closed player) can be dismissed.

### Fixed
- Opening a channel from Home no longer says "This channel could not be found" for feed videos without a channel link.
- Notifications are only made for videos published in the last three days from channels already followed, not for a new channel's old videos.
- Comments show placeholders while another sort order loads.

## [0.3.0-beta.1]

### Added
- Play queue: "Play next" and "Add to queue" in a video's menu, an "Up next" list on the video page that you can reorder by dragging, remove from or clear (with Undo), and Shuffle; Shuffle also on playlists; a Repeat switch in the player.
- Seek bar previews: dragging the seek bar shows the picture for that moment.
- Player gestures in fullscreen: swipe the left side for brightness (past the bottom returns to the system's auto brightness) and the right side for volume; pinch to zoom the picture to fill the screen.
- Caption style: size, background (outline, shade or solid) and a switch for the creator's own style. Captions now keep their creators' colours, bold and position, so chat-style captions (a streamer's Twitch chat, say) appear where they were authored.
- Volume boost (Android): +3, +6 or +9 dB for quiet videos.
- Take a break reminder and a daily Shorts limit, both off by default.
- One blocklist for channels and title words, applied to Home, Search, Explore, Subscriptions, Related, the Shorts player and notifications; copy and paste it to share.
- Subscription groups: filter the Subscriptions page, its channel strip and its Shorts by a group; add a channel to groups from its page.
- Shorts player: a three-dot menu (also by holding the picture) with Description, Captions, Playback speed, Save to, Copy link, Go to channel; Shorts opened from a channel now play that channel's Shorts in order.
- Channel pages: @handle and video count, a clearer open tab, a calm "Vela can't show this tab yet" state with Open in browser instead of an error, and animated swiping between tabs.
- Mini player: swipe it down or sideways to close it (with Undo); tapping the tab you are on scrolls to the top.
- Pure black (AMOLED) theme switch under Customize.

### Changed
- Thumbnails that run edge to edge are square, others use smaller corners; LIVE and SHORT badges are readable in dark theme; unselected chips have an outline.
- The Shorts player's buttons and captions are easier to read.

### Fixed
- Videos opened from a link had no title in Continue watching, History, the mini player and notifications; they are looked up now and old entries are repaired.
- Topic ("Artist - Topic") channels showed no content: their albums are listed as playlists.
- Channel sort chips did not show which one was selected; "Joined Joined" in About; the Store tab that could never load is gone.
- Scrubbing a long video could crash the app.

## [0.2.0-beta.1]

### Added
- Material 3 Expressive throughout: one red theme (light, dark or system), expressive navigation bar and rail, large collapsing app bars, segmented lists, morphing buttons and chips, wavy progress and loading indicators.
- Redesigned Settings (two panes on wide windows), Library, History, Downloads, playlists, Search (result types, filters sheet, a mixed "All" page with a channel card and Shorts blocks), Subscriptions (the Following strip, an All channels page with a bell per channel), the video page, description sheet, player controls, comments and the Shorts player.
- Notifications: a bell in the top bar opens a feed of new videos from your subscriptions, finished downloads and health alerts; each channel has an All / Personalized / None bell next to Subscribe; a background check runs every few hours on Android.
- Wavy (Material) seek bar, the new default; the earlier styles remain in Settings > Customize.
- A new app icon.
- Downloads waiting for Wi-Fi now explain why and offer "Use mobile data" and a shortcut to the download settings.
- Download is a split button on the video page (start now, or choose quality).

### Changed
- Palettes, fonts, icon packs, shapes and glass settings were removed in favour of the single Material 3 Expressive look (the theme and progress bar choices remain).
- Switching tabs is a quick cross-fade; the player sheet and the mini player redraw far less while playing, dragging or paused.
- Opening Shorts clears the mini player instead of leaving it docked.
- Video descriptions from sources that return HTML now show plain text with real links.
- Release builds from CI are always signed (see docs/RELEASING.md) and the release is published, not left as a draft.

### Fixed
- Playback failed with a 403 for signed-in users because yt-dlp picked a client whose links need a PO token; Vela now asks for the web_safari client.
- Search results opened scrolled down because the list anchored on its footer.
- The comment placeholder looked like a video card.

## [0.1.1-beta.3]

### Added
- Keyboard: `j` / `l` seek 10 s back / forward, `k` play/pause, `c` captions (with the existing `m`, `f`, Escape, space and arrows).
- Desktop: a refresh button (bottom right) and Ctrl+R on Home, Explore and Subscriptions, which have no pull to refresh there.
- Dragging the mini player up pulls the video page up with your finger; its picture grows from the mini player's picture as you drag (a tap animates it).

### Fixed
- The Linux .rpm/.deb carried the default Java icon (jpackage replaced it); it now ships the Vela icon.
- Keyboard shortcuts keep working after the player's buttons fade out (focus is taken back).
- Desktop: the playback keys (Space, j/k/l, m, c, f, arrows) only act on the video page, not while the mini player or PiP is showing over another page.
- Desktop fullscreen: the controls hide again after a moment and a tap toggles them (the hidden pointer made Compose replay a hover event that showed them again).
- Desktop fullscreen: the mouse pointer hides together with the controls and returns when the mouse moves.
- Desktop: holding Space for fast playback works in fullscreen and on the video page (a clicked button no longer takes the key; X11's key-repeat release/press pairs no longer end the hold), and "2× speed" shows while it is held, for the keyboard as for a screen hold.

## [0.1.0-beta.2]

### Added
- Channel Videos can be sorted by Latest, Popular or Oldest over the whole channel (sorted by YouTube), not just the loaded videos.
- Setting "Region for content" (Home page of Settings).
- Share in every video's three-dot menu; on desktop it copies the link ("Copy link"), as does Share on the video page.
- YouTube links open in Vela: Android "open with" and share, `vela <link>` on Linux (a running Vela receives it), and pasting a link into Search.

### Fixed
- Desktop: the player's menu buttons (volume, captions, quality, speed, sleep, fullscreen) were missing in the windowed player.
- Desktop: the window class is now `Vela`, so the dock shows the Vela icon instead of the Java one; all icon sizes are sent.
- The play/skip buttons no longer overlap the buffering spinner.

### Changed
- The video page grows out of the mini player when it is opened from it.

## [0.1.0-beta.1]

First public beta. Android is the primary platform, Linux is supported, Windows is experimental.

### Watching
- Home (account recommendations or Trending), Explore (Trending and topics), Search (videos, channels and playlists, with length and sort filters, suggestions), channels (Home, Videos, Shorts, Live, Playlists, About), playlists, Shorts player, live streams with chat and replay.
- Playback with Media3 (Android) and libmpv (desktop): quality and codec choice, captions, audio tracks, speed, sleep timer, chapters, previous/next, autoplay, mini player (card or picture-in-picture), fullscreen, Picture-in-Picture.
- SponsorBlock, Return YouTube Dislike and DeArrow as separate, switchable modules.

### Reliability
- A fallback chain of sources for every video: anonymous NewPipeExtractor first, an alternate client, yt-dlp, and a signed-in account only last. The chain remembers what works, shows which source served a video and why the others failed (Health and diagnostics), and runs health checks.
- yt-dlp on desktop is found on the system or kept up to date by the app (SHA2-256SUMS verified, rolled back if the check fails); on Android it is embedded.
- Signed remote configuration to adjust client profiles and canary videos without a new build (off until a URL is set).

### Accounts and safety
- Optional Google accounts (cookie import or web login), several accounts, per-account data. A governor paces every account request, budgets writes, and a kill switch pauses the account. Credentials are kept in the platform secret store (Android keystore-backed storage, libsecret or an encrypted file on Linux, DPAPI on Windows).
- Age-restricted playback is off by default.

### Offline and data
- Downloads with quality choice, queue, pause/resume, storage cap and automatic download rules; history, subscriptions, playlists and watch later stored per account on the device; backup and restore.

### Customisation
- Looks: colour palettes, light/dark/pure black, several icon packs, fonts, corner radius, glass effects, bar styles, tab selection and order, performance mode.
- Keyboard shortcuts and media keys (MPRIS) on desktop.

### Packaging
- Android APK (arm64-v8a), Linux .deb, .rpm and portable tarball, Windows .msi and portable zip (experimental).

[Unreleased]: https://github.com/greenaltambe/Vela/compare/v0.6.1-beta.6...HEAD
[0.6.1-beta.6]: https://github.com/greenaltambe/Vela/compare/v0.6.1-beta.5...v0.6.1-beta.6
[0.6.1-beta.5]: https://github.com/greenaltambe/Vela/compare/v0.6.1-beta.4...v0.6.1-beta.5
[0.6.1-beta.4]: https://github.com/greenaltambe/Vela/compare/v0.6.1-beta.3...v0.6.1-beta.4
[0.6.1-beta.3]: https://github.com/greenaltambe/Vela/compare/v0.6.1-beta.2...v0.6.1-beta.3
[0.6.1-beta.2]: https://github.com/greenaltambe/Vela/compare/v0.6.0-beta.2...v0.6.1-beta.2
[0.6.0-beta.2]: https://github.com/greenaltambe/Vela/compare/v0.6.0-beta.1...v0.6.0-beta.2
[0.6.0-beta.1]: https://github.com/greenaltambe/Vela/compare/v0.5.0-beta.4...v0.6.0-beta.1
[0.5.0-beta.4]: https://github.com/greenaltambe/Vela/compare/v0.5.0-beta.3...v0.5.0-beta.4
[0.5.0-beta.3]: https://github.com/greenaltambe/Vela/compare/v0.4.0-beta.1...v0.5.0-beta.3
[0.4.0-beta.1]: https://github.com/greenaltambe/Vela/compare/v0.3.0-beta.1...v0.4.0-beta.1
[0.3.0-beta.1]: https://github.com/greenaltambe/Vela/compare/v0.2.0-beta.1...v0.3.0-beta.1
[0.2.0-beta.1]: https://github.com/greenaltambe/Vela/compare/v0.1.1-beta.3...v0.2.0-beta.1
[0.1.1-beta.3]: https://github.com/greenaltambe/Vela/compare/v0.1.0-beta.2...v0.1.1-beta.3
[0.1.0-beta.2]: https://github.com/greenaltambe/Vela/compare/v0.1.0-beta.1...v0.1.0-beta.2
[0.1.0-beta.1]: https://github.com/greenaltambe/Vela/tag/v0.1.0-beta.1
