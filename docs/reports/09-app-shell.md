# 09: app shell and local-first viewing

## Summary

The debug screen is replaced by a real app: Home (trending), Subscriptions (cached feed, Shorts tab, channel management, file import), Library (history, Watch Later, local playlists) and Settings (generated screen, with the old debug content under Diagnostics). Search sits in a top bar on every screen. Video and channel pages are built, and a docked mini player survives navigation. Everything visual comes from `LookTokens` (Default light/dark and Pure Black); sleep timer, volume, desktop shortcuts, real fullscreen and Android Picture-in-Picture are in. Stages A, B, C and D are all implemented; items not finished are listed in Known issues. Verification found and fixed a native use-after-free from task 08 (details below).

## Verification

| Check | Result | Verified by |
|---|---|---|
| Offline tests: shared 387 runs (common tests run on JVM and on the Android host), androidApp 24, extractor-newpipe 48, extractor-ytdlp 82, player-mpv 64 | all pass | running |
| New unit tests: timestamps/chapters/description parsing, feed steps (hide watched, min length, keywords incl. regex, shorts), feed merge, history grouping, Takeout CSV and NewPipe JSON import, sleep timer (fake clock), volume/mute, shortcuts, LookTokens JSON round-trip, schema entries, RSS parser, render-size cap, render-loop/sink race | pass | running |
| DB: playlists (create/rename/delete/add/remove/move, Watch Later protected), subscriptions, search history, history list/delete/clear, feed cache; v1->v3 and v2->v3 migrations on a real file DB | pass | running (in-memory and file SQLite) |
| `FeedService` against a fake extractor: concurrency limit, one failing channel keeps its cache, unsubscribed channels dropped, no overlapping refreshes | pass | running |
| `:extractor-newpipe:liveTest` (new `LiveFeedTest`) | trending 15 items + next page; related 20; channel Shorts tab 48 items; RSS feed HTTP 200 for 6/6 channels | running (real YouTube) |
| `:desktopApp:liveTest` `DesktopUiLiveTest` (Compose UI test, real libmpv, real YouTube): search from the top bar, open a result, 1080p60 playing, shortcuts, scroll to comments, subscribe, Back to the mini player (still playing), Subscriptions feed shows the channel's 15 RSS items, speed, 360p, close from the mini player | pass; 58.2 fps rendered | running |
| `:desktopApp:liveWindow` (real window, resize across the wide/narrow breakpoint, fullscreen, pause, seek, quality, close) | exit 0, no `malloc`/SIGSEGV after the fix | running |
| `:desktopApp:run` 40 s, then SIGTERM | started, no exceptions on stderr, process gone, no `hs_err` | running |
| `:androidApp:assembleDebug` | succeeds | compile-only |
| Android navigation and Back, bottom bar, file picker, PiP (auto-enter, play/pause action, PiP layout), Coil images on Android | compiles | compile-only |
| Pull-to-refresh by touch | compiles; the Refresh button was clicked on desktop via the feed | compile-only (touch) |
| How it looks: spacing, colours, density, Pure Black, glass | not observed (no display access) | not verified |

### Numbers

- **RSS feed vs channel tab** (6 channels, real network): the public feed answered HTTP 200 for all 6 with no bot check, 12-746 ms each (the first request includes connection setup), 15 entries each, all with publish dates and thumbnails, **none with a duration**. Six channels fetched concurrently: 26 ms total. The feed marks Shorts through the `/shorts/` form of the entry link: 3 to 12 of the 15 entries per channel were flagged. The channel Videos tab gives durations and (approximate) dates for 30 items but costs a full channel request. Decision: RSS first, channel tab as fallback.
- **Fullscreen CPU, render-size cap** (process CPU, fraction of one core; screen is exactly 1920x1080, `liveWindow`, 8 s of fullscreen each):

  | Video | Cap off ("before") | Cap on ("after") |
  |---|---|---|
  | 1080p60 VP9 | 134% | 128% (no change: widget size equals video size) |
  | 720p60 VP9 | 129% | 73% |

  The cap only helps where the window is larger than the video. A 1080p video on a 1080p screen is not helped, and still costs 120-137% in fullscreen against about 65% in a 700x500 window; I did not isolate where that time goes (the per-frame 8 MB copy in Skia is the suspect, not measured).
