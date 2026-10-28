# Task 21: gap filling

Status words: **VERIFIED** (how) or **NOT VERIFIED** (why). Phone = OPPO CPH2707, Android 16, USB adb. Screenshots in `docs/reports/21-screens/` (phone: library, history, auto-download-rules, search-suggestions, search-filters, live-chat, live-controls-LIVE, backup-restore-preview, captcha-activity, health).

## 1. Summary

Everything the task asked was done or measured; what is NOT VERIFIED is listed per row and again in section 5. New defects found and fixed while verifying: History silently capped at 500 rows (added "Load older history"); the captcha activity's buttons sat under the status bar; the Rules screen title was doubled; backups contained the account governor's breaker state and profile choices (now excluded); search/Home showed an unexplained empty list when Settings > Feed rules hid everything (now "N hidden by your feed filters"); a mis-set phone setting (feed minimum length 57 min, from a stray tap of mine) was reset. Chat replay is measured and synced to the playhead; profiles are built behind an Experimental switch (off); the dead `account.reportActivity` setting is gone from the UI.

## 2. Verification

| # | Item | Check | Result | Verified by |
|---|---|---|---|---|
| A1 | Self-test `--es run all`, final build | 11 checks incl. canaries, 20 s playback, seek, pause/resume, download+merge, StrictMode control | **VERIFIED**: failed 0 of 11 on Wi-Fi, no `FATAL EXCEPTION` in logcat. A first run on mobile data only failed 2 (20 s playback advanced 4.8 s; download timeout): a slow mobile link, not reproduced on Wi-Fi | phone |
| A2 | Live stream (4xDzrJKXOOY, 24/7 lofi) | LIVE label, DVR seek, past-the-end seek | **VERIFIED**: 0 stalls in 40 s; "-0:22 behind live" + Go live appeared at ExoPlayer's normal offset, so the threshold was raised to 40 s and the player then showed "LIVE" (screenshot); DVR seek 60 s back lands and plays; a seek past the end lands on the live edge (PLAYING). The raw position steps back ~3 s every few seconds (sliding window), which is why the UI uses the offset | phone (self-test + screenshots) |
| A2 | "Go live" button press on the phone | | **NOT VERIFIED** on the phone (button was shown; pressing it was not done after the threshold change; the mpv Go live was measured on Linux: 58 s -> 16 s behind) | - |
| A2 | Ended live stream (RTs0C5dV5cM, 3410 s, has chat replay) | plays, seeks | **VERIFIED**: resolves, plays 20 s, seeks, pauses, resumes | phone (self-test `play`) |
| A3 | Live chat panel | messages appear | **VERIFIED** on a real live stream (screenshot: real messages with emoji shortcuts) | phone |
| A3 | "nothing requested until opened" / "closing stops polling" | | requested-on-open: unit/UI test only; **closing stops polling NOT VERIFIED on the phone** (the polling is a coroutine keyed on the panel's open state; cancelled by Compose, not measured by watching requests) | desktop UI test |
| A4 | Automatic downloads | Rules screen, add a rule, "Check now", job registered | **VERIFIED**: rule added for Watch later; with a video in Watch later "Check now" said "Queued 1 new video." (Wi-Fi); on mobile data it said "waiting for Wi-Fi" as designed; `dumpsys jobscheduler` shows two WorkManager jobs (`NET` + `BATNOTLOW`, due in +3 h and +5 h 54 m: the auto-download and the health job) | phone |
| A5 | Backup/restore | create via picker, restore via picker, preview, twice = no change | **VERIFIED**: saved `vela-backup.json` via the system dialog; restore showed the preview; 1st restore "1 would change", 2nd "0 would change, 5 already match, 0 new" | phone |
| A6 | Process death | speed 1.5 + 360p cap, `kill -9`, reopen | **VERIFIED**: stored snapshot `...|1.5|360`; after the kill the mini player's Play resumed at speed 1.5 (media session `speed=1.5`) at the saved position. "For that video only" is unit-tested, not done on the phone | phone + unit |
| A7 | Captcha activity | opens on a harmless page, shows it, returns, cookie store accepts and expires | **VERIFIED**: debug scenario opens the activity on Google's public reCAPTCHA demo page (screenshot), "Done" returned to the waiting request (solved=false: the demo sets no exemption cookie), the challenge was published for the banner and cleared; the cookie store accepted a value, would send it only to YouTube hosts (not googlevideo/ytimg) and expired after 6 h (fake clock). Found and fixed: the buttons were under the status bar. **The real unblock (YouTube accepting the cookie) is UNPROVEN.** | phone |
| A8 | Screenshots | | 10 phone screenshots saved (list above); **NOT all touched screens**: no phone shots of the Accounts profile section (experimental, off), Subscriptions, the large-list states or the replay chat panel | phone |
| B | Screen states, network screens | dead network / captive-portal HTML / hang -> error row with Retry, Retry works | **VERIFIED**: Home, Search, Channel, Subscriptions feed, video page (comments, related share this path) x 3 failure kinds | desktop UI |
| B | Shorts | | shares the Subscriptions feed's state code (same `FeedService`, same error row); **not separately exercised** | - |
| B | Channels list, Playlist page, Library, History, Downloads, Watch later | empty / populated / 2000 rows | **VERIFIED**: each composes only visible rows (6-60 nodes of 2000), History opens in 92 ms, Downloads 2000 rows in 196 ms, the Library with 2000 playlists and 2000 subscriptions stays lazy, scroll to the end works; empty History says "Nothing watched yet." **Found and fixed**: History showed only the newest 500 (now "Load older history", 500 at a time). Smoothness of scrolling 2000 rows **on the phone: NOT VERIFIED** | desktop UI |
| B | "Channel tabs (videos/playlists/about)" | | the channel screen has no tabs (videos only), so nothing to audit; description shown above the list | n/a |
| C1 | MPRIS signals | `dbus-monitor` on the session bus while state changes | **VERIFIED**: 6 `PropertiesChanged` signals captured from `org.mpris.MediaPlayer2.Player` with `PlaybackStatus` (Paused/Playing), `Metadata` (`xesam:title`, `mpris:trackid`, length, art URL) and `Rate`; the real app registers the name with Identity "Vela". `Seeked` is not emitted and Position is not announced (clients poll it) | desktop (real session bus) |
| C1 | Media keys and the GNOME widget | | **FAILED on the owner's desktop: the widget showed nothing.** The service is on the bus with `DesktopEntry="vela"` but no `vela.desktop` exists, the likely cause (unconfirmed). Fix pending a decision on how Vela is launched/installed | owner |
| C2 | Owner ran `pkill -TERM` then `pgrep` for yt-dlp/ffmpeg: no orphans (a download was not confirmed to be in flight). Download mid-flight, SIGTERM/window close | partial kept, resumes, no orphans | partial file kept and resume from the bytes on disk: **VERIFIED** (existing unit test `aKilledProcessResumesFromTheBytesOnDiskNotFromZero`); helper processes (yt-dlp/ffmpeg) destroyed on shutdown: **VERIFIED** (process test with a real child + the shutdown hook, idle SIGTERM of the real app exited cleanly); **the real app killed with a real download in flight: NOT RUN** | unit/process test |
| C2 | A component throwing mid-download | marked failed with a reason, app stays up | **VERIFIED**: the exception text is kept as the reason, the next download completes | unit |
| C3 | Ended live stream in libmpv | plays and seeks | **VERIFIED**: RTs0C5dV5cM plays; a seek to 600 s lands (mpv time-pos 604 s); backward seek lands | desktop (real libmpv) |
| D | Chat replay | probe, sync to the playhead | **VERIFIED measured**: `get_live_chat_replay` works anonymously; every action carries `videoOffsetTimeMsec`; the continuation is `liveChatReplayContinuationData` (no `timeoutMs`). A seek restarts with `currentPlayerState.playerOffsetMs`: it returns a chunk that COVERS the target time (not starting at it: asked 600 s, got 86 s..1495 s), and the panel shows only messages at or before the playhead. Phone: replay opens, first chunk has offsets, seek chunk covers 600 s, live chat polls (self-test); desktop UI: only messages <= playhead shown. **The replay panel itself on the phone: NOT VERIFIED** (could not navigate to it reliably) | phone (client), desktop (UI) |
| D | Detection | | NewPipe does not flag ended streams (RTs0C5dV5cM came back as a normal video), so the panel is offered when a NON-live video longer than 15 min has a chat replay (one anonymous request per such page) | measured |
| E1 | Profiles storage + UI | list, per-account choice, header only for a non-default profile | built: `account/accounts_list` -> profiles, `ProfileStore` per account, selector in Accounts behind Settings > Account "Experimental" (**default OFF**), `X-Goog-PageId` only when a non-default profile is chosen | unit |
| E2 | Safety | read-only check first, revert on failure, no writes as unverified | **VERIFIED by unit tests**: a read made as that profile must come back as that profile or nothing is stored; a failed read stores nothing; an unverified non-default choice blocks writes (no write is even attempted); all calls through the governor (audit entries, a tripped breaker refuses the check) | unit |
| E | With a real second profile | | **NOT VERIFIED**: the throwaway account has one profile; the page-id rule ("<pageId>\|\|<userId>") is an assumption | - |
| E3 | Leak audit | | green (`CredentialLeakAuditTest` in the innertube suite) | unit |
| F | `account.reportActivity` | | it was never read by anything; removed from the settings list and from the onboarding (replaced by "Not available: what you watch stays on this device"); test pins that no setting promises it; local history works (all history tests); merged history labels account entries; decision written in `docs/SPEC.md` section 11 | unit + desktop UI |
| G1 | SponsorBlock submission | shape vs official docs | **VERIFIED**: `POST /api/skipSegments` JSON body (videoID, userID 30+ chars, userAgent "Name/Version", service, videoDuration seconds, segments[{segment:[s,e], category, actionType}]) matches wiki.sponsor.ajay.app "API Docs" read 2026-10-28; fake-transport test only, nothing sent to the real server; 400/403/409/429 are failures and never retried. Also: each account now submits under its own private id | unit |
| G2 | Home "Recommended" | | fallback to Trending kept; **the one thing unknown: the real feed shape** (the throwaway account's Home was empty; the parser is verified only on a hand-built fixture) | - |
| G3 | Dependency freshness, tiers | | phone Health screen after "Run checks now": "NewPipeExtractor v0.26.5 is up to date (checked 2026-10-28)", anonymous 10/10 and alt client 9/9 working (screenshot). yt-dlp copies and the remote-config check were **not re-run** in this task | phone |
| H1 | Full suite | `./gradlew allTests test :androidApp:assembleDebug :androidApp:assembleRelease --continue` after the LAST code change | see section 4 | desktop |
| H2 | Secrets | `git status` | no cookie, key or secret file is untracked (checked) | desktop |

## 3. Files created/changed

New: `Profiles.kt`, `InnertubeAccountProfiles.kt`, `ProfilesFlowTest`, `LargeListsUiTest`, `SubmitShapeTest`, `LiveMpvEndedStreamTest`, `docs/reports/21-screens/`. Changed: chat (`LiveChat.kt` offsets + seek, `LiveChatClient`, VideoScreen panel), `PlayerView` (live edge threshold), `LibraryScreens` (History paging), `SearchScreen`/`HomeScreen` (hidden-by-filters line), `AccountSafetyUi` (no reporting switch), `AccountsScreen` (profile section), `Settings` (experimental switch; reportActivity removed from the list), `Backup` (exclusions), `CaptchaActivity` (insets), `DownloadRulesScreen` (title), debug `SelfTestActivity` (scenarios `live`, `chat`, `captcha`), `docs/SPEC.md` section 11. Not committed.

## 4. Full-suite result

`./gradlew allTests test :androidApp:assembleDebug :androidApp:assembleRelease --continue -q`, started after the last code change (`LiveChatUiTest` rewrite): **green**, empty output, debug and release APKs assemble. Android runtime stays compile-only except where marked phone. The first attempt of the earlier run (full22) had one stale `LiveChatUiTest` failure, fixed.

## 5. Known gaps accepted (for the owner to sign off)

1. Real captcha unblock unproven.
2. Replay chat panel and "Go live" button not driven on the phone; closing chat stops polling not measured on the phone.
3. Real second profile (brand channel) never tried; Experimental stays off.
4. The real app killed mid-download (SIGTERM) not exercised; covered by unit/process tests.
5. GNOME media widget: shows nothing (see C1); needs a `vela.desktop`.
6. Large-list scrolling smoothness on the phone not measured; Shorts tab not separately exercised.
7. Real Home "Recommended" feed shape unseen; age-restricted playback on Android parked.
8. yt-dlp freshness and remote-config checks not re-run here.

## 6. Manual checklist for the owner (only what could not be done)

1. Linux: play a video, press the keyboard media keys and look at the GNOME media widget (play/pause/next, title, artwork).
2. Linux: start a download, `pkill -TERM -f com.greenaltambe.vela.MainKt`, confirm `pgrep -a "yt-dlp|ffmpeg"` is empty, reopen: the download resumes.
3. Phone: open a long replay of a real stream with chat (e.g. search "Replay Live Chat" and pick one that has a chat box under the video), "Show chat replay", seek: messages follow the playhead.
4. Phone: on a live stream press "Go live" after seeking back.
5. If you have a Google account with a brand channel: turn on Settings > Account > Experimental, choose the profile and tell me what the confirmation says.
