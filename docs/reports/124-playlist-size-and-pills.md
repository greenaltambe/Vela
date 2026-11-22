# 124 Playlist picture size and sort pills

## 1. Summary
On a wide window the Search > Playlists rows showed a picture about 640 px wide, far bigger than the Channel > Playlists rows. The picture's cap (168 dp) never applied because `fillMaxWidth(0.36f)` handed down a fixed width that a following `widthIn(max)` cannot reduce. The picture is now 36% of the row but never wider than 340 dp, in both places. Channel > Playlists also shows its sort choices ("Date added (newest)" and the others) as pills like the Videos tab instead of a one-chip dropdown.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `./gradlew allTests` (no test covers these two spots) | pass | running |
| Picture width and the pills on screen | untested | not run (needs a human) |

## 3. Files created/changed
- `shared/.../ui/screens/ChannelScreen.kt`: `CourseOrPlaylistCard` picture width (min of 36% and `PlaylistPictureMax`), `SortAndFilterBar` (groups of up to 5 options are pills; longer ones stay a dropdown)

## 4. Decisions the spec didn't dictate
- Cap is 340 dp, a little under what the channel page showed (about 435 px): the channel page's picture gets slightly smaller too, so both look the same and neither is huge. Phones are unaffected (a third of the row is well under it).
- Pills apply to any filter group of 5 options or fewer, not only playlists.

## 5. Known issues / unverified assumptions
- Not seen on screen. Assumes the Playlists sort group arrives as a dropdown group with at most 5 options (it did: one chip showing "Date added (newest)").
- Selecting a pill reloads the tab as the dropdown items did.

## 6. Manual test checklist
1. Search for something, open the Playlists filter on a wide window: pictures are no wider than the channel page's.
2. Open a channel > Playlists: the sort shows as pills (e.g. Date added (newest), Last video added); the active one is highlighted; tapping another reloads the list in that order.
3. Open a channel > Videos: Latest / Popular / Oldest still work.
4. Narrow the window or use the phone: playlist rows look as before.
