# 123 Floating mini player (in-window)

## 1. Summary
New per-device setting "Minimised video" (Settings > Playback): Automatic (default), Mini bar, Floating window. Automatic is the bar on a phone and the floating window on desktop. The floating window is a 16:9 video inside the app that can be dragged (it settles into the nearest corner) and resized (grip in the corner opposite its resting corner). It opens into the video page and collapses back with the same morph and springs as the bar. The setting uses a new `device.` key prefix that is excluded from sync and from backups.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `NavigationUiTest`: window is 16:9, bottom right, takes no room; drag settles in nearest corner; tap opens the page; grip resizes and keeps its corner; Bar choice still gives the bar; the key is not synced or backed up | pass | running (desktop Compose UI test) |
| `PlayerPolishTest` and the other bar tests (default Automatic is the bar in tests, which have no desktop marker) | pass | running |
| `./gradlew allTests` (first run caught two real problems: the setting was on no settings page, and the overlay used raw colours the theme scan forbids; both fixed) | pass after the fixes | running |
| Look and feel of the drag, the spring, the shadow, hover controls; real mpv frames inside the window | untested | not run (needs a human) |
| Android | compile-only; not run | compile-only |

## 3. Files created/changed
- New: `shared/.../ui/player/FloatingPip.kt` (state, corner snapping, resize, overlay, `rememberFloatingMini`)
- Changed: `ui/player/PlayerSheet.kt` (geometry for both styles, drag/resize gesture, rounded corners and shadow), `ui/player/PlayerSheetState.kt` (`pip`), `ui/screens/VideoScreen.kt` (`miniPicture`: where the picture rests when minimised), `ui/AppShell.kt` and `ui/nav/AppNavigation.kt` (the bar's reserved strip is 0 high for the window), `core/settings/Settings.kt`, `SettingCopy.kt`, `SettingsPages.kt` (Playback page owns the key), `backup/Backup.kt`, `sync/SyncDomains.kt`
- Test: `desktopApp/src/test/.../NavigationUiTest.kt`

## 4. Decisions the spec didn't dictate
- Setting key `device.miniPlayerStyle`; the `device.` prefix is for any future per-device choice.
- Same morph as the bar: the sheet's position, size and the picture's place are all interpolated by the one progress value, so the window grows into the page and shrinks back with the same springs.
- Window size: 360 dp wide to start, 240 dp minimum, at most 60% of the page area (and 720 dp); 16 dp margin; kept inside the page area beside the rail.
- Buttons (title, play/pause, open page, close) show on hover on desktop, always on touch screens, and while paused. A thin progress line is always shown. No seek in the window.
- Closing from the window has no Undo (the bar's swipe-away has one; the window has no swipe-away).
- Position and size are kept for the session only, not across restarts.

## 5. Known issues / unverified assumptions
- The window floats over the page and can cover content; drag it away.
- It is inside the app window, not a separate always-on-top window (decided earlier: Wayland/libmpv constraints).
- A window resize clamps the size and keeps the corner, but it was only tested at one window size.
- On Android with "Floating window" chosen the window sits over the bottom navigation bar region's margin, not above it; untested on a device.

## 6. Manual test checklist
1. On desktop open a video, then leave its page (back or the page's down arrow): a floating window appears bottom right and the page keeps scrolling underneath.
2. Click it: it grows into the video page with the usual spring; collapse it again: it shrinks back to the corner.
3. Drag it to another corner: it settles in the nearest one. Flick it: it goes where the flick points.
4. Hover: title, play/pause, open and close buttons appear. Pause, move away: buttons stay.
5. Drag the grip (opposite corner) outwards and inwards: it resizes, 16:9, and stays in its corner.
6. Settings > Playback > Minimised video: choose Mini bar, then Floating window; the change applies at once.
7. On the phone: it is still the bar (Automatic). Change the desktop's setting, run a sync: the phone's setting does not change.
8. Try Wayland/GNOME and a very small window (about 600 px wide).
