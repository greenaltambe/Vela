# Task 17: major reliability (partial: Stage 0, A, B; Stage C started: baseline measured, Linux token minting failed, device spike not built)

## 1. Summary

The phone was connected over wireless debugging, so Stage 0 worked and found real bugs: the alt-client tier failed with `NetworkOnMainThreadException` (fixed, green on the device), and downloads failed to merge VP9 into MP4 (fixed, device-verified). Stage A is done: the yt-dlp `GET(music)` 403 is yt-dlp's TV-client early-403 window (measured; the canary now waits and retries once), the Health screen shows only the latest run's status with older runs under "History", and the icon strip is not ours. Stage B: network-handoff re-resolve is implemented and unit-tested but **not yet proven on the device**, and per-account identity storage exists with no consumer. **Stage C (age-restricted video on Android) was not started**: no spike was built or measured, so Android still has no route for those videos and keeps the friendly failure. The device went offline during the Wi-Fi handoff test (see Known issues), so all later device rows are "pending device".

## 2. Verification

| Check | Result | Verified by |
|---|---|---|
| Baseline on device before the fix: `ALT_CLIENT` canaries `android.os.NetworkOnMainThreadException` | reproduced (self-test on the main thread, OPPO CPH2707, Android 16 / SDK 36) | running, on device |
| After the fix: self-test canary run, main thread, StrictMode network death on | ANONYMOUS 10/10 ok, ALT_CLIENT 9/9 ok; run took 18-19 s | running, on device |
| Playback smoke on device (dQw4w9WgXcQ): resolves in 2.5-3.2 s via NewPipe, starts, position advances 20.1 s in 20 s, seek to ~55 s, pause, resume | all pass | running, on device |
| Download + merge on device (19 s video, <=144p): before: `muxer cannot put video/x-vnd.on2.vp9 in mp4`; after (H.264 re-plan): COMPLETED, 504 566 bytes, mp4 | pass | running, on device |
| StrictMode (debug builds: network, disk reads/writes on main, leaked closeables, log; death on network in the self-test) | no violation lines in logcat during canary + playback + download runs | running, on device (log grep; see issues) |
| Structural test: alt client and stream probe never run the blocking transport on the caller's thread | pass (`NoCallerThreadIoTest`, thread named `pretend-main`) | running (JVM) |
| yt-dlp `GET(music)` 403, 3 runs of the real yt-dlp 2026.10.26 + curl with yt-dlp's own headers | TVHTML5 URLs: 403 for the first ~8-14 s (`available_at` in the future), 206 afterwards; visionOS audio (139) always 206 | running (real network) |
| Canary retry once after `available_at`: early 403 then ok is logged as ok with both attempts; two 403s fail with both attempts in the detail | pass | running (fake clock) |
| Health: status from the latest run only, older runs under History; old stored rows still decode | pass (3 new tests) | running; the Compose screen itself was not looked at on a screen |
| Network handoff: network error re-resolves and resumes at the position with growing backoff, bounded (3), then surfaces; off = surfaces | pass | running (fake clock, fake controller) |
| Per-account identity: reused, swapped per account, stale/failed refresh keeps device id, removed with the account and by sign-out-everywhere | pass (3 tests) | running |
| Plain `./gradlew :desktopApp:run`: JVM args contain no hot-reload agent | none found | running |
| `./gradlew allTests test :androidApp:assembleDebug :androidApp:assembleRelease` | exit 0, no failures printed (quiet mode; counts not captured) | running |
| Release APK contains no `SelfTestActivity` (dex scan), debug APK does | 0 hits vs 20 | running |
| Handoff on device over USB (Wi-Fi off 20 s, then on; net UNMETERED→METERED→UNMETERED): playback stayed PLAYING, position 0→73.4 s in 75 s, no stall. The new re-resolve path did NOT fire (recovery note null): the buffer covered the gap, so that code is still only proven by unit tests | pass | running, on device |
| Background playback on device (real app via UI: search, open Rick Astley, then HOME): media session PLAYING, position 18.5 s → 40.3 s over 23 s in the background; `VelaPlaybackService` isForeground=true, type 0x2 (mediaPlayback), media notification present with 2 actions | pass for state; whether sound was audible was not checked | running, on device (dumpsys) |
| Process death on device (`kill -9` while playing at 46.3 s, app in background): the foreground service restarted the process at once (new pid), session state NONE, app opens on Home with no mini player; the watch-history row had position 39 322 ms (saved by the 15 s writer, so up to ~15 s lost), so reopening the video resumes there | playback and mini player are **not** restored (by design: nothing persists them); position resume works | running, on device (dumpsys, sqlite of vela.db) |
| Media buttons on device (`adb shell input keyevent`, real app playing): MEDIA_PAUSE → PAUSED, MEDIA_PLAY → PLAYING, PLAY_PAUSE toggles both ways; MEDIA_NEXT with no next item: kept PLAYING | pass | running, on device |
| Audio focus on device (self-test `--es run focus`; the "other app" is a second focus request inside our own process, not a real third-party app or call): transient loss → PAUSED then resumed by itself on release; ducking request → kept PLAYING; permanent loss → PAUSED and stayed paused | pass | running, on device (stand-in) |
| Slow-stream failover (new): buffering for 12 s after playback had started re-resolves ONCE per video at the position, preferring a source other than the one that served last (the chain tries the others first, the avoided tier last, never dropped); setting "Switch source when a stream keeps buffering", default on; short stalls, the setting off, and a second stall do nothing; the Diagnostics log gets a "preferring another source" note | pass | running (fake clock; chain test with two stub tiers). NOT forced on the device: no way to produce a real 12 s stall on demand |
| Mini player after process death (new): the mini player's video is saved in settings and restored idle (shows Play) on the next start; Play starts it and resumes from the saved watch position. On the phone: played, `kill -9`, relaunch: mini player shown with Play; Play resumed at the saved position (saved 68.09 s, playing at ~68.1 s a few seconds later) | pass | running, on device + unit test of the snapshot |
| Foreground-service rules, static review (this phone is Android 16 only; 12/14/15 not tested): manifest declares FOREGROUND_SERVICE_MEDIA_PLAYBACK and FOREGROUND_SERVICE_DATA_SYNC with matching `foregroundServiceType`; the download service passes the type to `startForeground` on API 29+, handles Android 15's dataSync timeout (`onTimeout` pauses the queue), and its start from the background is wrapped in `runCatching` (Android 12+ throws `ForegroundServiceStartNotAllowedException`; the download then runs only while the process lives, with no notification); playback's service is Media3's MediaSessionService; POST_NOTIFICATIONS requested at runtime | review only | not run on those OS versions |
| Owner's hands-on test on the phone: pausing on Bluetooth disconnect works (becoming-noisy proven by a real disconnect); lock-screen play/pause and seek bar work; "previous" restarts the video (nothing before it); "next" was sometimes visible and did nothing | pass except "next" | owner, on device |
| "Next" fix: the media session now hides skip-to-next (`NoQueuePlayer` ForwardingPlayer in VelaPlaybackService.kt). After the fix the session's action mask has no skip-to-next bit for a normal video (7339995) and for a live stream (7339739); I did not measure a before-state that showed the button, so the cause (Media3 offering next for some media) is inferred, not reproduced. Not seen on the lock screen by me | compile + dumpsys on device | running, on device (dumpsys) |
| Owner's hands-on test: a real phone call pauses playback and it resumes from the same point when the call ends; unplugging a wired headset pauses | pass | owner, on device |
| Becoming-noisy (adb part): the platform refused `am broadcast AUDIO_BECOMING_NOISY` from adb (protected broadcast), so it could not be triggered. `dumpsys activity broadcasts` while playing shows a receiver for that action owned by our pid (indirect evidence; ExoPlayer is built with `setHandleAudioBecomingNoisy(true)`) | the owner then confirmed it by really disconnecting Bluetooth (row above) | running (dumpsys only) |

