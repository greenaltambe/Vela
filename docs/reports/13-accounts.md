# Task 13: accounts, authenticated tier, alternate-client tier, sync

## 1. Summary

Accounts are in: a LOCAL account always exists, Google accounts are added by cookies.txt import (Linux and Android) or a WebView sign-in (Android, compile-only). Credentials live in libsecret or an AES-GCM file on Linux and in a Keystore-encrypted store on Android, and never in the DB, logs, diagnostics or backups. Tier 3 (iOS/Android player profiles, no PO token) works live. Tier 2 works on Linux through `yt-dlp --cookies` and is **not available on Android**, because both Android options were measured to fail. Subscriptions are pulled from the account; writes (subscribe/like/playlist), history reporting, cookie rotation, Home "Recommended" and the Settings account card/top-bar switcher are **not done**.

## 2. Verification

| Check | Result | Verified by |
|---|---|---|
| `./gradlew allTests test` (all modules) | green: shared jvm 401, shared android-host 264, innertube-auth 25, extractor-ytdlp 87, extractor-newpipe 48, desktopApp 19, player-mpv 64, net-okhttp 27, androidApp 32 | running |
| `:androidApp:assembleDebug` | builds (Keystore store, WebLoginActivity, backup rules) | running (compile only; nothing Android-runtime executed) |
| SAPISIDHASH vectors | 3 vectors computed with Python `hashlib`, match | running |
| Redaction (cookie/header/JSON/URL/e-mail samples, idempotent), `TierAttempt` details redacted, test with leaked cookies in an error message | pass | running |
| Cookie jar: Netscape incl. `#HttpOnly_`, Set-Cookie rotation, expiry/removal | pass | running |
| Migration 5→6 (Account columns) with data kept, plus the older-version chain | pass | running |
| `AccountManager`: guaranteed LOCAL, add/dedupe by identity, switch persisted, sign out, remove deletes creds + all account data, sign out everywhere | 10 tests pass | running |
| Per-account data separation (subscriptions swap on switch) | pass, also through the Compose UI | running |
| Chain: anonymous→account→alt→yt-dlp order, `ALWAYS`/`NEVER`/`ONLY_ON_FAILURE`, no account → tier skipped with "No signed-in account", memory cleared on account change, no credentials in `TierAttempt` | 6 new tests pass | running |
| Linux secret stores: AES-GCM round trip, no plaintext on disk, 0600, tamper/lost key reads as empty, libsecret keeps secrets off argv, fallback choice | 6 pass | running |
| Real libsecret on this machine | store/lookup/clear round trip OK | running |
| Auth yt-dlp tier: private 0600 temp file, deleted on success and failure, rotated cookies written back, no account → unavailable | 5 pass | running |
| Parsers on scrubbed recorded fixtures (account menu, subscriptions); lenient to missing/renamed fields | pass | running |
| AuthSession: signing headers, Set-Cookie rotation persisted, 429 backoff, jittered pacing, 401 → logged out | pass | running |
| Login: valid import, expired cookies rejected with clear message, garbage rejected with no network call, `checkSession` (network error does not sign out) | pass | running |
| Sync: idempotent, never deletes, other accounts untouched, failure/empty/signed-out keep data | 4 pass | running |
| Alt client: parsing, ordering, playability→error mapping, PoToken seam, bundled profiles + override | 7 pass | running |
| Accounts UI (switch, masked e-mail, sign out banner, remove + sign-out-everywhere need confirmation); layout invariants now include the Accounts screen | pass | running (desktop Compose) |
| Live, real cookies: account info, 121 subscriptions without duplicates, broken cookies → `LoggedOut` | pass | running |
| Live, no cookies: tier 3 matrix (below) | pass | running |
| Live, real cookies: age-restricted video through tier 2 (yt-dlp) → 19 video + 8 audio streams | pass | running |
| Repo scan for any of the 18 cookie values in the working tree | none found (416 files) | running |
| WebView login, Keystore, backup exclusion on a device | not run | compile-only |

### Measurements

**Tier 3 matrix** (anonymous, 2026-10-27; one `Range: bytes=0-1023` GET of a stream per cell; HTTP 206 = playable):

| Profile | normal | music | short | live* | age-restricted |
|---|---|---|---|---|---|
| IOS | 206 (22v/5a, + HLS) | 206 | 206 | UNPLAYABLE | LOGIN_REQUIRED |
| ANDROID | 206 (24v/6a) | 206 | 206 | UNPLAYABLE | LOGIN_REQUIRED |
| ANDROID_VR | URLs returned, GET **403** | LOGIN_REQUIRED | LOGIN_REQUIRED | UNPLAYABLE | LOGIN_REQUIRED |
| TVHTML5 | UNPLAYABLE | LOGIN_REQUIRED | LOGIN_REQUIRED | UNPLAYABLE | LOGIN_REQUIRED |
| MWEB | UNPLAYABLE | UNPLAYABLE | UNPLAYABLE | UNPLAYABLE | LOGIN_REQUIRED |
| WEB_EMBEDDED | ERROR | ERROR | ERROR | ERROR | ERROR |

