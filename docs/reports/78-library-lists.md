# 78-79 History, Downloads and Playlist on one list design

## 1. Summary
History, Downloads and each playlist now share one frame (`LibraryScaffold`: a medium collapsing app bar with a back arrow, the title in up to two lines, a subtitle such as "15 videos" or "77.4 MB used · 116.9 GB free", and an overflow menu) and one row (`MediaRow`: 16:9 picture with duration pill and a bar along its foot, two-line title, supporting lines, trailing buttons), directly on the page with sticky tonal group labels (Today / Yesterday, Downloading / Waiting / Saved). Swipe-to-delete is gone: long press selects rows (a "N selected" bar with Select all, Save, Download, Delete); every removal is hidden at once and shows Undo, and the real delete runs after the toast. History: search in the bar, the YouTube account history is a menu item (loaded once, under its own label), Clear all in the menu with one confirm. Downloads: a slim wavy storage bar, the Wi-Fi banner kept, one tonal button per row for pause / resume / retry, folder / automatic / settings in the menu, a loading shape on the picture of active downloads. Playlist: Play all and Download all on one row, rows with move / remove in their menu.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests`, `:desktopApp:test` (history, large-list, layout-invariant and merge tests adapted) | pass, 0 failures | running |
| Phone: History (rows, sticky day pill, subtitle), selection mode (2 selected, bar actions), Downloads (storage bar, Saved group, rows) | seen | running on device |
| Phone: Undo toast after delete, Playlist page, downloading / failed rows, Clear all, account history, desktop widths | not looked at | not verified |

## 3. Files created/changed
- New `ui/components/{MediaRow,LibraryScaffold}.kt` (`MediaRow`, `MediaRowSkeleton`, `LibraryScaffold`, `PageAction`, `DeferredRemoval`), `ui/screens/{HistoryScreen,PlaylistScreen}.kt`, `SaveManySheet` in `SaveSheet.kt`.
- Rewritten: `DownloadsScreen.kt` (screen and row), the old History / Playlist code removed from `LibraryScreens.kt`.
- Tests adapted: `HistoryMergeUiTest` (opens the menu first), `LargeListsUiTest` (scrolls to the "older" button).

## 4. Decisions the spec didn't dictate
- Row title tag stays `card.title` and the picture `card.thumbnail`, so the layout checks keep covering these rows.
- A deleted download keeps downloading for the 5 s of the toast, then is stopped and removed.
- Playlist Rename / Delete playlist stay on the Library page's tile menu (not moved into this page's menu).

## 5. Known issues / unverified assumptions
- No shuffle on playlists (the app has no shuffle play yet).
- Desktop (1280 x 800) rendering of these three pages was not looked at.
- `MediumFlexibleTopAppBar` with a subtitle worked on the phone; the subtitle is one line and cut with an ellipsis.

## 6. Manual test checklist
1. History: long press a row, tap two more, Delete: rows vanish with an Undo toast; Undo brings them back; wait: they stay gone.
2. History menu: search, include account history, Clear all.
3. Downloads: start one on mobile data (banner and buttons), pause / resume / retry from the row button, delete from the row menu and from a selection, Undo.
4. A playlist: Play all, long press to select and Remove, row menu Move up / down, Download all.
