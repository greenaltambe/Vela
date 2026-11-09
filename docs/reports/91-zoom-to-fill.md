# 91: Pinch to zoom the picture to fill the screen

## 1. Summary
In fullscreen on Android, spreading two fingers zooms the picture to fill the screen (edges cropped, no bars); pinching brings it back to fit. A small pill says "Zoom to fill" or "Fit". The choice resets per player view (it is not saved). Desktop ignores it.

## 2. Verification
| Check | Result | Verified-by |
|---|---|---|
| `assembleDebug`, existing tests | pass | running |
| Pinch gesture and the Media3 `RESIZE_MODE_ZOOM` switch | built and installed; **two-finger input cannot be sent over adb, so it was not exercised** | compile-only |
| Single-finger brightness/volume swipes and double-tap seek still work alongside | not re-tested after this change | not verified |

## 3. Files changed
`player/PlayerHost.kt` (`Surface(modifier, fill)` with a default that ignores `fill`), `androidApp/.../AndroidPlayerHost.kt` (resize mode), `ui/player/PlayerView.kt` (gesture, pill).

## 4. Decisions
- Fullscreen only. Thresholds: fingers 15% further apart = fill, 13% closer = fit. A two-finger gesture consumes its events so it never starts a swipe.
- Not saved between videos or restarts.

## 5. Known issues
- Untested on the device (see above). If a pinch also triggers a brightness or volume change, tell me.
- No button alternative yet for people who cannot pinch; one could go in the player's settings sheet.

## 6. Manual test checklist
1. Open a 16:9 video in fullscreen on a tall phone (bars at the sides). Spread two fingers: the picture fills, the pill says "Zoom to fill".
2. Pinch: back to "Fit". Check the swipes still work.
