# Task 20: core completion (the leftovers, then the remaining P2 features)

## 1. Summary

Done in this task: every P2 feature now exists in some form (search filters and suggestions, auto-download rules, backup and restore, live chat), plus most reliability leftovers: a timeout around every screen's data source, cache rebuild with salvage of a damaged database, speed/quality restored after a process kill, the Android captcha flow, Linux MPRIS and a process-shutdown fix. **Not done:** the live-streams measurement on both players (A6), the screens-states audit for every list screen (only Home, Search, Channel and the video page were exercised), and these task-19 leftovers: watch-history reporting, profile selection, merged history, playlist reorder, playlist create/rename/delete screens, per-account SponsorBlock/DeArrow data. Android-runtime parts are compile-only unless a row says "on device" (phone: OPPO CPH2707, Android 16): the self-test (11 checks, including playback and a download) passes with the new build and the Backup screen renders.

## 2. Verification

| Item | Check | Result | Verified by |
|---|---|---|---|
| A1 Screen states | Home, Search, Channel and the video page against a dead network, a captive portal's HTML where JSON was expected, and a call that never answers: each ends in an error row with Retry, and Retry loads the content; `TimeoutExtractor` (75 s) wraps every screen's source and the player's resolve | pass (12 UI cases + unit) | running (desktop UI) |
| A1 | Subscriptions feed, Shorts, Channels list, Library/History/Playlists/Downloads (local, no network), Comments/Related states separately, screenshots at phone and desktop sizes | **not done** | not run |
| A2 MPRIS | The service on the real session bus: status, position, rate, metadata (title, artist, length, art URL), PlayPause/Next/Previous/Stop/Seek/SetPosition reach the app, state changes are read back; in the running app `busctl --user` shows `org.mpris.MediaPlayer2.vela` (Identity "Vela", Stopped, CanControl true) | pass | running (session bus) |
| A2 | Whether GNOME's media widget and the keyboard's media keys drive it, and PropertiesChanged signals being received by GNOME | not observed | not verified |
| A2 Shutdown | Idle app + SIGTERM: exits, the bus name is released, no yt-dlp/ffmpeg/mpv left; helper processes are tracked and destroyed by a shutdown hook (new): a running `sleep` started through the yt-dlp runner is killed by `destroyAll` | pass | running |
| A2 | A component crashing mid-download, window-close with a download in flight | not exercised | not run |
| A3 Captcha | The extractor wrapper: captcha with a page URL → banner → solved → the call is repeated exactly once; second captcha, dismissal, timeout or a captcha without a URL → the original failure, no loop; the exemption cookie goes only to youtube.com / youtubei.googleapis.com, never to stream or image hosts, and expires; it is excluded from backups | pass (4 tests) | running (fakes) |
| A3 | The WebView activity, solving a real captcha, the cookie actually unblocking requests | **unverified live** | compile-only |
| A4 | Speed and quality-cap picks are kept across a restart (saved with the mini player) and applied to the next start of that video only | pass (unit); not run on the phone after a kill | running (JVM) |
| A5 Caches | `CacheMaintenance.rebuild` clears ApiCache, SegmentCache, FeedItem and the `health.*`/`release.*` rows and nothing else (accounts, downloads, playlists, history, subscriptions, settings kept); "Rebuild caches" button in Diagnostics | pass | running (JVM) |
| A5 Corruption | A file with damaged cache pages is detected; salvage moves it aside (kept), builds a new one and copies every non-cache table column by column (a table that cannot be read is named); the desktop asks before doing it | pass (test with pages overwritten) | running (JVM); the Android file is not recovered (it reports) |
| A6 Live streams | Playback, DVR seek, live edge, ended streams on both players | **not done**. (Only incidental: during task 17/18 a Trending live stream played on the phone and media actions were checked.) | not run |
| B1 Search | Duration (<4, 4-20, >20 min) and sort (most viewed, shortest, longest) chips applied to the LOADED results, an explanatory line when nothing matches, search-as-you-type suggestions (NewPipe's suggestion extractor) | pass (unit + 2 UI) | running (desktop UI) |
| B1 | NewPipeExtractor 0.26.5's YouTube search takes only a content type (read from its source): **no source-side sort, duration or upload-date filter exists**, and channel/playlist results have no model here, so those are not offered | known limit | source read |
| B2 Auto-download | Rules per channel and Watch later; only videos newer than the rule; one summary, not one notification per video; Wi-Fi-only per rule; failed check is reported; disabled rules ignored; the storage cap removes watched AUTOMATIC downloads oldest-watched first (manual and unwatched never); watched automatic downloads are removed after N days; nothing deleted is fetched again; always through the anonymous downloads chain | pass (5 engine tests + 2 UI tests) | running (fake manager/storage) |
| B2 | Linux 30-minute timer and the Android 6-hour WorkManager job actually running, the Rules screen on the phone | **not run** | compile-only |
| B3 Backup | One JSON file (schema v2) with settings (incl. per-channel/video overrides), subscriptions, local playlists + Watch later, optional history and search history; never accounts, cookies, the SponsorBlock id, downloads, caches, the remote-config version or the captcha cookie; preview before restore; restore only adds (merge by name for playlists, by video+time for history), twice = no change; an old v1 file is upgraded; a newer or foreign file is refused with a message | pass (4 unit + 2 UI round-trip tests) | running (JVM/desktop) |
| B3 | Android document picker (create/open) and the Linux file dialog | the Android screen renders on the phone; picking/saving a file was not done | compile + screenshot |
| B4 Live chat | Anonymous polling of `next` → `live_chat/get_live_chat` for a real live stream: 69 messages, 10 s delay, next continuation (probe, 2026-10-28); parser (text, paid, membership, replay unwrapped, unknown items ignored), de-duplication across polls, delay clamped to 3-30 s, no renderer = no chat; the panel appears only on live videos, requests nothing until opened, shows paid messages, says "no live chat" quietly | pass | running (parser/client + UI), one real probe |
| B4 | Replay chat of an ended stream is read in order, **not synchronised to the playhead**; the replay endpoint itself was not measured; Android not run | known limits | not run |
| B5 | Multi-account switcher and per-account data done in task 19; settings search covers the new settings (they are in the settings list) | partly checked | settings list only |
| All | `./gradlew allTests test :androidApp:assembleDebug :androidApp:assembleRelease --continue` | green (no failures printed) | running |
| Device | Self-test on the phone with this build (`--es run all`): canaries, playback 20 s, seek, pause/resume, download + merge, StrictMode control | failed 0 of 11; no crash in logcat | running, on device |

## 3. Files created/changed

- **A**: `extractor/TimeoutExtractor.kt`, `extractor/Captcha.kt`, `ui/screens/AccountSafetyUi.kt` (CaptchaBanner), `androidApp/.../auth/CaptchaActivity.kt` (+ manifest), `NewPipe OkHttpDownloader` (exemption cookie), `data/CacheMaintenance.kt`, `jvmMain/data/DatabaseRecovery.kt`, `PlaybackSession` (overrides, `media`, restore), `NowPlayingSnapshot`, `desktop mpris/MprisService.kt`, `jvmMain/platform/ChildProcesses.kt` (+ hooks in `SystemProcessRunner`, `FfmpegMediaMerger`, desktop `main.kt`), Diagnostics buttons.
- **B**: `extractor/SearchFilters.kt`, suggestions in `Extractor`/`NewPipeExtractor`/chain, `SearchScreen`; `downloads/AutoDownload.kt`, `DownloadRulesScreen`, channel-page switch, `AutoDownloadWorker.kt`, desktop timer, notifiers; `backup/Backup.kt`, `BackupFiles`, `BackupScreen`, `AwtBackupFiles`, Android document picker; `extractor/LiveChat.kt`, `innertube/LiveChatClient.kt`, chat panel in `VideoScreen`.
- **Tests**: `ScreenStatesTest`, `TimeoutExtractorTest`, `CaptchaTest`, `CacheMaintenanceTest`, `MprisServiceTest`, `ChildProcessShutdownTest`, `SearchFiltersTest`/`SearchFiltersUiTest`, `AutoDownloadTest`/`DownloadRulesUiTest`, `BackupTest`/`BackupUiTest`, `LiveChatParserTest`/`LiveChatUiTest`, `NowPlayingSnapshotTest`, `PlaybackSessionTest`.
- Dependencies added: `dbus-java-core` + `dbus-java-transport-native-unixsocket` 5.1.1 and `slf4j-nop` (desktop only).
- Not committed.

## 4. Decisions the spec didn't dictate

- **Search**: filters are client-side on loaded results because no tier can filter at the source; "relevance" keeps the source order; unknown lengths/views sort last.
- **Auto-download**: rules live as JSON in settings (no schema change); new = published after the rule was created (no backlog); at most 5 videos per rule per pass; the storage cap (previously display-only) now binds automatic downloads only, using a 200 MB estimate per video because the real size is not known before resolving; "delete after watched" for automatic downloads has its own delay (default 3 days, 0 = keep) beside the existing immediate delete-after-watch setting.
- **Backup**: the LOCAL account's personal data only (a Google account's data comes back from the account); settings are overwritten by the file's values, everything else only added; the SponsorBlock user id is left out as private.
- **Cache rebuild**: `remoteConfig.version`/`file` are NOT caches (clearing the version would allow replaying an old signed config).
- **Captcha**: only on Android; Linux has no WebView, so no banner there.
- **MPRIS**: Next calls the queue ("play all"), Previous restarts the video (Vela has no history stack); volume is not exposed.

