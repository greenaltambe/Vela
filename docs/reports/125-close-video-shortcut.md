# 125 Close video: Ctrl+X and a cross

## 1. Summary
Ctrl+X (Cmd+X on a Mac) closes the playing or paused video from the mini player or the video page: it leaves fullscreen, shrinks the page away, stops playback and shows a "Player closed" toast with Undo. The video page's player has a cross button right after the back arrow (wide players: desktop and fullscreen) that does the same.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `ShortcutsUiTest`: Ctrl+X from the video page, from the mini player, and with nothing playing; the cross sits on the back arrow's line after it and closes the video | pass | running (desktop Compose UI test) |
| `./gradlew allTests` | pass | running |
| Undo restoring the video; real mpv stopping | untested | not run |

## 3. Files created/changed
- `ui/AppShell.kt` (Ctrl+X handler), `ui/player/PlayerSheetState.kt` (`dismiss` takes an `onClosed` callback), `ui/player/PlayerView.kt` (`onClose` cross), `ui/screens/VideoScreen.kt` (`closeVideo`), `CHANGELOG.md`
- Test: `desktopApp/src/test/.../ShortcutsUiTest.kt`

## 4. Decisions the spec didn't dictate
- Ctrl+X was unused in the app (Ctrl+W closes a tab, Ctrl+M minimises). On pages other than the video page a focused text field takes Ctrl+X first (it still cuts).
- The cross also shows in fullscreen and closes from there; it is hidden on the small phone player (three-button top bar).
- Undo reopens the video where it stopped (the same Undo as swiping the mini bar away).

## 5. Known issues / unverified assumptions
- On the video page the key is taken before focused controls, so Ctrl+X there never cuts text.
- The undo toast on a Linux/Wayland window was not looked at.

## 6. Manual test checklist
1. Play a video on the video page, press Ctrl+X: the page shrinks away, the video stops, a toast offers Undo; Undo brings it back.
2. Minimise it (Ctrl+M), pause, press Ctrl+X: it closes.
3. Press Ctrl+X with nothing playing: nothing happens.
4. In fullscreen press Ctrl+X: it leaves fullscreen and closes.
5. On the video page the top left shows the back arrow and a cross beside it; the cross closes the video.