- **Bug found by the verification, fixed.** The first no-cap `liveWindow` run died with `malloc(): unsorted double linked list corrupted` right after the window shrank from 1100 to 700 px. Cause: crossing the wide/narrow breakpoint rebuilt the page (the layout had two different branches), which disposed the video surface, and `SkiaFrameSink.close()` freed the bitmaps while mpv's render thread was still writing into them (task 08's `detachSink` also detached unconditionally and did not wait for the frame in flight). This was latent in task 08; it only showed once the layout could change under a playing video. Fixed twice: the shell keeps one structure for every layout (page state and surface survive a resize), and `detachSink` now waits for the current frame and only removes the sink it was given. Regression test: a renderer that blocks mid-frame, detach must not return until it finishes.

## Files created/changed

**Design tokens / theme (shared)**
- `ui/look/LookTokens.kt`, `LocalLook.kt`, `ComponentIds.kt`, `Effects.kt`; `ui/VelaTheme.kt` (rewritten); `core/settings/Settings.kt`, `Setting.kt` (sections `APPEARANCE`, `LIBRARY`; new settings), `SettingsResolver.kt` (observable `revision`).

**Navigation / screens / components (shared)**
- `AppServices.kt`, `App.kt`, `ui/AppShell.kt`, `ui/nav/Navigator.kt`, `ui/components/{Components,Format}.kt`, `ui/screens/{Home,Search,Video,Channel,Subscriptions}Screen.kt`, `LibraryScreens.kt` (library, playlist, history), `SettingsHost.kt`, `Common.kt`; `ui/player/{PlayerPanel (incl. PlayerOptions),SeekBar,Shortcuts}.kt`; `ui/DebugScreen.kt` (now `DiagnosticsScreen`); `ui/SettingsScreen.kt` (no Back button). `ui/PlayerScreen.kt` deleted (replaced by `PlayerPanel`).
- `ui/platform/PlatformUi.kt` (+ android/jvm actuals): back handler, text file picker, `WindowControls`, PiP flag.

**Logic (shared)**
- `library/{Timestamps,DescriptionText,HistoryGrouping,SubscriptionImport,SleepTimer}.kt`, `feed/{FeedSteps,FeedMerge,FeedService}.kt`, `extractor/RssFeed.kt`.
- `extractor/Extractor.kt` (+ `trending`, `related`, `channelShorts`, `recentUploads`; default `Unplayable("not supported")`), `Models.kt` (`VideoSummary.uploadedAt`), `StubExtractor.kt`, `chain/FallbackChain.kt`.
- `player/PlaybackSession.kt` (sleep timer, volume, mute), `player/PlayerController.kt` (`setVolume`).
- `data/LibraryRepositories.kt`; SQLDelight: `2.sqm` (migration to v3), `SearchHistory.sq`, `Playlist.sq`, `FeedItem.sq`, extra queries in `Subscription.sq`, `WatchHistory.sq`.

**Extractors / player**
- `extractor-newpipe`: `NewPipeExtractor.kt` (trending via the kiosk, related, Shorts tab, RSS-first `recentUploads`), `ItemMapping.kt` (upload date).
- `player-mpv`: `SoftwareRenderLoop.kt` (frame lock, `detachSink`), `MpvPlayerController.kt` (`setVolume(Float)`, `detachSink`), `RenderSize.kt` (cap).

