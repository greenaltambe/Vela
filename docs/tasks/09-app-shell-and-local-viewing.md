# Task 09: real app shell + local-first viewing (no login yet)

Follow CLAUDE.md (report to docs/reports/09-app-shell.md; terminal reply max 5 lines, only what I must do). Read docs/SPEC.md sections 2, 5, 7.
This is a big task: build in stages, commit nothing, keep tests green at each stage, and record any stage you could not finish in the report.

## A. Design-token plumbing and app shell
- `LookTokens` (serializable data class: colours, typography incl. font family, corner radii, spacing, density, blur/effect flags, icon set id) provided via CompositionLocal; `VelaTheme` reads it. Ship one default Look (clean, system font, follows system light/dark) plus Pure Black variant. No hardcoded colours/dp/fonts outside the token defaults.
- Named components with stable string IDs (`ComponentIds`): each reusable piece (VideoCard, ChannelRow, SectionHeader, MiniPlayer, BottomNav, CommentItem, ChapterRow...) takes its config from the active Look. Add a single `Effects` wrapper (blur/glass) that degrades to plain when `performanceMode` is on (a setting).
- Navigation: bottom nav on Android, side rail on wide desktop: Home, Subscriptions, Library, Settings; Search reachable everywhere. Back handling on both platforms. A docked mini player above the nav that survives navigation (tap to expand to the video page). Move the old DebugScreen content into Settings > Diagnostics.

## B. Extractor additions (all tiers; unsupported tiers return Unplayable("not supported"))
- `trending()` (Home), related videos on VideoInfo (or `related(videoId)`), channel tabs if cheap (videos/shorts), and recent uploads by channel for the feed. Keep the continuation-token rules from task 04.

## C. Screens
- Home: trending through the feed pipeline. Search: history table (per account) with clear button, suggestions optional, paged results, de-duped.
- Video page: player on top; title, channel row with Subscribe (local), description (HTML to rich text, links open externally, timestamps like 1:23 seek the player), chapters (VideoInfo chapters, else parsed from description timestamps) shown as a list and as seek-bar markers with the current chapter title, comments (paged, replies expandable via replies(); hide the sort control when the extractor reports it unsupported), related videos.
- Channel page: header (avatar, name, subscriber count, description), Videos tab with paging, Subscribe toggle.
- Library: Watch history (from local WatchHistory: per-day grouping, delete one, clear all; setting `history.localOnly` default off, schema-declared), Watch Later and local playlists (create/rename/delete, add/remove/reorder, play-all/queue). New tables per account; migrations via the existing system.
- Subscriptions: local subscriptions in the DB under a default local account (accountId model stays ready for real accounts), subscription management screen, import from YouTube Takeout `subscriptions.csv` and a NewPipe JSON export (file picker on both platforms).
- Subscription feed: fetch recent uploads for all subscribed channels concurrently with a concurrency limit (prefer the public RSS feed https://www.youtube.com/feeds/videos.xml?channel_id=ID if it works without bot checks - measure and report; else channel tab), cache in a FeedItem table, show cached feed instantly then refresh, pull-to-refresh, per-channel failure doesn't fail the feed.
- Feed pipeline steps, all driven by schema settings and resolved per channel: HideShorts (HIDE / SHOW / SEPARATE_TAB with a Shorts tab inside Subscriptions), HideWatched (uses local history), MinLength, KeywordRules (block/allow, case-insensitive, regex optional). Document where duration/Shorts info is unavailable from the source and how you handled it.

## D. Player features
- Chapters (above), sleep timer (presets + end of video; in `PlaybackSession`, common code, fake-clock tests; shown on the player), Picture-in-Picture on Android (auto-enter when leaving with video playing, play/pause remote action), desktop keyboard shortcuts (space, arrows seek 5s, shift-arrows 10s, f fullscreen, m mute, up/down volume, </> speed) and a real fullscreen toggle plus volume control wired to the existing `setVolume`.
- Fix `PlayerScreen`/video page scrolling at all window sizes. Reduce desktop fullscreen CPU by capping mpv render size to min(widget size, video size) and letting Skia upscale; measure 1080p60 fullscreen before/after and put numbers in the report.

## Rules
- Every user-facing option declared in the settings schema (label, keywords, section, choices/range) so the generated settings screen and search pick it up automatically.
- Tests: pipeline steps, history grouping, playlist ops, timestamp parsing, CSV/JSON import (fixtures), feed merge/cache logic, sleep timer. UI tests only where cheap (Compose UI test as before for key flows on desktop).
- Verification as in previous tasks: offline tests, liveTest, :androidApp:assembleDebug, :desktopApp:run (+ Compose UI-test driven flow: search, open video, scroll comments, subscribe, see it in feed). State clearly what is compile-only (PiP, Android navigation).
- Manual test checklist for me at the end of the report, split Android / Linux.
