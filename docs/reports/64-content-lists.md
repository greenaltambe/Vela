# 64 Content lists, expressive pass

## 1. Summary
First pass over the lists of content. The three-dot menu is now vertical (done earlier). The horizontal video row can draw as a tonal container in a given shape, so History days, playlist videos and similar lists read as segmented stacks (large outer corners, small joins). Feed thumbnails use (reverted after review: too round) corners, badges are pills, row thumbnails and channel-playlist / download rows use larger rounded corners and tonal fill. This is a partial pass: carousels, Search layout, Shorts cards, Explore and comments were audited but not restyled (section 5).

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests`, `:desktopApp:test`, `:androidApp:installDebug` | pass, 0 failures; installed on the phone | running |
| Phone: Home, Library, History (account card, Show, list) | seen, corners fixed after review | running on device |
| Phone: Playlist, Downloads, Channel playlists | not looked at | not verified |

## 3. Files created/changed
- `ui/components/Components.kt`: `VideoCard` gets `containerShape` (tonal horizontal row), extra-large feed thumbnails, pill badges, centred row content.
- `ui/screens/LibraryScreens.kt`: History grouped per day as segmented stacks with primary day headings; playlist video rows use the tonal container.
- `ui/screens/ChannelScreen.kt`: playlist card tonal, larger corners.
- `ui/screens/DownloadsScreen.kt`: larger thumbnail and row corners.
- `docs/reports/64-content-lists.md`.

## 4. Decisions the spec didn't dictate
- Reused `segmentedShape` from the settings components rather than a new list component.
- Gap between segmented rows is a literal 2 dp because `ListItemDefaults.SegmentedGap` needs an opt-in at that call site.

## 5. Known issues / unverified assumptions
- Not restyled: Home / Subscriptions / Search carousels, Shorts cards, Explore, Related list, comments, All channels.
- Layout tests were not extended; appearance not checked on device.

## 6. Manual test checklist
1. History: each day is one rounded stack with a primary-coloured day heading; remove an entry with the X.
2. Open a playlist: rows are tonal, swipe left removes, the "..." menu is vertical.
3. Downloads and a channel's Playlists tab: tonal rows with rounder thumbnails.
4. Home feed: thumbnails have rounder corners, duration badge is a pill.
