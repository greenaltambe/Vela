# 50 - Player buttons, bottom-sheet menus, speeds, description sheet

## 1. Summary
Player buttons now squeeze/spring, glow and give a haptic on press; play/pause, mute, fullscreen etc. swap icons with a pop. Every player menu (captions, audio, quality, speed, sleep timer) is a modal bottom sheet with staggered rows. Speed offers 14 chips (0.25x-4x) plus a 0.05 slider with -/+. The description card is gone from the video page: the title (ending in "...more") opens a "Description" sheet with likes/views/published cards, the description, chapters, transcript, channel and tags.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `:shared:compileKotlinJvm`, `:androidApp:compileDebugKotlin`, `assembleMeasure` | pass | compile |
| `allTests` | 568/569; the failure is the existing `LookBuilderTest` raw-styling guard in the owner's uncommitted Scrubber.kt / ChaptersSheet.kt / PlayerSheet.kt, none in the new files | verified-by-running |
| Device (CPH2707): description sheet opens from title with stat cards, description, Chapters, Transcript cards | seen in screenshot | verified-by-running |
| Device: speed sheet, 2x chip selects, readout and slider follow, pause/play button toggles | seen in screenshots | verified-by-running |
| `PlayerControlsTest` (formatSpeed, SpeedChoices, groupedCount, publishedParts) | pass | verified-by-running |
| Device: captions, audio-track, quality and sleep-timer sheets (selected row + check, scrolling), description sheet channel card + tags, transcript page (English first, de-duplicated lines, current line highlighted) | seen in screenshots | verified-by-running |
| Device: landscape fix (below), haptic feel, animation smoothness, Stats-for-nerds row, chapters card, fullscreen/landscape sheets, desktop | not checked | unverified |

## 3. Files created/changed
- New: `ui/player/PlayerControls.kt` (PlayerHaptics, PlayerIconButton, SwapIcon, PlayerMenuSheet/Row, SpeedMenuContent, `reveal`), `ui/screens/DescriptionSheet.kt`.
- `ui/player/PlayerView.kt`: buttons -> PlayerIconButton/SwapIcon, dropdown menus -> sheets, animated seek badge, haptics on double-tap, hold and seek.
- `ui/screens/VideoScreen.kt`: description card removed; title block opens the sheet.
- `extractor/Models.kt` (`VideoInfo.tags`), `extractor-newpipe` StreamMapping, `extractor-ytdlp` YtDlpMapping + JsonAccess (`strings`), `innertube-auth` AltClient (`keywords`): tags.
- New test: `shared/src/commonTest/.../ui/player/PlayerControlsTest.kt` (speed labels, chip list, grouped counts, published date); the sheet reuses `compact()` from Ryd.kt for likes.
- `desktopApp/src/liveTest/.../DesktopUiLiveTest.kt`: "2.0x" -> "2×".

- Found on the device and fixed: turning the phone sideways while the video played in the mini player forced fullscreen and drew the fullscreen player into the mini card (stretched poster across the bar). `VideoScreen.kt` now treats fullscreen as belonging to the expanded page and leaves it when the page is not open. (Existing bug, not from this task.)
- Transcript: opens in English (hand-made before auto-generated), collapses the repeated lines of auto-generated captions (`dedupeCues`, tested).

- Landscape (checked on the device by rotating over adb, settings restored afterwards): the mini card is now placed inside the left/right system insets and the rail (it overlapped the rail by ~60 px), capped at 640 dp wide; opening the mini player (swipe up or tap) while sideways goes straight to fullscreen (`PlayerSheet.kt`); the poster image no longer shows in the letterbox bars beside a fullscreen video once it has played (`PlayerView.kt`).

- Video page round 2 (all seen on the device in portrait; landscape minimise seen in landscape): title block press area no longer clips the views text (`bleed` + small corners); new `SubscribeButton` (gradient fades to tonal, icon/label swap, haptics); Like/Dislike as one joined control with tight inner corners (`ActionChip(chipShape)`); Share / Copy link / Copy with timestamp unified into one Share button -> `ShareSheet` (share, share at time, copy, copy at time, open in browser); Download dialog -> bottom sheet (`DownloadDialog` keeps its signature and test tags); the description is now an in-page panel under the picture (`DescriptionPanel`, drag handle to close, closes/is closed by comments and chapters); player buttons sit on translucent discs (`ControlButton`) that deepen with the press; menu sheets open at full height (the Download button was cut off at half height); speed sheet redesigned (readout + caption, custom slider, round -/+, 4-column chip grid via `ChoiceChipGrid`).
- Landscape minimise: leaving fullscreen while sideways now means "minimise to the mini player" (back arrow, exit button, Back key, swipe down). Before, it showed the expanded page in landscape, where the 16:9 picture is taller than the screen (cropped, magnified, rail visible). The window leaves fullscreen only once the card has settled, and the card geometry no longer depends on fullscreen, so it does not jump.

