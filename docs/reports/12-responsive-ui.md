# 12: usable, responsive UI (phone first, then tablet/desktop)

## Summary

The UI is rebuilt around a window-size system and a set of reusable, token-driven components, so it works at 320 dp and at 1280 dp. Six defects from the owner's screenshots are fixed: cards are vertical on phones (title no longer one character wide), the permanent search field is replaced by a proper top bar and a full search view, nav labels fit ("Subs"), the player is a pinned 16:9 picture with overlay controls (custom seek bar, menus instead of chip rows), and the stale "previous video" state is fixed at its source in `PlaybackSession`. Verification is mechanical and visual: 52 screenshots at six sizes (reviewed by me, with fixes made because of them) plus assertions for overflow, text size, 48 dp touch targets, card title width, nav labels and contrast. **Nothing was run on a phone**: insets, gestures, rotation and Picture-in-Picture are compile-only.

## Verification

| Check | Result | Verified by |
|---|---|---|
| Offline tests: shared 359 on the JVM (237 common ones also on the Android host), desktopApp 16 (the UI suite below), androidApp 32, net-okhttp 27, player-mpv 64, extractor-newpipe 48, extractor-ytdlp 82 | all pass | running |
| `ScreenshotsTest`: 14 tests render 52 PNGs (every key screen at 320x640, 360x800, 412x915, 800x1280, 1280x800 and landscape 800x360; Pure Black at 360x800 and 1280x800) with long titles, long channel names, 1.2 billion views, CJK text, live / short / downloaded / half-watched cards, empty search, failed and queued downloads | pass; PNGs in `docs/reports/12-screens/` | running (real Compose rendering, `captureToImage`) |
| `LayoutInvariantsTest`, 10 screens x 6 sizes (light) and 3 sizes (Pure Black): nothing wider than the window; no text overflowing its width; no text under 12 sp; every tappable at least 48 dp (touch area); every card title at least 120 dp wide; bottom-bar labels whole (one line, not ellipsized); text contrast 3:1 against the page | pass | running |
| Layout logic: size-class breakpoints, gutters from tokens, grid columns never below the minimum card width, seek-bar geometry (thumb wholly inside at both ends, tap and thumb share one mapping), "3 days ago" formatting, search view replaces rather than stacks | 10 tests pass | running |
| Player state per video (the stale-frame defect): starting another video unloads the old one at once (state is back to idle: no old position, duration or frame), a failed resolve does not leave the old video, rapid switching ends on the last video only | 3 new tests pass | running |
| `:desktopApp:liveTest`, updated for the new UI, real YouTube + libmpv + ffmpeg: search from the top bar, open, play (53 fps), shortcuts, comments, subscribe, feed; player controls through the overlay and menus; speed 2.0x and quality 360p from the menus; SponsorBlock skip, **Undo**, prompt, DeArrow toggle; download from the page, Downloads list, offline playback, delete; offline page with all network calls forbidden (0 extractor calls, 0 HTTP) | 5/5 pass | running |
| `:desktopApp:run` 20 s then SIGTERM | exits, nothing on stderr, no `hs_err` | running |
| `:androidApp:assembleDebug` | succeeds | compile-only |
| Android: edge-to-edge insets, status/navigation/gesture bars and cutouts, rotation into fullscreen and back, immersive mode, predictive back, Picture-in-Picture with the new player | compiles | **compile-only** |
| Nothing overlaps the system insets | **not asserted**: a desktop test has no system bars. The shell applies `safeDrawing` insets in code (top bar, side rail, bottom nav, video player) | not verified |

### Screenshots I reviewed and what changed because of them

I opened these (all under `docs/reports/12-screens/`): `home` at 360x800 and 1280x800 and 800x360, `video` at 320x640, 360x800, 800x360 and 1280x800 (light and Pure Black), `search` at 320x640, `settings` at 360x800 and 1280x800, `subscriptions` at 360x800 and 800x1280, `downloads` at 360x800, `channel` at 360x800, `library` at 360x800. The others were rendered and covered by the assertions, but I did not open each one.

