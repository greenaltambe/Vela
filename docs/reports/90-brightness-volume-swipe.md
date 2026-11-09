# 90: Brightness and volume swipes in fullscreen

## 1. Summary
In fullscreen on Android, a vertical swipe on the left half of the picture changes the screen brightness, on the right half the media volume, with a small pill showing the name, a bar and the percentage. Controls hide while you swipe. Brightness is set on the app's window only and handed back to the system setting when leaving fullscreen. Desktop is unchanged (it has its own volume slider).

## 2. Verification
| Check | Result | Verified-by |
|---|---|---|
| Swipe maths (up raises, down lowers, clamps, 80% of height covers the range) | `SwipedLevelTest`, 4 tests pass | running |
| `allTests`, `assembleDebug` | pass | running |
| Right-half swipe down changes volume; indicator "Muted 0%" | seen in landscape fullscreen | running (phone) |
| Left-half swipe up changes brightness; indicator "Brightness 72%" mid-swipe | seen | running (phone) |
| Indicator brightness after hiding controls on swipe | built and installed; not re-captured | compile-only |
| Brightness returns to the system setting on leaving fullscreen | not tried | not verified |
| Desktop | not affected (no `LocalPlayerLevels` there) | code reading |

## 3. Files changed
`ui/platform/PlatformUi.kt` (`PlayerLevels`, `LocalPlayerLevels`, `swipedLevel`), `androidApp/.../MainActivity.kt` (levels, brightness reset), `ui/player/PlayerView.kt` (gesture, indicator), test `ui/platform/SwipedLevelTest.kt`.

## 4. Decisions the spec didn't dictate
- Fullscreen only: in the small player vertical swipes belong to the page (scrolling, opening the sheet).
- Brightness never goes fully black (floor 1%) so the picture can still be seen to swipe back up.
- Volume uses the media stream directly, so the system volume panel stays in step.
- Not in the settings yet (always on in fullscreen); the Shortcuts for desktop are unchanged.

## 5. Known issues
- A swipe starting on the seek bar or while it is being used is ignored by the existing gesture lock; edge cases around the lock were not tested.
- During my test I muted the phone's media volume; I set it back to step 8.

## 6. Manual test checklist
1. Play a video, go fullscreen. Swipe up/down on the left half: brightness pill, screen changes. Right half: volume pill.
2. Leave fullscreen: brightness returns to your normal level.
3. Double-tap to seek and hold for 2x still work.

## 7. Update after your feedback
- **Bug fixed: swipe down to leave fullscreen stopped working.** My swipe handler consumed every vertical drag on the whole picture, so the existing swipe-down-to-leave on the parent never saw it. Now only the left 35% (brightness) and right 35% (volume) react; the middle 30% is untouched and swiping down there leaves fullscreen again. Verified on the phone (centre swipe returns to the page).
- **Brightness "Auto":** at the bottom end of the brightness swipe, going a little further (8% of the range past 0) hands the screen back to the system's own brightness (auto-brightness included) and the pill says "Auto"; swiping up again takes manual control from the current level. Same idea as ReVanced/NewPipe. Verified on the phone ("Brightness Auto").
- Tests: `SwipedLevelTest` now 6 (adds the Auto threshold and the unclamped level).
- Trade-off: swiping down in the left or right third now changes brightness/volume instead of leaving fullscreen; use the middle or the back button to leave.