## 5. Known issues / unverified assumptions

- Not done: A6 (live streams on both players), the full screens-states audit and its screenshots, watch-history reporting, profile selection, merged history, playlist reorder, playlist create/rename/delete screens, per-account SponsorBlock/DeArrow data, live-chat replay synchronisation, channel-type search results.
- Android runtime of: the Rules screen, WorkManager auto-download, the captcha activity, live chat, document pickers, search filters/suggestions on the phone, restoring speed/quality after a kill. They compile and share the tested logic; only the Backup screen was looked at.
- The `captcha` banner, the exemption cookie's effect on YouTube, and GNOME's reaction to the MPRIS service are assumptions until tried.
- The desktop DB-recovery dialog and the Linux timer were not run. The Android database has no salvage (a damaged file is only reported).
- The storage-cap estimate (200 MB) can be wrong in either direction.
- A flaky timeout in `LayoutInvariantsTest` appeared once under heavy machine load in task 19.

## 6. Core complete? (docs/SPEC.md → status)

| Item | Status | Where |
|---|---|---|
| P0 Extractor layer (search, info, streams, channels) | done | tasks 3-6, 15-17 |
| P0 Playback (quality/codec, speed, background audio, PiP, sleep timer) | done | tasks 7-9, 17 (phone: background, focus, handoff, calls) |
| P0 Account login with sync (subscriptions, history, playlists, watch later) | **partial**: login, subscription sync, playlists/Watch later mirror, writes done; **watch-history sync/reporting not done** | reports 13, 19 |
| P0 Subscription feed | done | tasks 9-12 |
| P0 Comments, Chapters | done | tasks 5, 9 |
| P1 SponsorBlock (actions, notifications) | done; segment **submission**: not verified here | report 10 |
| P1 Return YouTube Dislike, DeArrow | done | report 10 |
| P1 Downloads (queue, resume, audio-only, merge, service, folder) | done; on-device download + merge verified | reports 11, 17 |
| P1 Offline library | done | report 11 |
| P1 Shorts control | done | report 9/12 |
| P1 Account fallback playback (section 4) | done on Linux; **Android: done in task 22** (embedded yt-dlp, anonymous first, account last; verified on the phone) | reports 13-15, 22 |
| P2 Feed filters | done | task 9/12 |
| P2 Search filters, history | **done within source limits** (client-side), history done | this report |
| P2 Auto-download rules | **done, unverified live** | this report |
| P2 Local playlists, local-only history | done | tasks 9, 13 |
| P2 Live chat | **done (anonymous polling); replay not synced; Android unverified** | this report |
| P2 Backup and restore | **done** | this report |
| P2 Multi-account | done | report 19 |
| P2 Settings search | done | tasks 9, 12 |

