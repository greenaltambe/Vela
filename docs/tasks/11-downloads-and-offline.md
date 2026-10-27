# Task 11: downloads and offline library (P1, "very important")

Follow CLAUDE.md (report docs/reports/11-downloads.md). Prerequisites: tasks 09 and 10 done. Read docs/SPEC.md sections 2 (P1), 3, 5; the Download/DownloadOfflineData tables already exist.

## Core (common)
- `DownloadManager` (commonMain): queue with concurrency limit (setting), states QUEUED/DOWNLOADING/PAUSED/MERGING/COMPLETED/FAILED, pause/resume/cancel/retry with backoff, progress flows for the UI, survives process death (state in DB, partial files on disk). Resume re-resolves a fresh stream URL through the FallbackChain when the stored one is expired/403 (reuse expiry logic from PlaybackSession; do not duplicate it badly - extract shared code).
- Ranged chunked downloads (10 MB bounded ranges; we measured 12-17x speedup over open-ended), resume from existing partial files via Range, integrity check by expected length, per-stream headers. Selection: chosen quality/codec for video (reuse StreamSelector with a download-specific constraint set; AVC preference option for compatibility), or audio-only (best audio, optionally keep original container .m4a/.webm/.opus).
- Merge video+audio without re-encoding (ffmpeg `-c copy`). Abstract as `MediaMerger`. Desktop: system ffmpeg (detect, diagnostics note if missing). Android: the spec asks for bundled ffmpeg, but ffmpeg-kit is retired; evaluate options (a maintained community ffmpeg build vs MediaMuxer/Media3 Transformer remux) with a real test on what I can verify, pick one, justify with evidence in the report, keep it behind `MediaMerger`. Container choice must play in our own players and be sensible for other apps (mkv/webm/mp4 as appropriate to codecs).
- Storage abstraction `DownloadStorage`: Android app-specific dir by default, user-chosen folder via SAF tree URI (incl. SD card) with persisted permissions; desktop XDG videos dir default, configurable. Settings in the schema: folder, default quality, default audio-only, Wi-Fi only, concurrent downloads, delete-after-watch (off), storage cap (display only for now).
- Offline data saved with each download: thumbnail file, description, chapters, SponsorBlock segments (task 10 repo), DeArrow title/thumbnail, first N pages of comments (setting, default 2) - all into DownloadOfflineData / files. The video page and player must work with no network from this data.

## Platform
- Android: foreground service (or WorkManager long-running foreground worker - choose, justify) with progress notification, pause/resume/cancel actions, completed/failed notifications, runs when the app is closed, respects Wi-Fi-only via NetworkMonitor, handles notification permission denial gracefully.
- Desktop: runs in the app process; notifications via `notify-send` if present (optional, diagnostics note if absent).

## UI
- Library > Downloads: list with progress, per-item actions, storage usage, play offline, delete (with confirm), "download" buttons on the video page (quality picker sheet, audio-only toggle) and in lists (long-press/menu), multi-select download for playlists/Watch Later.
- Offline playback: `PlaybackPlan.Local` through both players (Media3 with file/content URI; mpv with path/URI); offline player screen shows chapters, SponsorBlock, DeArrow, comments from stored data. Opening a downloaded video while offline never calls the network.

## Verification
Tests: queue state machine, resume after process death (simulate), URL re-resolve on 403, chunk assembly, merge command building, storage abstraction (fake FS), plan selection. Live: download a short video end-to-end on desktop (video+audio, merge, play with mpv, verify duration/streams with ffprobe) and an audio-only one; record throughput. Android: compile-only plus unit tests - say so; give a precise manual checklist (download while app open, close app, check notification and completion, SD card folder, airplane-mode playback, pause/resume, kill app mid-download and resume, storage full).
