# 68 Search All page, full-width results, Home chips

## 1. Summary
Search results use the full-width thumbnail cards (the Home look) on All, Videos and Live; wide windows get the grid. The All page is now composed: a channel card for a clear channel match (picture, name, subscribers, Subscribe, Go to channel), a "From <channel>" row, the videos, Shorts blocks after the 3rd video and then every 8, and a Channels shelf after the 6th. Home's filter chips now use the same expressive toggle chip as Search (one shared `ExpressiveChip`).

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests` (new `SearchFeedTest`: block placement, no repeats, strict channel match, latest row), `:desktopApp:test`, `:androidApp:installDebug` | pass, 0 failures | running |
| Phone: "tech linked": channel card + "From TechLinked" row, full-width cards, Home chips | seen | running on device |
| Phone: Channels shelf in the list | seen | running on device |
| Phone: Shorts block in the list after filtering to relevant Shorts | not seen after the last change (before it, unrelated Shorts appeared) | not verified |
| Subscribe on the card, tablet / desktop grid, light mode | not tried | not verified |

## 3. Files created/changed
- New: `search/SearchFeed.kt` (`planShortsSlots`, `topChannel`, `latestFrom`), `ui/components/ExpressiveChip.kt`, test `search/SearchFeedTest.kt`.
- `ui/screens/SearchScreen.kt` (All composition, vertical cards, Shorts keep only items holding every searched word, auto-paging for Shorts), `ui/search/SearchParts.kt` (`TopChannelCard`, chips use `ExpressiveChip`), `ui/screens/HomeShelvesUi.kt` (Home chips).

## 4. Decisions the spec didn't dictate
- The channel card appears only on a strict name match (name equals the query ignoring case, spaces and punctuation, or one contains the other with 70% overlap and 5+ characters).
- The card's Subscribe is the local subscription (same call as the channel page), then the feed reloads.
- The list jumps back to the top when the channel card arrives, so it is never left above the view.
- Shorts: from a separate "<query> #shorts" search, thin pages fetch up to 4 pages.

## 5. Known issues / unverified assumptions
- Searches with no channel match show no card (by design), and a query whose Shorts do not name the query shows no Shorts block.
- Not added: a Playlists shelf, mic button.
- Filters and Videos / Live views cover loaded results only.

## 6. Manual test checklist
1. Search "tech linked": card with Subscribe / Go to channel, then "From TechLinked", then cards; subscribe and check Subscriptions.
2. Scroll the All page: Shorts block after the third video and later ones (if the query has Shorts), a Channels shelf.
3. Search "kotlin": no channel card, plain full-width cards.
4. Home: chips look like Search's chips; tap a few (the feed still filters).
5. A wide window: results become a grid with blocks spanning.
