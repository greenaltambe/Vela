# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/)
(pre-releases are `-alpha.N`, `-beta.N`, `-rc.N`).

## [Unreleased]

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

[Unreleased]: https://github.com/OWNER/REPO/compare/v0.1.0-beta.1...HEAD
[0.1.0-beta.1]: https://github.com/OWNER/REPO/releases/tag/v0.1.0-beta.1