The core is complete except: (Android age-restricted playback was closed by task 22), watch-history reporting/sync, live-stream measurement on both players, and the unverified-on-device items listed in section 5.

## 7. Manual test checklist for the user

1. Phone: Library > Downloads > Automatic downloads: add a rule for a channel that uploads often, tap "Check now" (it only queues videos newer than the rule, so a channel that uploaded before would show "nothing new"); the notification is one summary.
2. Settings > menu > Backup and restore: create a backup (pick a place), change a setting, restore it: the preview lists what would change; restore; check subscriptions, playlists and settings. Do it on Linux too.
3. Search: type a word: suggestions appear; open results: the "Any length" and "Relevance" chips filter/sort what is loaded.
4. Open a live stream: "Show live chat" under the title; check messages and that nothing is requested until you open it.
5. Linux: play something, press the keyboard's play/pause and next keys; the GNOME media widget shows title and artwork (not verified here). `busctl --user introspect org.mpris.MediaPlayer2.vela /org/mpris/MediaPlayer2` shows the interfaces.
6. Linux: start a download, then `pkill -TERM -f com.greenaltambe.vela.MainKt`: no `yt-dlp`/`ffmpeg` left in `pgrep -a`.
7. Settings > Diagnostics: "Rebuild caches" clears caches and says what it cleared; nothing else changes.
8. Android captcha (hard to provoke): if a captcha banner ever appears, "Solve it" opens the page; after Done the request should retry once.
9. Tell me whether to continue with the not-done list, starting with live-stream measurement (it needs a real live channel on both players).

