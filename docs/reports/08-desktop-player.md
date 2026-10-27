# 08: desktop player (libmpv, software render API)

## Summary

`:player-mpv` is a pure-JVM libmpv binding (JNA) plus a full `PlayerController`. mpv renders into a Skia bitmap (`MPV_RENDER_API_TYPE_SW`),
so no native window embedding is needed. It plays 1080p60 VP9 + Opus through the unchanged `PlaybackSession`, `PlayerScreen` and stats
panel at 60.0 fps with 0 dropped frames, about 60% of one core. The default is the render API; the IPC fallback was **not built** because
step 1 worked. mpv's default HTTP behaviour is throttled by googlevideo (30 s of read-ahead in 20.4 s); a loopback chunking proxy
fixes it (1.2 s). All agent-side runs used `mpv-libs` 0.41.0-5.fc44 unpacked into a
scratch directory (no root). The owner then installed `mpv-libs` system-wide, ran the manual checklist and reported "it all seems to be working"
(a general statement, not per-item numbers). Without the package the app still starts and says there is no player.

## Environment (step 0)

| Fact | Value |
|---|---|
| `java -version` | OpenJDK Zulu 21.0.11 (so JNA; `java.lang.foreign` is final only in 22+) |
| `ldconfig -p \| grep -i mpv` | nothing |
| `mpv --version` | not installed |
| `$XDG_SESSION_TYPE` | wayland |
| `which vlc` | not installed |
| Audio | PipeWire 1.6.10 (wpctl) |
| GPU | AMD Cezanne (Vega); `/dev/dri/renderD128` present |
| Used for testing | `mpv-libs` 0.41.0-5.fc44 + `mujs`, `compat-lua-libs`, `vapoursynth-libs`, `libXScrnSaver`, `libXpresent`, extracted with `rpm2cpio` into the session scratch dir, loaded via `LD_LIBRARY_PATH` |
| ffmpeg here | `ffmpeg-free` 8.1.3: VP9, AV1 (dav1d), Opus, AAC, and H.264 through Cisco `libopenh264` (the `fedora-cisco-openh264` repo is enabled). No HEVC. |

## Verification

| Check | Result | Verified by |
|---|---|---|
| `./gradlew test` (shared 184, androidApp 24, extractor-newpipe 48, extractor-ytdlp 82, **player-mpv 56**) | all pass | running |
| `:androidApp:assembleDebug` | succeeds | compile-only (nothing Android-runtime changed; no dependency from androidApp on the new modules) |
| `:player-mpv:liveTest` without libmpv | 8 tests skipped with the reason, none fail | running |
| Live: video+audio 20 s steady state, pause/resume, seek to 300 s and back, 2x speed (`LiveMpvPlaybackTest`) | pass: 60.0 fps rendered, 0 dropped, seek resumed in 0.9-1.0 s, 2x advanced 8000 ms in 4 s | running (real libmpv, real googlevideo) |
| Live: dead URL (`expire=1`) with and without the proxy | both give `StreamExpiredOrForbidden`, HTTP 403, in 100-300 ms; state `ERROR` | running |
| Live: **`PlaybackSession` recovery**: first `streams()` returns dead URLs, second real ones | session re-resolved once (2 calls) and reached PLAYING | running (`LiveMpvSessionRecoveryTest`) |
| Live: audio-only plan with no frame sink, `stop()`, double `release()` | pass; `current-ao=pipewire` | running |
| Real window (`:desktopApp:liveWindow`, 45 s): real `PlayerScreen` + `DesktopPlayerHost` + `PlaybackSession`; resize, fullscreen, pause, seek, quality 720p, 1.5x, close via the `onCloseRequest` path | 60 fps mpv-rendered, 49-60 fps drawn to the window (mostly 59-60); exit code 0 | running |
| Compose UI test with real clicks (`DesktopUiLiveTest`): type query, click Search, click first result, play past 30 s, Pause, Play, 2.0x, 360p, Back | pass; 60.0 fps rendered over 28 s, quality 1920x1080@60 -> 640x360@30 | running |
| `:desktopApp:run` (the real `main.kt`) 40 s with libmpv, then SIGTERM | started, no exceptions, exited | running |
| `:desktopApp:run` 25 s without libmpv | started, printed `Vela: no video playback: libmpv could not be loaded (...)`, exited | running |
| SIGTERM to the JVM while playing 1080p60 fullscreen | JVM gone in about 1 s, exit 143, no `hs_err` file, no `mpv` process | running |
| What it looks like on screen, tearing, audio audibility | not observable by the agent | not verified by the agent |
| Manual checklist (picture, smoothness, audio, seek, speed, quality, resize, fullscreen, close) with system-installed `mpv-libs` | owner reported "it all seems to be working"; no per-item numbers (CPU/GPU, tearing) given | verified by owner, manually |

