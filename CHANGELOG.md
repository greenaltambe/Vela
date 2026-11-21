# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/)
(pre-releases are `-alpha.N`, `-beta.N`, `-rc.N`).

## [Unreleased]

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

[Unreleased]: https://github.com/greenaltambe/Vela/compare/v0.5.0-beta.3...HEAD
[0.5.0-beta.3]: https://github.com/greenaltambe/Vela/compare/v0.4.0-beta.1...v0.5.0-beta.3
[0.4.0-beta.1]: https://github.com/greenaltambe/Vela/compare/v0.3.0-beta.1...v0.4.0-beta.1
[0.3.0-beta.1]: https://github.com/greenaltambe/Vela/compare/v0.2.0-beta.1...v0.3.0-beta.1
[0.2.0-beta.1]: https://github.com/greenaltambe/Vela/compare/v0.1.1-beta.3...v0.2.0-beta.1
[0.1.1-beta.3]: https://github.com/greenaltambe/Vela/compare/v0.1.0-beta.2...v0.1.1-beta.3
[0.1.0-beta.2]: https://github.com/greenaltambe/Vela/compare/v0.1.0-beta.1...v0.1.0-beta.2
[0.1.0-beta.1]: https://github.com/greenaltambe/Vela/tag/v0.1.0-beta.1