## Stage C progress (started after Stage B)

Measured so far (Linux, throwaway test account via `~/.vela-test/cookies.txt`, one yt-dlp call, 2026-10-28):

| Check | Result |
|---|---|
| yt-dlp 2026.10.26 `-v` with the account cookies on HtVdAasjOgU | works, but ALL 27 playable https formats and the 6 HLS formats come from the embedded web client (`WEB_EMBEDDED_PLAYER`, "WEB-E client"); the `tv_samsung` client returned no playable formats; no `pot=` in any URL; "PO Token Providers: none" |
| Consequence | yt-dlp's own signed-in route for this video IS the embedded client, which this project forbids, so it cannot be copied; the permitted route has to be the signed-in WEB/MWEB/TVHTML5 `/player` call with a PO token |
| Task 13 baseline | own `/player` with cookies on WEB/MWEB/TVHTML5 returned UNPLAYABLE even for a normal video, without a token |

**Linux token-minting attempt (you ran the script; I did not):** with `bgutils-js` 4.0.3 + `jsdom` 30.1.3: the WAA challenge fetch worked, the BotGuard snapshot ran and `GenerateIT` returned HTTP 200 (ttl 43 200 s), but creating the minter failed in both setups tried: interpreter evaluated inside the jsdom window → `APF:Failed`; interpreter in Node's global with jsdom `window`/`document` patched in → `PMD:Undefined` (plus `removeEventListener is not a function` inside the VM). BotGuard rejects the jsdom environment. So **no token was minted and the question "does a token turn UNPLAYABLE into streams for the signed-in age-restricted video" is still unanswered.** This says nothing yet about a real Chrome WebView on the phone, where the environment checks may pass.

