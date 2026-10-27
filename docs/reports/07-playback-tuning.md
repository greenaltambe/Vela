# 07: playback tuning, persistent settings, settings screen

## Summary

Buffering at 4K is explained and fixed. googlevideo throttles open-ended requests to about 5.8 Mbit/s, below a 4K stream's
bitrate, while bounded 10 MB ranges run at 78-102 Mbit/s. Progressive playback now goes through a chunked data source.
Quality is also capped per connection type (default 1080p on Wi-Fi, 720p on mobile data) and the player has a stats panel.
Settings persist in SQLite on both platforms and a schema-driven settings screen is reachable from the debug screen.
While verifying, one real yt-dlp-tier bug turned up and was fixed: the TV client's URLs return 403 until about 4 s after they
are issued.

## Verification

| Check | Result | Verified by |
|---|---|---|
| Offline tests: shared 184, androidApp 24, extractor-newpipe 48, extractor-ytdlp 82 | all pass | running |
| `liveTest`: extractor-newpipe 13 (incl. throughput), extractor-ytdlp 11 | all pass | running (real network) |
| Throughput measurement (see below) | open-ended 5.8 Mbit/s vs chunked 78-102 Mbit/s | running (real network) |
| `ChunkedDataSource` against a local Range-capable server (Robolectric, real `DefaultHttpDataSource`) | 12 tests pass | running |
| `ChunkedDataSource` against real googlevideo (one-off, test deleted afterwards) | 95.5-99.3 Mbit/s vs 5.8-5.9 plain, 20 MB | running (real network) |
| Settings written in one "run" read in the next (file DB, XDG path) | pass | running |
| `:desktopApp:run` (40 s, `XDG_DATA_HOME` pointed at a scratch dir) | started, no exceptions, created `vela/vela.db` (57 KB) there | running |
| `:androidApp:assembleDebug` | succeeds | compile-only |
| Network monitor, load control, bandwidth meter, stats collection, stats panel, settings screen UI, notification/permission wiring | compiles | compile-only |
| Behaviour on a phone: Wi-Fi vs mobile resolution, buffering, seeking with chunks, persistence across kill | not run | compile-only |

## Throughput measurements

Test: `extractor-newpipe/src/liveTest/.../LiveThroughputTest.kt` (liveTest source set, not part of `check`). It downloads 20 MB
of a real 1080p AVC stream (itag 137, NewPipe-resolved, 80.9 MB long) over `HttpURLConnection`, the same HTTP stack
`DefaultHttpDataSource` uses. Three rounds, rotating the order of the modes. Two full runs:

| Mode | Run 1 mean (min-max) | Run 2 mean (min-max) |
|---|---|---|
| open-ended, no `Range` header (what ExoPlayer sends from position 0) | 5.7 (5.6-5.8) Mbit/s | 5.8 (5.7-5.9) |
| open-ended, `Range: bytes=0-` | 5.7 (5.6-5.8) | 5.7 (5.4-5.9) |
| 2 x 10 MB, `Range` header | 71.4 (44.5-86.4) | 92.6 (78.5-102.3) |
| 2 x 10 MB, `&range=` URL parameter | 83.6 (77.8-87.4) | 96.7 (92.8-100.6) |

Time to first byte was 20-180 ms in every mode, so chunking costs nothing noticeable. A 20 MB open-ended download took
about 29 s versus about 2 s chunked.

Conclusion: chunked ranges are not worse, they are 12-17x faster, so the chunked data source was implemented. 5.8 Mbit/s is
below the bitrate of the 4K VP9 stream the old "best" default picked (about 9 Mbit/s), which is the buffering you saw.
The `Range` header variant was chosen over `&range=`: it is within noise of it, and does not depend on the URL shape.

## Files created/changed

**Shared (commonMain)**
- New: `core/network/NetworkMonitor.kt` (`NetworkType`, `NetworkMonitor`, `FixedNetworkMonitor`),
  `core/settings/SettingEditing.kt`, `player/PlaybackStats.kt`, `ui/SettingsScreen.kt`.
- Changed: `core/settings/Settings.kt` (`maxQuality` replaced by `maxQualityUnmetered`/`maxQualityMetered`),
  `core/settings/SettingsResolver.kt` (`hasValue`), `player/StreamSelector.kt` (network constraint, per-network caps),
  `player/PlaybackSession.kt` (network monitor, `networkType`, wait for `availableAt`), `player/PlayerHost.kt` (`stats`),
  `extractor/Models.kt` (`StreamSet.availableAt`), `ui/PlayerScreen.kt` (stats panel, "Auto" quality button),
  `ui/DebugScreen.kt` (Settings entry), `App.kt`.

**Shared (jvmMain)**: new `data/XdgPaths.kt`.

**Android**
- New: `playback/ChunkedDataSource.kt`, `playback/AndroidNetworkMonitor.kt`.
- Changed: `playback/PlaybackSources.kt` (chunked progressive sources, `streamingLoadControl()`), `playback/Media3PlayerController.kt`
  (stats, dropped frames), `playback/PlaybackRuntime.kt` (SQLite settings, network monitor, bandwidth meter, load control),
  `playback/AndroidPlayerHost.kt`, `AndroidManifest.xml` (`ACCESS_NETWORK_STATE`), `androidApp/build.gradle.kts`.

**Desktop**: `desktopApp/.../main.kt` (settings from `$XDG_DATA_HOME/vela/vela.db`, in-memory fallback if it cannot be opened).

**yt-dlp tier**: `YtDlpMapping.kt` (`availableAt` from `available_at`).