## Measurements

All with `LiveMpvMeasureTest` (`./gradlew :player-mpv:liveTest --tests '*LiveMpvMeasureTest*'`); CPU is the whole JVM process (JVM +
in-process libmpv threads) as a percentage of one core, 12 cores available. Video: Big Buck Bunny 4K60 upload, VP9 itag 303
1920x1080@60 (4.0 Mbit/s) + Opus.

**Throughput, 30 s of read-ahead, paused, 1080p60 (2 rounds):**

| Mode | Time to 30 s buffered | Effective rate |
|---|---|---|
| default mpv/ffmpeg (open-ended `Range: bytes=N-`) | 20.4 s, 20.3 s | 7.1 Mbit/s |
| through `ChunkingProxy` (10 MB bounded ranges) | 1.2 s, 1.3 s | 109-119 Mbit/s |

`curl` agrees: a bounded 10 MB range ran at 101 Mbit/s, open-ended at 4 Mbit/s (same as task 07's 5.8 vs 78-102). ffmpeg's HTTP protocol
has no chunk-size option (its options are `seekable`, `multiple_requests`, `offset`, `end_offset`... none bound requests), so a proxy was
the only way to get bounded ranges.

**CPU, 1080p60 VP9, 15 s each after 3 s settle (0 dropped frames, 60.0 fps in every row):**

| Config | CPU |
|---|---|
| software decode, render 1280x720, mpv defaults (zimg on) | 110% |
| software decode, render 1920x1080, zimg on | 95% |
| **software decode, 1280x720, `sws-allow-zimg=no`** (now the default) | **60%** |
| software decode, 1920x1080, `sws-allow-zimg=no` | 72-80% |
| `hwdec=auto-safe` (mpv picked `vulkan-copy`), 1280x720, zimg on | 93% |
| `hwdec=vaapi-copy`, 1280x720, zimg on | 114% |
| `hwdec=vaapi-copy`, 1920x1080, zimg off | 66% |

Other: 1080p24 VP9: 27% (1280x720) / 34% (1920x1080); with `vaapi-copy` 20%. 1080p60 **H.264** (6.9 Mbit/s, libopenh264): 98% (720p) /
118% (1080p). In the live window: 60-80% windowed at 1100x760, 45-62% at 700x500, **115-160% fullscreen 1920x1080** (includes Compose).

**Decision: software decode, zimg off.** In SW render mode hwdec has to copy frames back (`-copy`), and the colour conversion dominates,
so VAAPI saves little and adds a driver dependency. `hwdec` stays configurable (`MpvConfig.hwdec`).

**Not a usable number:** "avg render ms" in the logs is about one frame period (16.5 ms at 60 fps, 41 ms at 24 fps): `render()` is
paced by mpv, so it is not the cost of a frame. The CPU column is the real cost.

## Files created/changed

**New module `:player-mpv`** (pure JVM, `api(:shared)`, JNA 5.17.0; package `com.greenaltambe.vela.player.mpv`)
- `MpvClient.kt`: typed wrapper interfaces (`MpvBackend`, `MpvClient`, `MpvSoftwareRenderer`, `MpvEvent`), the seam for the fake native layer.
- `JnaMpv.kt`: JNA binding. Read/write lock around the native handle, so use-after-free and double shutdown are impossible; renderer closed before core.
- `MpvLibrary.kt`: finds libmpv (`-Dvela.libmpv`, `VELA_LIBMPV`, `libmpv.so.2`, `mpv`).
- `MpvOptions.kt`: pure plan -> `loadfile` (options, `audio-file`, headers, start, pause) and the init options.
- `MpvErrors.kt`: pure error mapping (+ URL redaction). `MpvStateMapper.kt`: pure property snapshot -> `PlayerState`. `MpvCodecs.kt`: `decoder-list` -> `PlaybackConstraints`.
- `ChunkingProxy.kt`: loopback range-chunking HTTP server. `SoftwareRenderLoop.kt`: render thread + `FrameSink`.
- `MpvPlayerController.kt`: the `PlayerController` (also `stats`, `setVolume`, `attachSink`, `setSurfaceSize`).
- Tests (56): `MpvOptionsTest` (+`MpvCodecsTest`), `MpvErrorsTest`, `MpvStateMapperTest`, `MpvPlayerControllerTest` (fake client, scripted events), `ChunkingProxyTest` (local range server), `Fakes.kt`.
- liveTest: `LiveMpvPlaybackTest`, `LiveMpvSessionRecoveryTest`, `LiveMpvMeasureTest`, `LiveMpvSupport.kt`.

**`:desktopApp`**
- New `playback/`: `DesktopPlaybackRuntime` (controller + `PlaybackSession`, one `close()`), `DesktopPlayerHost` (the `PlayerHost`), `SkiaFrameSink` (3 Skia bitmaps handed to mpv as native memory).
- `main.kt`: one database for settings **and** watch history (in-memory fallback), chain built here, libmpv failure -> no player, window close and a shutdown hook release mpv.
- `build.gradle.kts`: `:player-mpv`, `modules("jdk.httpserver")` for packaging, `liveTest` source set + `liveWindow` task. liveTest: `LiveWindow.kt`, `DesktopUiLiveTest.kt`.

**Build**: `settings.gradle.kts` (`:player-mpv`), `gradle/libs.versions.toml` (`jna`).

## Decisions the spec didn't dictate

- **JNA, not FFM**: JDK is 21. The binding is one file (`JnaMpv.kt`); moving to `java.lang.foreign` later touches only it.
- **Chunking via a loopback proxy** inside the process (JDK `HttpServer`, 127.0.0.1, ephemeral port), 10 MB chunks like Android. Bonus: it applies
  each stream's own headers (mpv has only one global header set), relays the upstream status so mpv's own error text names it, and reports mid-stream
  failures (a truncated stream looks like a clean EOF to mpv). HLS and chunking-off bypass it and use `http-header-fields` / `user-agent`.
- **Errors come from two places**: the proxy (exact status, immediately, per stream role, even if only the audio URL is dead) and mpv's `end-file`
  error + log lines (non-proxied). At most one error event per `load`. Dead statuses are the Android set: 401, 403, 404, 410, 416.
- **Decoders decide the plan**: `decoder-list` becomes `PlaybackConstraints` (here AVC, VP9, AV1; HLS only if H.264 exists), so a machine without H.264 never gets an AVC plan.
- **zimg off, software decode** (measured, above). `keep-open=yes` so ENDED is observable; `play()` after the end seeks to 0 like Android.
- **Render size = widget pixel size**; mpv letterboxes, Skia blits 1:1. Three bitmaps so the render thread never waits for the UI.
- **Everything runs off the caller's thread** (one control thread, one event thread, one render thread), so UI-thread calls never block on mpv. Position publishes at most every 250 ms.
- **mpv kept inert**: `config=no`, `load-scripts=no`, `ytdl=no`, no OSC, no input bindings.
- **Desktop now persists watch history** (same SQLite file as settings).
- **IPC fallback not built** (the task allowed this when step 1 works).

## Known issues / unverified assumptions

- **Libmpv is a system dependency** (`sudo dnf install mpv-libs`); nothing in the repo bundles it. Agent tests used a scratch copy; the owner's manual run used the system package.
- **Not observed by the agent**: the picture, tearing, audio. The owner's manual run found it working but gave no CPU/GPU figures, so the CPU numbers here are the agent's own measurements only. "Drawn" fps counts our blits to the window, not compositor presentation. Window-drawn dipped to 49-54 fps for single seconds around resize/fullscreen/quality changes.
- **The window runs under XWayland** (Compose Desktop uses AWT). Native Wayland (WLToolkit) untried.
- **Fullscreen is the costly case**: render size is the full 1920x1080 (115-160% CPU in the live window). Capping the render size to the video size and letting Skia upscale would cut it; not done.
- At 1.5x speed mpv renders ~90 fps while the window draws 60: wasted work, harmless here.
- `PlayerScreen` (shared debug UI) is not scrollable: at wide windows the 16:9 surface pushes Speed/Quality rows off-screen (my first UI test missed them and had to use a 640 px window). Not touched.
- **HLS through mpv is untested live** (the selector only picks it when no streams exist), and so is AV1 at 1080p60. Only VP9 and H.264 were played.
- The proxy fetches chunks one after another with no prefetch, retries a broken chunk twice, and has no limit on concurrent connections. Seeks open a new upstream range; measured seek-to-resume was 0.9-1.0 s.
- `bandwidthEstimateBps` is the proxy's last 10 MB chunk rate (or mpv's `cache-speed` with chunking off); `bufferedAheadMs` is `demuxer-cache-time - time-pos`.
- Volume: `setVolume(0..130)` is unit-tested against the fake only and **not wired to any UI**; not heard live.
- `loadfile ... replace -1 <options>` needs mpv >= 0.38 (the index argument); 0.41 was tested.
- Network type is `FixedNetworkMonitor` (UNMETERED) as before.
- JVM shutdown after SIGTERM was fast and crash-free, but I can't prove the hook's `release()` ran to completion (no output); the window-close path is the verified one.