| Seen | Changed |
|---|---|
| Vertical cards showed the channel truncated and the numbers cut off on one line | Channel on its own line, "views · age" under it |
| Video page showed an empty channel and no comments | That was my fixture marking the open video as downloaded; the fixture was wrong, but it exposed that an offline page with missing info showed a blank channel name: it now falls back to the video's own |
| Video at 320: the chapter title ran into the -10 s button | Chapter name is shown inside the player only on wide windows, under the player ("Chapter: ...") on phones |
| "Contribute (submit a segment or thumbnail)" wrapped and clipped | Label is "Contribute" |
| Settings: big gap under each group header; search field wider than the list; slider track not aligned | Header text sits at the bottom of its row; the search field has the list's width and edge |
| Search at 320: result text only ~93 dp wide beside the thumbnail and menu | Thumbnail 36% (was 40%), tighter gap: text now 120 dp; channel and numbers split onto two lines |
| 800x360 landscape: related videos squeezed to one word per line | Beside the player, MEDIUM windows get full-width thumbnails; EXPANDED uses rows |
| **Pure Black: titles, channel names and descriptions were black on black** | `VelaTheme` now wraps everything in a `Surface` that sets the content colour (found only by looking: it needed a new contrast assertion so it cannot return) |
| "more" / "Contribute" / "DeArrow title" buttons indented from the content edge | A `LinkButton` that starts at the edge |
| Library: playlist name truncated by Rename and Delete buttons | Moved into a ⋮ menu |
| Live-test failure, not a screenshot: the SponsorBlock "skipped / Undo" notice disappeared with the controls after 3 s | It has its own always-visible spot at the top of the picture |

Mechanical checks also found: Compose Desktop does not enforce Material's 48 dp touch minimum (Android does), so every IconButton, button and switch was 40 dp on desktop. The Look's `minTouchDp` now sets it on both platforms. My first overflow check was wrong (it flagged any text narrower than its constraint); it now compares line width to the laid-out width and ignores deliberate ellipsis.

## Files created/changed

**Layout system and tokens**
- `ui/layout/WindowSize.kt` (`WindowSizeClass`, `gutterFor`, `gridColumns`, locals); `ui/look/LookTokens.kt` (`mediumMinWidthDp`, gutters per class, `minTouchDp`, `minTextSp`, `gridMinCardDp`, `onMedia` / `mediaScrim` / `mediaBackground` colours), `LocalLook.kt` (`Vela.gutter`, `contentPadding`, `minTouch`, `icons`), `VelaTheme.kt` (content colour surface, touch minimum), `VelaIcons.kt` (own 27-icon `ImageVector` set behind `iconSetId`).

**Components** (`ui/components/`): `Components.kt` (VideoCard vertical/horizontal with LIVE, SHORT, duration, downloaded and watched-progress badges; top bar with back/search/overflow; bottom nav and side rail with icons and short labels; mini player with thumbnail, icon buttons and progress line; `ActionChip`, `LinkButton`, `Avatar`), `Scrubber.kt` (the custom seek/volume/slider control), `States.kt` (skeleton, empty state, error row with Retry), `Format.kt` (`formatAge`).

**Screens** (`ui/screens/`): `Lists.kt` (`VideoCollection`: one column of vertical cards on phones, an adaptive grid on wider windows, horizontal rows for lists; loading / empty / error states), Home, Search (full search view), Subscriptions (Shorts and Feed as grids), Library, Playlist, History, Channel, Downloads, Video (pinned player; second column beside it when wide; action row; collapsed description), Settings (`SettingsHost`, `SettingsScreen`), `ModuleWidgets.kt`; `ui/AppShell.kt` (size classes, one structure for all layouts, insets, predictive back); `ui/player/PlayerView.kt` (overlay controls, menus, bottom-sheet-style menus for speed/quality/sleep/stats), `Shortcuts.kt`; removed `PlayerPanel.kt` and `SeekBar.kt`.

**Player / data**: `PlaybackSession.kt` (unload the previous video on start), `VideoSummary.isLive` (NewPipe mapping), `HistoryLibrary.progress`, `Downloads.completedIds`, `Navigator.replace`.

**Android**: `MainActivity.kt` (`WindowControls`: immersive fullscreen, landscape on entering, rotating while a video plays enters fullscreen, portrait leaves), `AndroidManifest.xml` (`enableOnBackInvokedCallback`). **Desktop**: `main.kt` (`LocalVolumeSlider`).

**Tests**: `desktopApp/src/test/.../ui/{UiHarness,ScreenshotsTest,LayoutInvariantsTest}.kt` (new offline UI test set; the build passes `vela.screens.dir`), shared `ui/LayoutLogicTest`, `PlaybackSessionTest` additions; live tests updated (`DesktopUiLiveTest`, `DownloadLiveTest`, `LiveWindow`).

## Decisions the spec didn't dictate

