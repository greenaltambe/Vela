# 114 Search start page: recent searches only

## 1. Summary
The empty Search page now shows only "Recent searches" (with Clear all). The "Recently watched" and "Subscriptions" carousels are gone. Each recent search has the same up-left arrow as a suggestion: it puts the text in the search field (with a trailing space, no search) so it can be edited; typing then shows suggestions as usual. The cross to remove one stays.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `allTests`, desktop + Android compile | pass | verified-by-running / compile-only |
| Look and behaviour in the app | not run | not verified |

## 3. Files created/changed
- Changed: `ui/screens/SearchScreen.kt` (`SearchStart`)
- Created: this report

## 4. Decisions the spec didn't dictate
- With no history the page says "Search for videos, channels and topics." as before.
- Test tag for the arrow: `search.recent.fill`.

## 5. Known issues / unverified assumptions
- None known.

## 6. Manual test checklist
1. Open Search with history: only the recent list shows, no carousels.
2. Tap the arrow on a recent search: it appears in the field (cursor after it), suggestions show, nothing is searched.
3. Tap the row itself: it searches. The cross removes the entry; Clear all empties the list.