## Manual test checklist

0. Install the library: `sudo dnf install mpv-libs` (optional: `mpv`). Then `./gradlew :desktopApp:run`.
1. Search "big buck bunny 60fps", click a result. Expect picture and sound within a few seconds; open **Stats**: resolution `1920x1080@60` (if the default 1080p cap applies), codec `vp9`, `buffered ahead` ~60 s, `dropped frames` ~0.
2. Let it play 2 minutes. Is 60 fps smooth, no tearing or judder? Is audio in sync and coming from the default PipeWire device (check `pavucontrol`/Settings > Sound shows "Vela")?
3. Seek with the slider (forward past what is buffered, backward). Should resume in about a second.
4. Speed buttons 0.5x ... 2.0x: pitch/sync sensible, position moves accordingly.
5. Quality row: pick 720p, then 360p, then Auto. Playback continues from the same position each time.
6. Pause/Play; let a video play to its end: it should show ENDED, and Play restarts it.
7. Resize the window (drag, maximize). Picture should keep aspect with black bars and stay smooth; try a very small window too.
8. Fullscreen: Compose's default window has no fullscreen button here; use your GNOME shortcut (Super+Up maximizes). Tell me whether `liveWindow`'s fullscreen step (`./gradlew :desktopApp:liveWindow`) looked right, and its CPU.
9. CPU/GPU: open `htop`/Resources while playing 1080p60 windowed and maximized; report CPU% for the `java` process, and GPU use (should be light; decode is software).
10. Close the window with the X button **while playing**: it must close at once; check `pgrep -a java` and `pgrep mpv` show nothing left, and no `hs_err_pid*.log` in the repo.
11. Back button during playback, then open another video: no stale audio from the previous one.
12. Without the package (before step 0), the app should start and say "Desktop player not implemented", with `Vela: no video playback: ...` on stderr.
