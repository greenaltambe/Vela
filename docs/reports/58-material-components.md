# 58 Material components (phase 4 of 5)

## 1. Summary
The custom building blocks are gone in favour of Material 3 components. Buttons, the video page's action row, Subscribe, the search fields, switches, segmented choices, sliders in Settings, list rows in Settings and Customize, toasts, loading indicators and pull-to-refresh are now Material's own (expressive where the library has an expressive version). Video cards use the Material ripple instead of the custom press squeeze and hover outline. The progress bar (`Scrubber`) and the player controls keep their customization. The sheets inside the video page (comments, description, chapters) were restyled by the theme only, not rebuilt as `ModalBottomSheet`.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests` + `:desktopApp:test` (1,086 test cases, 0 failures) | pass | running |
| `:androidApp:assembleDebug`, `:desktopApp:smokeTest` | pass | compile / running |
| Desktop renders (phone size, light and dark): video page, Settings, a Settings page, Customize, Search | seen, look right | running (test render) |
| Anything on the phone: ripple feel, the snackbar position, pull-to-refresh indicator, slider dragging | not tested | compile-only |
| Home, Channel, Shorts, Library, Downloads, Accounts renders | not looked at | not verified |

## 3. Files created/changed
- `ui/components/Components.kt`: `PrimaryButton` / `SecondaryButton` / `LinkButton` are thin wrappers over `Button` / `OutlinedButton` / `TextButton`; `ActionChip` is a `FilterChip` with a leading icon (solid icon while on); `VideoCard` uses `combinedClickable` with the theme's indication, large corners; the nav marker and the rest of the old bar code were already gone.
- `ui/components/SubscribeButton.kt`: `Button` to subscribe, `FilledTonalButton` once subscribed, haptics kept.
- `ui/components/SearchField.kt`: a filled `TextField` in a pill with the search icon and a clear button.
- `ui/components/States.kt`: `LoadingSpinner` (Material's `LoadingIndicator`); `RefreshBox`: expressive pull-to-refresh indicator.
- `ui/look/Motion.kt`: removed `pressFeedback`, `focusRing`, `hoverWash`, `popOnSelect`, `SegmentedPills`, `VelaSwitch`, `InputModality`; `pressable` is now plain `clickable`; `UndoHost` draws a Material `Snackbar` (`UndoSnackbarHost`, placed by the shell above the mini player and the bar).
- `ui/screens/SettingControls.kt`: `Switch`, `SingleChoiceSegmentedButtonRow` (2 to 4 short choices) or filter chips, M3 `Slider` (value written when the thumb is released).
- `ui/screens/SettingsHost.kt`, `CustomizeScreens.kt`: `ListItem` rows; settings search is a pill field with a search icon.
- Loading: `CircularProgressIndicator` replaced in Channel, Shorts, Description sheet, player buffering, desktop refresh button.
- `ui/AppShell.kt`: undo snackbar in the page area; keyboard-modality tracking removed.
- Tests: `MotionTest` (the custom switch and pills test removed).

## 4. Decisions the spec didn't dictate
- Thin wrappers (`PrimaryButton` etc.) were kept for the label-only convenience; they add no styling.
- The like / dislike pair is two filter chips side by side (not a split button): the library's split button does not toggle.
- Settings sliders use Material's slider; the player's seek bar and the Customize "Progress bar" previews still use `Scrubber`.
- `ModalBottomSheet` was not adopted for the comment / description / chapter panels: they sit inside the morphing video page and share its gestures, so they keep their structure.
- Skeleton shimmers stay (Material has no skeleton component).

## 5. Known issues / unverified assumptions
- Not run on a device (phone away after phase 3): ripple on large cards, the snackbar overlapping the Android navigation bar, the pull-to-refresh indicator, the wavy and morphing shapes at 60 fps.
- The shell-level snackbar shows only above the page; a toast raised while the video page is open sits under the sheet.
- `LoadingIndicator`, `PullToRefreshDefaults.LoadingIndicator` and the segmented row use experimental or alpha APIs of material3 1.12.0-alpha03.
- `HScrollRow` (the sideways row with desktop arrows), `Avatar`, `SectionHeader`, `Carousel` and the empty / error states remain custom compositions of Material parts.

## 6. Manual test checklist
1. Video page: Like / Dislike / Save / Share are chips, filled and with a solid icon when on; Subscribe is a filled button, then tonal.
2. Tap cards in Home, Search and Subs: a ripple (no squeeze); long-press a DeArrow card to flip the title.
3. Settings: rows are list items; switches and the 2 to 4 choice segmented rows work; drag a slider (playback speed) and check the value word updates and the setting saves on release.
4. Do something with an Undo (hide a video on Home): the snackbar appears above the bar, Undo works, it goes after ~5 s.
5. Pull down on Home: the expressive loading indicator; open a slow channel page and check the in-place loading shape.
6. Search page and Settings search: pill field, search key submits, clear works.
7. Dark mode on all of the above.
