# Task 18: minor reliability fixes

## 1. Summary

Done: OS health alerts (item 2), the live yt-dlp host test (3), storage safety except corruption repair (5, partly), transport timeout audit with one fix (6, partly), the rotating redacted log with Export (7), the position-0 hardening (9), StrictMode positive control and run-id windows (10), the AudioHardening investigation (11), the battery-optimisation hint (12), VP9 + Opus into WebM (14), the age-restricted and controversial settings (16). **Skipped as large:** the captcha WebView flow (1), MPRIS and the clean-shutdown work (4), restoring quality/speed after process death (13). Item 15 is documented below. Android runtime parts were checked on the phone (OPPO CPH2707, Android 16) over USB where noted.

## 2. Verification

| Item | Check | Result | Verified by |
|---|---|---|---|
| 16 | Setting off: the first tier that reports an age restriction ends the request, the account and yt-dlp are never asked; the player shows "Age-restricted videos are disabled in settings" with an "Open setting" button; setting on: existing routes used. Controversial: contentCheckOk/racyCheckOk in the alt client follow the setting | pass (chain test both states, Compose UI test, alt-client body test) | running (JVM/desktop UI) |
| 16 | Android UI for the message | not looked at on the phone | compile-only |
| 14 | A merger that rejects VP9 + AAC gets VP9 + Opus in WebM when an Opus track exists, else H.264 + AAC in MP4; the merger is asked (`canMerge`), nothing hard-coded | pass (2 tests); on the phone the 19 s test video downloaded as `.webm` (VP9 + Opus, 437 474 bytes, COMPLETED) where before the fix it failed and after the first fix was an `.mp4` | running, unit + on device |
| 9 | A player that resets to IDLE at position 0 no longer overwrites the saved position (last real position kept) | test passes; **the cause of the one earlier 0-position row is NOT proven**, this guards the most plausible path (state transition to IDLE during `persist`); I did not run the test against the old code | running (fake clock) |
| 10 | StrictMode positive control: a deliberate main-thread disk read is found in this process's logcat by the same check | pass on the phone (`--es run strict`) | running, on device |
| 10 | Self-test canary rows use the run id, not "the last 2 minutes" | pass (full `--es run all`: 11 rows, failed 0) | running, on device |
| 11 | AudioHardening notice: `dumpsys audio` "Hardening enforcement" event log lists many apps (Stremio, Firefox, WhatsApp, a system screenshot service) as "would be muted"; it is a system-wide audit log, our player has no mute flag, and none was logged in a fresh background run | not ours, no code change; whether sound is audible in the background was not measured by ear (the owner's earlier background/call/lock-screen tests reported playback working) | running, on device (dumpsys) |
| 12 | Battery hint: play, `kill -9`, relaunch → dialog "Playback was stopped by the system"; its button opened the system "App battery usage" list (this phone's page for the intent) | pass; "once only" is by code (a stored flag), not re-tested; false positive possible after a manual force-stop | running, on device |
| 2 | Alert fires once when a failure streak reaches 2, not again while it continues, again after a recovery | pass (unit) | running (fake clock) |
| 2 | Android: notification channel `health_alerts` posted with a made-up alert | notification record present in `dumpsys notification`; text not read from the shade | running, on device |
| 2 | Linux `notify-send` | command exited 0; I did not see the pop-up | running (exit code only) |
| 3 | Real yt-dlp 2026.10.26 with the test cookies on a normal video: hosts contacted: `www.youtube.com` (5 requests, all with cookies), `manifest.googlevideo.com` (1, no cookies) | live test passes; fails on a new host or a cookie to any host except youtube.com / youtubei.googleapis.com / accounts.google.com | running (real network); the age-restricted path's hosts were not measured |
| 5 | DB: backup copy `vela.db.bak-v<old>` before a migration (kept); a failed migration leaves the file untouched and the error names it; a future-version or non-SQLite or damaged file is reported with "nothing was changed or deleted" and the app runs in memory with a dialog | pass (3 new JVM tests) | running (JVM); the Android backup in `onUpgrade` is compile-only; the desktop dialog was not looked at |
| 5 | Low disk: a download fails before transferring when free space < remaining bytes + merge output + 50 MB, and works on Retry after space is freed | pass (fake storage) | running (JVM); not run on the phone |
| 6 | Transport audit: every OkHttp client has connect/read/call timeouts (NewPipe 15/30/60, auth 15/30/45, modules 5/8/12, range fetcher 15/30 with a read-timeout-bounded body, remote config 20 s call); the yt-dlp updater's downloader had only a connect timeout and now has 60 s for headers and 5 min total, interruptible | one fix; tests pass | running |
| 7 | Rotating log (512 KB, two files), redacted twice, tier attempts + governor audit lines; a simulated session with markers (cookie, SAPISIDHASH, visitorData, bearer, pot/sig) leaves none in the file; rotation bounded | pass (2 tests); the Export button writes the file and shows the path (Linux `~/Downloads`, Android the app's external files folder) | running (JVM); Export itself not clicked on either platform |
| all | `./gradlew allTests test :androidApp:assembleDebug :androidApp:assembleRelease` | exit 0, no failures printed (quiet mode, counts not captured); release APK built | running |

## 3. Files created/changed

- **16**: `Settings.kt` (`allowAgeRestricted`, `allowControversial`), `ExtractorError.AgeRestrictedDisabled`, `FallbackChain` (`allowAgeRestricted`), `AltClientExtractor` (`contentChecks`), `PlayerView`/`VideoScreen` (message, `openAgeSetting`), wiring in `DesktopWiring.kt` and `PlaybackRuntime.kt`; tests `AgeRestrictedSettingTest`, `PlayerFailureUiTest`, `AltClientTest`.
- **14**: `DownloadManager.choose`, `MediaMerger.canMerge`, `AndroidMediaMerger`; tests in `DownloadManagerTest`, `Fakes.kt`.
- **9, 10**: `PlaybackSession.persist/lastGood`, `PlaybackSessionTest`; `androidApp/src/debug/.../SelfTestActivity.kt` (`strict`, `alert` scenarios, run-id windows).
- **12**: `PlaybackRuntime` (was-playing flag), `MainActivity.explainBatteryOptimisationOnce`.
- **2**: `ExtractorHealth.onAlert`, `HealthNotifier.kt` (Android), `NotifySendNotifier.healthAlert` (Linux), wiring; test in `ExtractorHealthTest`.
- **3**: `extractor-ytdlp/src/liveTest/.../LiveTrafficHostsTest.kt`.
- **5**: `JvmDatabaseDriverFactory.kt` (`DatabaseStartupException`), `AndroidDatabaseDriverFactory.kt`, desktop `main.kt` dialog, `DownloadManager` low-disk check; tests `JvmDatabaseFactoryTest`, `DownloadManagerTest`.
- **6**: `YtDlpUpdater.kt` (`HttpsDownloader` timeouts).
- **7**: `net-okhttp/.../FileLogSink.kt`, `LogLineSink`/`TierAttempt.logLine`, `DbAuditLog(sink)`, `AppServices.exportLog`, `DebugScreen` Export button, desktop and Android wiring; test `FileLogSinkTest`.
- Not committed.

## 4. Decisions the spec didn't dictate

- **16**: the age check lives in the chain (it stops at the first tier that says "age-restricted", by text match as before), so with the setting off no account call is ever made for such a video; downloads obey it too. "Controversial" only controls the alternate client's content-warning flags (NewPipe and yt-dlp handle their own content checks, not changed). "Open setting" opens the Settings tab, it does not scroll to the switch. Both default OFF as asked, so **age-restricted playback on Linux is now off until the viewer turns it on** (it was on before).
- **14**: first the same video with an Opus track, then H.264 + AAC; if no H.264 stream fits the height cap the same VP9 stream stays and the merge can still fail (edge case, not handled).
- **12**: "background playback killed" is inferred from "the last process was PLAYING and never recorded a pause/stop"; that includes a manual force-stop.
- **5**: the file is never repaired or deleted; the viewer is told. The backup is made once per old version and kept.
- **7**: the log lives next to the DB on Linux and in `filesDir` on Android; no scrubbing beyond the existing `Redaction`.

## 5. Known issues / unverified assumptions

- **Skipped (large):** item 1 (captcha WebView, exemption cookie, retry), item 4 (MPRIS needs a D-Bus library; shutdown/orphan-process audit not done), item 13 (quality/speed restore needs plumbing through `start()`; auto-play stays off), the DB corruption **rebuild** path of item 5 (a repair needs to know which tables are caches; detection and a clear message exist), and the screens half of item 6 (loading/empty/error/retry for every list screen, captive-portal HTML, no-network start were not exercised). Transports were audited, not screens.
- Item 15 (code-review only, not tested): foreground-service start from the background on Android 12 / 14 / 15. This phone runs Android 16 only. What exists: `VelaDownloadService.start` is wrapped in `runCatching` (Android 12+ may throw `ForegroundServiceStartNotAllowedException`; the download then runs only while the process lives, without a notification); the dataSync timeout (Android 15) pauses the queue in `onTimeout`; playback uses Media3's `MediaSessionService`. Kept in the manual checklist below.
- Slow-stream failover, from task 17, still only unit-tested.
- Android Export button, Android DB backup in `onUpgrade`, the Android health notification text and the desktop DB-problem dialog were not exercised on a screen.
- `Settings.restoreNowPlaying` is hidden (not in `all`); `slowStreamFailover` and the two new switches are in Settings > Playback.

## 6. Manual test checklist for the user

1. Settings > Playback: "Allow age-restricted videos" is OFF. Open an age-restricted video (e.g. HtVdAasjOgU) on Linux: you see "Age-restricted videos are disabled in settings" and "Open setting". Turn it ON and reopen: it plays through yt-dlp. On Android with it ON you get the old friendly failure with Open in browser.
2. Linux: `./gradlew :desktopApp:run`, Settings > Diagnostics > Export log: the file appears in `~/Downloads`; open it and confirm there is no cookie or token text.
3. Phone: Diagnostics > Export log; the path shown is under `Android/data/com.greenaltambe.vela/files`.
4. Phone: play something, kill the app (`adb shell run-as com.greenaltambe.vela kill -9 $(adb shell pidof com.greenaltambe.vela)`), reopen: the battery dialog appears once.
5. Download a VP9 video on the phone (e.g. 360p): the file is `.webm`.
6. With the network off, start a download: it fails; with low free space the message says "Not enough free space".
7. Foreground services on Android 12/14/15 (not testable here): start a download, background the app, switch network: confirm the notification stays or the download resumes when you reopen.
8. Optional: `./gradlew :extractor-ytdlp:liveTest --tests '*LiveTrafficHostsTest*'` after any yt-dlp update; it fails if a new host appears.
