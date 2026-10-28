# Task 16: reliability part 3 (partial: Stage 4, parts of Stage 5, the task-16 changes; Stages 2 and 3 not done)

## PO tokens today: what already works (feasibility read, 10 lines)

1. **NewPipeExtractor v0.26.5** has a host-implemented interface `PoTokenProvider` (read from the bundled sources): `getWebClientPoToken`, `getWebEmbedClientPoToken`, `getAndroidClientPoToken`, `getIosClientPoToken`, each returning `PoTokenResult(visitorData, playerRequestPoToken, streamingDataPoToken?)`; null means "none for this client".
2. It is registered with the static `YoutubeStreamExtractor.setPoTokenProvider(...)`. In 0.26.5 the extractor calls it only for the **Android and iOS** clients (the web ones are declared but no call site uses them: grep). It ships no implementation, so the host app must mint tokens.
3. The Android and iOS tokens are described in its docs as DroidGuard/iosGuard products that cannot be minted outside the real app; a WebView-BotGuard token only covers WEB-family clients (so it would not help the clients NewPipe asks for).
4. **yt-dlp 2026.10.26** has a plugin framework (`yt_dlp/extractor/youtube/pot/`: `PoTokenProvider`, `PoTokenRequest`, `PoTokenResponse`, a memory cache). `-v` prints "PO Token Providers: none" here: no built-in provider. Tokens can also be passed by hand with `--extractor-args youtube:po_token=...`. Community providers (e.g. a bgutil plugin running node) are third-party and I did not install or test one.
5. yt-dlp's own age-restricted route needs no token here: it won with `web_embedded` plus a JS runtime (task 15), which this task forbids replicating.
6. Consequence for the design: a Vela `PoTokenProvider` should mirror NewPipe's shape (per client, `visitorData` + player token + streaming token, null = unavailable) and, on Linux, be offered to yt-dlp as a plugin rather than re-implemented.
7. Not measured: whether IOS/ANDROID/web streams need `pot=` today (IOS/ANDROID returned 206 without it in the task-15 matrix, 2026-10-28).

## 1. Summary

Done: freshness reporting (a warning only when a newer release exists), per-tier client/extractor version in Diagnostics, the NewPipe empty-comment investigation, Stage 4 (signed remote config with script and docs, managed yt-dlp updater with checksum and rollback, health alert banner, Android WorkManager canary), a distinct anonymous captcha error, and the debug simulate-failure menu. **Not done:** Stage 2 (PO tokens), Stage 3 (Android account playback), the Android captcha WebView flow, network-handoff and slow-stream failover, per-account persisted identity, the host-allow-list assertion test, and the Report's token matrices. Age-restricted playback on Android is therefore unchanged: no route, friendly failure kept. Linux is still served by yt-dlp.

## 2. Verification

| Check | Result | Verified by |
|---|---|---|
| `./gradlew allTests test :androidApp:assembleDebug`: androidApp 32, desktopApp 26, shared jvm 462, shared android-host 320, newpipe 48, ytdlp 107, player-mpv 64, net-okhttp 27, innertube-auth 48 | all green | running |
| `ReleaseCheck` (7): up-to-date is not a warning, warns only for a newer release, one request per day and across restarts, offline keeps the last answer, never-checked+offline is not a warning, version compare, stale yt-dlp copy named with command | pass | running (fake transport/clock) |
| Remote config (19 incl. file signed by `scripts/sign-remote-config` via openssl): tamper, wrong key, no key, rollback/replay, expired, future, oversized, garbage, http refused, empty URL, offline, restart reload, kill switch, expiry fallback | pass | running |
| yt-dlp updater (9): checksum ok/mismatch/missing, version mismatch, failed canary rolls back (with and without previous copy), download failure, previous kept, asset choice, mode logic | pass | running (real shell-script "binaries") |
| Live updater: real `yt_dlp_linux` 2026.10.26 downloaded (41 166 968 bytes), SHA2-256SUMS verified, `--version` matched, real canary served a video | passed; 26.1 s total, canary 4.3 s | running (real network, temp dir) |
| Health alert: needs 2 full-failure runs in a row, names tiers and last good time, survives restart, one working tier = no alert, recovery clears | pass | running |
| Fault injector (4): 429/captcha/offline errors, one-shot expired URLs, null injector untouched, account tier never wrapped; distinct `Captcha` error from NewPipe `ReCaptchaException` and yt-dlp text | pass | running |
| NewPipe live tests on v0.26.5 | 18/18 after the comment-assertion relaxation (earlier) | running |
| WorkManager canary, Ed25519 on Android, banner on a device, Diagnostics menu on a phone | not run | compile-only |

## 3. Files created/changed

- **Freshness**: `shared/.../health/ReleaseCheck.kt` (new: `ReleaseCheck`, `ReleaseWatch`, `ToolCopy`, `ManagedUpdater`); `HealthScreen.kt`; `AppServices.kt`; desktop `main.kt`, Android `MainActivity.kt`; `NewPipeVersion.warning` removed.
- **Diagnostics versions**: `ExtractorTier.version`; `FallbackChain` success line now "client <profile>; <extractor version>" (e.g. `client ios; innertube-profiles.json`, `NewPipeExtractor v0.26.5`, `yt-dlp 2026.10.26 (/path)`).
- **Remote config**: `innertube-auth/.../config/{RemoteConfig,RemoteConfigWiring}.kt`, `remote-config-pubkey.txt` (empty on purpose), `scripts/sign-remote-config`, `docs/remote-config.md`, setting `remoteConfig.url`; `AltClientExtractor` profiles and health canaries read it.
- **Updater**: `extractor-ytdlp/.../YtDlpUpdater.kt`, setting `ytdlp.autoUpdate` (OFF / ASK default / AUTO), managed copy in `<data dir>/bin`, wiring in `DesktopWiring.kt`, Health button.
- **Health**: `ExtractorHealth` alert/streak, `HealthBanner` in the shell, `androidApp/.../HealthWorker.kt` (12 h, network + battery-not-low), `androidx-work` in the catalog.
- **Failures**: `ExtractorError.Captcha` / `FailureReason.CAPTCHA`; `extractor/chain/FaultInjector.kt`, Diagnostics buttons.
- **Tests/live**: `ReleaseCheckTest`, `RemoteConfigTest` (+fixtures), `YtDlpUpdaterTest`, `LiveUpdaterTest`, `FaultInjectorTest`, `ServedDetailTest`, health tests, captcha tests.
- Not committed.

