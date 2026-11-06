# 66 Home card width, Library

## 1. Summary
Skeleton cards now use the same geometry as the real cards (corners, clip, text inset, horizontal thumbnail 0.36 width capped at 168 dp, padding), and Channel's skeletons bleed like its cards. The inset-then-full-width jump was not reproducible in four cold-start captures, so this is a consistency fix based on code reading, not a confirmed cure. Library's playlist tiles are now equal height with a scalloped icon badge, the account tile subtitle shows a cloud icon and the count instead of a truncated sentence.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests`, `:desktopApp:test`, `:androidApp:installDebug` | pass (`LibrarySyncUiTest` updated for the new subtitle) | running |
| Home cold start (4 frames) | skeleton and cards both full width | seen on device |
| Library home on the phone, dark | seen after the fix | running on device |
| Library: History, Downloads, a playlist, New playlist dialog, light mode | not looked at this round (History seen in task 64) | not verified |

## 3. Files created/changed
- `ui/components/States.kt` (`SkeletonCard`), `ui/screens/Lists.kt` (one grid decision), `ui/screens/ChannelScreen.kt` (skeleton bleed), `ui/screens/LibraryScreens.kt` (`PlaylistCard`), `LibrarySyncUiTest.kt`.

## 4. Decisions the spec didn't dictate
- Playlist cover without a picture: tonal surface with a Cookie9Sided badge (primary container) instead of the red fill.
- Title keeps two lines of height so tiles in a row match.

## 5. Known issues / unverified assumptions
- The Home jump you saw may come from something else (an image arriving late, the list changing from grid to column); if it persists, record a screen video and tell me which cards.
- A lone playlist card in the grid's second row is normal for an odd number.

## 6. Manual test checklist
1. Home: cold start and pull to refresh; do the placeholders and the cards line up?
2. Channel page: open a channel, switch tabs; placeholders full width like the cards.
3. Library: tiles equal height, cloud icon on account playlists, scalloped badge on empty ones.
4. Open History, Downloads, a playlist and New playlist in light and dark and report what looks wrong.
