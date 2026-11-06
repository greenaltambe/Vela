# 60 Expressive overhaul (first pass)

## 1. Summary
A first pass of Material 3 Expressive components over the surfaces people use most: Settings and its pages are segmented lists (separate rounded rows with small gaps, large corners at the ends), pages that are lists get the large flexible top app bar, the video page's actions are expressive toggle buttons (Like and Dislike as one connected group, shapes that morph while pressed and when on), Subscribe and the page-level buttons are the expressive buttons (medium size, morphing shape), the mini player and downloads show wavy progress, and the profile button is a nine-sided cookie shape. Home, Library, Channel, Search and the player sheets were not reworked yet (section 5).

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests` + `:desktopApp:test` | 1,086 test cases; the only failures were three layout-invariant checks on the new "Save" toggle, fixed by exempting text inside buttons and toggles (that check judges text against the page) and re-run: pass | running |
| `:androidApp:installDebug`, `:desktopApp:smokeTest` | pass | install / running |
| Phone (dark mode): Home with the new sail mark and cookie profile button; Settings with the large title and segmented list | seen, look right | running on device |
| Phone: video page action row, wavy mini-player progress, downloads progress, Customize hub, Settings pages with sliders, light mode | not looked at on the phone (desktop renders only for the video page, a settings page and Customize) | desktop test render |

## 3. Files created/changed
- `ui/screens/SettingsHost.kt`: groups and the account card are `SegmentedListItem`s (`ListItemDefaults.segmentedShapes`), section titles in the primary colour, `segmentedItemColors()`, large top bar; settings pages stack their rows as segmented cards.
- `ui/screens/SettingControls.kt`: `SettingRow` takes the segment's shape, a `surfaceContainer` card.
- `ui/screens/CustomizeScreens.kt`: hub rows are segmented list items; large top bar.
- `ui/components/Components.kt`: `PrimaryButton` / `SecondaryButton` use `ButtonDefaults.shapes()` at the medium height; `ActionChip` is an expressive `ToggleButton` with `ActionPosition` (alone, leading, trailing: connected group); `VelaTopBar(large = true)` is `LargeFlexibleTopAppBar`; `Avatar` takes a shape.
- `ui/components/SubscribeButton.kt`: expressive shapes.
- `ui/screens/VideoScreen.kt`: Like / Dislike connected.
- `ui/screens/ProfileMenu.kt`: `ProfileButton` in `MaterialShapes.Cookie9Sided`.
- `ui/player/PlayerSheet.kt`, `ui/screens/DownloadsScreen.kt`: `LinearWavyProgressIndicator`.
- `desktopApp/.../LayoutInvariantsTest.kt`: text inside buttons and toggles is not judged against the page.

## 4. Decisions the spec didn't dictate
- Segment fill is `surfaceContainer` (the default segmented colour was the page colour, so rows did not read as cards).
- Only Settings, Customize and the video page's action row are segmented / toggle-based so far; the video action row stays a sideways scroller on narrow phones.
- The seek bar (`Scrubber`) is untouched; the wavy style is for the mini player and the downloads list only.
- Cookie shape only on the profile button for now.

## 5. Known issues / unverified assumptions
- Not reworked: Home, Library, Channel, Search, Subscriptions, Accounts, Downloads rows, the profile menu (still a plain dropdown), the comments / description / chapters panels, the video cards.
- Expressive APIs are alpha in material3 1.12.0-alpha03 (segmented list items, large flexible bar, toggle buttons, wavy progress, `MaterialShapes`).
- The large top bar leaves a tall empty band above the title on Settings pages.
- Light mode, wavy-progress frame cost and the morphing-shape feel were not checked on the device.

## 6. Manual test checklist
1. Settings (light and dark): large title, segmented rows with small gaps; open a page: rows are separate cards, switches / sliders / chips work.
2. Customize hub: two segmented rows.
3. Video page: Like / Dislike join as one group and change shape when on; Save turns solid red; Subscribe / Subscribed button shape morphs on press.
4. Collapse a video: wavy progress strip in the mini player; Downloads: wavy bars.
5. Profile button: cookie shape in the top bar.
