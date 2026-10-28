# Task 22: age-restricted and account playback on Android (time-boxed spikes, then ship or stop)

Read CLAUDE.md, docs/SPEC.md and docs/tasks/parked-android-age-restricted.md (everything established there stays true). Do not commit.
Report: `docs/reports/22-android-age-restricted.md`, usual verification table (VERIFIED how / NOT VERIFIED why).
Terminal reply: 5 lines max, only instructions for the owner. Phone: OPPO CPH2707, Android 16, adb as in task 17 Stage 0.
Test videos: age-restricted HtVdAasjOgU plus 5 normal videos (one live, one 4K, one with only separate streams).
Account use: throwaway account via the existing login/cookie path only; every call through AccountGovernor; leak audit extended
to any new place that touches cookies. No remote solver services. No embed-site spoofing (no third-party embedUrl, no fake referer).
The Grayjay-shaped anonymous embedded request stays OUT unless the owner says otherwise in the report review.

Goal: an age-restricted video plays on the phone, using the account only as the LAST tier and only when the video needs it.
Stop rule: each spike has a budget (see below). If nothing plays on the device, write what was tried, keep the current friendly
failure plus "Open in browser", and finish. A clean "does not work" is an acceptable result.

## Spike B first: embedded yt-dlp on Android (this mirrors what already works on Linux)
1. Licence/AGP/minSdk/ABI check of the candidates (e.g. a youtubedl-android style library: Python + yt-dlp + ffmpeg in the APK,
   or Chaquopy + yt-dlp wheel). Pick one, justify in the report (APK size, 16 KB page size support on Android 16, maintenance).
2. A JS runtime is required by current yt-dlp for YouTube challenges: find out what the Android bundle can run (QuickJS, deno binary
   in the native lib dir, etc.) and prove it with one real extraction.
3. Wire it as an `extractor-ytdlp`-style tier on Android: same runner abstraction, same managed updater/rollback and SHA2-256SUMS
   check as Linux (the update must work without root; store under app files), same error mapping to ExtractorError.
4. Anonymous use first (this alone gives Android a real yt-dlp fallback tier = parity with Linux). Then `--cookies` from the
   existing account store: write a temp cookies file in app-private storage with restrictive perms, delete it after the call,
   never log it, include it in the leak audit. Tier order stays: NewPipe, alt client, yt-dlp anonymous, account LAST (data-driven).
5. Playback of the resulting URLs through the existing Media3 chunked data source (no open-ended ranges).
6. Measure on the phone: success on HtVdAasjOgU and the 5 normal videos, cold/warm latency, APK size delta, memory, battery-ish
   (CPU time per resolve), behaviour offline, behaviour when the update fails. 
7. Budget: if after reasonable effort the runtime or cookies path cannot work on the device, stop B and say exactly why.

## Spike A only if B fails or is unacceptable (size/latency): signed-in Innertube with a WebView PO token
Follow the "If revived / Spike A" notes and the Grayjay addendum (verify_age -> signed /player with sts, contentCheckOk, racyCheckOk,
visitorData and a BotGuard token from a hidden WebView on a youtube.com origin, URLs deciphered with NewPipe's player-JS code if its
API allows). Success = real 206 GET on the device for the age-restricted video AND the 5 normal videos. Budget: one honest attempt.
If this works, also report whether it gives the tracking URLs needed for opt-in watch-history reporting (do NOT build reporting here).

## Product behaviour (whichever spike ships)
- Setting "Allow age-restricted videos" stays default OFF; the account tier remains opt-in per account as already designed.
- When allowed and the video is age-restricted: clear message while resolving ("using your account"), the governor audit log shows the call,
  circuit breaker/kill switch still stop it. Failure -> friendly message + Open in browser, as today.
- Diagnostics/Health: show the Android yt-dlp tier (version, runtime, last canary, last update, rollback state) like Linux.
- Canary for the new tier added to ExtractorHealth; fault-injection tests cover tier failure, update failure, cookie-file cleanup on crash.

## Closing
1. Self-test on the phone includes: age-restricted play (throwaway account), anonymous yt-dlp tier play, one download through the new tier.
2. Full suite `./gradlew allTests test :androidApp:assembleDebug :androidApp:assembleRelease --continue`, run AFTER the last code change.
3. Leak audit green, no cookie file left behind after success, failure and process kill.
4. Update the parked doc (shipped or closed with evidence) and the "Core complete?" table.
5. Owner checklist: only what you could not do.
