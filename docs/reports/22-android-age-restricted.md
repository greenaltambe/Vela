# Task 22: age-restricted and account playback on Android

Phone: OPPO CPH2707, Android 16, 4 KB pages, arm64. Throwaway account `google:7773f2` (owner confirmed). Nothing was committed.

## 1. Summary

Spike B worked, so Spike A (WebView PO token) was not attempted. yt-dlp now runs inside the APK (youtubedl-android 0.18.1: Python 3.12 + QuickJS) as an `extractor-ytdlp`-style tier on Android. The age-restricted video HtVdAasjOgU **plays on the phone anonymously** (NewPipe and the alt client fail, yt-dlp serves it, 20 s of playback + seek + pause + resume pass), with no account involved; the account tier (yt-dlp with the throwaway's cookies, through the governor) also works as the last tier. Android also gets the anonymous yt-dlp fallback tier (parity with Linux), a verified updater with rollback, and a Health entry. Costs: +19 MB release APK, about 16 s and 11.5 s of Python CPU per resolve (the QuickJS challenge solve), 76 MB peak in the Python process.

## 2. Verification

| # | Item | Result | Verified by |
|---|---|---|---|
| 1 | Candidate choice | youtubedl-android (JunkFood02 fork, Maven Central `io.github.junkfood02.youtubedl-android:library` 0.18.1, 2025-11-16; original yausername repo last push 2025-11-16): minSdk 24, ABIs arm64-v8a/armeabi-v7a/x86/x86_64, Python 3.12.11, QuickJS (`libqjs.so`, 2025-04-26), bundled yt-dlp 2025.11.12. **GPL-3.0** (see section 5). Chaquopy was not evaluated (the first candidate worked) | read the AAR + Maven metadata |
| 2 | 16 KB page size | all 140 `.so` inside the bundled Python plus `libqjs.so`/`libpython.so` have LOAD alignment `0x4000`; 0 libs at 4 KB | `readelf -lW` on the extracted AAR. **Not run on a 16 KB device** (this phone is 4 KB) |
| 3 | JS runtime proven with one real extraction | QuickJS ran the player challenge; dQw4w9WgXcQ resolved, best 2160p, GET video 206 and audio 206 | phone (self-test `ytdlp`) |
| 4 | Anonymous tier, normal videos (yt-dlp 2025.11.12, warm) | dQw4w9WgXcQ 6.5 s, kJQP7kiw5Fk 5.6 s, jNQXAC9IVRw 5.8 s, LXb3EKWsInQ (4K, 31 video + 8 audio) 6.0 s: all GET 206. Live 4xDzrJKXOOY: HLS URL, GET 200 | phone |
| 5 | Age-restricted, **bundled** yt-dlp 2025.11.12 | fails: "Sign in to confirm your age" (5.1 s) | phone |
| 6 | Updater (SHA2-256SUMS, version check, canary, rollback, no root) | wrong checksum: refused, version unchanged; failed health check: previous copy back; real update 2025.11.12 -> 2026.10.26 installed (17 s incl. canary), `yt-dlp.previous` kept, no temp files left; the update **survives a process restart** (library `init` does not overwrite it) | phone (self-test `ytdlpupdate`) + 8 JVM tests (`EmbeddedYtDlpTest`) |
| 6b | **First launch updates yt-dlp by itself** (added after the first review) | phone put back to the fresh-install state (yt-dlp 2025.11.12 installed through the updater), app opened normally, nothing pressed: ~15-20 s later the file was 2026.10.26 (after the 15 s start delay), `yt-dlp.previous` kept, and the log has "yt-dlp 2026.10.26 installed in the app's data folder" (canary passed, no rollback). It installs the **verified tag** (not "latest"), only when the installed copy is older and `ytdlp.autoUpdate` is not Off; same checksum/version/canary/rollback. Retried every 6 h until it succeeds; desktop is unaffected (its updater does not opt in) | phone + 5 JVM tests (`VerifiedMinimumTest`) |
| 7 | Age-restricted, yt-dlp 2026.10.26, anonymous | resolves: 19 video (best 1080p) + 8 audio + HLS, GET video 206 audio 206, 16.5 s | phone |
| 8 | **Age-restricted plays** (setting ON, through the chain + ExoPlayer) | tiers: ANONYMOUS FAILED(UNPLAYABLE) 0.7 s, ALT_CLIENT FAILED(SIGN_IN_REQUIRED) 0.4 s, YT_DLP SUCCESS 16.5 s; playback starts, +19.6 s in 20 s, seek, pause, resume | phone (self-test `playage`) |
| 9 | Setting OFF (default) | `AgeRestrictedDisabled`, only ANONYMOUS was tried, neither yt-dlp nor the account was called | phone (self-test `ageoff`) |
| 10 | Account tier (last), throwaway | HtVdAasjOgU via yt-dlp `--cookies` as `google:7773f2`: 19 video, GET 206, 17 s, **governor audit +1**, cookie file (mode `rw-------`, app-private `no_backup/ytdlp-cookies`) gone afterwards | phone (self-test `ytdlpauth --ez throwaway true`) |
| 11 | Cookie file after a **process kill** | killed mid-call: the file is left (`vela-<uuid>.cookies.txt`), and it is deleted when the process starts again. **Found and fixed**: the sweep first ran only when the tier was first built, so a restart that resolved nothing left it; it now runs at runtime start | phone (adb force-stop) |
| 12 | Cookie cleanup, crash/timeout/cancel | file deleted after: yt-dlp failure, runner exception, timeout, start failure, cancellation | JVM tests (`AuthenticatedYtDlpExtractorTest`, 9 tests) |
| 13 | Leak audit | Android yt-dlp code contains no `Log.`/`println`; cookie values never on a command line (existing test); `CredentialLeakAuditTest` green; no cookie file is created outside the app-private dir | JVM tests |
| 14 | Download through the new tier | tier order forced to YT_DLP first for the test (then restored): jNQXAC9IVRw 144p download + merge COMPLETED, `servedBy=YT_DLP` | phone (self-test `ytdlpdl`) |
| 15 | Offline | yt-dlp fails in ~1 s with `Network`: "Unable to download API page: [Errno 7] No address associated with hostname"; no hang | phone (Wi-Fi + data off, restored) |
| 16 | Tier failure / update failure fault injection | chain falls to the next tier on a yt-dlp failure (existing chain tests); updater failure paths in row 6 | JVM tests + phone |
| 17 | Latency | cold init (first unpack of ~53 MB, from timestamps of the first run) ~2 s; resolve 5.5-6.5 s with 2025.11.12; **16-17 s with 2026.10.26 every time** (the player-JS challenge is solved again per call; `--cache-dir` only cached the signature functions, no gain) | phone |
| 18 | CPU / memory | python child CPU **11.3-11.7 s per resolve** (`/proc/self/stat` cutime+cstime), app process 0.3-2.8 s; Python process peak RSS 76 MB (sampled once per second, may be a little low); Java heap 6-46 MB | phone |
| 19 | APK size | release 17 MB -> **36 MB** (+19 MB), debug 23 -> 43 MB; arm64-v8a only (the AAR carries 4 ABIs, ~15 MB each); no ffmpeg artifact added (+139 MB AAR avoided) | build |
| 20 | Health canary | YT_DLP tier 10 checks ok on the phone; but a full canary run became 120 s -> added `slowTiers`: yt-dlp gets one video, streams + GET only | phone + JVM test (`ExtractorHealthTest`) |
| 21 | Health screen | embedded copy shown with its version, "embedded in the app (Python 3.12 + QuickJS...)", stale detection against the newest release, install button, and the last update/rollback message kept across restarts (`health.ytdlp.lastUpdate`, not in backups) | compile-only for the Android screen; shared UI covered by the desktop UI tests |
| 22 | Message while resolving | "Age-restricted or sign-in video: trying a slower source (can take up to 30 s)…", "Using your account…" only when the account may serve and yt-dlp has already failed, "Trying the next source…" otherwise | JVM tests (`ResolveNoteTest`, 5); the text on the phone screen: **not looked at** |
| 23 | Spike A, tracking URLs for watch-history | not attempted (Spike B succeeded). whether yt-dlp's JSON exposes YouTube's tracking URLs was **not checked**, so reporting stays unbuilt (task rule) | NOT VERIFIED |
| 24 | Full suite | see section 4 | desktop |

## 3. Files created/changed

New: `androidApp/.../ytdlp/EmbeddedYtDlp.kt` (init, cookie sweep, version reader, `EmbeddedProcessRunner`), `EmbeddedYtDlpUpdater.kt` (OkHttp downloader + verified updater), `androidApp/src/test/.../ytdlp/EmbeddedYtDlpTest.kt`, `shared/.../ui/screens/ResolveNote.kt` + `ResolveNoteTest`, this report.
Changed: `gradle/libs.versions.toml`, `androidApp/build.gradle.kts` (library, okhttp, `:extractor-ytdlp`, arm64 filter, extracted native libs, keep `.zip.so` unstripped), `extractor-ytdlp/build.gradle.kts` (comment: now shared with Android), `PlaybackRuntime.kt` (YT_DLP + AUTHENTICATED tiers, updater, cookie sweep at start, slow-tier canary), `MainActivity.kt` (yt-dlp release watch + updater), `ReleaseCheck.kt` (`installsVerifiedMinimum`, `verifiedMinimumToInstall`) + `HealthScreen.kt` start effect + `VerifiedMinimumTest`, `PlayerView.kt` + `VideoScreen.kt` (resolving note), `HealthScreen.kt` (last update always shown), `ExtractorHealth.kt` (`slowTiers`) + test, `AuthenticatedYtDlpExtractorTest.kt` (crash cases), `Settings.kt` (age setting label), debug `SelfTestActivity.kt` (scenarios `ytdlp`, `ytdlpupdate`, `ytdlpv`, `ytdlpdl`, `ytdlpauth`, `playage`, `ageoff`, `whoami`; `all` now includes ytdlp, playage, ytdlpdl), `docs/SPEC.md` section 11, `docs/tasks/parked-android-age-restricted.md` (closed), `docs/reports/20-core-completion.md` (table).

## 4. Full suite

`./gradlew allTests test :androidApp:assembleDebug :androidApp:assembleRelease --continue`, run after the last code change. First run: 109 tests in `:extractor-ytdlp` ran and **1 failed**, `SystemProcessRunnerTest.cancelledRealProcessIsReallyKilled` (a real-process timing test in code this task did not touch; the machine was also running the build). It passes alone (10/10) and the full run was repeated: **green**, debug and release APKs assemble. Android runtime parts are verified on the phone only where marked above; everything else Android is compile-only.

The suite was run again after the last change (the first-launch update, row 6b): **green** (empty output, both APKs assemble).

Leak audit: `CredentialLeakAuditTest` is part of the suite (green); `git status` shows no untracked cookie/secret files.

## 5. Decisions the spec didn't dictate / known issues

- **Licence:** youtubedl-android is GPL-3.0. NewPipeExtractor (already shipped) is GPL-3.0 too, so the app is already GPL-bound if distributed; the repo has no LICENSE file yet. Owner decision before any release.
- **Latency is the price:** yt-dlp 2026.10.26 needs ~16 s and ~11.5 s of Python CPU on this phone for every resolve, because the QuickJS challenge solve is not cached. It is only reached when NewPipe and the alt client fail (age-restricted, bot checks), and the tier-order setting can move it. Not tried: restricting yt-dlp's clients with `--extractor-args` to skip the challenge (could be faster, could lose formats).
- **Bundled yt-dlp is too old for age-restricted** (2025.11.12), so the app installs the verified release 2026.10.26 by itself ~15 s after the first start (row 6b). Until that has finished (or if the device is offline, or `ytdlp.autoUpdate` is Off) age-restricted videos give the friendly message. The download is from github.com/yt-dlp over HTTPS and is the one network call the user did not trigger.
- **Python minimum:** yt-dlp may one day require a newer Python than the library's 3.12; then the tier fails until the library is bumped. The library is maintained by one person (last release 2025-11-16).
- **Library `versionName()` is null** unless its own updater ran; the version is read from the zipapp instead (a `#!` line precedes the zip, which Android's `ZipFile` rejects, so it is streamed).
- The update asset is `yt-dlp` (zipapp), verified against the release's `SHA2-256SUMS` over HTTPS; its own `__version__` must equal the tag. The library's built-in updater (no checksum) is not used.
- The yt-dlp tier is also canary-checked on a reduced schedule (one video) because of its cost.
- A `ytdlp-cookies` file exists only during an account call; never in logs; swept at start.
- `:extractor-ytdlp` (JVM) is now a dependency of the Android app. Its desktop-only classes (`SystemProcessRunner`, `HttpsDownloader` use `java.net.http`) are never loaded on Android; release R8 is off, so nothing complained. If minify is turned on later it needs `-dontwarn java.net.http.**`.
- Not verified: a 16 KB device; the new resolving text on screen; the Health screen on the phone; battery in mAh (CPU time only); `armeabi-v7a`/x86 builds (filtered out); Chaquopy alternative; Spike A.

## 6. Manual test checklist for the owner

1. Install the new debug/release build. Settings > Playback: "Allow age-restricted videos" is already ON on your phone (it was before my tests); open `HtVdAasjOgU`: expect the note "trying a slower source" and playback after ~15-20 s. On a **fresh install** the app updates its own yt-dlp about 15-30 s after the first start (needs network); wait for that before the first age-restricted video.
2. Diagnostics > Health: the yt-dlp release line shows the embedded copy and version; after the install, "Last yt-dlp update: ...".
3. Turn the setting OFF and open the same video: friendly message with "Open in browser", and the log shows no YT_DLP attempt.
4. Switch off Wi-Fi/data and open an uncached video: a network error within a few seconds, no endless spinner.
5. Decide the GPL question and whether ~16 s per hard-video resolve is acceptable; tell me if I should try `--extractor-args` client restriction.
