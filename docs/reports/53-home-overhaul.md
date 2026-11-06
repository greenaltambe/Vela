# 53 Home overhaul

## 1. Summary
Home now has one loading look, stable shelf places and pinned chips. Shelf places no longer depend on how many videos have loaded. The chips sit above the list and filter the feed only; picking one scrolls to the top and the shelves step aside. Phone testing found two chip bugs (blank screen on a topic with no matches; "Music" matching "MOST"), both fixed. All 17 desktop test failures that predated this task are fixed, and so is the `LookBuilderTest` failure. Not done: a full `HomeState` holder rewrite and off-main-thread filtering (section 5).

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests` + `:desktopApp:test` + `:androidApp:compileDebugKotlin` | all pass, 0 failures (was 17 desktop + 1 shared) | running |
| New tests: `HomeLayoutTest` (places independent of video count), `HomeLogicTest` (Music whole words), `HomeSourceUiTest` (chips stay displayed and a filter drops shelf placeholders; first load has at most 3 list placeholders and no shelf placeholder over them) | pass | running |
| Phone (debug build over adb): cold start, chips pinned while scrolling, "Under 10 min" filters and jumps to top, "Tech" shows Tech videos, "Music" with no match shows the empty state, "Show all" restores Continue watching and the feed | seen | running on device |
| Phone: gap under the chips (empty header row) | found, fixed, re-checked | running on device |
| Frame times / recomposition counts | not measured | not done |

## 3. Files created/changed
- Home: `home/HomeLayout.kt`, `home/HomeLogic.kt` (`isMusic`), `ui/screens/HomeScreen.kt`, `HomeShelvesUi.kt`, `Lists.kt` (shared by other list screens; new params optional), `ui/components/Carousel.kt`.
- Desktop failures: `ui/nav/Navigator.kt` (a video opened before the sheet exists is remembered and opened on attach; leaving it before then leaves the mini player), `ui/nav/MiniSlot.kt` (new), `ui/nav/BottomChrome.kt`, `ui/AppShell.kt`, `ui/player/PlayerSheet.kt` (mini player follows the bar, honours placement and gap, carries the `component.mini_player` tag; real page width passed on), `ui/screens/VideoScreen.kt` (`pageWidthPx`), `ui/VelaTheme.kt` (`labelSmall` at caption size).
- Raw styling replaced by theme tokens: `ui/components/Scrubber.kt`, `ui/player/ChaptersSheet.kt`, `ui/player/PlayerSheet.kt`.
- Tests: `HomeLayoutTest`, `HomeLogicTest`, `HomeSourceUiTest`, `AccountActionsUiTest` (let the sheet arrive), `NavigationUiTest` (scroll to the real end), `ChannelTabsNativeUiTest` (scroll to the button), `ScreenStatesTest` (names the screen on failure).

## 4. Decisions the spec didn't dictate
- Chips sit in a `Column` above the list (not a lazy `stickyHeader`), so phone list and desktop grid share the code. The "Recommended unavailable" note and "N hidden by feed filters" line sit under them, not in the list: a first list item that appears after loading landed above the videos, out of sight.
- With a chip on, shelves are hidden. A chip with no matches in what is loaded fetches up to 4 more pages behind placeholders, then shows "Nothing in your feed matches ..." with "Show all".
- While the feed's first answer is awaited only Continue watching is above the placeholders; on an error or empty feed the error row comes first with the shelves under it.
- 3 list placeholders (was 6); shelf placeholders shimmer and match the real card; footer is a placeholder card while paging; the next page is requested 3 cards before the end (all lists using `onEndReached`).
- Per-shelf `contentType`, keys on all items, shelves ease their height.
- Music chip matches whole words only; the 11 sp badge text now uses the caption size (12 sp floor).
- Desktop: the video page is laid out at the sheet's real open width (window minus rail); before, it was the full window width, so the right side (Subscribe, Retry) was clipped beside the rail.

## 5. Known issues / unverified assumptions
- The exploration's duplicate-key claim was wrong: `PagedList.append` already drops repeated ids. Strong skipping is on, so I did not build a `HomeState` holder or move filtering off the main thread. The wins are fewer repeated database reads and placeholders, not profiled frame times.
- Phone: topics come from your history and change between launches ("Tech" had results one launch, none another); topic chips are plain word matches on title or channel name.
- Continue watching still has no avatar; Shorts still use `hq720` fallback thumbnails and have no "..." menu.
- The mini-player and video-page fixes were checked by desktop tests only; on the phone I saw the mini player in its normal place but did not exercise shrink-on-scroll or the placement settings.
- A detail: the carousel row on the phone is clipped about 40 px before the right screen edge (seen in screenshots, same as before this task).

## 6. Manual test checklist
1. Cold start Home: 3 shimmering cards, no flat grey blocks; shelves then appear without the feed jumping.
2. Scroll down: chips stay pinned. Tap "Under 10 min", a topic chip, then "All": jumps to top each time; shelves return on All.
3. Tap a topic with few results: placeholders, then either results or the empty state with "Show all".
4. Music chip: no videos that merely contain "most", "mixed", etc.
5. Scroll to the end of the feed: more cards arrive before the bottom.
6. Pull to refresh: one spinner only.
7. Settings > Customize: change bar style, mini player placement and gap; the mini player should sit on the bar (or the chosen gap above it) and move down when the bar shrinks on scroll. Not checked on the phone.
8. Open a video from Home and collapse it; open the Subscribe button on a desktop window with the rail.
