# 118 Cast to TV (DLNA/UPnP)

## 1. Summary
New pure-JVM module `:cast-dlna` (SSDP discovery, UPnP AVTransport SOAP, no new dependencies) plus a shared `CastManager`/`SwitchingController`. A cast button in the player (next to captions) opens a device sheet; picking a TV re-plans the video as one muxed-AVC or HLS URL, hands that URL to the TV and drives it (play, pause, seek, position). Works on Android and desktop. NOT tested against a real TV.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` (incl. new StreamSelector cast tests, ThemeSourceScanTest) | pass | running |
| `:cast-dlna:test` (10 tests: description parsing, SSDP header, time format, XML escaping, load/seek/pause/stop/ended/lost-TV against a fake TV) | pass | running (fake TV, no network) |
| `:androidApp:compileDebugKotlin`, `:desktopApp:compileKotlin` | pass | compile-only |
| Discovery, playback on a real TV, Android multicast lock | not run | unverified |

## 3. Files created/changed
- New: `cast-dlna/` (Upnp.kt, Discovery.kt, DlnaController.kt, DlnaBackend.kt, tests); `shared/.../cast/Cast.kt`; `shared/.../ui/player/CastSheet.kt`
- Shared: `StreamSelector.kt` (`PlaybackConstraints.castOnly`), `PlaybackSession.kt` (`replan()`), `PlayerHost.kt` (`cast`), `PlayerView.kt` (button, sheet; captions bar now uses a theme shape, fixing a ThemeSourceScanTest failure from report 100), `AppIcons.kt` + Material icon sets (`cast`)
- Android: `PlaybackRuntime.kt`, `AndroidPlayerHost.kt`, `MainActivity.kt` (two reads of the controller state), `AndroidManifest.xml` (ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE), `build.gradle.kts`
- Desktop: `DesktopPlaybackRuntime.kt`, `DesktopPlayerHost.kt`, `build.gradle.kts`; `settings.gradle.kts`

## 4. Decisions the spec didn't dictate
- DLNA/UPnP first (owner choice), single-URL streams only (owner choice): muxed AVC (usually up to 720p), else HLS, else audio-only. No local merging proxy.
- Casting forces AVC (TVs rarely decode VP9/AV1 over DLNA).
- Streams needing Cookie/Authorization headers are refused with a message (a TV cannot send headers).
- Speed, volume and boost are ignored while casting (AVTransport has none).
- Local player is stopped when casting starts; "Stop casting" resumes locally at the TV's position.

## 5. Known issues / unverified assumptions
- Whether TVs accept googlevideo URLs with only the URL (no User-Agent) is unmeasured. URLs are IP-bound, so phone/PC and TV must share a public IP (same Wi-Fi is fine; VPN on the phone breaks it).
- Many TVs only report position after Play; seek-on-start waits up to 4 s for PLAYING.
- Android 17+ "local network" permission may be needed later; not handled.
- While casting on Android the media notification/lock-screen controls follow the local (stopped) player, not the TV.
- Sponsorblock skips, history and next-video autoplay follow the TV's reported position/state; not tested.
- No Chromecast (Google Cast) support, by design.

## 6. Manual test checklist
1. Phone and TV on the same Wi-Fi, TV's DLNA/"media renderer"/screen-share enabled. Play a video, tap the cast icon (top right / bottom row).
2. The TV appears within ~6 s. Tap it: the video starts on the TV near the same position; the phone goes quiet.
3. Pause/play and drag the seek bar: the TV follows. Watch the position keep updating.
4. Try a video with no 360p/720p muxed stream (HLS or "no stream a TV can play" message).
5. Cast icon > Stop casting: playback resumes on the phone at the TV's position.
6. Repeat on desktop (Linux) with the same TV.
7. If the TV shows an error or nothing happens, send me the TV model and the message shown in the sheet.
