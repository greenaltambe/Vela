# 38 – Shorts player actions, comments under the video, Ribbon progress bar

## 1. Summary
The Shorts player has like and dislike (with Return YouTube Dislike counts when on), Comments, mute, share and "open as video". A Short opened from a card anywhere in the app (Home, search, channel, subscriptions...) now opens in the Shorts player, starting on it (turned off with Shorts hidden). Comments are a panel in the page that never covers the video: on the video page it fills the room under the picture; in the Shorts player the picture shrinks to the top and the panel takes the rest. Links in the description use the palette accent. The Spikes progress bar is replaced by Ribbon: bold with a dot, with four thin silky ribbons lifting off the top of the watched part: each a smooth hump from the start of the bar to the dot, breathing and sliding past each other.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| Phone: Progress bar page shows Ribbon (wavy ribbons weaving around the watched part, dot; two frames differ) and the animated Wave | seen | phone screenshot |
| Shorts player actions, opening a Short from a card into the Shorts player, comments panel on the video page and in Shorts, description link colour | not exercised | compile-only |

## 3. Files changed
`ShortsScreen.kt` (actions, comments, `start`), `CommentsSheet.kt` (`CommentsPanel`, `CommentsController`), `VideoScreen.kt` (panel under the picture, link colour), `Navigator.kt` (`Route.ShortsPlayer`, Short routing, `Video.asVideoPage`), `AppShell.kt`, `Scrubber.kt` (Ribbon), `LookSettings.kt`, `CustomizeScreens.kt`, icon packs (`comment`)

## 4. Decisions
- A Short is recognised by the source flagging it (channel Shorts tabs, the Shorts shelf, subscription links); an ordinary search result that is short but not flagged still opens on the video page.
- The Shorts player stops playback when you leave it; the mini player is not used for Shorts.
- A previously chosen "Spikes" bar falls back to the default.

## 5. Known issues
- The comments panel is not a draggable sheet: it opens and closes with its X (and Back).
- While the Shorts comments are open the picture is smaller, so some of its sides are letterboxed.

## 6. Manual test checklist
1. Open a Short from a channel's Shorts or a subscriptions card: the Shorts player opens on it; swipe for more; Back returns.
2. In the Shorts player: like, dislike (counts when RYD is on), Comments (panel under a shrunk picture), mute, share, Open.
3. On a normal video: Comments button opens the panel under the picture; the picture keeps playing and is never covered.
4. Customize > Progress bar > Ribbon: ribbons weave around the watched part and drift.
