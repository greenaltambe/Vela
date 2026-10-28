# Task 20: finish the core (remaining P2 features + reliability leftovers)

Follow CLAUDE.md (report docs/reports/20-core-completion.md; terminal reply max 5 lines + anything the owner must do). Prerequisite: task 19 done. Use the adb + self-test loop from task 17 for Android evidence, and screenshot review as in task 12 for UI. Commit nothing. Stage order below; finish or stop at a clean point and say why. After this task the core product is complete by docs/SPEC.md and the customisation phase starts.

## A. Reliability leftovers (from tasks 17/18)
1. Screen-states audit: every list screen and the video page has exercised loading / empty / error / retry states (Home, Search, Subscriptions feed + Shorts + Channels, Library, History, Playlists, Downloads, Channel, Comments, Related). Cover captive-portal HTML instead of JSON, dead network, and app start with no network. Screenshot tests at phone and desktop sizes; no screen may spin forever (hard timeout then error with Retry).
2. Linux: MPRIS over D-Bus (play/pause/next/prev/seek/metadata/art, GNOME media keys) and a shutdown audit (no orphan mpv/ffmpeg/yt-dlp after SIGTERM, window close, or one component crashing).
3. Android captcha WebView flow: anonymous Captcha error -> WebView solve -> store the exemption cookie -> inject into anonymous requests -> retry once. Fakes for tests; label unverified live.
4. Restore the player's own state after process death (quality choice, speed); auto-play stays off.
5. DB cache rebuild path: define which tables are caches (feed cache, health, DeArrow/SponsorBlock/RYD caches, thumbnails) and offer "rebuild caches" that never touches accounts, downloads, playlists, history or settings; use it for corruption recovery of cache tables.
6. Live streams: they were never measured. Test live playback on both players (HLS/DASH live, DVR seek, going live-edge, ended streams) with a real live channel, fix what breaks, and make the live badge/state correct everywhere.

## B. Remaining P2 features from docs/SPEC.md
1. Search filters: type, duration, upload date, sort (what NewPipeExtractor supports; yt-dlp tier degrades gracefully), plus search suggestions if cheap; search history already exists.
2. Auto-download rules: per channel and for Watch Later: new videos, quality, audio-only option, Wi-Fi only, storage cap with oldest-watched-first cleanup, auto-delete after watched (with a delay), runs via WorkManager on Android and a timer on Linux, through DownloadManager and the anonymous tiers (account never used for downloads by default). UI under Library > Downloads > Rules and per-channel overflow. Notification summaries, no spam.
3. Backup and restore: one file (zip/JSON with schema version) holding settings (from the schema), subscriptions, local playlists, history, per-channel overrides, search history optional; never credentials or downloads; restore previews what changes and merges safely; migration across schema versions; works on both platforms; round-trip test.
4. Live chat: read-only live chat panel for live streams and replay chat for ended streams by polling YouTube's live-chat continuation (anonymous), with emote/superchat rendering kept simple, rate-limited and cancelled when leaving the video; works under the paced transport; degrade quietly when unavailable.
5. Multi-account polish if task 19 left gaps (switching UI, per-account data separation) and settings search covering everything added here.

## C. Verification and report
Offline tests, liveTest, desktop UI flows with screenshots reviewed, on-device self-test additions for new Android parts (auto-download worker, backup/restore, live playback), and a manual checklist for phone and Linux. List clearly what is compile-only. Finish with a short "core complete?" table mapping every P0/P1/P2 item in docs/SPEC.md to done / partial / not done with the report or file that proves it.
