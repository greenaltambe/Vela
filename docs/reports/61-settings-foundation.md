# 61 Settings redesign, phase 1: foundation, home, generated pages

## 1. Summary
Settings now has one frame and one row system. Every settings screen gets Material's large flexible top bar whose title collapses as the list scrolls, a centred column and the same margins. Rows are segmented cards (large corners at the ends of a stack, small where they meet) with a short title, a one-line description and a control: a switch row, segmented buttons for two or three short options, a picker row that opens a bottom sheet for longer lists (searchable for the 52 countries), a slider with its value and unit, and a text value that opens a dialog and saves once. On windows of 840 dp and wider Settings is two panes: the list on the left, the open page on the right. The SponsorBlock categories fold into one expandable row. The Customize hub is on the new rows; the Customize pages, Accounts, Backup, Health, Download rules and Account activity are phases 2 and 3.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests` + `:desktopApp:test` (1,090 test cases, 0 failures) | pass | running |
| New `SettingItemTest`: switch, segmented option, picker sheet, slider reset icon, text dialog (one write, none while typing), two-pane (list stays, page replaced, Back leaves in one step) | pass | running |
| `SettingsUiTest`, `LayoutInvariantsTest` (dead Customize routes pruned, Customize pages, Feed and Downloads pages added) | pass | running |
| `:androidApp:installDebug`, `:desktopApp:smokeTest` | pass | install / running |
| Desktop renders: home (light), Playback (dark), Extras, Safety (dark), 1280 x 800 two-pane | seen, look right | running (test render) |
| Phone: Settings home in dark mode (collapsing title, tiles) | seen | running on device |
| Phone: pages, picker sheet, dialog, sliders, light mode | not looked at on the phone | not verified |

## 3. Files created/changed
- New `core/settings/SettingCopy.kt`: short titles, one-line descriptions and slider units for about 70 settings (`Setting.title`, `.description`, `.unit`).
- New `ui/settings/`: `SettingsScaffold.kt` (collapsing large bar, centred list, `settingsSection`, `segmentedShape`, `segmentedItemColors`), `SettingItem.kt` (all control types, `SettingChoiceSheet`, `SettingTextDialog`).
- `ui/screens/SettingsHost.kt` rewritten: `SettingsHome` (search, account row, groups, tonal expressive-shaped icon tiles), `SettingsPageScreen` (sections, "Manage ..." row for linked pages, scroll-to-highlight from search, page reset in the top bar's menu), `SettingsPaneHost` (two panes at >= 840 dp), `SettingsRouteContent`.
- `ui/AppShell.kt`: the Settings routes (Settings, pages, Customize, Accounts, Backup, Health) go through `SettingsPaneHost`; in the wide layout the area is one animation destination, so only the right pane animates.
- `ui/components/Components.kt`: `VelaTopBar` takes a scroll behaviour and hides its Back arrow while embedded in the right pane (`LocalEmbeddedInPane`).
- `core/settings/Settings.kt`: `home.source` gets its two choices (it was a free text field).
- `ui/screens/SettingControls.kt` reduced to `setWithUndo`; old `SettingRow`, `SettingsBlock`, `SliderWord`, the placeholder boxes and the slide-length delay are gone.
- `ui/screens/CustomizeScreens.kt`: uses `SettingItem` and the shared shapes.
- `ui/theme/MaterialOutlined.kt`, `MaterialFilled.kt`: the "more" icon is the vertical three dots (it was sideways).
- Tests: `SettingItemTest` (new), `SettingsUiTest`, `LayoutInvariantsTest`, `Task23ScreensTest`.

## 4. Decisions the spec didn't dictate
- Titles and descriptions come from a copy table keyed by setting key instead of rewriting the schema's labels; the long labels stay as the searchable sentence, so search, backup and tests are unchanged.
- Per-page reset is in the top bar's overflow menu (`settings.pageMenu` then `settings.resetPage`); sliders show a reset icon while changed (invisible, same space, when at the default); other controls reset through the page menu or, for pickers and text, a "Reset to default" action in the sheet or dialog.
- Slider tick marks are drawn only when a range has 9 steps or fewer.
- Rows that link to another screen (Health, Automatic downloads) show one "Manage ..." row on top (`settings.openLinked`).
- In the two-pane list, picking a page uses `replace` once a Settings page is open, and `push` from the bare Settings route.
- Settings home gets a Back arrow on phones when there is somewhere to go back to (it had none).

## 5. Known issues / unverified assumptions
- Account activity, Download rules, Diagnostics and the Profile menu are not in the two-pane (they are separate full screens); Accounts, Backup and Health are in it but still draw their old (phase 3) content.
- The Customize hub and its pages still use the old layout around the new rows until phase 2.
- `account.tierOrder` is still a text field (a reorder UI is a follow-up).
- Alpha APIs: `LargeFlexibleTopAppBar` with `exitUntilCollapsedScrollBehavior`, `SegmentedListItem`, `MaterialShapes`.
- Not looked at on the phone: the picker sheet, the text dialog, slider dragging, the collapsing title on the sub pages, light mode.

## 6. Manual test checklist
1. Settings: the large title shrinks as you scroll; rows are rounded cards with small gaps; tiles are tonal shapes.
2. Open Playback: sliders show a value with its unit (1x, 10 s), a reset icon appears when changed, and the video segmented buttons / quality picker row open a sheet.
3. Open Playback > Maximum quality on Wi-Fi: a sheet with a radio list; Safety > Order sources are tried in: a dialog, nothing saved until Save.
4. Extras: "Segments by category" expands to ten rows; Home page: "Home shows" is a picker (Recommended / Trending), not a text box.
5. Search "region": a result row; tapping it opens the page scrolled to and highlighting the setting.
6. Page menu (three vertical dots): "Reset this page", then Undo in the snackbar.
7. Rotate a tablet / use a desktop window >= 840 dp: list on the left, page on the right, picking another page changes only the right side, Back leaves Settings.
