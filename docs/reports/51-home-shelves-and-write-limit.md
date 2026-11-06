# 51 Home shelves, quick filters, dismissals; account write limit

## 1. Summary
Home is now a set of shelves (Continue watching, New from your subscriptions, Because you watched X, From channels you watch a lot, Your interests, Shorts) above the existing Recommended/Trending list. Each shelf loads, fails and is cached on its own; a shelf that has nothing or breaks is simply not drawn. Quick filter chips (All, Unwatched, Under 10 min, Live, Music, plus the viewer's own topic chips) filter locally. "Not interested" and "Hide <channel> from Home" (with Undo) are in each card's menu. Shelves are reordered or hidden in Customize > Home. Separately: the hourly account-write limit now reads in plain local time and a refused like/subscribe/playlist write stays applied locally and is queued.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests` | 592 run, 1 fails: `LookBuilderTest` (raw colours in the owner's uncommitted Scrubber/ChaptersSheet/PlayerSheet), unchanged | running |
| `HomeLogicTest` (ranking by several lists, caps per channel, filters, dismissals, channels-watched-a-lot, keywords, new-since) | pass | running |
| `HomeShelvesTest` (parse/encode/order/none) | pass | running |
| `WriteBudgetTest` (queued write, replace by key, plain local-time message) | pass | running |
| Home on the phone: chips, Continue watching, subscriptions, Because you watched, channels, interests, Shorts shelves; Not interested + Undo toast; Under 10 min chip | seen | running on device |
| Customize > Home page (switches, Up/Down) | compiles, not opened on the device | compile-only |
| Queue drain after the limit frees a slot, in the real app | not exercised (needs 10+ real writes) | compile-only |

## 3. Files created/changed
- New: `core/settings/HomeShelves.kt`, `home/HomeLogic.kt`, `home/HomePrefs.kt`, `home/HomeSources.kt`, `ui/screens/HomeShelvesUi.kt`, tests `HomeLogicTest`, `HomeShelvesTest`, `WriteBudgetTest`.
- Changed: `HomeScreen.kt`, `HomeFeedStore.kt` (per-shelf cache, 20 min), `Common.kt` (menu: Not interested / Hide channel), `CustomizeScreens.kt` (Home page), `LookSettings.kt` (`look.home.shelves`, `look.home.chips`), `AppServices.kt`, `Navigator.kt` + `AppShell.kt` (`Route.HomeShelf`), `Accounts.kt` (`ActionResult.Queued`), `InnertubeAccountActions.kt`, the like/subscribe/Watch later call sites.

## 4. Decisions the spec didn't dictate
- Recommended/Trending stays as the endless list under the shelves (it is the only paged source); shelves sit above it.
- "Because you watched": seeds are the last three videos watched to the end (else the last three watched); ranking = number of related lists a video is in (dominant), then its position; at most 2 per channel; watched videos, seeds and Shorts are dropped.
- "From channels you watch a lot": a channel with 2+ different watched videos that is not subscribed. "Your interests": the three most frequent words (4+ letters, no filler) in the last 150 history titles; the shelf searches two of them; topic chips use the same words.
- Dismissals are stored per account as settings rows (so backup carries them); a hidden channel is also dropped from the channel/interest/Shorts sources.
- Write queue: only like, subscribe/unsubscribe and playlist add/remove are queued; a later write on the same thing replaces the earlier one. The queue is in memory (lost if the app is closed before the limit frees a slot).

## 5. Known issues / unverified assumptions
- Not done from the proposal: "N new" pill on pull-to-refresh (a pull replaces the list as before), the "background listening" lean, the "download-ready" label, and fading watched videos (the existing Settings > Feed "hide watched" drops them). "See all" on Because-you-watched, channels and interests opens a plain list of that shelf, not a re-ranked one.
- "Music" is a guess from titles and channel names. "Under 10 min" skips videos whose length is unknown.
- The subscription shelf refreshes the feed in the background (the same call the Subscriptions page uses), so the first Home visit after a long time costs those requests.
- Shorts shelf asks 4 subscribed channels for their Shorts; it is hidden when none answer.

## 6. Manual test checklist
1. Open Home: shelves appear one by one (grey placeholders first); pull to refresh reloads them.
2. Tap each chip; shelves and the list filter; "All" restores.
3. On a shelf card tap the ... button: "Not interested" and "Hide <channel> from Home" hide it at once; the toast's Undo brings it back; Customize > Home > "Bring back hidden..." clears all.
4. Customize > Home: switch a shelf off, move one up/down; Home follows. Turn "Quick filters" off.
5. Subscription shelf: a "New" marker on uploads newer than your previous visit to Home.
6. Like/unlike a video more than the hourly limit (Settings > Account); the like stays applied and a note says when it will reach the account; the wording is local time ("Hourly account-action limit reached. Try again after 10:37 AM.").