- Round 3: Share sheet roomier (taller rows, icon discs, "Send / Copy / Elsewhere" groups); Save is now a bottom sheet (`SaveSheet`: account Watch later + local playlists, tap toggles, stays open); the description panel closes by dragging its handle OR its header row (and the transcript header). One fullscreen model: portrait page swipe up / expand button -> landscape fullscreen; in fullscreen, back arrow / exit button / swipe down / Back key -> portrait video page (Android locks portrait until the phone is held upright, then hands rotation back to the sensor); swipe down on the portrait page -> mini player. The sheet drag is disabled while fullscreen. Seen on the device: swipe down in fullscreen -> portrait page, swipe up on the page -> landscape fullscreen, back arrow -> portrait page, Save sheet, Share sheet, description swipe-to-dismiss.

- Round 4: player disc buttons have more room around the glyph (glyph 0.42x the button, disc 0.88x); captions button taps captions on/off, long-press opens the language sheet (`PlaybackSession.toggleCaptions`, also used by the `C` shortcut); comment total and subscriber count (see section 4); three-dot icon thinner; the bottom row shows time and chapter as two translucent pills with the fullscreen button at the end, more room above the seek bar; in fullscreen the controls sit inside a margin (gutter x1.5 sides, 12 dp top/bottom) instead of touching the screen edges. Verified: compiles (`assembleMeasure`, extractor modules), `allTests` 573/574 (the same `LookBuilderTest` failure). Not yet seen on the device.

- Round 5: in the small portrait player the seek bar lies on the picture's bottom edge and the time / chapter pills / fullscreen button sit just above it (16 dp + 12 dp lower than before), with a clear gap below the play buttons. Tapping the time swaps elapsed for time left ("-18:32 / 19:27"), shown on the device; the choice is kept across videos until the app closes; live streams do not toggle.

## 4. Decisions the spec didn't dictate
- Haptics use Compose's `HapticFeedbackType` (the system touch-feedback setting still governs); no new setting.
- Keyboard speed stepping (`steppedSpeed`, `,`/`.`) keeps the old 0.25-2x ladder (a test pins it); the sheet reaches 4x (the settings schema already allowed 4.0).
- No "Note" card (no such feature). Transcript is built from the video's caption tracks (WebVTT), not a separate source. Tapping a tag searches it.
- Subscriber count: `VideoInfo.subscriberCount` (NewPipe `uploaderSubscriberCount`, yt-dlp `channel_follower_count`), shown under the channel name on the video page when the tier reports it. Comment total: `CommentsPage.totalCount` (NewPipe `CommentsInfo.commentsCount`, first page only), shown on the Comments row and panel header instead of the loaded count; falls back to "20+" when the tier gives none.
- "Captions on" uses the language last picked this session (else the first human-made track, else the first). It is not persisted across app restarts (no setting exists).
- The three-dot video menu icon was already three horizontal dots in every icon pack; I made it 20 dp and the quieter text colour so it reads thinner. If you meant something else, tell me which screen.

## 5. Known issues / unverified assumptions
- `./gradlew :desktopApp:compileLiveTestKotlin` already fails on `AgeRestrictedWiringLiveTest.kt:36` (BinaryLocator vs LogLineSink); unrelated and not part of `check`.
- Tags depend on the extractor tier returning them; none shown if empty. Transcript needs a caption track the app can fetch.
- On a quick `adb input tap` the controls may be hidden (they auto-hide after 3 s), so a tap just reveals them; real use is unaffected.

## 6. Manual test checklist
1. Open a video, tap play/pause, prev/next, mute, fullscreen: each should squeeze, glow, vibrate lightly, and icons should pop when they change.
2. Tap speed: pick chips (try 3x, 4x), drag the slider, use the arrows; check the speed really changes. Hold the picture for fast playback (haptic on hold).
3. Open captions, quality, sleep timer (and audio on a multi-language video): each should be a bottom sheet; choose an item, it should close.
4. Tap the title: check Likes/Views/Published, the description (links, timestamps, See more), Chapters (opens the chapter panel), Transcript (lines follow playback, tap seeks), channel card, tags (tap searches).
5. (The phone dropped off wireless debugging after the checks above, so nothing beyond item 2 of the table was re-checked.)
6. Play a video, minimise it to the mini player, then turn the phone sideways: the mini card must stay a normal bar (not stretched), and expanding it then rotating should still go fullscreen.
7. Phone sideways with a video minimised: swipe up on the mini card (and tap it): it should open in fullscreen with black bars beside a 16:9 picture, not the home screen showing through; the card should sit clear of the left rail.
8. Video page: tap the title block (no cut-off text), Subscribe / Subscribed, Like+Dislike pair, Share (sheet), Download (sheet, button visible), title -> description panel under the video. Player: buttons on discs; speed sheet.
9. Sideways in fullscreen: back arrow, exit-fullscreen button and the Back key must all collapse to the mini card.
10. Hold the phone sideways on the video page: it should go fullscreen as before; leave fullscreen with the arrow while still sideways: it must stay on the portrait page until you hold the phone upright. Expand button in portrait: should enter fullscreen.
11. Try the sheets in fullscreen/landscape and with Reduce motion on.
12. Captions button: tap turns captions on/off in the language you last chose; hold opens the language list.
13. Video page: channel row shows subscribers; Comments row/panel shows the real total. Portrait player bottom row: time and chapter pills, then the seek bar. Fullscreen: buttons have a margin from the screen edges.
14. Tap the time pill in the player: it toggles elapsed / remaining. Check the seek bar drags easily from its bottom-edge position.