`innertube-profiles.json` therefore enables IOS then ANDROID and ships the other three disabled. \*The live video id used was not live ("recording not available"), so **live is untested**. Region-locked was not tested (no second region available).

**Account-authenticated calls:** ANDROID/IOS/ANDROID_VR with the web cookies → HTTP **400** (app clients reject web auth). WEB, TVHTML5, MWEB with cookies → UNPLAYABLE even for a normal video; WEB_EMBEDDED → ERROR. So an own Innertube `/player` call with cookies does **not** give streams today.

**NewPipe with the account's headers injected** (request-scoped via a thread-local coroutine element; implemented, measured, then removed): every call fails with `Could not get visitorData`, including a normal video. Anonymous NewPipe says "age-restricted video cannot be watched anonymously". Conclusion: no NewPipe account tier.

**yt-dlp `--cookies` (tier 2 on Linux):** age-restricted video without a JS runtime: storyboards only ("n challenge solving failed"). With `--js-runtimes node` (which `createYtDlpTier` already detects): 19 video + 8 audio streams. The same video anonymously through yt-dlp also succeeded in the live test, so the account tier is needed for NewPipe/Innertube, not for yt-dlp; yt-dlp's own anonymous clients cover it. The cookies were written back by yt-dlp once during the run (1 write-back).

**RotateCookies:** `POST accounts.google.com/RotateCookies` returned **401** in three variants (no Authorization, SAPISIDHASH for accounts.google.com, SAPISIDHASH for youtube.com). Not implemented. The original cookies still authenticated afterwards. Rotation we do get is the Set-Cookie on normal calls (persisted by `AuthSession`) and yt-dlp's write-back. Whether that keeps a session alive longer than the browser export is **unmeasured** (needs days).

**Account data:** `account/account_menu` → name, e-mail, avatar parse. `channelId` is `null` (the menu has no UC id), so the account id is derived from the e-mail hash. FEchannels returns no channel list; `FEsubscriptions` returns it (121 channels, no continuation tokens for this account). Parser found 121 where a raw count of `gridChannelRenderer` was 122 (one has no title; unexamined).

## 3. Files created/changed

**Shared (common)**: `auth/{Redaction,Sha1,Cookies,SecretStore,Accounts}.kt` (new); `Account.sq` + `5.sqm` (new columns, delete queries); `AppServices` (per-account repositories + one FeedService per account, `accounts`, `login`, `webLogin`, `sync`); `FallbackChain` (`signedInAccount`, memory reset, account label in details, redacted details), `TierMemory.clear`; `PlaybackSession` (`accountId` is now a provider); `ui/screens/AccountsScreen.kt` (new), `Route.Accounts`, `AppShell` (content keyed by active account), `SettingsHost` menu entry.
**Shared (jvm)**: `auth/LinuxSecretStores.kt` (libsecret via `secret-tool`, AES-GCM file fallback).
**`:innertube-auth` (new, pure JVM)**: `AuthTransport`, `AuthSession`, `InnertubeClient`, `InnertubeParsers`, `Json` (lenient navigation), `AltClient` (`ClientProfile`, `PoTokenProvider`, `AltClientExtractor`), `InnertubeAccountLogin`, `InnertubeAccountSync`; resources `innertube-web.json`, `innertube-profiles.json`; tests, scrubbed fixtures and `capture_fixtures.py`; `liveTest` for account and tier 3.
**`:extractor-ytdlp`**: `AuthenticatedYtDlpExtractor` (+ test, live test), `createYtDlpTier(onReady)`.
**Desktop**: `main.kt` (secret store, AccountManager, tiers, login/sync), `DesktopPlaybackRuntime` (account provider), UI harness + `AccountsUiTest`, layout invariants.
**Android**: `auth/AndroidKeystoreSecretStore`, `auth/WebLoginActivity`, `PlaybackRuntime`, `MainActivity`, manifest + `res/xml/{backup_rules,data_extraction_rules}.xml`.
**Tests**: `AuthCoreTest`, `AccountManagerTest`, `AccountAwareChainTest`, `LinuxSecretStoresTest`, `JvmDatabaseFactoryTest` (v5→6).
Not committed.

## 4. Decisions the spec didn't dictate

