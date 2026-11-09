# 102: Swipe the mini player away, tap the open tab to go to the top

## 1. Summary
- **Swipe to close the mini player.** With the video minimized, swipe it **down** or **sideways** (left or right): it follows your finger, fades as it goes, and when it leaves the video stops, exactly as the close (X) button. A click haptic confirms. A toast "Player closed" with **Undo** brings the video back into the mini player and plays it from where it stopped (a swipe is easier to do by accident than the X). A drag that is too short springs back; swiping **up** still opens the video page.
- **Tap the open tab again to scroll to the top** (Home, Subscriptions, Library, any list screen). Before, tapping the tab you were on did nothing.

## 2. Verification
| Check | Result | Verified-by |
|---|---|---|
| Swipe down: mid-swipe frame shows the bar faded and lower; then the video closed and "Player closed / Undo" appeared | seen | running (phone) |
| Undo: the mini player returned with the pause button (playing) | seen | running (phone) |
| Sideways flick closed it and showed the same toast | seen | running (phone) |
| A slow 34 dp drag (below the threshold) sprang back and the bar stayed where it was | seen | running (phone, UI dump) |
| A 7 dp press-and-move counts as a tap and opens the page (existing behaviour) | seen | running (phone) |
| Home scrolled well down, then the Home tab tapped: back at the top | seen (before/after screenshots) | running (phone) |
| `NavigatorTabTest` (reselecting counts, another tab does not, a deeper page just goes back to the tab) | 2 tests | running |
| `allTests`, build | pass | running |
| Tablet / wide layout (the bar sits beside a rail), right-to-left, desktop (no touch) | not tried | not verified |
| The haptic, and swiping while the video is paused or buffering | not checked | not verified |

## 3. Files changed
`ui/player/PlayerSheet.kt` (gesture: down or sideways takes the bar away, tracked with its own velocity; offset and fade), `ui/player/PlayerSheetState.kt` (`swipeX`/`swipeY`, `swipeAlpha`, `dragSwipe`, `releaseSwipe`, `reopen`; the close is shared with the X button), `ui/nav/Navigator.kt` (`tabReselected`), `ui/screens/Lists.kt` (scroll to top), test `NavigatorTabTest`.

## 4. Decisions
- Distances: down 56 dp or a firm downward flick; sideways 30% of the width or a firm flick. Fast flicks only need a quarter of that, so a small quick flick closes it and a slow short drag does not.
- Sideways was added to the down swipe you asked for because it is the common way mini players close, and it costs nothing extra.
- The bar fades as it leaves (it does not stay solid), and the same animation is used both ways.
- Undo restarts the video through the player (it resumes at the saved position), so it comes back playing, not paused.
- The tab tap only scrolls the list on screen; it does not refresh it (pull down for that).

## 5. Known issues
- The Undo toast lasts five seconds like the app's other toasts.
- If the video was buffering when closed, Undo restarts it from the last saved position, which may be a few seconds earlier.

## 6. Ideas not built (cheap, say which you want)
Long-press the mini player for "Open channel / Share"; double-tap the bar's picture to seek; a one-tap "Copy link at this time" in the player's menu; show remaining time in the title bar while minimised; auto-hide the mini player's progress bar when paused.

## 7. Manual test checklist
1. Minimize a playing video, swipe the bar down, and sideways: closes with a toast; Undo.
2. Drag it only a little: springs back. Swipe it up: opens the page.
3. On a long list, tap the tab you are on: scrolls to the top.