## 8. Update after the owner's test (search suggestions, live streams)

- **Search suggestions fixed**: `PacedExtractor` did not forward the new `suggestions` call, so it answered "not supported". Fixed, a structural test now checks that every wrapper (paced, timeout, captcha, fault, chain) forwards every Extractor method, and a live test shows NewPipe returning 8 suggestions through the pacing wrapper.
- **Live streams (A6), measured on a real stream (4xDzrJKXOOY)**:
  - Phone (Media3/HLS): plays; the reported position jumps back about 3 s every 6-10 s because the live window slides (not a playback glitch); DVR seek 60 s back works; a seek past the end ended in BUFFERING. Fixed in code: a seek at/after the end goes to the live edge, the player reports `liveOffsetMs`, the UI shows "LIVE" / "-m:ss behind live" with a "Go live" button. **Not re-run on the phone after the fix.**
  - Linux (libmpv): plays smoothly; a seek past the cache left the shown position stuck at the target (630 s shown, 20 s real). Fixed (the override expires after 3 s). mpv ignores seeks towards the live end, so "Go live" reloads the stream: measured offset 58 s -> 16 s after Go live. Ended streams were not tested on either player.
- **Still not done**: screens-states audit for the remaining list screens, watch-history reporting, profile selection, merged history, playlist reorder and the playlist create/rename/delete screens, per-account SponsorBlock/DeArrow data, live-chat replay sync. The full test suite was not re-run after these last changes (only compile and the live tests above).

## 9. Update: the not-done list, second pass

Full suite after these changes (`allTests test :androidApp:assembleDebug :androidApp:assembleRelease --continue`): green, no failures printed. The phone was not connected, so **nothing below ran on the phone** and the Android live-stream fixes from section 8 are still not re-measured there.

