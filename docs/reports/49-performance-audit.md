# 49 - Performance audit (scroll and navigation lag)

## 1. Summary
Static audit of the Compose UI for scroll/navigation lag, plus two low-risk fixes. Nothing was profiled (the agent cannot run Android), so the ranking below is unmeasured. The biggest suspects are build-level (no R8, no baseline profile; debug builds are far slower) and a handful of recomposition/main-thread patterns.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `:shared:compileKotlinJvm`, `:androidApp:compileDebugKotlin` | pass | compile-only |
| `allTests` | 568/569 pass; the one failure (`LookBuilderTest.noScreenSpellsARawColourRadiusOrFontSize`) is raw `Color.White`/`RoundedCornerShape` in your uncommitted Scrubber.kt, ChaptersSheet.kt, PlayerSheet.kt | verified-by-running |
| Frame-time improvement | unknown | not measured |

## 3. Files created/changed
- `shared/.../ui/screens/Lists.kt`: channel-avatar lookup (SQL query) moved off the main thread via `rememberAsync`; `open`/`channel` click lambdas remembered; unused `scope` removed.
- `androidApp/.../MainActivity.kt`: `requestHighRefreshRate()` asks for the display's highest refresh-rate mode (compile-only).
- `shared/.../ui/screens/ChannelScreen.kt`: tab panes wait for the channel landing (skeletons meanwhile), so the placeholder tabs no longer fire a request that fails with "not supported" before the real page lands.
- `docs/reports/49-performance-audit.md`: this report.

- `shared/.../extractor/Models.kt`: `VideoSummary` marked `@Immutable` (its `Instant` field made it "unstable", so every card recomposed when the feed list was rebuilt).
- `androidApp/.../MainActivity.kt`: data layer (database, accounts, modules, extractor chain) warmed on a background thread from `onCreate`; the PiP-params collector (which builds ExoPlayer + the playback session) now starts when the main thread is idle.
- `androidApp/.../PlaybackRuntime.kt`: the "was playing" collector (which forced ExoPlayer to be built at construction) also starts on idle.

Launch cause found: the whole object graph (DB, Keystore-backed accounts, NewPipe, ExoPlayer, WorkManager scheduling, remote config) was built on the main thread before the first frame.

- `androidApp/build.gradle.kts`: new `measure` build type (release code, debug-signed, not debuggable; `adb install -r` over the debug app keeps data). `./gradlew :androidApp:assembleMeasure`.
- `shared/.../ui/player/PlayerSheet.kt`, `ui/screens/VideoScreen.kt`: the mini-player morph no longer reads its progress in composition. Progress is a provider read in layout/graphicsLayer/draw lambdas; composition only follows threshold flags (`derivedStateOf`). The details list stays composed once shown and is laid out at a fixed full size; the sheet shadow fades out by 30% progress.

## Measured on the device (CPH2707, 120 Hz, adb gfxinfo, scripted swipes)
| scenario | janky frames | 90th pct | cold start |
|---|---|---|---|
| Home scroll, **debug** build (what was installed) | 20.9% | 38 ms | 1370 ms |
| Home scroll, release-like `measure` build | 4.5-6.0% | 14-15 ms | 430-560 ms |
| Minimise+expand x3, before morph fix | 44% (expand 59%) | 36 ms (p99 81-150) | |
| Minimise+expand, after | 26-52% min / ~26% expand | | |
| Expand p99 frame | 125-150 ms -> 44-65 ms | | |
After the fix the UI thread averages ~6 ms per frame; the remainder is ~7 ms GPU (video surface + rounded clip) per frame, against an 8.3 ms budget at 120 Hz.

## 4. Decisions the spec didn't dictate
- Did not enable R8 (the build comment says NewPipe/Python/JNI keep rules are untestable without a device).
- Did not add a baseline profile or restructure the player screens in this pass: both need device measurement first.

## 5. Known issues / unverified assumptions
Remaining findings, not yet fixed:
1. Release build: `isMinifyEnabled = false`, no baseline profile. Likely the largest cause of first-scroll/first-navigation jank. Debug builds are much slower still.
2. `VideoScreen.kt` (~line 308), `PlayerSheet.kt` (78, 304), `PlayerView.kt` (131-154) read playback position / ~10 flows at the top level; the position ticks every 500 ms (`Media3PlayerController.POSITION_INTERVAL_MS`), recomposing the whole page. Compose strong skipping (Kotlin 2.4) softens this but it is still worth isolating to the scrubber/time label. Left alone because those files have uncommitted edits of yours.
3. Every card has its own `VideoMenu` (state + conditional sheet); `pressFeedback` (two `animateFloatAsState`) and `appearOnce` on every card.
4. `AnimatedContent` destination transition composes old and new screens together; new screens do first-time DB/network work during it.
5. Other main-thread DB reads in composition: `VideoMenuSheet` (`playlists.all()`, `isDownloaded`), `filterFeedQuick` first frame. Not swept fully.
6. Images have no size hints; avatars decode full-size images. `Effects` blur and per-skeleton shimmer are minor.
7. An image loader (`ImageLoaderSetup.kt`) already existed; an earlier duplicate of mine was removed.
8. The channel error cause is inferred from code (race between placeholder-tab load and landing), not reproduced.

9. The mini-player animation is still not 120 Hz smooth (GPU-bound: ~7 ms/frame). Next candidates: a Perfetto trace of the transition, the PlayerView surface (resizing a SurfaceView per frame), the Home list drawn behind it.
10. The lag you saw was mostly the debug build: install a release/`measure` build for real use.

## 6. Manual test checklist
1. Install a **release** build (`./gradlew :androidApp:assembleRelease`, or `collectReleaseApk`), not a debug/Run build, and note whether the lag is still there.
2. Turn on the frame-stats overlay (setting `debugFrameStats`), scroll Home for 10 s, switch all tabs, open a video; note the janky-frame numbers printed as `VELA-FRAMES`.
3. Repeat after this change and send both sets of numbers so the next steps (baseline profile, player recomposition) can be prioritised.
4. Check Home/Subscriptions cards still show channel avatars (they now appear a moment after the list).
5. Check the display now runs at its top refresh rate (Settings > Display may cap it; the frame-stats budget is 11.1 ms), and that opening a channel shows skeletons, not "Could not load".
6. Cold-start the app and note how long until Home shows; scroll Home. Compare with the numbers from step 2.