- **Size class is computed from the window's real width in the shell** (`BoxWithConstraints`) and provided as a CompositionLocal, so the same code is right on a phone, a rotated phone, a tablet and a resized desktop window. Landscape phones (about 800 dp) are MEDIUM: rail, grid, player beside the details.
- **Search is a screen, not a field.** Each top bar has a search icon (the rail has a search item); the search view starts from its own route's query (never a stale one) and a new query replaces the screen, so Back is one step. The top bar is hidden on the video and search screens, which draw their own.
- **The player is pinned, not a list item.** (Found in task 10: as a list item it was disposed when scrolling to the comments.) Its controls auto-hide after 3 s while playing and stay up while paused, buffering, failed or being dragged.
- **The video page's details and comments are one list on phones and two columns on MEDIUM/EXPANDED**, with the player above the details in the left column.
- **The poster and state follow the real session.** The player draws its video's thumbnail and zero position until the session is Ready *for that video*; the root cause (the controller still holding the previous video during resolve) is fixed in `PlaybackSession`, not only masked in the UI.
- **Volume slider on desktop only** (a flag set by the desktop entry point); Android shows mute.
- **Share copies the `youtu.be` link** (no platform share sheet yet).
- **Captions button is a visible, disabled placeholder**, as asked.
- **Avatars are the channel's first letter** (no avatar URL is available from search or the feed).
- **Touch minimum is a Look token** (`minTouchDp`), applied by the theme on both platforms.
- **Icons are drawn as vector paths in one file** and sized by the Look's `iconSetId`.
- **The Downloads list and Library rows are lists, not grids** (they have actions per row).

## Known issues / unverified assumptions

- **Not run on a device**: insets and cutouts, gesture navigation, predictive back, rotation, immersive fullscreen, Picture-in-Picture, the overlay player's touch behaviour, the 48 dp targets on real hardware, and how the Android keyboard resizes the search view.
- **The icon paths are hand-drawn** and I looked at them only at small sizes in screenshots (they read correctly: home, subscriptions, library, settings, play, pause, rewind, forward, volume, captions, quality, speed, timer, fullscreen, share, save, download). The "quality" and "captions" glyphs are rough.
- **Contrast is asserted at 3:1 for text colours that the components set or inherit**, against the page background only; text over thumbnails and the video is excluded by design, and I did not check the secondary text colour against every surface.
- **Pull-to-refresh, the date format and the keyboard on a phone** are unobserved. **Dark mode following the system** is not rendered in tests (Pure Black and light are); the dark Look uses the same code path as Pure Black.
- **Chapter name inside the player** appears only on wide windows; on phones it is under the player.
- **The mini player has no swipe-to-dismiss.**
- **Skeletons are static** (no shimmer) on purpose.
- **Text scale**: the tests run at font scale 1.0; large system font sizes were not tested.
- **The Subscriptions "Shorts" tab shows Shorts from the last 15 uploads per channel** (unchanged), now as a grid on wide windows.
- **`./gradlew test` does not run `:shared`'s tests** (found in task 11; CLAUDE.md still says it does).
- **Screenshots in the repo are 2.9 MB** of PNG and regenerate on every `:desktopApp:test`; delete them if you do not want them committed.

## Manual test checklist

**Android phone**
1. Open the app: Home shows one column of big thumbnail cards; titles take up to two lines and are readable; the bottom bar shows Home / Subs / Library / Settings with icons, no truncation. Scroll: nothing overflows the right edge.
2. Tap the search icon: the full search view opens with the keyboard, your last words are gone, history shows when the field is empty; search "x", press Back once and you are on Home.
3. Open a video: the 16:9 player is pinned under the status bar with a back arrow overlay. Tap the picture: controls appear (centre play/pause and +/-10 s, seek bar, time, mute, quality, speed, sleep timer, fullscreen) and vanish by themselves after about 3 s while playing. Drag the seek bar: a time bubble follows the thumb; tap on the bar to jump; the thumb stays inside the bar at both ends. Open the speed, quality and sleep menus.
4. **Switch videos quickly** (open one, Back, open another, or tap a related one): the player must show the new video's thumbnail and "resolving" at 0:00, never the old picture or old time. Report anything stuck.
5. **Rotate while playing**: it enters fullscreen with the same controls; rotate back: the page returns at the same scroll position. The fullscreen button does the same without rotating. Back gesture in fullscreen leaves fullscreen first.
6. Press Home while playing: it enters Picture-in-Picture; expand it back; the page is intact. Predictive back: swipe back slowly from a screen and watch the preview.
7. Scroll the page below the player (title, Save/Download/Share row, description with "more", chapters, related, comments): the player stays put. Open a video, scroll down, go to the mini player (Back), tap the mini player: you return where you were.
8. Look for text that looks tiny (anything that feels below normal caption size), clipped words, or controls hard to hit with a thumb. Try the largest system font size and tell me what breaks.
9. Settings: group headers, switches aligned right, chips wrap, sliders usable; type in the search field at the top. Subscriptions Feed/Shorts/Channels tabs. Library, Downloads, a channel page.
10. Pure Black in Settings > Appearance: everything readable, nothing black on black.

**Linux** (`./gradlew :desktopApp:run`)
1. Resize the window from very narrow to very wide: bottom bar -> side rail at about 600 px, grid columns appear, the video page gets a second column at about 600 px. Do it while a video plays: it must not restart or lose the scroll position.
2. The volume slider and mute are in the player controls; keyboard shortcuts (space, arrows, f, m, up/down) still work; fullscreen shows the same overlay.
3. Look through the screenshots in `docs/reports/12-screens/` and tell me what still looks wrong.