**Tests**: `StreamSelectorTest`, `PlaybackSessionTest`, new `SettingEditingTest`, `PersistentSettingsTest` (jvm),
`ChunkedDataSourceTest` (Robolectric), `PlaybackMappingTest` (+ network classification), `YtDlpMappingTest`,
live: `LiveThroughputTest`, `LiveYtDlpTest`.

**Build/docs**: `gradle/libs.versions.toml` (Robolectric 4.17), `CLAUDE.md` (new), this report.

## Decisions the spec didn't dictate

- **`playback.maxQuality` was replaced**, not kept. The two per-network settings already express a cap, and "Best available"
  is one of their choices. Defaults: 1080p Wi-Fi/unmetered, 720p mobile/metered. There is nothing to migrate.
- **The quality menu's choice is an explicit cap** that beats the per-network settings for that playback ("Auto" clears it).
  Offline counts as metered.
- **Network changes mid-playback are exposed, not acted on**: `PlaybackSession.networkType` (shown in the stats panel).
  The next plan (start, refresh, quality-menu action) uses the connection at that moment.
- **Chunk size 10 MB**, `Range` header, one request after another on one connection source. Applied to progressive sources
  only; HLS segments are small and left alone.
- **Load control**: min buffer 30 s, max 60 s, 2 s to start, 5 s to resume after a stall, 10 s back buffer kept, byte caps
  left on. Reasoning is in `streamingLoadControl()`'s KDoc: the 30 s/60 s gap makes the radio load in bursts; the size caps
  protect the Java heap at 4K (about 70 MB per minute). ExoPlayer's defaults are 50 s/50 s.
- **Settings screen controls come from the schema only**: boolean = switch, `choices` = buttons, `range` = slider (snapped
  to `step`), anything else = text. Writes are validated against codec, choices and range; invalid input writes nothing.
  Global layer only; per-channel overrides made elsewhere are untouched by Reset.
- **`StreamSet.availableAt` and a bounded wait** (at most 10 s) in the session before the first request, on start and refresh.
- **Desktop falls back to in-memory settings** (with a stderr line) rather than not starting if the database cannot be opened.
- **Android settings and history now share one SQLite database** (`vela.db`), opened lazily.
- **Robolectric (4.17, SDK 34)** added as a test dependency, to test the data source with the real Media3 classes.

## Known issues / unverified assumptions

- **yt-dlp tier, found while testing:** with a JS runtime enabled, yt-dlp returns TV-client (`TVHTML5`) URLs that answer 403
  for roughly the first 2-4 s. Without a runtime it returns different-client URLs (`VISIONOS`) that work at once. Measured: with
  `--js-runtimes node:...` 403 on 140/251/313 immediately after resolving, 206 after 2 s; without the flag 206 immediately.
  Fixed by honouring `available_at`. The earlier live test only passed because it happened to use format 139 (a
  different client).
- **Chunking with seeks is only tested against a local server.** Open at an arbitrary position, bounded `DataSpec` length,
  clamped length, exact multiples, 416 at the end, and a server that ignores `Range` are covered; ExoPlayer's real
  seek/reopen pattern on a phone is not.
- A server that ignores `Range` costs one extra request (the probe is closed and the request repeated unbounded).
  Not observed with googlevideo.
- The bandwidth estimate in the stats panel comes from ExoPlayer's `DefaultBandwidthMeter` and starts from its initial
  guess until enough transfers have been measured; early values are not real measurements.
- `httpStatusOf` (HTTP status buried in an ExoPlayer exception) is not unit-tested with a real `InvalidResponseCodeException`.
- The throughput numbers are from one machine and one connection (about 100 Mbit/s). The throttling ratio is what
  matters: your phone's real link will cap the chunked speed lower.
- AV1 is only offered when a hardware decoder exists (unchanged), and there is still no UI for audio language.
- The stats panel's "client/tier" is the extractor client label of the current streams (`newpipe`, `yt-dlp`); the tier
  history is in the diagnostics list beneath it.

## Manual test checklist

1. Install the debug build and open a search result on **Wi-Fi**. Open Stats. Expect `network: UNMETERED` and a resolution of
   1080p or lower; note the resolution, `bandwidth estimate`, and `buffered ahead` once it settles.
2. Turn Wi-Fi off, use **mobile data**, open the same video. Expect `network: METERED` and 720p or lower. Note the resolution.
3. Switch network while a video is playing. Expect the quality **not** to change, and the Stats `network:` line to change.
4. Pick a higher quality (for example 2160p) from the Quality row on mobile data: it must play (explicit pick beats the cap).
   Tap Auto: it returns to the cap on the next load.
5. Compare startup and buffering against before: play a 1080p video on your slow connection. Report whether it still stalls and
   what `buffered ahead` and `dropped frames` show during a stall.
6. Seek around (forward past what is buffered, backward, to near the end). Each seek should resume within a couple of seconds
   and never error. This exercises the chunked reads and is the main thing that could not be tested on a phone.
7. Open Settings from the main screen. Change "Maximum quality on mobile data" to 480p, flip "Audio only" on and back off,
   edit "yt-dlp location", move the playback speed slider. Each change should apply instantly; "Reset" should return that one
   item to its default and grey itself out.
8. Search in the settings box for "codec", "wifi" and "data": the matching items should appear.
9. **Kill the app** (swipe it away from recents, then force-stop it in system settings), reopen, open Settings: every change from
   step 7 must still be there. Play a video and confirm the changed quality cap is honoured.
10. Background and lock-screen audio, resume position and expiry recovery from task 6 should behave as before; quickly recheck
    background audio and "leave a video and come back resumes where you left it".
11. On the desktop: change a setting, quit, restart; it persists. The database is at `~/.local/share/vela/vela.db`
    (or `$XDG_DATA_HOME/vela/vela.db`).
