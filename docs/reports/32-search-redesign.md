# 32 – Search field and start page

## 1. Summary
New `VelaSearchField` (rounded by the corner-radius setting, surface colours, palette-gradient outline while focused) replaces the Material outlined field. With nothing typed, the Search page shows recent searches as pills, recently watched videos and subscribed channels; typing hides them and leaves the field and suggestions. Search can already be a bar tab (Customize > Tabs & mini player > "Tabs in the bar"); it is now highlighted while the Search page is open.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| Phone: focused field with gradient outline, pills, suggestions replacing the start page when typing | seen | phone screenshots |
| Recently watched and Subscriptions rows | not seen (list was below the fold / not scrolled) | compile-only |
| Search tab highlighted when added to the bar | not tested | compile-only |

## 3. Files changed
`ui/components/SearchField.kt` (new), `ui/screens/SearchScreen.kt`, `ui/nav/NavConfig.kt`

## 4. Decisions
- The Search tab was already an option in the tab list, so no new setting was added.
- The search icon inside the field submits (tag `search.submit`); the separate submit button is gone.
- Recently watched uses the history table (no stored thumbnail or channel name, so thumbnails come from the video id and the channel name is blank until the video opens).

## 5. Known issues
- The floating bar still sits over the page above the keyboard while searching (unchanged).
- Other Material text fields elsewhere in the app (dialogs, settings) still use the old style.

## 6. Manual test checklist
1. Open Search: field focused with a gradient outline; recent searches are pills (tap searches, x removes, Clear empties).
2. Scroll down: Recently watched and Subscriptions rows; tap a video / channel.
3. Type: everything but the field and suggestions disappears; clear the field: it returns.
4. Customize > Tabs: switch Search on; the bar shows it and highlights it on the Search page.
