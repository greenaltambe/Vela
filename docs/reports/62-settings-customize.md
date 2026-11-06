# 62 Settings redesign, phase 2: Customize

## 1. Summary
Customize is now built on the same frame and rows as the rest of Settings. The hub has Theme (segmented buttons), the two page rows with a live summary ("Bold + dot", "6 shelves · Normal"), and the navigation-rail toggle, so every look setting has a visible row (the rail toggle was only reachable by search). The Progress bar page is a radio list where only the chosen style animates its preview. The Home page lists the shelves as rows with a switch and up / down arrow buttons (text buttons replaced), then density and chips, then a "Bring back hidden videos and channels" row. Each page resets from its top bar menu.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests` + `:desktopApp:test` (1,093 test cases, 0 failures) | pass | running |
| New `CustomizeUiTest`: hub rows and the rail switch, a shelf moves down (swaps with the next) and switches off, the progress radio list and the menu reset | pass | running |
| `SettingsUiTest`, `Task23ScreensTest`, `LayoutInvariantsTest` (Customize hub, progress and home pages) | pass | running |
| `:androidApp:installDebug`, `:desktopApp:smokeTest` | pass | install / running |
| Desktop renders: Progress bar (dark), Home page (light) | seen, look right | running (test render) |
| Phone: Customize screens | not looked at | installed, not viewed |

## 3. Files created/changed
- `ui/screens/CustomizeScreens.kt` rewritten on `SettingsScaffold`, `settingsSection`, `SettingItem`, `SegmentedListItem`; `CustomizePages.covered()` now includes the rail toggle.
- Tests: new `CustomizeUiTest`.

## 4. Decisions the spec didn't dictate
- The up / down arrows are always laid out (invisible while a shelf is off) so switching one shelf never moves the others.
- The old `customize.reset` / `customize.resetPage` buttons are now menu items under `customize.menu` (the hub's resets all look settings, a page's resets its own).
- The progress previews are static except the chosen one, to avoid several wave animations at once.
- Tags kept: `customize.row.*`, `progress.*`, `home.shelfRow/Toggle/Up/Down.*`, `customize.home.clearDismissed`, `customize.list`, `customize.page.*`.

## 5. Known issues / unverified assumptions
- Not viewed on the phone yet.
- `ResetPageButton` is no longer used by Customize and can be removed with phase 3.
- The shelf title wraps to two lines on narrow phones; the hint is cut at two lines.

## 6. Manual test checklist
1. Settings > Customize Vela: Theme segmented buttons, "Progress bar" and "Home" rows with a summary under each, the navigation-rail switch at the bottom.
2. Progress bar: tap each style; the selected row tints and its preview moves (wave, ribbon); open a video and check the seek bar.
3. Home: switch a shelf off and on, move one down and up, open Home and check the order; change "Shelves between videos".
4. "Bring back hidden videos and channels" shows the snackbar.
5. Three-dot menu on each page: "Reset this page" then Undo.
