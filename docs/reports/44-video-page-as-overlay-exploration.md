# 44 Exploration: the video page as an overlay (no code changed)

## 1. Summary
Your reading is right. Today the video page is a normal route: opening it swaps the whole screen (`AnimatedContent`, `contentKey` makes every non-video route a separate page), so the screen under it is disposed and has to be rebuilt when you minimise. The screenshot trick only covers that rebuild. Apps like YouTube keep the base screen alive and draw the player on top of it, so closing it reveals the real, already-built screen. Recommended: a two-stage change, the first small, the second optional.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| Video is a stack route, base screen disposed while it is up | read `AppShell.kt` (`AnimatedDestination`, `contentKey`, `prevShot`/`shotFade` hack), `Navigator.kt` | code reading |
| Pulling the page down reveals a bitmap, not the live screen | `AppShell.kt` draw code, `VideoScreen.kt` `setDrag`/`reveal` | code reading |
| Number of places that depend on `Route.Video` | 46 references in 17 files (screens, `Navigator`, tests) | grep |
| Behaviour on a device | not measured | n/a |

## 3. Files created/changed
None (this report only).

## 4. Decisions the spec didn't dictate: the options
**Stage 1 (recommended): layer, don't swap.** Keep `Route.Video` in the stack as now. In `AppShell`, render the base screen separately from the video:
- `base` = the current route, or, while the video is on top, the route directly under it. It is the only target of the existing `AnimatedContent`, so opening/closing the video no longer changes it: Home/Channel/Search stay composed, with their scroll, lists and loaded state.
- The video page is a separate layer drawn above `base`, with its own rise/sink animation. While it is pulled down, the user sees the real screen behind it; `prevShot`, `shotFade`, `LocalPageReveal` and the 450 ms picture trick can all be deleted.
- Pushing something from the video page (a channel) makes that route the new `base` and the video layer leaves, exactly as today.
- Cost: the base screen stays composed while the video plays. Draw it only when not fully covered (alpha 0 skips drawing); input is blocked by the video layer. Expect a small memory increase. Navigator, back handling, the mini player, queue and the 46 `Route.Video` uses stay as they are, so tests need little change.
- Size: roughly one file (`AppShell.kt`, `AnimatedDestination`) plus removing the screenshot code and a few lines in `VideoScreen.kt`.

**Stage 2 (optional, bigger): one expandable player container.** Video is no longer a route. A single container is drawn above the app with an expansion value 0..1: 0 is the mini player, 1 the full page; dragging sets the value, so minimising is the same surface shrinking and the player view is never re-parented. Needs: `Navigator` state split (`videoOpen` plus `nowPlaying`), Back closing the overlay before the stack, fullscreen/PiP conditions (`route is Route.Video` in `AppShell`) rewritten, mini player merged into the container, and the 46 route references/tests reworked. This is how YouTube behaves, but it is a rewrite of navigation for video, with higher risk (Android back, restore after restart, desktop).

## 5. Known issues / unverified assumptions
- The "rebuilt screen causes the flash" claim is from reading the code; I did not profile it.
- Stage 1 keeps two screens composed during playback: not measured for cost on a low-end phone.
- Going Back from a channel opened out of the video page would show the video layer over the screen below it (stack is [Home, Video, Channel]); needs a choice of animation, minor.

## 6. Manual test checklist (after Stage 1, if you want it)
1. Minimise a video from Home, Channel, Search, Library: the real screen is visible behind while dragging and nothing reloads or shifts afterwards.
2. Scroll position of the screen under the video is unchanged after minimising.
3. Open a channel from the video page, press Back: you return to the video page.
4. Fullscreen, PiP and the mini player (card and PiP styles) still work.
