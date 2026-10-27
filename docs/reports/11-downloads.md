# 11: downloads and offline library

## Summary

A download queue in common code (`DownloadManager`) with pause, resume, cancel, retry with backoff, a concurrency limit and Wi-Fi-only, whose state lives in SQLite and whose progress is the partial file on disk, so a killed process continues from the bytes already written. Streams are fetched in bounded 10 MB ranges (measured here at **34-37 Mbit/s average, 84-88 peak**), resolved fresh on every run through the fallback chain, and re-resolved when a URL dies (401/403/404/410). Video and audio are merged without re-encoding (system ffmpeg on Linux, the platform `MediaMuxer` on Android), the page of the video (info, description, chapters, thumbnail, SponsorBlock segments, DeArrow title/thumbnail, comments) is saved with it, and a downloaded video plays from its file with **zero extractor and zero HTTP calls** (verified). Library > Downloads, a Download button on the video page, a ⋮ menu entry and "Download all" on playlists are built. The Android pieces (foreground service, SAF folder, MediaMuxer merger, notifications) compile and are unit-tested under Robolectric; **none of it has run on a device**. Tasks 09 and 10 are not committed (the owner commits manually); I built on the working tree.

## Verification

| Check | Result | Verified by |
|---|---|---|
| Offline tests (see the counts line below the table) | all pass | running |
| Queue state machine: QUEUED -> DOWNLOADING -> MERGING -> COMPLETED, pause/resume/remove/retry in every state, enqueue of an existing video, concurrency limit (2 by default, 4 by setting), Wi-Fi-only (waits on metered, starts on Wi-Fi, stops a running transfer when Wi-Fi goes, offline holds everything) | 37 tests pass | running (fake storage, fetcher, merger; database is real SQLite) |
| **Resume after process death**: first run gets two chunks then the scope is cancelled mid-request (nothing writes a final state); a new manager on the same DB and files continues | video requested from byte 2000 and 3000 only (no re-download), 2 fresh resolves, bytes identical | running |
| **URL re-resolve on 401/403/404/410**, exactly one fresh resolve each; 429/5xx do not re-resolve; a URL rejected every time fails after 3 fresh tries | pass | running |
| Retry with backoff (1, 2, 4 s ...), the retry budget resets after a good chunk, connection errors retried, giving up reports the reason | pass | running |
| Chunk assembly (3500 bytes in 1000-byte ranges, last range clamped, every byte in order from both streams), part files while downloading, size-mismatch rejected and the bad partial deleted | pass | running |
| Merge failures: missing ffmpeg, failed merge, retry after a merge failure does not download again | pass | running |
| Plan selection with the playback selector: height cap, AVC preference, audio-only, HLS-only fails clearly; container choice for every codec pair; Android muxer rules | pass | running |
| Offline page saved with the download (description, chapters from the description, comments limited to the setting's pages, thumbnail, SponsorBlock); a failing part is noted, not fatal | pass | running |
| Storage abstraction: real files in a temp folder (append/resume sizes, rename, publish never overwrites, path traversal refused, assets apart, only our files counted) | 9 tests pass | running |
| Range fetcher against a local Range-capable server: 206/200/416, headers, streaming, **connection dropped mid-body keeps the right prefix and throws** | 6 tests pass | running |
| **Real ffmpeg**: WebM (VP9+Opus) and MP4 (MPEG-4+AAC) merges inspected with ffprobe (both streams present, codec names, duration 2.0 s), Opus remux to Ogg, broken input gives a clear error | pass | running (ffmpeg 8.1.3 on this machine) |
| `DesktopUiLiveTest` and `DownloadLiveTest` against the **real YouTube**: a real 82 s video downloaded (video+audio, merged to WebM VP9+Opus, 1072p, 33 MB), probed with ffprobe (`opus,audio` + `vp9,video`, duration 81.1 s), **played by mpv from the file**, the same video downloaded audio-only (1 audio stream, played), both deleted along with their thumbnail | pass | running |
| UI-driven flow: Download button -> quality chip -> confirm -> Library > Downloads -> row reaches COMPLETED -> Play (session is `offline`, plan `Local`) -> Delete with confirmation removes the row and the file | pass | running |
| **Offline page**: a downloaded video opened with every extractor method and every HTTP request forbidden and counted | **0 extractor calls, 0 HTTP requests**; plays; "Downloaded" shown; saved title, 3 chapters, 2 saved comments, SponsorBlock marker; seeking into the stored sponsor segment skips | running |
| `:desktopApp:run` for 20 s, then SIGTERM | started with no stderr output, exited on SIGTERM (143), no `hs_err` file, no orphan mpv or ffmpeg process | running |
| All live tests together (real YouTube, the three module servers, ffmpeg, libmpv) | net-okhttp 5/5, desktopApp 5/5, extractor-newpipe 17/18 (the one failure is the flaky comments test described in Known issues) | running |
| `:androidApp:assembleDebug` | succeeds | compile-only |
| Android: foreground service, SAF folder copy, MediaMuxer merge, notifications, permission denial, SD card | compiles; **8 Robolectric tests** cover storage selection, container rules, the progress notification and permission-less result notifications | compile + unit only |
| Android behaviour on a phone (service survival with the app closed, notification actions, SD card, airplane mode, storage full) | not run | not verified |

Offline test counts: shared 346 on the JVM (224 of them are common tests that also run on the Android host), androidApp 32, net-okhttp 27, player-mpv 64, extractor-newpipe 48, extractor-ytdlp 82. **Build note: `./gradlew test` does not run `:shared`'s tests in this project layout** (checked with `--dry-run`; the CLAUDE.md line "Offline tests: `./gradlew test`" therefore misses the largest suite). `./gradlew allTests`, or `:shared:jvmTest :shared:testAndroidHostTest` explicitly, run them. I did not edit CLAUDE.md.

### Numbers

- **Transfer speed, real YouTube, chunked 10 MB ranges** (same machine and connection as task 07's measurement, where open-ended requests ran at 5.8 Mbit/s): 34.3 Mbit/s average / 84.3 peak for a 33 MB 1072p stream, and 36.8 / 87.6 on a second video (39 samples each, taken from the manager's own speed estimate). Whole job, including resolving the streams, the merge and the page extras: 33 MB in 10.0-10.6 s (about 25-27 Mbit/s end to end); an 8 MB 536p job took 8.0 s, where fixed costs dominate.
- **Audio only:** 1.0-1.3 MB Opus/WebM in 5.2-5.4 s (also dominated by fixed costs: resolve, the page extras and 10 MB-range round trips).
- **ffmpeg merge** of a 33 MB WebM pair is part of the 10 s above; the merge itself is a stream copy and was not separately timed.

### Android merger evidence (the task asked for a decision backed by something I can verify)

| Option | What I checked | Finding |
|---|---|---|
| **ffmpeg-kit** (the spec's choice) | Maven Central's search index lists `com.arthenica:ffmpeg-kit-min` 6.0-2 (published 2023-09-13) and nine older versions; I requested the `.pom` and `.aar` of the newest, plus several version spellings, from `repo1.maven.org` | **HTTP 404 for every file**, and no `maven-metadata.xml`: the artifacts are listed but withdrawn. The project is retired. Not usable. |
| Maintained community ffmpeg | Searched Maven Central for other ffmpeg artifacts for Android | The only maintained one is `org.bytedeco:ffmpeg` 7.1-1.5.11 (Nov 2024): JNI libraries without a command line, **20.6 MB (arm64) and 23.1 MB (x86_64) per ABI, and no 32-bit ARM build (404)**. A merge would have to be written against libavformat through JavaCPP bindings, and it adds a third party's native code that I cannot run here. Not chosen. |
| **`MediaMuxer`** (chosen) | Android's "Supported media formats" page, Muxer column | H.264 -> MP4; VP9 -> WebM or MP4; AV1 -> MP4; AAC -> MP4; Opus -> Ogg, WebM, MP4 (Android 10+). That covers every pair the app downloads: H.264+AAC -> MP4, VP9+Opus -> WebM, AV1+Opus -> MP4, others -> MP4. Zero APK size, platform-maintained. The rule is encoded in `Containers.forAndroidMuxer` and unit-tested. |

I could not run `MediaMuxer` (no device or emulator in this environment): the choice rests on the vendor's documentation and on the artifact checks above, and the merger itself is compile-only. If a pair is rejected on a real device the download fails with the muxer's message and keeps the two streams.

## Files created/changed

**Core (shared, common code)**
- `downloads/{Models,DownloadRepository,DownloadManager,ChunkedFetch,Containers,Platform,OfflineCollector,Downloads}.kt`: queue, repository over `Download`/`DownloadOfflineData`, ranged fetching with resume and backoff, container rules, the interfaces platforms implement (`DownloadStorage`, `RangeFetcher`, `MediaMerger`, `DownloadNotifier`), the offline page collector, the holder the UI uses.
- `player/StreamLease.kt` (`StreamLeases`): **expiry and re-resolve logic extracted from `PlaybackSession`**, which now uses it too; `PlaybackSession.kt` (`startLocal`, offline client, no re-resolve for files), `PlaybackPlan.kt` (`Local`), `PlaybackAddon.kt` (`offline` flag), `modules/sponsorblock/SponsorBlockAddon.kt` (uses the stored segments offline).
- Schema: `4.sqm` (migration to v5), `Download.sq` (`planJson`, offline columns, new queries). Settings: 10 download settings and a `DOWNLOADS` section.
- UI: `ui/screens/DownloadsScreen.kt` (screen, quality dialog), `VideoScreen.kt` (Download button, offline mode), `Common.kt` (⋮ menu entry), `LibraryScreens.kt` (Downloads entry, Download all), `AppShell.kt` (delete-after-watch), `nav/Navigator.kt` (route), `DebugScreen.kt` (Diagnostics lines), `PlatformUi.kt` (+ folder picker actuals).
- `data/XdgPaths.kt` (videos folder).

**JVM implementations (`:net-okhttp`)**
- `OkHttpRangeFetcher`, `FileDownloadStorage`, `FfmpegMediaMerger`; tests for each.

**Desktop**
- `downloads/DesktopDownloads.kt` (storage provider, `notify-send` notifier, factory), `main.kt`.

**Android**
- `downloads/{AndroidMediaMerger,AndroidDownloadStorage (incl. SafDownloadStorage),DownloadNotifications,VelaDownloadService,AndroidDownloads}.kt`, `playback/{PlaybackRuntime,PlaybackSources,Media3PlayerController}.kt` (local files), `MainActivity.kt`, `AndroidManifest.xml` (data-sync foreground service and permission), `build.gradle.kts` + `libs.versions.toml` (documentfile).

**Player**
- `player-mpv/.../MpvOptions.kt`, `MpvPlayerController.kt` (`Local` plans, never routed through the proxy).

**Tests**
- shared: `downloads/DownloadManagerTest` (+ `Fakes`), `ContainersTest`, `PlaybackSessionTest` (offline), `XdgVideosDirTest`, `JvmDatabaseFactoryTest` (v4->v5), `SettingEditingTest`.
- net-okhttp: `OkHttpRangeFetcherTest`, `FileDownloadStorageTest`, `FfmpegMediaMergerTest`. androidApp: `AndroidDownloadsTest`. desktopApp liveTest: `DownloadLiveTest`.

## Decisions the spec didn't dictate

- **The partial file is the progress.** The next range always starts at the partial file's size; a finished stream is renamed from `.part` to its final name (that rename is what a resume treats as "done"). No byte counter has to be kept in sync with the disk, which is why the process-death test passes by construction.
- **Every run resolves fresh URLs; the plan stores itags, not URLs or headers.** URLs and request headers (which can include cookies once login exists) are not persisted. A resume finds the same streams again by itag, then by codec and height; if they are gone it starts that stream over and says so.
- **Working area + publish.** Everything is written in a local working folder and a finished file is `publish`ed to the viewer's folder: a rename on the same file system (Linux and Android app folder), a copy into the tree for an Android SAF folder. This keeps partial files out of the folder the viewer sees and makes SAF (which cannot append reliably) workable.
- **Foreground service, not WorkManager** (Android). Downloads are long, user-started and need a live progress notification with Pause and Cancel. WorkManager's long-running work would run this same foreground service underneath while adding a second scheduler and a second place that persists the queue. The service (`dataSync` type) only keeps the process alive and stops itself when the queue is empty; the manager is the one in `PlaybackRuntime`, shared with the UI. Android 15 caps `dataSync` services at roughly six hours a day; `onTimeout` pauses the queue cleanly.
- **Android merger: `MediaMuxer`** (evidence above), behind `MediaMerger`. The merger says which containers it can write and picks the container itself (`containerFor`), so the download's file name and format always agree.
- **Container rules:** H.264+AAC -> mp4, VP9/AV1+Opus -> webm, the mixed pairs -> mkv on Linux (ffmpeg); on Android per the muxer table (AV1+Opus goes to MP4).
- **A downloaded video is always played from its file and shown from its saved page.** Not "when offline": it never asks the network for the video, info, related or comments, which is what makes "opening it offline never calls the network" true by construction (and tested with all calls forbidden). If it was already streaming online when opened from Downloads, the page switches to the file.
- **Audio-only files get ` (audio)` in the name**, so the two downloads of one video never look like duplicates. Audio-only keeps the original container by default (`.m4a` for AAC, `.webm` for Opus); turning that off remuxes Opus to `.opus` (Ogg) where the merger can (ffmpeg, Android 10+), else the original is kept.
- **Streams are chosen with the playback selector** (all codecs allowed, the viewer's height cap, no HLS) so playback and download agree on what "720p" means; the quality dialog sets a **cap** (best at or below it), not an exact height.
- **Wi-Fi-only** holds queued downloads on metered or offline connections and stops running ones when the connection stops qualifying (they keep their bytes and return to the queue). Desktop has no connection-type detection yet (its monitor is fixed to unmetered), so the setting has no effect there.
- **Retry policy:** transient failures (connection errors, 5xx, 429) retry per chunk up to 5 times with 1/2/4/8/16 s waits; the budget resets after each good chunk; then the download is FAILED and the viewer can Retry. A dead URL is a separate budget (3 fresh resolves).
- **Delete after watching** removes a finished download when it plays to the end (off by default). **Storage cap** is displayed against usage only, as asked.
- **Saved extras are a snapshot at download time**: comments are the first pages (default 2, no replies), SponsorBlock segments go into the same store the player uses (so they work offline through the normal add-on), DeArrow's title and thumbnail are saved with the download.
- **A second download with the same file name gets `(1)`**, never overwriting.
- **Migration to schema v5** adds a resume-plan column and the offline-page columns by `ALTER TABLE`; the migration test builds the old shape by dropping those columns.

## Known issues / unverified assumptions

- **Nothing Android ran on a device.** The foreground service, notification actions, permission denial, SAF folder (including the final copy into it), `MediaMuxer` merging, Picture-in-Picture interplay and process restarts are compile-only plus Robolectric unit tests of the pure parts. Whether a `START_STICKY` restart of the service after the process dies is allowed to go foreground on Android 12+ is an assumption.
- **SAF folders:** a finished file is copied into the tree, so it briefly needs the space twice; `freeBytes` is unknown for a SAF tree; if the copy fails the merged file stays in the working area and a retry publishes it (see below) instead of downloading again.
- **MediaMuxer may reject some pairs on some devices** (the documentation table is the only evidence); then the download FAILS with the muxer's message and keeps both streams.
- **Pause during MERGING or publishing is not possible** (it is quick, and there is no partial state to keep).
- **HLS-only and live videos cannot be downloaded** (fails with "Nothing to download").
- **The quality dialog offers a fixed list of caps**, not the heights that exist for the video (that would need a request before the dialog); asking for 4K on a 1080p video gives 1080p.
- **The Downloads list has no thumbnails**, only the saved thumbnail on the video page of a downloaded video (it is saved and stored, but cards still use the network image URL).
- **Notifications on desktop depend on `notify-send`** (present here; I cannot see whether the pop-ups appear). Without it nothing breaks; Diagnostics says so.
- **Interrupted `.part` files after a crash** are trusted by size. A file whose last chunk was half-written is detected only by the final size check against the server's total, not by a checksum.
- **A comment page limit of 0 to 10 pages** is saved; replies are not.
- **A pre-existing live test is flaky and not caused by this work:** `LiveNewPipeTest.commentsPagingAndReplies` failed once in a full live run and then passed 3 of 3 when rerun alone: YouTube reorders the "top" comments between the test's two requests ("Comment ... is not on the first page; pass Comment.repliesContinuation", the documented behaviour of `replies()` without a token). I did not touch the comments code.
- **Deleting a download whose folder was changed since** works (the stored URI is absolute), but the "used" figure counts what the database knows, not the folder.
- **Moving the download folder does not move existing files.**
- **Auto-download rules** (new videos, Watch Later) are P2 and not built.

## Manual test checklist

**Linux** (`./gradlew :desktopApp:run`; `ffmpeg` and `ffprobe` are installed here)
1. Open any video. Press **Download** (next to Subscribe): pick 720p and press Download. Library > Downloads shows it with a progress bar, a percentage and a speed in MB/s. A pop-up should appear when it finishes (`notify-send`); tell me if it does not.
2. When it is done, find the file in `~/Videos/Vela` (a name like `Title [id].webm` or `.mp4`) and open it in another player (e.g. Totem / VLC): picture and sound, and the duration is right. Tell me which container you got and whether other apps play it.
3. Press **Play** on the row: the video page says **Downloaded**, plays the file, and still shows the description, chapters, saved comments and SponsorBlock markers. **Disconnect the network first** (or turn Wi-Fi off) to check it never complains.
4. Start a long download (a 20-30 minute video, 1080p), **pause** it, then **resume**: it continues (the percentage does not go back to 0). Then start it again and **kill the app** (`pkill -f vela` or close the terminal) mid-way, start the app again: it resumes from where it was without starting over (look at the speed and percentage).
5. Download two audio-only videos: files end in ` (audio).webm` or `.m4a`; they play in another player. In Settings > Downloads turn off "keep the original container" and download an Opus one: it should end in `.opus`.
6. Change the folder: Downloads > Change folder, pick another folder; a new download goes there. "Use default" returns to `~/Videos/Vela`.
7. Playlists / Watch Later: open one with several videos and press **Download all**, choose a quality: they queue (two at a time by default; change "Simultaneous downloads" in Settings > Downloads).
8. Delete a download (Delete, confirm): the row, the file and its thumbnail are gone. Turn on "Delete a download after watching it to the end", play a short one to the end: it disappears.
9. Remove ffmpeg from the PATH temporarily (or rename the binary) and start a video download: it should FAIL with "ffmpeg was not found ... install it", keep its two streams, and **Retry** after restoring ffmpeg must finish without downloading again. Settings > Diagnostics shows the same ffmpeg line.
10. Settings > Downloads: toggle Wi-Fi-only (no effect on desktop, see Known issues) and look at the other options for anything confusing.

**Android** (all of this is unrun; please be strict)
1. Download a video while the app is open: the notification shows progress with **Pause all** and **Cancel all**; it finishes with a "Download complete" notification. Check the file plays in the Downloads screen and in another app (Files / a player) if it is in a reachable folder.
2. **Close the app** (swipe it away) during a download: the notification stays and the download completes. Reopen: it shows as completed.
3. **Kill the app** from the app info screen mid-download: it should come back and continue (or at least be resumable with Resume).
4. Deny the notification permission (it is asked when the first download or video starts): downloads still run and finish, only the notifications are missing.
5. Folder: Downloads > Change folder > pick a folder on the **SD card**: new downloads end up there and play from there. Tell me if anything fails (the copy at the end, the permission after a reboot).
6. **Airplane mode** with a finished download open: it plays, shows its description, chapters, saved comments and SponsorBlock marks; nothing hangs waiting for the network.
7. Wi-Fi only (default): start a download on mobile data: it waits ("Waiting for Wi-Fi"); join Wi-Fi: it starts. Turn the setting off and it runs on mobile data.
8. Try 1080p H.264, 1080p VP9 and an AV1 video if you have one: report any "Could not merge" failure with the device model and Android version.
9. Fill the phone's storage (or choose a tiny folder) and download something large: it should FAIL with a clear message rather than crash; tell me what the message says.
