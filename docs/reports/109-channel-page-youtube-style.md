# 109 Channel page: YouTube-style lists, menus, wide Subscribe, refresh, tabs auto-hide

## 1. Summary
Channel page videos are now rows like the official app (picture left, title and numbers right) with the three-dot menu from Home on each. The Playlists tab rows got the stacked-picture look, a count badge, "channel · Playlist" and a three-dot menu. Subscribe is full width under the description. The page can be refreshed (pull down, or the corner button / Ctrl+R on desktop) and keeps each tab's selected sort/filter. A new setting hides the channel tab row while scrolling.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `allTests`, desktop + Android compile (incl. LookSettingsTest / customize coverage with the new key) | pass | verified-by-running / compile-only |
| Layout against the screenshots, refresh keeping filters, tab hide animation | not run | not verified |

## 3. Files created/changed
- Changed: `ui/screens/ChannelScreen.kt`, `core/settings/LookSettings.kt`, `SettingCopy.kt`, `ui/screens/CustomizeScreens.kt`, `LookSettingsTest.kt`
- Created: this report

## 4. Decisions the spec didn't dictate
- "Auto hide of the top categories": read as the channel page's tab row (Home / Videos / Shorts / Playlists / Posts), new setting Customize > Layout > "Hide channel tabs while scrolling" (`look.nav.hideChannelTabsOnScroll`, off by default). It uses the same scroll tracker as the bar/chips setting. The tab row slides away only while pinned. Explore's topic chips and Subscriptions' group chips are part of their lists, so they already scroll away with them; tell me if you meant those staying pinned and hiding instead.
- Sort chips (Latest / Popular / Oldest) are list items and scroll normally.
- Refresh: re-reads the header and tab list, reloads the open tab with its selected filter; other tabs reload (with their own filter) the next time you open them. Filters live in the page's feed state, so they are untouched.
- Playlist menu has Open and Share only. The playlist's own page (its videos) already used the standard row with the three-dot menu, so it is unchanged.
- Thumbnail width is the shared card's (36% of the row, max 168 dp), a little narrower than YouTube's.
- Shelves (Home tab of a channel) keep tall cards, now with the menu too.

## 5. Known issues / unverified assumptions
- Sticky tab row translating out is untested on a device; the tab row's own space stays reserved at the top while pinned but the content scrolls beneath it, so no gap is expected.
- Playlists' sort ("Sort by") is still the existing chip bar.

## 6. Manual test checklist
1. Open a channel > Videos: rows like YouTube, each with a three-dot menu (download, save, share...).
2. Playlists tab: stacked pictures, "Channel • Playlist", menu with Open / Share.
3. Header: Subscribe is full width under the description; subscribing shows the bell next to it.
4. Videos tab: pick Popular, pull down to refresh (desktop: corner button or Ctrl+R): still Popular, list reloaded.
5. Switch to Playlists and back after a refresh: both still show their chosen sort.
6. Settings > Customize > Layout > "Hide channel tabs while scrolling": scroll down a channel, the tab row slides away, scroll up and it returns.

## Follow-up: uneven side padding on the Videos tab
Cause: the horizontal video card pads its row by `Spacing.xs` (4 dp) and the menu button has 14 dp of room around its dots, so the picture sat 4 dp inside the gutter on the left and the dots 18 dp inside on the right. Fix in `ChannelItemCard` (channel page rows only, not shelves): the row is widened 4 dp to the left, and the menu is nudged 18 dp right, so picture and dots both line up with the page gutter. Compile and `allTests` pass; not seen on a device.
Test: open a channel > Videos and check the picture's left edge and the dots' right edge sit the same distance from the screen edges (also in the Playlists tab: its menu was not adjusted; tell me if it looks uneven too).
