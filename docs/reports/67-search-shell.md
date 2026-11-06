# 67 Search shell (phase 1 of 2)

## 1. Summary
Search has a new frame: a pill search bar with a tune button, one row of expressive toggle chips (All, Videos, Shorts, Live, Channels, Playlists) with a check on the chosen one, a filter bottom sheet (length as segmented buttons, order as a radio list, Reset) instead of two dropdown chips, and segmented rows for recent searches and suggestions. Shorts has its own 2-column grid, fed by a separate "<query> #shorts" search because the video search returns almost no Shorts. A pre-existing bug is fixed: a search list opened scrolled several items down (it anchored on its "Load more" footer). Paging is now automatic (the button is gone), and the video page no longer prints details errors; it shows a short auto-hiding message. The mixed "All" page (channel card, Shorts shelves) is phase 2.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests`, `:desktopApp:test`, `:androidApp:installDebug` | pass, 0 failures (`SearchFiltersUiTest` updated for the sheet) | running |
| Phone: start page, results, chips, Shorts grid (real Shorts), filter sheet, scroll-position fix | seen | running on device |
| Phone: Live, Channels, Playlists chips | not opened | not verified |
| Phone: light mode, tablet / desktop width | not looked at | not verified |
| Details notice on the video page | not triggered (details loaded) | not verified |

## 3. Files created/changed
- New `ui/search/SearchParts.kt`: `SearchType`, `SearchTypeChips`, `FilterTuneButton`, `SearchFilterSheet`, `SearchTextRow`, `ShortsGrid`.
- `ui/screens/SearchScreen.kt`: new header and chip row, Shorts feed, views for Videos / Live, friendly error text, segmented recent searches and suggestions, automatic paging with a placeholder footer.
- `ui/screens/Lists.kt`: the footer only appears once there is a video (scroll anchor bug).
- `ui/screens/VideoScreen.kt`: details error becomes a toast ("Some details couldn't be loaded right now.").
- `desktopApp/.../SearchFiltersUiTest.kt`.

## 4. Decisions the spec didn't dictate
- Videos and Live are views of the loaded results; if fewer than 8 match they fetch up to 4 more pages.
- Shorts are kept from the "#shorts" search when YouTube marks them or they last 60 s or less.
- The tune button is hidden on the Channels and Playlists chips (the filters do not apply).
- Mic button not added (no speech input in the app).

## 5. Known issues / unverified assumptions
- Result cards on All are still the horizontal rows; the full-width cards, channel card and Shorts shelves come in phase 2.
- Filters apply to loaded results only (as before).
- The "#shorts" search can return non-Shorts of 60 s or less (tiny videos); not verified how often.
- The toast reuses the undo toast host (5 s), not a separate component.

## 6. Manual test checklist
1. Search "kotlin coroutines": the list starts at the top, more results arrive as you near the end.
2. Tap Shorts, Videos, Live, Channels, Playlists; tap the tune button, pick "< 4 min" and "Most viewed", Reset.
3. Empty search field: recent searches as rows with X; tap one; "Clear all".
4. Type a few letters: suggestions as rows; the arrow copies the text.
5. Open a video where details fail (airplane mode after it starts): a small message appears and goes away.
