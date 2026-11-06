# 52 Home carousels and layout

## 1. Summary
Shelves now sit between the videos of Home's list (YouTube style) instead of five stacked above it; Continue watching is the one that goes first. Every row of videos that scrolls sideways is the same `Carousel` (large snapping cards of the standard `VideoCard`, Shorts as portrait cards, channels as tiles) and every strip of chips is the same `ChipRow`. The clipped first letters in cards are fixed. Shelves load when their place comes near, keep their last content if a refresh fails, and a source that throws is tried once more.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests` | 598 run, 1 fails: `LookBuilderTest` (raw colours in the owner's uncommitted Scrubber/ChaptersSheet/PlayerSheet), unchanged | running |
| `HomeLayoutTest` (slots for 0/short/long lists, density steps, once-only, order kept) | pass | running |
| Home on the phone: chips, large carousel cards peeking the next, shelves between videos, no clipped text in the feed cards, Continue watching progress bar and "left" line | seen | running on device |
| Subscriptions screen (channel tiles, portrait Shorts) on the shared carousel | seen | running on device |
| Library "Recently watched", Search start strips, Explore/Search chips | compile-only (same components) | compile-only |
| Airplane-mode and forced-failure drills for stale shelves | not run | not verified |

## 3. Files created/changed
- New: `ui/components/Carousel.kt` (`Carousel`, `VideoCarousel`, `ShortsCarousel`, `ChannelCarousel`, `CarouselSkeleton`, `ChipRow`), `home/HomeLayout.kt`, `HomeLayoutTest`.
- Changed: `HScrollRow.kt` (content padding, snap), `Components.kt` (`VideoCard` no longer clips its text), `Lists.kt` (`VideoCollection(injections)` replaces the single injected block), `HomeScreen.kt`, `HomeShelvesUi.kt` (now only the chips), `HomeFeedStore.kt` (stale entries kept), `HomeSources.kt` (retry once, shelf subtitle), `LookSettings.kt` + `HomeShelves.kt` (`look.home.density`), `LibraryScreens.kt`, `SearchScreen.kt`, `SubscriptionsScreen.kt`, `ExploreScreen.kt`.

## 4. Decisions the spec didn't dictate
- First shelf after 3 videos, then one every 5 (Few 8, Many 3); order is the viewer's from Customize > Home; a short list holds shelves at its end; a shelf needs 3 videos after filters.
- The first two shelves load at once, the rest when their place composes; at most 3 load at a time; an old cached shelf is shown and refreshed, and only a good answer replaces it.
- Cards in carousels are the feed's own card (same menu, badges, text); carousel width is 82 % of the page on a phone, 320-340 dp on larger windows.
- Continue watching and Library "Recently watched" use one `WatchRecordCarousel` (progress on the picture, "7:34 left").

## 5. Known issues / unverified assumptions
- Not done from the plan: chips are not sticky (they scroll away with the header); Shorts have no special slot (they follow the viewer's order); the Channel page's sort chips still use their own row.
- A shelf placed after a video index moves when the list is shorter than its slot (it waits at the end and takes its place as the list grows), so a shelf can shift down once while paging.
- Carousel rows assume a gutter-padded parent (they bleed through it); the one place without padding (Search filters) passes `bleed = false`.

## 6. Manual test checklist
1. Home: chips, Continue watching on top, three videos, then a shelf, more videos, another shelf; carousels show one big card and the next one's edge and snap on a swipe.
2. No card in the feed, Related or shelves has a first letter cut by a corner.
3. Customize > Home: "Shelves between videos" Few/Normal/Many changes the spacing; reorder shelves.
4. Pull to refresh: shelves stay visible and refresh; with the network off, reopen Home: shelves still show.
5. Library, Search (empty query), Subscriptions, Explore: rows and chips look like Home's.
