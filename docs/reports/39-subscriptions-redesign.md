# 39 – Subscriptions redesign (plus Shorts panel animation, link parsing, ribbon fixes)

## 1. Summary
Subscriptions is one scrolling page without tabs: a row of the channels you follow ("View all"), a row of their recent Shorts (when Shorts are on; a tap opens the Shorts player), then the feed. "View all" opens a new All channels page (a grid with search, Import, press and hold to unsubscribe). Also: the Shorts comments panel now animates and can be dragged down to dismiss, Shorts action buttons have more space and no labels (counts stay), links/timestamps in comments and descriptions are parsed from anchors (timestamp links seek, redirect links open the target), and the Ribbon progress bar's heartbeat jump and missing-ribbon bug are fixed.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| Phone: Subscriptions shows Channels row + View all, Shorts row, feed; View all opens the channel grid; a Shorts card opens the Shorts player on it | seen | phone screenshots |
| Ribbon bar draws again with several humps | seen | phone screenshot |
| Shorts comments animation and drag to dismiss, comment/description link and timestamp behaviour, ribbon heartbeat gone | not exercised | compile-only |

## 3. Files changed
`SubscriptionsScreen.kt` (rewritten, `AllChannelsScreen`), `AppShell.kt`, `ShortsScreen.kt`, `CommentsSheet.kt`, `LinkedText.kt` (new), `DescriptionText.kt`, `VideoScreen.kt`, `Scrubber.kt`

## 4. Decisions
- Shorts leave the feed list; they live in their row (the feed never repeats them).
- The channels row shows every subscribed channel (scrollable); "View all" has the grid, search (shown once there are more than 8 channels), and the old Import and Unsubscribe features.
- Unsubscribe is press and hold on a channel tile.

## 5. Known issues
- The Shorts row uses channel Shorts found in the cached feed; a channel whose recent uploads contain no Shorts adds none.
- Timestamps inside comment text without a link are found by the same time pattern as in descriptions.

## 6. Manual test checklist
1. Subs tab: no tabs; scroll the channel row sideways; View all opens the grid; search, press and hold to unsubscribe, Import.
2. Tap a Short in the row: the Shorts player opens on it; Back returns.
3. Shorts player: open and close the comments (smooth), drag the handle down to dismiss.
4. A video with timestamps in a comment: tap one, the video seeks; links open.
