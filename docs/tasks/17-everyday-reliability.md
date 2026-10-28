# Task 17: everyday reliability (the failures a user actually meets)

Follow CLAUDE.md (report docs/reports/17-everyday-reliability.md; terminal reply max 5 lines + anything the owner must do). Context: PO-token work (tasks 15/16 Stages 2-3) is DROPPED for now (NewPipe's token hook only covers Android/iOS clients, whose tokens cannot be minted outside the real apps; a WebView token only helps web clients we do not use). Do not start it. Do the open items from task 16 that matter day to day, then a hardening pass. Commit nothing; build in stages; finish or stop at a clean point and say why.

## A. Open reliability items from task 16
- Network handoff (Wi-Fi to mobile, VPN change, brief offline) and slow-stream failover in PlaybackSession for BOTH players: re-resolve and resume at the position, bounded; if throughput stays below the stream bitrate for N seconds while buffering, re-resolve once preferring a different tier/profile (setting, default on). Chaos-harness scenarios with fake clock; explain the cause in Diagnostics.
- Per-account persisted identity: visitorData, device id, client version, user agent stored (encrypted, per account) and reused; refresh only on staleness/failure; unit tests; switching account swaps identity.
- Live assertion (skips without VELA_TEST_COOKIES) of the hosts real yt-dlp contacts with the account cookies; fail if a new host appears.
- Android captcha WebView flow: when an anonymous request returns Captcha, offer a WebView solve, store the exemption cookie, inject it into anonymous requests, retry once. Compile-only here: say so, test with fakes.
- OS-level health alert: Android notification (channel, once per streak) and Linux notify-send, in addition to the banner.

## B. Hardening pass (audit, then fix; list every finding in the report)
- Network calls: every call has a connect/read/total timeout and is cancellable; no screen can spin forever; every list screen and the video page has loading/empty/error/retry states that were exercised in tests; app start with no network, with a dead network, with captive-portal HTML instead of JSON.
- Process death and restore: playback position, queue, mini player state, active account, open download queue; returning from background after the process was killed.
- Android media behaviour: audio focus (duck/pause on other apps, calls), becoming-noisy (headset unplug) pauses, Bluetooth/headset media buttons, notification and lock-screen controls consistent with state, resume after a call, foreground service start restrictions on Android 12+ and 14+ (specific foreground service types), battery optimisation hints when background playback is killed.
- Linux: MPRIS over D-Bus (play/pause/seek/metadata, media keys under GNOME) and clean shutdown (no orphan mpv/ffmpeg/yt-dlp processes after kill -TERM, window close, crash of one component).
- Storage: low-disk handling for downloads and DB, DB corruption detection with a safe rebuild path for caches (never silently delete downloads or accounts), migration failure handling with a clear error and backup of the old DB file before migrating.
- Logging: a bounded rotating redacted log file the owner can share (Settings > Diagnostics > Export), including tier attempts and governor decisions, never credentials; verify with the redaction tests plus a grep test over a full simulated session.

## C. Verification
Offline tests incl. new chaos scenarios; live tests where network helps; :androidApp:assembleDebug; desktop run + Compose UI flows for error/empty/offline states with screenshots reviewed as in task 12. Mark compile-only items. Manual checklist for phone and Linux (airplane mode and network switch during playback, kill app mid-playback and reopen, unplug headphones, call interruption, Bluetooth button, GNOME media keys, low-storage download, corrupted cache file).
