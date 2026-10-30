# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/)
(pre-releases are `-alpha.N`, `-beta.N`, `-rc.N`).

## [Unreleased]

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

[Unreleased]: https://github.com/greenaltambe/Vela/compare/v0.1.0-beta.2...HEAD
[0.1.0-beta.2]: https://github.com/greenaltambe/Vela/compare/v0.1.0-beta.1...v0.1.0-beta.2
[0.1.0-beta.1]: https://github.com/greenaltambe/Vela/tag/v0.1.0-beta.1