## 4. Decisions the spec didn't dictate

- Staleness: the daily GitHub releases check (via the shared API client, cached in settings) decides everything; age alone never warns. Health says "up to date (checked <date>)" or "<x> is out of date: <new> is available". yt-dlp copies are listed with `STALE` and the command (`sudo dnf upgrade yt-dlp`, `pipx upgrade yt-dlp`, `yt-dlp -U`); the app never changes system copies.
- Remote config envelope: base64 payload + Ed25519 signature; strictly increasing `version`; expiry; the key is a bundled resource that is **blank until you generate one**. Until then the URL setting does nothing. On Android below 13 the signature check is unavailable and the bundled data stays (assumption: `Signature("Ed25519")` exists from API 33).
- yt-dlp updater uses the standalone `yt-dlp_linux` (or `_aarch64`) asset, which needs no Python; checksum is the published SHA2-256SUMS over HTTPS (its GPG-style `.sig` is not verified). The managed copy takes part in "newest version wins". AUTO installs during the periodic check; a failed canary restores the previous copy.
- Health alert: a run is a full failure when no tier resolved any stream; alert after 2 in a row; dismissal lasts until the streak grows. No OS notification was built (banner only).
- Simulated failures exist only in debuggable Android builds and when the desktop app starts with `VELA_DEBUG=1`; they wrap every tier except the account tier.

## 5. Known issues / unverified assumptions

- **Stage 2, Stage 3:** not started, so no token provider, no token matrices, no Android account playback and no new test of tier 2 with a token. The "Open in browser" UI test from task 15 stands.
- **Stage 5 not done:** captcha WebView flow and exemption cookie; network handoff and slow-stream failover (no chaos scenarios added); per-account persisted visitorData/device id; live assertion of hosts yt-dlp contacts. The captcha error is mapped but nothing opens the challenge yet (the NewPipe URL is carried in `Captcha.url`, unused).
- **Item 4 (empty comment):** NewPipe's own source returns an empty description when a comment's `contentText` is completely empty (`Description.EMPTY_DESCRIPTION`, referencing upstream issue 380). So it is an upstream empty comment, not a Vela mapping gap; no mapper change. I did not capture the raw comment JSON to say whether it was a sticker or emoji-only comment (unverified).
- **Item 3 limit:** yt-dlp's `--dump-single-json` does not say which player client it used, so the yt-dlp lines show the version/path and "client picked by yt-dlp itself, see yt-dlp -v".
- Windowed/background: WorkManager is compile-only; the banner appears on next app open.
- The updater check video is `dQw4w9WgXcQ`; if it ever goes away every update would roll back.
- Ed25519 remote-config key and hosting are yours to set up; nothing has been signed for real.
- Tests only exercise the remote config, updater and banners against fakes except the one live updater run.

## 6. Manual test checklist for the user

1. Linux: `./gradlew :desktopApp:run`, Settings > Diagnostics/Health: NewPipe line reads "up to date (checked <today>)" (no age warning); the yt-dlp section lists both copies; the Fedora `/usr/bin` copy shows STALE with `sudo dnf upgrade yt-dlp`.
2. Set "Update the app's own yt-dlp copy" to Ask me: if a newer yt-dlp than the one in use exists, Health shows "Install yt-dlp … in the app's folder"; press it and check `~/.local/share/vela/bin/yt-dlp` appears and Diagnostics shows the updater line. Set it to Off: no button.
3. Remote config: `scripts/sign-remote-config --gen-key ~/k.pem`, put the printed line in `innertube-auth/src/main/resources/remote-config-pubkey.txt`, rebuild; host a signed file per `docs/remote-config.md`, set the URL; restart: Diagnostics "remote config" line says accepted. Re-serve the same file (same version): refused (rollback). Edit one character of the hosted file: refused (signature). Disable `IOS` in it: the alt-client Diagnostics lines stop using IOS.
4. Simulated failures: `VELA_DEBUG=1 ./gradlew :desktopApp:run`, Settings > Diagnostics, press "HTTP 429 from every source", open a video: the player explains the failure; try Captcha and Offline; then "Expired stream URLs (next resolve only)" and a normal video: it should re-resolve and play.
5. Health banner: with simulated 429 on, press Health > Run checks now twice (on Android, debug build): the "Playback may be broken" banner appears, Dismiss hides it; set "No simulated failure" and run checks once: it disappears.
6. Phone (debug APK): repeat 4 and 5; Diagnostics shows the same simulate buttons only on the debug build. Check WorkManager scheduled the job (`adb shell dumpsys jobscheduler | grep vela` or App Inspection).
7. Airplane mode and a Wi-Fi to mobile switch during playback: no new handoff logic was added; report what you see.
8. Decide whether Stage 2 (PO tokens) and Stage 3 should continue; both remain open and the age-restricted video on Android still has no route.
