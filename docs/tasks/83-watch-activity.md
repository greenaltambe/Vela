# Task 83: watch activity with the YouTube account (report, read back, hide watched)

Read CLAUDE.md and docs/SPEC.md (section 11 status notes), docs/reports/13-accounts.md, 14-reliability.md, 19-account-features.md and docs/tasks/parked-android-age-restricted.md (Grayjay notes). Do not commit.
Report `docs/reports/83-watch-activity.md` (fixed sections from CLAUDE.md; the Verification table must separate VERIFIED-BY-RUNNING from compile-only and from NOT VERIFIED). Terminal reply 5 lines max.
Prefer measuring over assuming: every protocol detail marked (UNVERIFIED) below must be confirmed against a real response/ping before it is relied on, and the measured result goes in the report.

## Why

Home "Recommended" (`FEwhat_to_watch`, `InnertubeAccountReads.recommended()`) is YouTube's recommender, and YouTube never learns what the owner watches in Vela, because reporting to the account is NOT implemented (SPEC section 11, decision 2026-10-28; `Settings.accountReportActivity` was removed from the UI; onboarding says "Vela never adds to your YouTube history"). Confirmed by the owner: Vela's Home is nearly identical to the official YouTube Home, i.e. the same already-watched videos keep coming back.
Second cause: `HideWatched` only knows the LOCAL `WatchHistory` table (`WatchHistoryRepository.watchedVideoIds()`, per account), so videos watched in the official app/site are never hidden, and `feed.hideWatched` is off by default.
Third (to design for): completion. YouTube has no "completed" event. It infers percent watched from periodic watchtime pings; a client that only pings on a timer (Vela's local history writer ticks every 15 s) under-reports unless it also sends a final ping at the real end, on pause and on stop.

Goal: (a) Vela can report playback to the signed-in account like Grayjay does, opt-in and governed; (b) watched state from the account (any device) is used to hide/mark videos in Vela; (c) account history can be pulled into the app automatically, by setting, and merged with the local history.

## Order of work

Phase 0 (spike) and Phases 1-3 are independent; do Phases 1-3 even if Phase 0 fails. Phase 4 is built only if Phase 0 succeeds.

## Phase 0: spike, can Vela get usable tracking URLs? (read-only, throwaway account)

Use the throwaway account (`VELA_TEST_COOKIES`, as `extractor-ytdlp` `LiveAccountTierTest` and the task 19 live tests do). Everything goes through the AccountGovernor (new `CallKind` is Phase 4; for the spike use PLAYBACK and keep to a handful of videos). Live code goes in a `liveTest` source set, never in `check`.
Question: where does the player response's `playbackTracking.videostatsPlaybackUrl.baseUrl` / `videostatsWatchtimeUrl.baseUrl` come from, such that pinging it WITH THE ACCOUNT'S AUTH makes the video appear in the account's history?
Try in this order, stop at the first that works, record all results (HTTP status, whether `playbackTracking` is present, whether the ping is accepted):
- A. Reuse the response Vela already gets: `AltClient.callPlayer` (anonymous ANDROID_VR etc.) and NewPipe/yt-dlp output. Check whether `playbackTracking` is in the response at all, then ping the URL with the account's cookies + SAPISIDHASH (the auth headers `AuthSession` already builds). (UNVERIFIED that an anonymously fetched URL registers against an account when pinged with account auth, and that a client/cookie mismatch is not rejected.)
- B. A signed-in WEB/MWEB/TVHTML5 `/player` call with cookies, `visitorData`, `signatureTimestamp` (sts), `contentCheckOk`, `racyCheckOk` (Grayjay-shaped request, see the addendum in parked-android-age-restricted.md). Task 13 saw UNPLAYABLE here without these; the question is only whether `playbackTracking` is returned even when `streamingData` is missing/UNPLAYABLE (we do not need playable streams for this).
- C. B plus a BotGuard PO token. Do NOT build this in this task; if only C works, write it up as the blocker with an estimate and stop Phase 4.
Acceptance test for a candidate: ping the playback URL once for a video the throwaway account has never watched, wait, read `history()` (`FEhistory`) and check the video appears. Remove it afterwards if the account's remove-from-history action is available; otherwise note in the report that the throwaway history was modified. For comparison, capture (by hand, owner-assisted if needed) what the real web client sends for the same video and diff parameter names against what we send.
Stop rule: if A, B both fail, write what was tried (status codes, response keys, names only, no tokens) in the report, leave Phase 4 unbuilt, and keep the SPEC note as it is.

## Phase 1: read the watched percentage from feed items

`InnertubeParsers.videos()` ignores watch progress. Add `watchedPercent: Int?` (0..100, null = not reported) to `VideoSummary` (`extractor/Models.kt`, default null, so nothing else breaks) and fill it for both shapes:
- classic `videoRenderer`: `thumbnailOverlays[].thumbnailOverlayResumePlaybackRenderer.percentDurationWatched`.
- `lockupViewModel` (the shape Home mostly uses now): progress bar overlay on the thumbnail (look for `thumbnailOverlayProgressBarViewModel` / `startPercent` under `contentImage...overlays`). (UNVERIFIED: exact path; find it in a real `FEwhat_to_watch` / `FEhistory` response and put a scrubbed trimmed fixture in the test resources, same rules as the task 47/48 fixtures: no visitorData, cookies, emails.)
Note from report 19: the throwaway account's Home was empty, so a real signed-in feed was never parsed. Use an account with some history for the fixture (owner's own, scrubbed) and say in the report if you could not.
Tests: fixtures give the expected percent for watched/partly watched/unwatched items, and null when the renderer has no progress info.

