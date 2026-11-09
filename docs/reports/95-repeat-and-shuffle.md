# 95: Repeat and Shuffle (first gap from the research report)

## 1. Summary
- **Repeat:** a switch in the player's Settings sheet ("Play this video again when it ends"). While on, the video restarts at the end and autoplay-next is held back. It belongs to the video it was set on: starting another video clears it.
- **Shuffle:** a "Shuffle" button beside "Play all" on playlists (plays the playlist in random order), and a "Shuffle" action in "Up next" (reorders the queue, with Undo).
- Also in this round: the scrubbing crash fix (report 93, section 7).

## 2. Verification
| Check | Result | Verified-by |
|---|---|---|
| Repeat belongs to its video; Shuffle keeps the same videos and is deterministic for a fixed seed; single item unchanged | `NavigatorQueueTest` (now 9 tests) | running |
| `allTests`, build | pass | running |
| Repeat on the phone: switch turned on, video of 18 s then looped (positions 0:13, 0:04, 0:14, 0:05 sampled over 30 s) | seen | running (phone, via UI dump) |
| Shuffle buttons on a playlist and in Up next | built; not tapped on the phone | compile-only |
| Scrubbing crash: the 10-minute video scrubbed six times end to end, no crash in the log | seen | running |

## 3. Files changed
`ui/nav/Navigator.kt` (`repeatOne`, `shuffleQueue`), `ui/AppShell.kt` (repeat at end), `ui/player/PlayerView.kt` (Repeat row), `ui/screens/VideoScreen.kt` (wiring, queue Shuffle, autoplay held while repeating), `ui/screens/PlaylistScreen.kt` (Shuffle button), `ui/components/Scrubber.kt` (crash fix), tests `NavigatorQueueTest`, `StoryboardPlacementTest`.

## 4. Decisions
- Repeat is per video, not a saved setting, so it cannot surprise you on the next video.
- "Repeat the queue/playlist" is not included (only repeat one).
- Repeat is only in the player's Settings sheet, not on the main control row.

## 5. Known issues
- Repeat does not apply to Shorts in the Shorts player (they have their own loop behaviour, not checked).
- During my testing my taps once landed in WhatsApp, which had come to the front on your phone: three taps on its conversation screen (no typing, nothing sent, to my knowledge). I then pressed Home and relaunched Vela. Please glance at that chat in case a tap opened something.

## 6. Manual test checklist
1. Player settings > Repeat on, let a short video end: it restarts. Open another video: Repeat is off.
2. A playlist > Shuffle: plays in a random order. Up next > Shuffle: reorders, Undo restores.
