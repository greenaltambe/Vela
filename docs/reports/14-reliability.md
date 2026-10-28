# Task 14: reliability hardening and account safety

## 1. Summary

Stages A (account safety governor), B (fault-injection harness), H (account-last tier order) and the core of E/F (health canaries, NewPipe staleness warning, anonymous pacing) are built and tested. Every request that carries credentials now goes through one `AccountGovernor` per account (serialised, spaced, budgeted, circuit breaker with 1 h / 6 h / 24 h cooldowns, kill switch, audit log). The account is spent last, and only for playback unless the user allowed more. **Not done:** C (PO tokens: no provider, no measurements with tokens), D (no new Android tier 2 measurement; friendly failure + "Open in browser" is done), remote signed config, the yt-dlp updater, WorkManager scheduling, captcha WebView, network-handoff and slow-stream failover, per-account persisted visitorData. Details in section 5.

## 2. Verification

| Check | Result | Verified by |
|---|---|---|
| `./gradlew allTests test` (all modules) + `:androidApp:assembleDebug` | green: shared jvm 438, shared android-host 296, innertube-auth 30, extractor-ytdlp 90, extractor-newpipe 48, desktopApp 23, player-mpv 64, net-okhttp 27, androidApp 32 | running |
| Governor: spacing + jitter, per-kind budgets, kill switch, breaker open/half-open/closed, escalation 60→360→1440 min, sustained-success reset (10), `tryNow`, repeated LOGIN_REQUIRED rule, serialisation, persistence across restart (DB), audit has no credentials | 16 + 3 tests | running (fake clock) |
| `AuthSession` is the only place a Cookie/SAPISIDHASH is built and sends only via the governor; 429 opens the breaker and the next call is refused unsent; `/sorry` redirect and "unusual traffic" pages → breaker; JSON mentioning captcha is not a block | pass | running |
| yt-dlp account tier through the governor: 429-looking stderr opens the breaker (second call never starts a process), hourly budget enforced, cookie values never on argv, anonymous yt-dlp never gets `--cookies` | pass | running |
| Credential-leak audit (offline, marker cookies through login + checkSession + sync): credentials only on allow-listed hosts, never in URLs/bodies/audit/messages/`toString`; alt client sends no credential header | pass | running |
| Strictly anonymous operations (search, trending, comments, replies, related, RSS feed) never reach the account tier; channels/details follow their settings (default off); downloads use `NEVER` unless allowed | pass | running |
| Chaos table (9 rows) + breaker cooldown with fake clock + account switch mid-failure + sign-in jump + ALWAYS/NEVER | pass | running |
| Tier order is data (`account.tierOrder`), validated: account never before an anonymous tier unless ALWAYS | pass | running |
| UI: onboarding gate (both acknowledgements, reporting off by default), banner on Home and Library for an open breaker, "Try now" asks first, signed-out banner, activity screen + purge, layout invariants incl. Accounts, Account activity | pass | running (desktop Compose) |
| Migration 6→7 (AccountAudit) with data kept | pass | running |
| ExtractorHealth: per-tier summary, account tier never probed, non-206 GET and empty stream set (SABR) reported, staleness, persistence codec; NewPipe version matches the catalog; PacedExtractor limits concurrency and spacing | pass | running |
| Live: alt client still returns 206 without a token (canary), 3 normal/music/short videos | pass | running (real network) |
| Live: tier order latency and tier mix (below) | measured | running (real network, real yt-dlp, real account) |
| Live: hosts that real yt-dlp sends cookies to (below) | measured | running |
| Android WebView sign-in, Keystore, breaker/banner on a device | not run | compile-only |

### Measurements

**H: failure-path latency** (age-restricted video `HtVdAasjOgU`, 5 trials each, fresh chain per trial, account minimum spacing 0 so the order is the only difference):

| Order | Served by | min / median / max |
|---|---|---|
| old: NewPipe → **account** → alt → yt-dlp | account (5/5) | 4881 / 5104 / 5547 ms |
| new: NewPipe → alt → yt-dlp → account | anonymous yt-dlp (5/5) | 4999 / 5531 / 5914 ms |

The new order costs about +430 ms (+8 %) on this video and sends **no** request with the account. Tier mix on 5 videos with the new order: normal, music, short, a second normal → NewPipe (1.2 to 2.0 s each); age-restricted → anonymous yt-dlp (4.5 s). The account tier was reached for **0 of 5**. "A normal week of use" was not measured (it needs a week); on these videos the expectation "rarely" holds. Yt-dlp playing this age-restricted video anonymously is what makes the account unnecessary here; the account tier remains the answer for a real bot check.

**Hosts real yt-dlp contacts with cookies** (`yt-dlp --cookies … --print-traffic`, age-restricted video, node runtime): 5 requests to `www.youtube.com` carried a Cookie header; `manifest.googlevideo.com` (1 request) carried none. No other host was contacted. (Two further "requests" in the traffic dump were JSON bodies, not hosts.) Cookie values were never printed.

