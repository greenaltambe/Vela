# 136 Feed overhaul, task 4: the Home chip row, a Filter button, per-chip lists

## 1. Summary
Signed-in Home now shows YouTube's own chips above the feed (All, your custom feeds, topics, Live, Recently uploaded, ...), in YouTube's order.
Tapping one loads that chip's feed with its own endless paging; going back to All shows the feed again without asking YouTube. The old local
chips (All, Unwatched, Under 10 min, Live, Music, your topics) are replaced by a Filter button at the start of the row (All videos, Unwatched,
Under 10 min) that narrows whichever list is open. The "create a custom feed" chip is not drawn. Shelves show on All only.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `HomeSourceUiTest` (12; seven new or rewritten): chips shown and the create chip left out; a chip loads its own list and All returns from memory (no new request); an empty chip leaves the row and "Show all" returns; the Filter menu narrows on top of the open chip; signed out shows only the Filter button | pass | running (desktop Compose UI tests, fake `AccountReads`) |
| `./gradlew allTests` | pass | running (offline) |
| `:androidApp:compileDebugKotlin` | pass | compile-only (not run on a device) |
| Chip behaviour on the real account | measured in tasks 1-2 (parsing, paging); the screen itself not yet used on a real account | needs the owner |
| Stale chip token retry (`chipFirstPage`) | no automated test | unverified |

## 3. Files created/changed
- `shared/.../ui/screens/HomeScreen.kt`: chip state (`chips`, `chipLabel`, `hiddenChips`), `load()` per chip, `selectChip`, `chipFirstPage` (with one retry using fresh chips), a load counter so an answer for a chip that is no longer open is dropped, shelves only on All, topic chips removed.
- `shared/.../ui/screens/HomeShelvesUi.kt`: new `HomeChips` (Filter button with a menu, then YouTube's chips) replacing the old local chips.
- `shared/.../ui/screens/HomeFeedStore.kt`: chips, selected chip, empty chips and one list per chip, per account.
- `shared/.../core/settings/LookSettings.kt`, `SettingCopy.kt`: the "chips on Home" setting's name and text.
- `desktopApp/src/test/.../HomeSourceUiTest.kt`.

## 4. Decisions the spec didn't dictate
- The Filter menu offers All videos, Unwatched and Under 10 min only (single choice). The local Live and Music filters and the interest-topic chips are gone from the row; YouTube's Live and Music chips replace them. The enum entries stay for existing tests.
- A chip's list is kept for 30 minutes (like All); its scroll position is not kept (the list starts at the top).
- A chip with no videos is hidden for the session and the page says so, with "Show all".
- If the open chip is no longer in the reloaded chips, Home falls back to All.
- The row still follows the existing "Chips on Home" setting and hides on scroll as before.

## 5. Known issues / unverified assumptions
- The Filter menu is a Material dropdown: shape and position on a phone are unseen.
- Chip tokens' lifetime is unmeasured; the retry path (fresh chips, one more try) has no test.
- The per-chip "load more" relies on the continuation each chip returns; chips like Gaming/Live/Watched end after one page (measured), so the end of the list is just the end.
- "Pull to refresh" on a chip reloads that chip only; the chips themselves refresh when All reloads.
- Explore's icons (planned in section 8 of `feed-overhaul.md`) are not done here; they belong to the Explore task.

## 6. Manual test checklist
1. Signed in, open Home: a row starts with "Filter", then All, your custom feed, and topic chips. No "Custom feed" create chip.
2. Tap a topic chip: the list is replaced by that topic's videos; scroll down and more load. The shelves (Continue watching etc.) are gone.
3. Tap All: your feed returns instantly and the shelves are back.
4. Tap Live: the videos show their LIVE badge.
5. Tap Filter, choose "Under 10 min", then switch chips: the filter still applies. Choose "All videos" to clear it.
6. Pull to refresh while on a chip: only that chip's list reloads.
7. Open another tab and come back within a few minutes: the same chip is still selected.
8. Signed out (or sign out): only the Filter button above Trending.