| Item | Result | Verified by |
|---|---|---|
| Playlists | Library "New playlist" can also create it on the YouTube account (private); account-mirrored playlists (not Watch later / Liked) can be renamed and deleted on the account, and the local copy follows only when the account accepted; a refusal shows the reason and changes nothing locally. Local reorder (arrows) already existed; reordering an ACCOUNT playlist is not supported (the arrows on a mirror only move the local copy) | UI test passes | running (fake account actions) |
| Per-account SponsorBlock | each account submits under its own private id (local account keeps the original setting); DeArrow has no per-user data in this app | unit test passes | running |
| Merged history | History shows this device's history, plus a button that loads the account's history below it: de-duplicated by video (account entries carry no time), labelled "From your YouTube account", with a note ("2 on your account, 1 also watched here"); a failed read says so | 2 UI tests pass; the account's real history shape not seen (test account has none) | running (fake reads) |
| Screens states | Subscriptions feed added to the audit: dead network, captive portal HTML and a hang all end in an error row with Retry (5 screens x 3 failure kinds now) | pass | running (desktop UI) |
| Profile selection | Evaluated live: `account/accounts_list` answers the profiles with a selected flag, a `datasyncId` and a token (the throwaway account has one profile). Built: the parser, `InnertubeClient.profiles()`, and `AuthSession` sending `X-Goog-PageId` when a page id is set. **Nothing sets it yet** (no selection UI, no storage), so it is inert; the page-id rule ("<pageId>||<userId>") is an assumption that could not be verified with one profile | parser + header unit test | running |
| Watch-history reporting | **Not done, on purpose**: it needs the playback tracking URLs from an own /player response made with the account, which is the parked Android age-restricted area; no tier hands them to the app and the Linux yt-dlp route cannot report correctly | decision | - |

**Still open:** watch-history reporting (above), a profile-selection screen (plumbing only), re-running the Android live-stream check on the phone, ended live streams on both players, live-chat replay sync, Android runtime of the Rules screen / captcha / live chat / pickers.

## 10. Phone re-check (OPPO CPH2707, Android 16, USB)

| Check | Result |
|---|---|
| Live stream, self-test `--es run live` (4xDzrJKXOOY) | 2nd run: 0 stalls in 40 s, DVR seek 60 s back works, a seek past the end now lands at the live edge and keeps PLAYING (before the fix: BUFFERING). The 1st run paused after 4 s for no reason I could find (nothing held audio focus, screen awake); not reproduced. The raw position still steps back ~3 s every few seconds (sliding window), which is why the UI shows the live offset |
| Live UI | Player shows "LIVE"; it showed "-0:22 behind live" + "Go live" at the normal ExoPlayer offset, so the "live" threshold was raised to 40 s (fixed after this look; the new build shows "LIVE") |
| Live chat | Opened a live stream, "Show live chat": real messages stream in with emoji shortcuts; a normal video has no chat panel |
| Search | Suggestions appear while typing (8 for "lofi"); the duration and sort chips narrow the loaded results and show the "Nothing in the 19 loaded results matches" note |
| Automatic downloads | The Rules screen shows an existing rule with toggle/Edit/Delete; "Check now" reported "waiting for Wi-Fi" (the phone was on mobile data). Found a doubled title bar: fixed. WorkManager's 6-hour job itself was not observed |
| Backup | "Create backup" opens the system save dialog and saved `vela-backup.json`; "Restore from a file" opened the picker and showed the preview. **Found a flaw**: the file contained the account governor's breaker state (`governor.google-….state/until`), which must never be restored: now excluded (test pins it). Also shortened the "unknown setting" notes to one line |
| Not re-done | Captcha activity (cannot be provoked), ended live streams, Go live on the phone after the threshold change, the full suite after these last edits (compile + the backup, UI and live tests only) |
