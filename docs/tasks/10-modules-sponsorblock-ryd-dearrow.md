# Task 10: SponsorBlock, Return YouTube Dislike, DeArrow

Follow CLAUDE.md (report docs/reports/10-modules.md). Prerequisite: task 09 done and committed. Read docs/SPEC.md sections 2 (P1), 5.

## Shared HTTP
- Add one shared HTTP client for these third-party APIs (Ktor client with the OkHttp engine on both JVM and Android, or a thin interface with two implementations if Ktor causes trouble: your call, documented). Timeouts, retries with backoff, an in-memory + DB cache with TTLs, a User-Agent identifying the app, and graceful offline behaviour (modules never block playback or feed rendering; failure = feature silently inactive, visible in diagnostics).
- All three modules implement the existing `FeatureModule` interface, are individually toggleable via schema settings, and are testable with a fake HTTP layer.

## SponsorBlock
- Segment lookup with the privacy hash-prefix API (sha256 of videoId, first 4 chars; match locally), requested categories from settings. Per-category action setting (skip / mute / prompt / ignore) for: sponsor, selfpromo, interaction, intro, outro, preview, music_offtopic, filler, poi_highlight, exclusive_access; default sensible (skip sponsor/selfpromo/interaction, prompt others, ignore filler).
- Playback integration in `PlaybackSession` (common): skip on entering a segment (handle seeks into segments, overlapping segments, skip-once with undo, a minimum duration filter), mute action, prompt action (shows a "Skip" affordance for the segment's duration), skip notifications (toast/snackbar with Undo). Seek-bar markers coloured by category (colours come from LookTokens). Tests with fake controller + clock.
- Segment submission (user ID = local random UUID stored in settings, submit via API, from the player: mark start/end, choose category, review, submit) and voting optional if cheap. Never submit automatically.
- Segments cached per video for offline use (stored for downloads in task 11; expose a repository the downloader can call).

## Return YouTube Dislike
- Likes/dislikes/rating on the video page; optional in feed cards (setting, default off, batched and cached so it does not hammer the API). Handle API failures and archived videos.

## DeArrow
- Branding lookup (hash-prefix API): community titles and thumbnails with vote/lock handling, fallback to original when none or low confidence, per-surface settings (feed, search, video page, player title), "show original" long-press/toggle, title casing option, thumbnail from the DeArrow thumbnail service including the generated frame mode when only a timestamp exists. Implemented as `FeedStep` transforms plus a video-page hook; cached; never delays the first render of a list (apply when ready, no layout jumps if avoidable).
- Frame-pick mode: from the player, pick the current frame as a thumbnail suggestion and submit (confirm before sending).

## Verification
Offline tests with recorded API fixtures (capture real responses with a script like extractor-ytdlp/scripts/capture_fixtures.py, scrub nothing sensitive, note in README). Live tests (liveTest, skippable offline) against the real APIs for one well-known sponsored video. Build Android + desktop, Compose UI-test a flow on desktop. Manual checklist in the report: a video with known sponsor segments on each platform, each action type, undo, markers, RYD numbers, DeArrow on/off per surface, offline behaviour.