**Block signals the governor recognises** (which were observed in this project):

| Signal | Source | Observed? |
|---|---|---|
| Cookies rejected / signed out (account menu without an active account) | `LoggedOut` | **observed** (deliberately broken cookies, task 13) |
| HTTP 429 | status | assumed (never triggered) |
| `/sorry/` redirect, captcha | final URL, non-JSON body | assumed |
| "unusual traffic" | non-JSON body, stderr | assumed |
| account verification / "confirm it's you" | stderr/body regex | assumed |
| repeated LOGIN_REQUIRED after a valid session (2 in a row) | governor counter | assumed |

## 3. Files created/changed

**Shared**: `auth/Governor.kt` (governor, registry, patterns, audit/breaker interfaces), `auth/GovernorStores.kt` (DB breaker store, DB audit log, limits from settings); `AccountAudit.sq` + `6.sqm`; 9 account settings (`SettingSection.ACCOUNT`; `accountFallbackMode` moved there and relabelled, one source of truth), `account.tierOrder`; `extractor/chain/{TierOrder,PacedExtractor}.kt`; `FallbackChain` (tier order, account paused reason, op-based account policy, sign-in jump); `health/{ExtractorHealth,DbHealthStore}.kt`; UI `AccountSafetyUi.kt` (banner, onboarding, activity, session check), `HealthScreen.kt`, routes, `AccountsScreen` (onboarding, safety section, test 429); friendlier sign-in-required failure in `PlayerView`; `AccountManager.remove` also deletes the account's audit.
**`:innertube-auth`**: `AuthSession` (governor-only, block detection), `AuthFailure` implements `BreakerSignal`, login/sync via governors, `OkHttpStreamProbe`; tests (`CredentialLeakAuditTest`, session/sync updates) and live canary.
**`:extractor-ytdlp`**: `AuthenticatedYtDlpExtractor` governed.
**Apps**: desktop `main.kt` and Android `PlaybackRuntime`/`MainActivity` (governors, tier order, download chain, pacing, health).
**Tests**: `GovernorTest`, `GovernorStoresTest`, `chaos/{Chaos,ChaosScenariosTest}`, `ExtractorHealthTest`, `PacedExtractorTest`, `NewPipeVersionSyncTest`, `AccountsUiTest` (+4), `TierOrderLatencyLiveTest`, `LiveCanaryTest`.
**Scripts**: `scripts/bump-newpipe.sh` (syntax-checked only; **not run**, it would change the catalog).
Not committed.

## 4. Decisions the spec didn't dictate

- **Structural enforcement.** The only code that builds a Cookie or SAPISIDHASH is `AuthSession` (its header builder is `internal`, and `send` goes through the governor); the only other credentialed path is the yt-dlp tier, which also takes a governor. NewPipe's downloader has no hook for credentials (I removed the experiment from task 13), and the alt client has no credential parameter at all.
- **Budgets/defaults:** 20 playback resolves/h, 6 browse+session-check calls/min, 10 writes/h, 1.5 s minimum spacing plus up to a third jitter, kill switch off. Cooldowns 1 h / 6 h / 24 h; 10 consecutive successes forgive the escalation; a failed half-open probe escalates.
- Breaker state is stored in the Setting table (state only) so a restart keeps a cooldown. The audit log is a table capped at 1000 rows, per account, purgeable, removed with the account.
- `AccountOp` decides what the account may serve: playback always (when the mode allows), details/channels by setting (default off), everything else never. Health canaries never use the account.
- Downloads get their own `FallbackChain` over the same tiers with `NEVER` unless `account.downloadsMayUseAccount`.
- "Jump straight to the account": an anonymous tier that answered SIGN_IN_REQUIRED recently is skipped when an earlier tier says it again, provided the account tier is usable. In practice the existing tier memory already routes around after the first success, so the rule only matters when the account has not yet succeeded.
- Account-last is the default order for `ONLY_ON_FAILURE`; `ALWAYS` keeps the account first, `NEVER` removes it. The order setting cannot move the account earlier.
- `reportActivity` exists as a setting (default off) but **nothing reports yet** (no write path), so it has no effect today.
- Pacing for anonymous tiers: 4 concurrent, 120 ms between starts, applied to NewPipe and anonymous yt-dlp. Not measured against a real feed refresh beyond the unit test.

**Threat model** (what can get an account flagged → control → residual risk):

