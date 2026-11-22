# 128: Search via the top bar pill, playlist toasts

## 1. Summary
Search is simplified (the earlier field-focus/cross-fade work was reverted). Desktop: on a results page the top bar's search pill shows the searched
text; clicking it or pressing `/` opens the typing view (the search page's field, suggestions, recent searches) with that query already in the field.
Typing, suggestions and recents are the unchanged original behaviour. Adding/removing a video in a local playlist raises an Undo toast.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `:desktopApp:compileKotlin`, `:androidApp:compileDebugKotlin` | pass | compiled |
| `./gradlew :desktopApp:test` (all UI tests) | pass | verified by running |
| "/" focuses the field on the search page | not tested (no key-event test written) | unverified |
| Playlist toast appears | not tested (no new test) | unverified |

## 3. Files created/changed
- Search: `ui/nav/Navigator.kt` (`Route.Search.edit`), `ui/AppShell.kt` (pill shown with the query, `/` and pill open the typing view), `ui/components/Components.kt` (pill text), `ui/screens/SearchScreen.kt` (edit mode; no own field on a desktop results page).
- Toasts: `ui/screens/Common.kt` (video menu), `ui/screens/SaveSheet.kt` (Save to sheet).
- Docs: `docs/plans/backlog-search-and-playlist-toast.md`.

## 4. Decisions the spec didn't dictate
- Toast on add, remove and create-and-add, each with Undo. Watch later (account) keeps its existing message path.
- Typing view from a results page is pushed on top (Back returns to the results); submitting replaces it with the new results.
- Android keeps its own field on the results page (the pill is desktop only).

## 5. Known issues / unverified assumptions
- The search page is still its own route with its own field (not the top bar's pill); this change aligns behaviour, not structure.
- The shell re-grabs focus only on route change, so after clicking a result card "/" may need a click on blank page first.

## 6. Manual test checklist
1. Desktop: search "lofi", press `/`: cursor is in the field and no "/" was typed. Type: suggestions appear; Enter searches.
2. On the results page click the field with the query in it: suggestions for it appear; clear it: recent searches appear.
3. Press `/` from Home: opens search as before.
4. Save a video to a playlist (card menu and the Save sheet): toast "Added to <name>"; Undo removes it. Un-save: "Removed from <name>", Undo re-adds.
5. Android: repeat 2 and 4.
