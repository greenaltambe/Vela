# 89: Add to queue, Play next, Up next

## 1. Summary

A card's menu now has "Play next" and "Add to queue"; the video page lists the queue as "Up next" (tap to play now, cross to remove, Clear). Both actions give a toast with Undo. With nothing playing, either action starts the video. The queue itself already existed (Navigator, "Play all", auto-advance when a video ends); this adds the ways to fill and see it. Also fixed: the toast's Undo text was unreadable.

## 2. Verification

| Check | Result | Verified-by |
|---|---|---|
| Queue rules: add at end, play next at front, no duplicates, already-playing refused, nothing-playing starts, remove / clear / undo, play from the middle | `NavigatorQueueTest` (6 tests) pass | running (unit tests) |
| `allTests`, `assembleDebug` | pass | running |
| Card menu shows "Play next" and "Add to queue" | seen on Home | running (phone) |
| Add to queue with nothing playing starts the video (mini player appears) | seen | running (phone) |
| Add a second video while one plays: "Added to queue" toast with Undo; the video page shows "Up next" with the queued video and Clear | seen | running (phone) |
| Tapping a queued row, the cross, Clear + Undo, and the queue advancing when a video ends | not tried on the phone | not verified |
| Undo text colour after the fix (`SnackbarDefaults.actionColor`) | built and installed; the toast was not on screen again | compile-only |

## 3. Files created/changed

- `ui/nav/Navigator.kt`: `addToQueue`, `playNextUp`, `removeFromQueue`, `clearQueue`, `restoreQueue`, `playFromQueue`, `QueueResult`, `LocalNavigator`.
- `ui/AppShell.kt`: provides `LocalNavigator`.
- `ui/screens/Common.kt`: the two menu items and their toasts. `ui/screens/VideoScreen.kt`: "Up next" section, `QueueRow`.
- `ui/look/Motion.kt`: Undo button colour.
- Test: `commonTest/.../nav/NavigatorQueueTest.kt`.

## 4. Decisions the spec didn't dictate

- Adding a video that is already queued moves it (to the end, or the front for Play next) instead of listing it twice.
- The queue lives in memory only (as before): it is gone after the app restarts.
- No drag-to-reorder: "Play next" moves a video to the front and tap-to-play jumps ahead; reordering can come later.
- The queue is on the video page only, not in the mini player.
- The icon for "Add to queue" reuses the playlist icon (the bundled icon set has no PlaylistAdd).

## 5. Known issues / unverified assumptions

- Auto-advance at the end of a video, tap-to-play and Clear/Undo were not exercised on the phone.
- Shorts in the queue open on the ordinary video page.

## 6. Manual test checklist

1. On Home, open a card's ⋮ menu and choose "Add to queue" with nothing playing: it starts.
2. Add two more videos; open the video page and scroll to "Up next".
3. Tap a queued row (plays it, skipping the ones before), the cross (removes, toast has Undo), Clear (toast Undo restores).
4. Let a video finish: the next queued one should start.
5. Check the toast's "Undo" is readable in dark and light theme.

## 7. Update: reorder by dragging (after your feedback)
You confirmed Add to queue works and asked for drag-to-reorder with Material 3 expressive design.
- "Up next" is now a list of tonal rows (large shape): thumbnail, "Plays next" label on the first, title and channel, a cross, and a handle (three lines). Hold the handle and drag: the row lifts (lighter surface, shadow), the others slide a row out of the way on a spring, and it drops where you let go. Only the handle starts a drag, so scrolling the page with a finger on a row still works. Tap a row to play it now.
- Screen readers get "Move up", "Move down" and "Remove from queue" actions on every row (no drag needed).
- `Navigator.moveInQueue(from, to)`; test added to `NavigatorQueueTest` (now 7 tests).
- Verified on the phone: three videos queued, first row dragged to the end: lifted row seen mid-drag, others moved, final order correct, "Plays next" moved to the new first row.
- Not verified: the screen-reader actions; dragging when the list is longer than the screen (the page does not auto-scroll while dragging).