**Apps**
- `desktopApp`: `main.kt` (window state, fullscreen controls, `AppServices`), `playback/DesktopPlayerHost.kt` (render cap, `-Dvela.noRenderCap` switch for measuring), `playback/SkiaFrameSink.kt` (linear filtering), `build.gradle.kts` (`liveWindow` args), liveTest `LiveWindow.kt`, `DesktopUiLiveTest.kt` (rewritten for the new UI).
- `androidApp`: `MainActivity.kt` (PiP, `AppServices`), `playback/PlaybackRuntime.kt` (exposes `database`), `AndroidManifest.xml` (`supportsPictureInPicture`), `Media3PlayerController.kt` (`setVolume`).
- Build: `gradle/libs.versions.toml` (Coil 3.2.0), `shared/build.gradle.kts` (Coil, activity-compose for Android actuals).

**Tests**
- shared: `library/{Timestamps,HistoryGrouping,SubscriptionImport,SleepAndShortcuts}Test`, `feed/FeedStepsTest`, `ui/LookTokensTest`, `extractor/RssFeedTest`, additions to `PlaybackSessionTest`, jvm `data/LibraryRepositoriesTest`, `library/FeedServiceTest`, `JvmDatabaseFactoryTest` (updated to build the v1/v2 shape by dropping the v3 tables).
- player-mpv: `RenderSizeTest`, `SoftwareRenderLoopTest`, `Fakes.kt`. extractor-newpipe liveTest: `LiveFeedTest`.

## Decisions the spec didn't dictate

- **Images: Coil 3** (`coil-compose` + the OkHttp network engine on both platforms). Nothing else in the project could load a thumbnail.
- **Feed source: RSS first, channel tab fallback** (numbers above). RSS carries no duration, so **Min length keeps a video whose duration is unknown**, and the Shorts split relies on the feed's `/shorts/` link form (measured to be present). Videos from the channel-tab fallback have durations and are therefore filtered exactly.
- **Watch Later is a protected playlist** (`systemKey = "watch_later"`, created on first use, cannot be renamed or deleted); playlists reject duplicates and keep dense positions.
- **`history.localOnly` is a stored setting only**; there is no sync yet, so nothing consumes it. It is schema-declared (default off) as asked.
- **The player is pinned above the scrolling page** (picture, transport, seek bar, volume, fullscreen); speed, quality, sleep timer and stats are the first rows of the scrolling part (`PlayerOptions`). Found while testing task 10: with the picture inside the list, scrolling to the comments disposed it and drawing stopped (audio continued).
- **Mini player shows controls, not the picture.** The one video surface lives on the video page; leaving it keeps audio/playback going and shows title, play/pause and close. Tapping it reopens the page.
- **Tab switch resets the stack** (no per-tab back stacks). Related videos and queue advance replace the current video page instead of stacking.
- **Account id fixed to `"local"`** everywhere via the repositories' `accountId` parameter, so real accounts only need a different value.
- **Shortcuts use `onKeyEvent`, not the preview pass**, so a focused text field consumes its own keys first. Focus returns to the page on every navigation.
- **Sleep timer is wall-clock** (keeps counting while paused) and "end of video" applies to the current video only.
- **PiP via `setAutoEnterEnabled` on API 31+**, `onUserLeaveHint` before that; the PiP window shows only the video.
- **`SettingsResolver.revision`**: a `StateFlow` bumped on each write, so the theme reacts to a Look change immediately instead of after restart.
- **`PlayerController.setVolume` has a default no-op**, so test fakes and future backends are not forced to implement it.

## Known issues / unverified assumptions

