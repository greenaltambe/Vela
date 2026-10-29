# 41 – Captions, audio tracks, Linux hover/focus, video page layout, search cache

## 1. Summary
Captions work: the player's CC button is enabled when the video offers subtitles, opens a menu (Off, then each language, the uploader's before the automatic ones), and the chosen track is drawn over the picture. Videos with several soundtracks get an Audio track button (original first). On Linux, hovering the picture shows the controls and leaving hides them; hovering a video card shows a palette-gradient outline; click focus rings no longer appear (keyboard focus rings still do). On the video page the related videos sit below unless the window is at least 1000 dp wide, and the Comments button now sits under the description in every layout. "Download new videos automatically" shows only for subscribed channels. The search page keeps its results and scroll position when you return from a video.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| Phone: CC menu lists languages; picking English (auto-generated) shows subtitles over the picture; the Audio menu lists English (US) original, Indonesian, Bangla, Ukrainian, Russian, Hebrew, Dutch | seen | phone screenshots |
| Live: NewPipe gives 6 caption tracks for a test video (TTML); the same address with `fmt=vtt` answers `WEBVTT` | measured | network (temporary probe, removed) |
| Switching the soundtrack, hover and gradient border, controls on hover, focus-ring change, the 1000 dp video page layout, search cache, auto-download only when subscribed | not exercised | compile-only |

## 3. Files changed
`Models.kt` (`CaptionTrack`, `StreamSet.captions`, `AudioStream.trackName`), `StreamMapping.kt`, `PlaybackSession.kt` (captions, audio tracks), `Vtt.kt` (new), `PlayerView.kt` (CC and Audio menus, caption overlay, hover controls), `PlatformUi.kt` (`LocalTextFetcher`), `Modules.kt` (exposes the HTTP client), `AppShell.kt`, `Motion.kt` (focus ring, hover wash), `Components.kt` (card hover outline), `VideoScreen.kt`, `ChannelScreen.kt`, `SearchScreen.kt`, `SearchStore.kt` (new), icon packs (`audioTrack`)

## 4. Decisions
- Subtitles are drawn by the app from the WebVTT file (the same on Android and Linux). First attempt: merging the file into the Android player's media source failed with ERROR_CODE_FAILED_RUNTIME_CHECK, so it was dropped.
- NewPipe lists captions as TTML; the VTT address is the same URL with `fmt=vtt`.
- A soundtrack switch re-plans the video with that language's audio and resumes where it was.

## 5. Known issues
- Auto-generated captions are YouTube's rolling captions; the repeated first line is dropped but timing is the source's.
- The caption choice is kept for the video being watched only (not remembered across videos).

## 6. Manual test checklist
1. CC > a language: subtitles appear and follow playback; Off removes them. Audio track > another language: sound changes, picture continues.
2. Linux: hover a card (gradient outline), hover the player (controls), click buttons (no ring), Tab through controls (ring).
3. Narrow Linux window: related videos below the description; Comments under the description.
4. Search, open a result, go back: results and scroll position are there.