## Phase 2: use account watched state when hiding

- New setting `feed.watchedThresholdPercent` (float 1..100 step 1, GLOBAL, default 90, section FEED, copy in `SettingCopy`, shown next to "Hide watched videos"; add to the settings pages/search keywords like the other feed settings). Meaning: a video counts as watched when its best known progress is at or above the threshold. 1 reproduces today's "played at all".
- `WatchedLookup` gets progress awareness: a video is watched if (local history position/duration, or watchedMs/duration, >= threshold) OR (the item's own `watchedPercent` >= threshold) OR (it is in the synced account history, Phase 3, with percent >= threshold, or percent unknown). Keep `WatchedLookup.None` and the quick first-frame path (`filterFeedQuick`) working. `HideWatched` currently only receives ids; extend the step so it can see the item (it already has `VideoSummary`).
- Besides hiding: show the progress bar on the thumbnail of partly watched items from any source (the card already supports a bar for local history, `Lists.kt` `services.history.progress()`; feed it from `watchedPercent` too).
- Do not change `feed.hideWatched` default or scope (CHANNEL). Test first: a Home list where item X has `watchedPercent=100` and no local row is hidden with the setting on, shown with it off; threshold 95 keeps an item at 90.

## Phase 3: pull account history into the app, by setting

Today only a manual menu item (`history.loadAccount` in the History screen, `reads.history()` = `FEhistory`) shows account entries below the local ones, unsaved.
- Persist them: new table (SQLDelight, follow the repo's migration pattern and the per-account rule in CacheMaintenance/AccountManager.remove: deleted with the account, never touched by "clear cache") `AccountWatched(accountId, videoId, title, channelId?, percent?, seenAt)`, upsert by (accountId, videoId). Include in the per-account leak tests. Include it in Backup only if the owner's backup rules allow; default no (it is re-fetchable).
- New setting `history.accountSync` (choice, GLOBAL, section LIBRARY): `OFF`, `MANUAL` (default, today's behaviour, button also writes to the table), `ON_START`, `EVERY_6H`. Runs only for a signed-in account, through the governor as BROWSE with `patience` (it is a read-only listing, like the playlist sync), on a worker for Android (like `FeedRefreshWorker`) and a timer on desktop. Pages: stop after N entries or when it reaches an entry already stored (incremental); N configurable constant, default 200.
- The merged History view reads local + `AccountWatched`, de-duplicated by video and time window, source label "From your YouTube account" (reuse `history.accountHeader` strings and `HistoryMergeUiTest` expectations; update that test instead of weakening it).
- A failed sync keeps the stored data and shows "Could not read your YouTube history right now." in the History screen only (no popups).
- Everything here is a READ; it must work with `history.localOnly` on (that setting is about not SENDING; say so in its copy).
Tests: incremental stop, de-dupe, account isolation (a second account does not see the first's rows), failure keeps old data, setting OFF makes no request.

## Phase 4: report playback to the account (only if Phase 0 found a working source)

Design:
- Interface in `:shared` (commonMain), implementation in `:innertube-auth`: `ActivityReporter { suspend fun started(videoId, durationMs, ...); suspend fun progress(positionMs, state, ...); suspend fun ended(positionMs) }`, injected into `PlaybackSession`. `PlaybackSession` already tracks watched time and writes history on pause/end/error/stop and every `historyInterval`; call the reporter from the same places so the final position is always sent.
- Playback ping (once per session, when playback has really started, i.e. `settled` and playing): GET `videostatsPlaybackUrl` with `ver=2` and a fresh `cpn` (16 chars from `A-Za-z0-9-_`), authenticated like Phase 0. (From yt-dlp `--mark-watched`; UNVERIFIED parameters beyond that.)
- Watchtime pings (UNVERIFIED parameter names, confirm against a real web client capture): `videostatsWatchtimeUrl` with the same `cpn`, current media time `cmt`, watched segments as start/end lists `st`/`et`, `state` (playing/paused), real-time offset `rt`, video length `len`, and `final=1` on the last one. Send at a modest interval while playing (start with 30 s, make it a constant), and ALWAYS one on pause, on end (position = real end), on stop/switch video, and on error after progress. Seeking creates a new segment. Never send a ping whose position goes backwards without a new segment. This is the answer to "does completion get sent": only if we send the final ping, so test it explicitly.
- Gating, all required: `account.reportActivity` ON (re-add the setting, GLOBAL, default OFF, section ACCOUNT, copy "Tell YouTube what I watch"), `history.localOnly` OFF, active account is a signed-in Google account (not LOCAL), playback is not offline/downloaded (`PlaybackSession.OFFLINE_CLIENT`) unless a separate setting `account.reportOffline` (default OFF) is on, and a tracking URL is available for this session. Otherwise do nothing and add one Diagnostics NOTE per session saying why (no reasons beyond these names).
- Governor: new `CallKind.REPORT` with its own budget in `GovernorLimits` (`reportsPerHour`, default 600) and the usual spacing/breaker/audit; the audit shows `REPORT` with `ok`. A `GovernorDenied`, an HTTP error, or the breaker opening must NEVER interrupt or delay playback: reporting runs on its own scope, drops pings instead of queueing more than 3, and disables itself for the rest of the session after 3 failures in a row. A 401/403 or a signed-out page sets `NEEDS_RELOGIN` through the existing path. Never log tokens, cookies or full URLs (use `Redaction`; the tracking URLs carry tokens).
- Desktop yt-dlp route: report 19 says reporting from the Linux yt-dlp stream info "cannot report correctly". Only report when the tracking URL source is the one Phase 0 proved; otherwise skip with the NOTE.
- Update `AccountSafetyUi.kt` onboarding (line ~110: "Not available: what you watch stays on this device; Vela never adds to your YouTube history.") so the text matches the setting (when reporting exists: "Off by default. When on, Vela tells YouTube what you watch, like the official app."), `SettingCopy`, the Privacy page summary in `SettingsPages.kt`, and SPEC section 11 (replace the "NOT implemented" note with the result and the date).
Tests: fake transport recording pings: playback ping once; watchtime sequence for play, pause, resume, seek, end (final=1, position == duration), stop mid-video; nothing sent when any gate is off (one test per gate); governor denial and transport failure leave playback state untouched; 3 failures disable the reporter for the session; no secret appears in Diagnostics/audit output (extend the existing leak tests). Live (liveTest, throwaway account only): report one short video start to end, then confirm it shows in `FEhistory` with the expected percent, and remove it if possible.

## Out of scope

PO-token generation, profile (brand channel) selection, subscribing/liking changes, any change to how streams are extracted or played, reporting from the "Shorts" player unless it uses the same session path (then it is included, with the same gates), and making reporting default ON (owner decision after the live results).

## Hand back

In the report, a "Findings" block at the top of section 5 answering: which source (A/B/C) gave tracking URLs, what the real parameter set was, whether the account history showed the video, whether completion (final ping) changed the shown percentage, and how much of the repeated-recommendations problem went away for the owner's account (owner-run manual check; give the exact steps in section 6: watch one video to the end in Vela with reporting on, open the official app/site a few minutes later, check it is in History with a full bar and gone or lower in Home).
