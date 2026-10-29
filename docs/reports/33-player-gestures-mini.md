# 33 – Video page: dislike, no bar, pull-down to mini, swipe up/down, mini with video

## 1. Summary
The video page now hides the tab bar. Pulling down on the picture (portrait) shrinks the whole page toward the mini player's corner while the video keeps playing, then hands over to the mini player (regular card or picture-in-picture), which shows the live video with its progress line along the bottom. Swiping up on the picture enters landscape fullscreen; swiping down in fullscreen returns to portrait. A Dislike button sits next to Like (RYD count when RYD is on and known, otherwise no count).

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| Video page has no tab bar; Like + Dislike chips shown | seen | phone |
| Pull down on the picture: page shrinks to the corner mid-drag, mini card appears playing, progress line at its bottom | seen | phone (screenshots mid-drag and after) |
| Swipe up on the picture: device goes landscape (ROTATION_90); swipe down: back to ROTATION_0 | seen | phone (adb rotation check) |
| Picture-in-picture mini look with live video | not checked | compile-only |
| Desktop (mpv) mini player surface / gestures | not checked | compile-only |
| Dislike with account sign-in (rate call), RYD-off label | not checked | compile-only |

## 3. Files changed
- `ui/screens/VideoScreen.kt` (Dislike chip, drag-to-minimise, swipe up/down), `ui/player/PlayerGestures.kt` (new)
- `ui/AppShell.kt` (no bar on the video route, vertical page transition, mini surface), `ui/components/Components.kt` (MiniState.surface, progress at the bottom, video in both mini looks)

## 4. Decisions
- The shrink is a transform of the page, not a shared-element morph of the one video surface: the mini player draws its own surface a moment later (a brief handover). A true shared surface would need the player hoisted out of the page.
- The pull-down only starts on the picture (not from the details list), because a list inside a shrinking layer fights the finger.
- The gesture is detected on an untransformed wrapper (first attempt measured inside the shrinking layer and read as a swipe up).
- Without an account, Like/Dislike show "Sign in to rate videos".

## 5. Known issues
- The shrink target is the mini's corner, not its exact rectangle; the handover can show a small jump.
- Pull-down is portrait / compact windows only; wide windows keep the old behaviour.
- The Dislike label reads "Dislike" when there is no count.

## 6. Manual test checklist
1. Open a video: no tab bar. Pull down slowly on the picture: the page shrinks toward a corner; release past ~20 % (or flick): the mini player appears and keeps playing; release earlier: it springs back.
2. Switch Customize > Tabs > Mini player look to Picture-in-picture and repeat.
3. Swipe up on the picture: landscape fullscreen. Swipe down: portrait.
4. Check the mini card's progress line is at its bottom edge.
5. Signed in: Like / Dislike toggle each other; with RYD on the counts show.