- **Looks are untested by eye.** I could not see the screen: spacing, contrast, Pure Black, the side rail layout and the "glass" effect are unreviewed. The glass effect is a translucent blurred layer behind content, not a true backdrop blur, and is off in both built-in Looks (`blur = false`).
- **Android is compile-only**: navigation, Back, bottom bar, file picker, PiP, mini player, notification interplay, Coil loading, pull-to-refresh by touch.
- **Fullscreen 1080p60 on a 1080p screen is still 120-137% of a core.** The cap does nothing there; the cost is not isolated.
- **Comments sort control is hidden** because the NewPipe tier does not support it (as designed); there is no live test of expanding replies in the UI (the extractor's `replies()` has its task-04 coverage).
- **Description HTML** handling is a small hand-written reader (anchors, `<br>`, entities). Tier output other than plain text and simple anchors was not examined.
- **Chapters shown as seek-bar ticks and a title**; chapter and SponsorBlock marker positions are approximated against the slider track (10 dp inset), so ticks can sit a few pixels off the thumb's true position. The seek bar has no markers until task 10 supplies segments.
- **`channelShorts` has no pagination UI**; only the feed uses RSS flags. The Shorts tab inside Subscriptions lists Shorts from the feed (last 15 uploads per channel), not each channel's full Shorts history.
- **Feed refresh runs when Subscriptions opens and on demand**; there is no background refresh.
- **Subscription "import" needs a file picker**; on desktop that is the AWT `FileDialog`, which I did not click through (the parsers are unit-tested with fixtures only; the dialog is untested).
- **Wide layout threshold is 840 dp** (`SpacingTokens.wideLayoutMinWidthDp`); the Android tablet/foldable behaviour is unobserved.
- **Subscriptions are not fetched through the fallback chain's other tiers**: `recentUploads` is NewPipe-only (yt-dlp and the stub tiers answer "not supported"), so a NewPipe outage empties the feed refresh (cache still shows).
- The Diagnostics screen no longer plays video; it lists streams only.

## Manual test checklist

**Linux (`./gradlew :desktopApp:run`)**
1. App opens with a bottom bar when narrow (drag the window below about 840 px wide) and a side rail when wide. Resize back and forth **while a video plays**: playback, comments and scroll position must survive, no crash.
2. Home shows Trending. Type a query in the top bar, press Search: results appear; Load more adds items without repeats. Empty the field and open Search from the rail: recent searches show, "Clear" empties them.
3. Open a video: picture on top, then title, Subscribe, description, chapters, related, comments. Click a link in the description (opens the browser) and a `1:23` timestamp (seeks). Chapter rows seek; the current chapter's title shows next to the time.
4. Use the keyboard on the video page: space, left/right (5 s), shift+left/right (10 s), up/down volume, `m`, `<` `>` speed, `f` fullscreen, Esc leaves fullscreen. Typing in the search box must not trigger them.
5. Fullscreen button: the window goes fullscreen with the controls over the picture; leave it and the page is where you left it. Report CPU for 1080p60 fullscreen.
6. Sleep timer: choose 15 min, confirm "Sleep timer: 15 min" appears; choose "End of video" and let a short video end: it stays ended.
7. Press Back from the video: the mini player appears above the nav and the video keeps playing; Pause/Play and Close work; tapping the title reopens the page.
8. Subscribe on a video, open Subscriptions: the feed fills (cached first, then refreshed). Set Shorts to "Separate tab" in Settings > Feed and confirm Shorts move to the Shorts tab; "Hide watched", minimum length and keyword rules also work (Settings > Feed).
9. Subscriptions > Channels > Import file with a Takeout `subscriptions.csv` (and, if you have one, a NewPipe export). The message reports new/already/skipped counts.
10. Library: History groups by day, delete one, clear all (asks first). Create a playlist, add videos via the ⋮ menu, rename, reorder, remove, Play all (the next video starts when one ends); Watch Later cannot be renamed or deleted.
11. Settings: switch the Look to Pure black (applies immediately). Toggle Performance mode. Settings > Diagnostics still lists tier attempts.
12. Look at the UI and tell me what is ugly or misaligned; I could not.

**Android**
1. Install and open: bottom bar, top search, Home trending loads with thumbnails. System Back goes back through screens and does not close the app until the root.
2. Play a video, press Home: it enters Picture-in-Picture (Android 12+ automatically; on older versions on leaving). The PiP play/pause button works. Expand it back: the page is intact.
3. Rotate or resize (split screen): the video page keeps playing and keeps its state.
4. Sleep timer on the player; mini player after Back; background audio still works with the screen off (task 05 behaviour).
5. Subscriptions: pull down to refresh; Import file opens the Android picker and reads a CSV/JSON from Downloads.
6. Library, history, playlists as in the Linux list.
7. Report anything that looks wrong at phone width (spacing, clipped buttons, controls wrapping).