- **Tier 2 on Linux = yt-dlp `--cookies`**, not NewPipe injection (measured failure) and not an own `/player` call (measured UNPLAYABLE). The cookie file is a per-call private temp file, calls are serialised.
- **No tier 2 on Android.** The chain skips it ("No signed-in account" is the reason only when signed out; on Android the tier is simply not registered). Age-restricted videos therefore need either Linux, or remain unavailable on Android. This is the main gap.
- **Linux sign-in default: cookies.txt import.** KCEF/JCEF embedding and device-code OAuth were **not evaluated** (no time-boxed experiment was run); only (b) is delivered. Firefox-profile import not done.
- Account id = `google-` + first 16 hex of SHA-256(channelId ?: e-mail ?: name), so signing in again finds the same rows.
- Account label for logs/diagnostics is `google:<last 6 of id>`; e-mails are shown masked (`t***@gmail.com`).
- **Conflict rule for sync (subscriptions):** union. Remote channels missing locally are added; nothing is deleted locally or remotely; an empty/failed answer changes nothing. Deletion semantics wait for the write path.
- Android Keystore AES-GCM directly (no deprecated `security-crypto`); ciphertext in `vela_secrets` prefs excluded from cloud backup and device transfer. If the key is lost, the credential reads as absent and the account needs a new login.
- `AccountManager.remove` deletes subscriptions, playlists (+items), history, search history and feed cache of that account.
- Switching rebuilds every screen (content keyed by the active id); each account has its own `FeedService`.
- App-client profiles are plain data with `enabled` flags and an override-file path; every call reports the serving profile into Diagnostics as a NOTE.

## 5. Known issues / unverified assumptions

- **Not done:** write actions (subscribe/unsubscribe, like, playlists), videostats history reporting, Library showing account playlists, merged history, Home "Recommended" source, Settings > Account card and top-bar account switcher (the Accounts screen is reached from Settings' overflow menu), scheduled cookie rotation, KCEF/OAuth evaluation, Firefox import, NEEDS_RELOGIN banner outside the Accounts screen, periodic `checkSession`. `PoTokenProvider` exists with no implementation.
- Downloads and the download queue (`DownloadRepository`, `Downloads`) are still bound to the LOCAL account; DeArrow/SponsorBlock user data was not checked for per-account keying.
- `webLogin` is compile-only. The WebView adds `X-Requested-With` itself; Google may refuse the sign-in. If so, use Import cookies.txt (available on Android too).
- Android age-restricted playback needing an account is unsolved (see above). Live streams were not measured.
- Tier 3 URLs carry no PO token; they work today but this can change at any time. ANDROID_VR returned URLs that answered 403.
- yt-dlp tier 2 needs a JS runtime (node found here); without one it returns storyboards only and the tier reports Unplayable.
- A yt-dlp cookie write-back is compared by text; a harmless reorder could cause a redundant re-save.
- The measurements use the owner's throwaway account at one point in time; the account has ~121 subscriptions, which I did not alter (no write was sent).

## 6. Manual test checklist

**Linux**
1. `./gradlew :desktopApp:run`. Open Settings → ⋮ → Accounts. Confirm "Cookies are kept in libsecret (secret-tool)".
2. In a private Firefox/Chrome window sign in to YouTube, export cookies with a "cookies.txt" extension, close the window. Press **Import cookies.txt**, pick the file. Expect "Signed in as …" and the account active with a masked e-mail.
3. Press **Sync subscriptions now**; open Subscriptions: your channels are there. Press it again: "Added 0 new".
4. Switch to "This device": the subscriptions list changes; switch back.
5. Open an age-restricted video (e.g. `HtVdAasjOgU`) while signed in; Diagnostics shows which tier served it and `account google:xxxxxx`, never an e-mail.
6. Import a wrong/expired file: a clear message, nothing added.
7. Sign out (⋮ on the account): banner appears, `secret-tool search service vela` no longer lists it. Remove: confirmation, then the account and its data are gone.
8. Sign out everywhere: confirmation, every account needs sign-in.

**Android phone**
1. Install the debug APK. Settings → ⋮ → Accounts → **Sign in with Google**. Complete login (including 2FA/passkey if asked). Expect return to the app with the account active. If Google refuses the page, report the exact message and use Import cookies.txt instead.
2. Repeat checklist items 3, 4, 7, 8 above.
3. Confirm the WebView does not stay signed in (Settings → Accounts → Add again asks for login).
4. Check `adb shell run-as com.greenaltambe.vela cat shared_prefs/vela_secrets.xml`: values are base64 ciphertext, no cookie text. `adb backup`/cloud backup must not include that file.
5. Play a normal video signed in and signed out; Diagnostics shows the alt-client profile (IOS/ANDROID) only if NewPipe failed.

**Re-running the live tests:** `./gradlew :innertube-auth:liveTest :extractor-ytdlp:liveTest` (cookies from `VELA_TEST_COOKIES`, else `~/.vela-test/cookies.txt`; they skip cleanly without it). To refresh fixtures: `VELA_TEST_COOKIES=… python3 innertube-auth/src/test/resources/fixtures/capture_fixtures.py`, which refuses to write if anything secret-looking survives.
