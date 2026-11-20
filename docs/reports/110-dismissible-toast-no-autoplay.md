# 110 Dismissible toast, no autoplay of the restored video

## 1. Summary
(1) Every undo toast (including "Player closed") now has a cross that dismisses it at once. (2) After a restart the last video comes back in the mini player but no longer starts playing: its stream is resolved and loaded paused at the saved position, so the mini player's play button starts it.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `allTests`, desktop + Android compile | pass | verified-by-running / compile-only |
| Real restart behaviour, toast cross | not run | not verified |

## 3. Files created/changed
- Changed: `ui/look/Motion.kt` (dismiss action), `ui/player/PlayerSheetState.kt` (`takeRestored`), `player/PlaybackSession.kt` (`start(..., autoPlay)`), `ui/screens/VideoScreen.kt`
- Created: this report

## 4. Decisions the spec didn't dictate
- The restored video's page used to call `start()` as soon as it was composed in the mini player, which is what played it. It now asks the sheet whether this video was just restored and, if so, starts it with `autoPlay = false` (once; opening or re-opening any video clears the flag).
- The paused load still resolves the stream and buffers a little (needed so play is instant and the position is kept); no data beyond that is used.
- The toast's timer (5 s) is unchanged; the cross is added to all toasts, not only "Player closed".

## 5. Known issues / unverified assumptions
- On Android the playback service may show its notification for the paused restored video; not checked.

## 6. Manual test checklist
1. Play a video, minimise it, force-close the app, reopen: the mini player shows the video, silent and paused. Press play: it resumes from where it was.
2. Swipe the mini player away: the "Player closed" toast has a cross; tapping it dismisses the toast; Undo still works if tapped.
3. Open a different video after a restart: it plays normally.