**Where Stage C stands (stop point):** nothing for Spike A or B is built or registered; the Android friendly failure is unchanged. Not measured: token effect, URL ciphers / n-challenge on the web client, latency, APK size, memory. What the next step would cost (estimate, not measured): bundle bgutils-js (MIT) as an app asset, a hidden WebView on a youtube.com origin that runs it and hands back tokens (cached, refreshed ahead), the signed `/player` call under the governor, and stream URL deciphering (NewPipe's player-JS code if its API allows), with a real 206 GET on the device as the success test. It could still end in UNPLAYABLE, in which case none of it ships.

## 3. Files created/changed

- **Stage 0 (debug only, `androidApp/src/debug`)**: `debug/SelfTestActivity.kt` (canary, play, download, handoff scenarios; turns the screen on; writes `files/selftest.json` + `VelaSelfTest` logcat lines; never uses the account), `debug/DebugStrictMode.kt`, `debug/DebugApp.kt`, debug `AndroidManifest.xml`. Run: `adb shell am start -n com.greenaltambe.vela/.debug.SelfTestActivity --es run all` (`canary | play | download | handoff | all`), result via `adb shell run-as com.greenaltambe.vela cat files/selftest.json`.
- **A1**: `innertube-auth/.../AltClient.kt` (player call in `withContext(Dispatchers.IO)`), `AuthSession.kt` (`runBlocking(Dispatchers.IO)`), test `NoCallerThreadIoTest.kt`.
- **Download fix**: `MediaMerger.canMerge` (`Platform.kt`), `AndroidMediaMerger.canMerge/messages`, `DownloadManager.choose` re-plans with H.264 when the merger rejects the pair, test `AndroidMediaMergerTest`.
- **A2/A3**: `health/ExtractorHealth.kt` (`probeOnce`, runs, `TierHealth` from the latest run, codec with run field), `ui/screens/HealthScreen.kt`, `ExtractorHealthTest.kt`.
- **B**: `player/PlaybackSession.kt` (`maxNetworkRecoveries`, `networkBackoff`, `recoveryNote`), `PlaybackSessionTest.kt`; `auth/AccountIdentity.kt` (+ tests in `AuthCoreTest.kt`).
- Not committed.

## 4. Decisions the spec didn't dictate

- The root cause of A1: `AltClientExtractor.attempt` is a `suspend` function that called a blocking OkHttp `execute` directly, so on Android it ran on whatever dispatcher the caller used (Main for the Health screen). Fix at both blocking entry points (alt client, `AuthSession.send`); `OkHttpStreamProbe`, remote config and login/sync already used IO. NewPipe's tier uses `runInterruptible(Dispatchers.IO)`.
- Not audited line by line: RSS feed, SponsorBlock/DeArrow/RYD (they use `OkHttpTransport`, which is callback-based with `enqueue`, so it never blocks the caller), the release check (same transport). The on-device self-test with StrictMode death is the real guard for the paths it covers (canaries, playback, downloads).
- Download merge: Android's muxer rejected VP9 in MP4 on this phone, so a VP9 + AAC pair has no container; the manager now plans again preferring H.264 + AAC whenever the merger says a pair cannot be merged. The rule `VP9 in MP4 is unsupported` is **measured on one device only** (OPPO CPH2707); other devices may accept it, in which case they lose VP9 downloads in this pairing for no reason.
- Canary: waits for the URL's `available_at` (or 2 s if unknown), capped at 20 s, retries once, and records both attempts. A genuine 403 fails twice and shows both.
- Health: the tier status is the latest run only; "Last time a check passed" is the tier-wide last success, shown only in the failing sentence.
- Network recovery treats a `Network` player error like a dead URL (the IP changes on handoff, which can invalidate URLs): re-resolve, resume at the position, pause 2 s x attempt, wait up to 30 s while the monitor says OFFLINE, at most 3 in a row (reset after 3 s of progress).

## 5. Known issues / unverified assumptions

- **(Resolved: device reconnected over USB and the handoff ran.)** Earlier the device went offline during the handoff test. It was attached over wireless debugging (Wi-Fi); `adb shell svc wifi disable` cut the link and the matching enable never arrived. This was avoidable: Wi-Fi toggling cannot be driven over a Wi-Fi adb link. Please turn Wi-Fi back on. For the handoff test, connect by USB cable (then `svc wifi` works), or toggle Wi-Fi by hand.
- **Stage C not started** (no Spike A or B: no PO-token provider, no own web-client /player with cookies, no embedded Python). Nothing is half-built or registered for it; the friendly failure and the Linux yt-dlp route are unchanged. No numbers exist for the comparison table: all "not measured".
- **Stage B remainder**: slow-stream failover uses "buffering for 12 s" as the proxy for "throughput below bitrate" (throughput is not measured); the mini player is restored idle, not auto-playing, and the player's own state (quality choice, speed) is not restored; battery-optimisation hint when background playback is killed: not built; foreground-service rules on Android 12/14/15 are a code review only. One earlier run saved a watch position of 0 after ~19 s watched (row with positionMs 0, watchedMs 18.8 s) and the next start resumed from the beginning; I could not reproduce it in two later runs (positions saved correctly), cause unknown. Logcat on this OPPO/Android 16 phone printed `AudioHardening background playback would be muted ... level: partial` while playing in the background: a platform audio-hardening notice; I could not tell whether it actually muted (not measured by ear).
- **Account identity** is stored but nothing consumes it yet (no Android account tier, and yt-dlp on Linux uses cookies only).
- **StrictMode**: "no violations" is from grepping logcat for the policy-violation line during the runs; I did not capture a positive control (a deliberate main-thread read) to prove that the grep would catch one.
- The self-test's "latest run" window for tier rows is the last 2 minutes of results (it should use the run id); two rapid runs can mix.
- The Health screen was not rendered/looked at in a screenshot; phone-width layout is by reasoning (single scrolling column, `maxLines` set).
- A4: on a plain `run` there is no hot-reload agent in the JVM args; the packaged distribution was not built/run in this task. The Compose Hot Reload Gradle plugin is on the classpath via the Compose plugin (tasks `hotDev`, `hotReloadDev` exist), so the strip is most likely its dev tools from an IDE run; this was not reproduced with an IDE run.
- Test counts for the final suite run were not captured (quiet Gradle output); only the exit status and absence of failures.

## 6. Manual test checklist for the user

1. Turn the phone's Wi-Fi back on; for later device runs prefer a USB cable (`adb devices` must list it).
2. `adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk`, then `adb shell am start -n com.greenaltambe.vela/.debug.SelfTestActivity --es run all` (screen on, unlocked) and `adb shell run-as com.greenaltambe.vela cat files/selftest.json`: expect every row ok.
3. Handoff with USB attached: `--es run handoff`, then during the 75 s: `adb shell svc wifi disable`, wait 20 s, `adb shell svc wifi enable`. Expect final PLAYING and a recovery note in the JSON/logcat (`VelaSelfTest`).
4. Phone: open the app > Settings > Diagnostics > Health > Run checks now: the alt client reads "Working", older runs are under "History".
5. Phone: download any video at 360p or lower; it should finish (check Downloads).
6. Phone: start audio, kill the app from recents / `adb shell am kill com.greenaltambe.vela`, reopen: check position, mini player, active account (not built/verified here).
7. Unplug headphones, receive a call, press media buttons: check pause/resume (not verified here).
8. Linux: `./gradlew :desktopApp:run` and confirm the icon strip does not appear; if it does, send a screenshot.
9. Decide whether Stage C (Spike A: own web client with PO token; Spike B: embedded yt-dlp) should be its own task.