| Risk | Control | Residual |
|---|---|---|
| Request volume / machine-like timing | per-account budgets, spacing with jitter, serialisation, anonymous-first order (0 of 5 videos reached the account) | the budgets are guesses; no data on Google's thresholds |
| Repeated pushback ignored (429, captcha, verification) | breaker opens on those signals, escalating cooldowns, no calls while open, explicit "Try now" | patterns are assumed except sign-out; a new wording would not trip it |
| Credentials leaking to third parties | single choke point, allow-listed hosts, leak-audit tests, measured yt-dlp hosts, redaction | a future code path could still bypass it; tests only cover the flows listed |
| Account used for things that do not need it | `AccountOp` policy, downloads off by default, canaries anonymous | none identified |
| Session invalidation (rotated/expired cookies) | Set-Cookie persistence, write-back from yt-dlp, daily `checkSession` (network errors never sign out), banner | cookie lifetime unmeasured (needs days); RotateCookies returned 401 |
| Browser-like identity changes | bundled constant client version and user agent (no per-call randomisation) | visitorData/device id not persisted per account; client version goes stale |
| Third-party-client terms | onboarding gate with acknowledgements, throwaway recommended | the risk cannot be removed, only stated |

## 5. Known issues / unverified assumptions

**Not done (each would be its own stage):**
- **C:** no `PoTokenProvider` implementation (Android BotGuard WebView; Linux bgutil/node), and therefore **no token matrices**. The only PO-token-related fact measured: IOS/ANDROID still return 206 without a token today (canary above and task 13 matrix). No profile was enabled behind a provider.
- **D:** no new Android tier 2 measurement; task 13's results stand (own `/player` with cookies: UNPLAYABLE, NewPipe with headers: broken). Done: sign-in-required failures show an explanation and **Open in browser** (system URL handler) and no Retry button. This UI path is **not covered by a test** and not run on Android.
- **E:** no signed remote config (Ed25519) and no `sign-remote-config` tool; no yt-dlp updater or `ytdlp.autoUpdate`; periodic checks run only while the app is open (start + every 6 h), not via WorkManager or a Linux timer; no history graph beyond the last results.
- **F:** captcha WebView/exemption cookie, network-handoff and slow-stream failover not implemented. Existing `PlaybackSession` tests cover re-resolving on a mid-playback 403/expiry (task 06 era); I did not add handoff cases. The chain distinguishes captcha only inside the account path (`BlockSignal.CAPTCHA`); the anonymous `Blocked(captcha)` type was not added.
- **A:** per-account persisted visitorData/device id not implemented (identity is stable only because it is a bundled constant); `reportActivity`, `useForChannels`, `useForDetails` have no UI beyond the generated settings; no write path to govern yet; the yt-dlp `-v` host list was measured once, not asserted in a test.
- `scripts/bump-newpipe.sh` was not executed. The NewPipe release date (2026-08-15) comes from the GitHub API at the time of writing; v0.26.5 was the newest release then, and it is already 74 days old, so Diagnostics > Health shows the warning today.
- The latency numbers come from one machine/network and 5 trials; the account made about 10 real resolves during them.
- The Linux account tier needs a JS runtime (node here); without it yt-dlp returns storyboards only and the tier reports Unplayable.

## 6. Manual test checklist

**Linux (`./gradlew :desktopApp:run`)**
1. Settings → ⋮ → Accounts → Import cookies.txt: the onboarding dialog appears; Continue stays disabled until both boxes are ticked; the "tell YouTube what I watch" switch is off. Continue, pick the file, expect "Signed in as …".
2. Settings → Account section (generated): defaults are "Only on failure", reporting off, channels/details off, downloads-use-account off, budgets 20 / 6 / 10, kill switch off.
3. Accounts → Safety → **Test: simulate a rate limit**. A banner appears on Home and on Library ("The account is paused until HH:MM UTC because HTTP 429…"). **Try now** asks for confirmation. Settings → ⋮ → Account activity shows the audit lines.
4. Open the age-restricted video `HtVdAasjOgU`: it plays; Diagnostics shows which tier served it (expected: yt-dlp, not the account). Activity should list no new entry.
5. Settings → ⋮ → Health → **Run checks now**: NewPipe, alt client and yt-dlp rows go green; the NewPipe age warning is shown (it is over 60 days old).
6. Turn on the kill switch: Accounts → Sync fails with "Account traffic is switched off" and nothing appears in the audit log as sent. Turn it off again.
7. Sign out and Remove: confirmations still work; the audit for that account is gone.

**Android phone**
1. Install the debug APK. Repeat 1, 2, 3, 5 above (Accounts is under Settings → ⋮).
2. Open an age-restricted video signed out or without an account tier: the player explains why, offers **Open in browser**, and does not show Retry. Tapping the button opens the browser at the video.
3. Airplane mode during playback and a Wi-Fi ↔ mobile switch: playback should recover or show a clear error (existing session recovery; no new handoff logic was added, so report what you see).
4. `adb shell run-as com.greenaltambe.vela cat shared_prefs/vela_secrets.xml` still shows ciphertext only.

**Re-run the measurements:** `./gradlew :desktopApp:liveTest --tests '*TierOrderLatencyLiveTest*'` (uses the account about 10 times), `./gradlew :innertube-auth:liveTest --tests '*LiveCanaryTest*'` (no account).
