# Task 18: minor reliability fixes (small, independent stages)

Follow CLAUDE.md (report docs/reports/18-minor-reliability.md; terminal reply max 5 lines + anything the owner must do). Prerequisite: task 17 done. Each stage below is small and independent: do them in order, skip any that turns out to be large and say why. Use the on-device self-test and adb loop from task 17 wherever Android evidence is possible. Commit nothing.

1. Android captcha WebView flow: when an anonymous request returns the distinct Captcha error, offer a WebView solve, store the exemption cookie, inject it into anonymous requests, retry once. Fakes for tests; label unverified live.
2. OS-level health alert: Android notification (channel, once per failure streak) and Linux notify-send, in addition to the banner.
3. Live test (skips without VELA_TEST_COOKIES) asserting the set of hosts real yt-dlp contacts with the account cookies; fail if a new host appears.
4. Linux: MPRIS over D-Bus (play/pause/seek/metadata, GNOME media keys), clean shutdown (no orphan mpv/ffmpeg/yt-dlp after SIGTERM, window close, or one component crashing).
5. Storage safety: low-disk handling for downloads and DB; DB corruption detection with a safe rebuild path for caches only (never silently delete downloads or accounts); back up the DB file before running a migration and show a clear error if a migration fails.
6. Timeouts and states audit: every network call has connect/read/total timeouts and is cancellable; no screen can spin forever; each list screen and the video page has exercised loading/empty/error/retry states (captive-portal HTML instead of JSON, dead network, no network at start).
7. Logging: bounded rotating redacted log file, Settings > Diagnostics > Export, covering tier attempts and governor decisions, never credentials; a grep-style test over a full simulated session.
8. Small polish found while doing the above: list each item in the report with its status.

## Additions from the task 17 report (small items; skip any that is large and say so)
9. Investigate the one run that saved watch position 0 after ~19 s watched (positionMs 0, watchedMs 18.8 s) so the next start resumed from the beginning; add a regression test for the position writer if a cause is found.
10. StrictMode positive control: add a deliberate main-thread read in a debug-only test and show the log check catches it; make the self-test windows use a run id instead of "last 2 minutes".
11. Android 16 log notice "AudioHardening background playback would be muted ... level: partial" was seen while playing in the background: check what triggers it (audio focus / usage / foreground type) and whether audio was really muted; fix if it is ours.
12. Battery-optimisation hint when background playback is killed (detect, explain once, deep-link to the right settings screen).
13. Restore the player's own state after process death (quality choice, speed) where cheap; auto-play stays off.
14. Downloads: for a VP9 video prefer VP9 + Opus into WebM when the merger supports it before falling back to H.264 + AAC into MP4; keep the rule device-aware (ask the merger, do not hard-code the result measured on one phone).
15. Foreground service start-from-background on Android 12/14/15: write down what is code-review only and keep it in the manual checklist.
16. Add settings `playback.allowAgeRestricted` (default OFF) and `playback.allowControversial` (default OFF), shown in Settings > Playback with a short explanation. When off, an age-restricted video shows the clear message "Age-restricted videos are disabled in settings" with a button to open the setting; when on, the existing routes are used (Linux: yt-dlp; Android: friendly failure with Open in browser until a signed-in route exists). Tests for both states.
