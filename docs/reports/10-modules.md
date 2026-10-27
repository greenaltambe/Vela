# 10: SponsorBlock, Return YouTube Dislike, DeArrow

## Summary

One shared HTTP layer (new `ApiClient` over a small `HttpTransport`, OkHttp implementation in the new `:net-okhttp` module; timeouts, retry with backoff, memory + SQLite cache with TTLs, stale-on-failure, User-Agent, never throws) serves all three modules. SponsorBlock skips, mutes or prompts per category and per channel, with Undo, markers on the seek bar, a local segment store the downloader can call, and an explicit submit flow. Return YouTube Dislike shows on the video page and optionally on cards. DeArrow replaces titles and thumbnails on four independently switchable surfaces, with "show original" and a frame-suggestion flow. All three run against the real servers in the live tests; every failure leaves the feature quietly inactive and shows in Settings > Diagnostics. Task 09 is not committed (the owner commits manually); I built on the working tree.

## Verification

| Check | Result | Verified by |
|---|---|---|
| Offline tests: shared 291 JVM runs (212 of them are common tests that also run on the Android host), net-okhttp 5, player-mpv 64, androidApp 24, extractor-newpipe 48, extractor-ytdlp 82 | all pass | running |
| `ApiClient`: cache TTL, stale copy when offline, retry/backoff schedule (400/800/1600 ms), 404/4xx not retried, negative caching, POST never retried after a response, LRU eviction | pass (fake transport) | running |
| `Sha256` against published vectors (empty, "abc", the 448-bit vector, the million-"a" vector) and padding edge lengths (digests computed with Python `hashlib`) | pass | running |
| `SegmentEngine` (skip on entry, seek into a segment, end exclusive, skip-once, undo, overlap and chained segments, minimum duration, mute enter/exit, API `mute` type, prompt, highlight points, full-video labels) | pass | running |
| SponsorBlock against the **real captured answer** (the prefix response holds 148 other videos; only the requested video's segments are used; request carries the 4-char prefix and never the video id) | pass | running (captured fixtures) |
| SponsorBlock add-on: skip + notice + Undo, per-category and per-channel actions, prompt accept, ignored categories give no markers, disabled module sends nothing, outage never blocks and is logged, submission body and user id, rejected submission is a result not an exception | pass | running (fake HTTP over fixtures) |
| `SegmentRepository` (the downloader's entry point): store, offline use, refresh when older than 24 h, stale copy when the refresh fails, delete | pass | running |
| `PlaybackSession` add-on hooks: attach/start/states/stop, a throwing add-on cannot stop playback, add-on mute vs user mute, add-on seeks | pass | running |
| RYD: real answer parsed, unknown ids (404 **and** the all-zero answer seen live), memory cache, 429/outage inactive + logged, prefetch concurrency and no repeats | pass | running |
| DeArrow: real prefix answer parsed, vote/lock/original/threshold rules, locked-first ranking, generated-frame opt-in, casing, feed step (originals kept), per-surface switches, outage recovery, thumbnail-suggestion body | pass | running |
| `:net-okhttp:liveTest` against the real servers | SponsorBlock 6 segments (784 ms), RYD likes/dislikes (277 ms), DeArrow title + thumbnail URL (499 ms), the thumbnail service answered HTTP 200 with an image | running (real network) |
| `:desktopApp:liveTest` Compose UI test on a real sponsored video (real libmpv): votes line appears, DeArrow title replaced + "show original" toggle, markers loaded, seek 2 s before the sponsor segment → skipped to its end, notice shown, **Undo** returns to its start and it plays instead of skipping again, intro prompt → Skip button → jumped to the end of the intro | pass | running |
| Same suite's first test (search → open → shortcuts → comments → subscribe → feed → controls) after pinning the player | pass; 55.9 fps rendered, 627 frames drawn while scrolled to the comments | running |
| `:androidApp:assembleDebug` | succeeds | compile-only |
| Android: skip/mute/prompt on Exo position updates, notice banner, submit dialogs, DeArrow images via Coil, PiP interplay | compiles | compile-only |
| **Submission to the real servers** (segment and thumbnail) | **not run**: I did not send junk to community databases; request bodies are verified against a fake only | not verified live |
| How it looks (markers, banner, dialogs) | not observed | not verified |

### Numbers

- **Privacy lookups return a lot:** the 4-character SponsorBlock prefix answer for one video was 61 KB and carried 148 other videos' segments; the DeArrow prefix answer 18.7 KB. Both are cached under the prefix URL, so one request serves every video sharing it.
- **Latency** (single requests, this machine): SponsorBlock 784 ms, RYD 277 ms, DeArrow 499 ms. None of them is on the playback path.
- **A real surprise:** Return YouTube Dislike answers a video it has never seen with HTTP 200 and every count zero, not a 404. Treated as "unknown" (no 👍 0 👎 0 line); a test covers it.
- **The test video's data:** the fixture holds 6 segments (2 are "filler", which is ignored by default, so the seek bar shows 4 markers: two sponsor, an intro, an interaction reminder); the live lookup later returned the same 6. Votes and submissions change over time, so the live tests look the segment up at run time instead of hard-coding times.

## Files created/changed

**Shared HTTP (new)**
- `core/http/{Http,ApiClient,ApiCache,ModuleLog}.kt`, `core/Sha256.kt`; SQLDelight `3.sqm` (migration to v4), `ApiCache.sq` (`ApiCache`, `SegmentCache`).
- New module **`:net-okhttp`**: `OkHttpTransport.kt` (+ tests with MockWebServer, `liveTest/LiveModulesTest.kt`); `settings.gradle.kts`, `gradle/libs.versions.toml` (mockwebserver), both apps depend on it.

**SponsorBlock**
- `modules/sponsorblock/{Segments,SegmentEngine,SponsorBlockApi,SegmentRepository,SponsorBlockAddon}.kt`; `player/PlaybackAddon.kt` + `PlaybackSession.kt` (add-on hooks, add-on mute); UI `ui/screens/ModuleWidgets.kt` (prompt button, notice with Undo, submit flow), seek-bar markers via `SeekMarker` coloured from `LookTokens`.

**Return YouTube Dislike / DeArrow**
- `modules/ryd/Ryd.kt`, `modules/dearrow/DeArrow.kt` (service, vote/lock rules, casing, `DeArrowStep`, submission); `extractor/Models.kt` (`VideoSummary.originalTitle/originalThumbnailUrl`).

**Wiring / UI**
- `modules/Modules.kt` (factory), `modules/FeatureModule.kt` (placeholder objects removed), `AppServices.kt`, `core/settings/Settings.kt` (24 new settings), `ui/screens/{Common,Home,Search,Subscriptions,Video}Screen.kt`, `ui/components/Components.kt` (card long-press = original), `ui/player/PlayerPanel.kt` (+ `PlayerOptions`), `ui/DebugScreen.kt` (module status in Diagnostics), `feed/FeedStep.kt` (`with`).
- Apps: `desktopApp/.../main.kt`, `playback/DesktopPlaybackRuntime.kt`, `androidApp/.../MainActivity.kt`, `playback/PlaybackRuntime.kt`.

**Tests / fixtures**
- shared common: `core/http/{ApiClientTest,FakeHttp}`, `modules/SegmentEngineTest`; shared jvm: `modules/{Fixtures,SponsorBlockModuleTest,RydAndDeArrowTest}`, `JvmDatabaseFactoryTest` (v3→v4 case added); `PlaybackSessionTest` (add-ons); `SettingsSearchTest` (SponsorBlock now has many settings).
- Fixtures: `shared/src/jvmTest/resources/fixtures/modules/` with **`capture_fixtures.py`** (re-captures from the real APIs; documented in its docstring: nothing is edited or scrubbed, the data is public) and five captured responses (SponsorBlock x2, RYD x2, DeArrow x1). `desktopApp` liveTest: `DesktopUiLiveTest` (second test), `LiveWindow` (modules).

## Decisions the spec didn't dictate

- **HTTP: a thin interface + one OkHttp implementation, not Ktor.** OkHttp is already in the project; one implementation works on JVM and Android because both are JVM, so it lives in a tiny JVM module (`:net-okhttp`) instead of two actuals. Ktor's OkHttp engine would also have pulled a second OkHttp major version into the build. The fake-HTTP requirement is met by `HttpTransport` being an interface.
- **TTLs:** SponsorBlock 6 h, RYD 6 h, DeArrow 12 h; 404 answers are cached the same time (SponsorBlock's "no segments"). On a failed refresh an older copy is served and marked stale. The segment store is separate and has no expiry (downloads rely on it); it is refreshed when older than 24 h and online.
- **All ten SponsorBlock categories are always fetched**, whatever the actions are, so the stored copy is complete and a later settings change or a download never needs another request.
- **Actions are per category and per channel** (the settings layer already supports channel scope). `mute`-type segments can only mute; highlight points can only prompt; the "full video" label is shown nowhere and never skips.
- **Skip lands on the first position report inside a segment.** Android reports every 500 ms, desktop every 250 ms, so a skip can start up to that late; I did not add predictive seeking.
- **Notice is an inline banner for 6 s with Undo** under the picture (not a system toast or Material snackbar), because it is part of the video page and works the same on both platforms.
- **Submissions are explicit:** nothing is sent without the viewer marking the times, reviewing them in a dialog and pressing Send; the lookup path never sends the user id. The id is a random 36-character hex string made on first submit and kept in settings (Advanced).
- **Voting on segments is not implemented** (marked optional).
- **RYD "batching":** the API has no batch endpoint, so "batched" means at most 4 requests in flight, ids already known (or known to be unknown) skipped, a 6 h cache, and only ids on screen; the card badge is off by default.
- **DeArrow trust rule:** a submitted title/thumbnail is used if it is locked or has at least the configured votes (default 0); locked ones rank first; "original" votes win. Generated frames (a random time suggested by the service when nobody submitted) are opt-in.
- **Title casing** (as submitted / sentence / Title Case) is deliberately simple and English-oriented; DeArrow's `>` markers protect words; all-caps "shouting" titles are lowered, short all-caps words in normal titles are kept as acronyms.
- **DeArrow feed step reads only memory.** Lists render originals immediately, prefetch runs afterwards and the list recomputes when it arrives, so nothing waits on the network; the cost is that a title can change under the viewer's eyes when the answer comes in.
- **DeArrow keyword filters run on original titles** (the step is last in the pipeline).
- **One shared log (`ModuleLog`)** instead of putting module events into the extractor-tier log; Diagnostics shows both.

## Known issues / unverified assumptions

- **Submissions are untested against the real servers** (see Verification). The request shapes follow the APIs' documented fields (`videoID`, `userID`, `segments[].segment/category/actionType`, `videoDuration`; branding `thumbnail.timestamp`) but a real accept/reject has not been seen. Do one test submission yourself (checklist) before trusting it.
- **Player title in the media notification** uses DeArrow only if the branding was already cached when playback loads (typical when opening from a list, which prefetches); it does not update afterwards. There is no hook to change a playing item's metadata.
- **Markers and skip timing on Android are untested on a device.** Everything Android-side is compile-only.
- **Segments for a video that changes length** (re-uploads) are keyed by video id only; SponsorBlock's `videoDuration` field is parsed away and not used to discard mismatched segments.
- **Only the YouTube service id is used** (`service=YouTube`); the SponsorBlock "hash" field of entries is ignored (matching is by video id).
- **Casing and the 5-letter acronym rule are heuristics**; some titles will look wrong with sentence/Title Case on. "As submitted" is the default.
- **Settings screen:** the SponsorBlock user id is shown as an editable text field in Advanced (that is the schema-generated control); it is private and not meant to be shared.
- **DeArrow `thumbnail server` answers 200 with an image** here; I did not verify its behaviour for videos it has not generated (it may return 202/redirect while generating).
- **Offline behaviour** is tested through fake transports and the stale-cache path; I did not run the apps with the network cable pulled.

## Manual test checklist

**Linux** (`./gradlew :desktopApp:run`; use a recent video from a sponsor-heavy channel; the one the tests use is `DN953UW8iJ8`: open it from the search box by title or via a channel page)
1. Open the video. After a second or two the seek bar shows coloured markers (sponsor green, intro cyan, interaction purple ...) and the line `👍 … 👎 … ★ …` appears under the title. Neither delayed the picture or sound.
2. Seek to a few seconds before a green (sponsor) marker and let it play: it jumps over the segment and a "Skipped: Sponsor" line with **Undo** appears for ~6 s. Press Undo: you land at the segment start and it plays through this time.
3. Seek into the middle of a sponsor segment directly: it skips immediately.
4. Intro/outro segments (default "Ask"): a **Skip Intro** button shows under the picture while you are inside; pressing it jumps past.
5. Settings > Modules: change SponsorBlock: Sponsor to "Mute", reload the video: the sound drops during that segment and returns. Set it to "Do nothing": no marker, nothing happens. Set a minimum segment length of 30 s: shorter ones are ignored.
6. Settings > Modules: DeArrow. The page title is the community title with a "DeArrow title · show original" button; long-press a card in search or the feed to flip it to the original (and back). Turn DeArrow off for one surface at a time (feed, search, video page, player title) and confirm only that surface changes. Try the title casing choices.
7. Settings > Modules: turn on "Show likes and dislikes on video cards"; the cards in Home/Search fill in numbers a moment after the list appears (and stay off by default).
8. Offline: disconnect the network, open a video you already opened: markers and the votes line still appear from the cache; open a new one: it plays normally, no error, and Settings > Diagnostics > Modules lists the three as INACTIVE with the reason. Reconnect and open another video: they recover.
9. Contribute (below the picture): mark a start and an end on a real sponsor read, choose the category, Review and submit: the dialog shows the times; press Cancel once (nothing is sent). **Only if you want to**, send one real segment; report what the status line says. Same for "Use the frame at …" for a DeArrow thumbnail.
10. Look at the banner, markers and dialogs and tell me what looks wrong.

**Android**
1. Same checks 1-4 on the phone with a sponsored video; note whether skips feel late (position is reported every 500 ms).
2. Skips and Undo while the screen is on and while in Picture-in-Picture (the banner is not shown there; the skip should still happen).
3. Background audio with the screen off: sponsor segments are still skipped (the session keeps running).
4. Offline/airplane mode as in Linux step 8.
5. Settings > Modules and Advanced screens render and persist.
