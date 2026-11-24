# 139 Explore tab, navigation bar customization, Filter icon

## 1. Summary
Explore is now a tab (Home, Shorts, Subs, **Explore**, Library) in the bottom bar and the side bars. Customize has a new page, Navigation bar, with a live
preview of the bar, the tabs in order, a switch for Explore, a row that opens the Shorts setting, and a switch for the names under the icons. The Home Filter
button now has an icon. Shorts keeps its own setting and still removes its tab.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `NavCustomizationUiTest` (9): five tabs fit a 320 dp phone without overlap and every name inside the bar; the page shows preview, rows and switches; the Explore switch removes and restores the tab at once; names off leaves icons only, five tabs still work and icons are described for screen readers; Browse Explore still opens Explore with Explore hidden and no tab looks selected; the Shorts row shows its state and opens the Shorts settings; the hub row and summary; wide window: folded rail drops names, unfolded keeps them; Reset this page restores both | pass | running (desktop Compose UI tests) |
| `ShortcutsUiTest`: Ctrl+1..5 follow the visible tabs; hiding Shorts or Explore renumbers them; a number past the last does nothing | pass | running |
| `NavConfigTest`, `LookSettingsTest`, `NavigationUiTest` updated; `./gradlew allTests` and the full `:desktopApp:test` (all desktop UI tests) | pass | running |
| `:androidApp:compileDebugKotlin` | pass | compile-only |
| Looks on a real phone (five tabs, icons-only bar, the page) | not checked | needs the owner |

## 3. Files created/changed
- `core/settings/LookSettings.kt`: default tabs now include Explore; new settings `look.nav.showExplore` and `look.nav.showLabels` (both default on); `SettingCopy.kt`.
- `ui/nav/NavConfig.kt`: Explore and `showLabels` follow the settings. `ui/nav/AppNavigation.kt`, `ui/AppShell.kt`: bar, rail and desktop side bar take `showLabels`.
- `ui/screens/CustomizeScreens.kt`: the "Navigation bar" page (preview, Tabs, Names) and its hub row and summary.
- `ui/screens/HomeShelvesUi.kt`: Filter button icon (tune); `ui/screens/HomeScreen.kt`: the signed-out sign-in card now sits right after Continue watching (it was below all shelves, out of sight).
- Tests: `NavCustomizationUiTest.kt` (new), `ShortcutsUiTest.kt`, `NavConfigTest.kt`, `LookSettingsTest.kt`, `NavigationUiTest.kt`.

## 4. Decisions the spec didn't dictate
- Explore sits between Subs and Library. Home, Subs and Library cannot be hidden (the page says "Always shown"), so the bar never ends up with fewer than three tabs. Shorts is hidden only through its own setting; the row here shows its state and opens it, so the two never disagree.
- Names off: the bottom bar and the folded side bar show icons only; the icon carries the tab's name for screen readers. The unfolded desktop side bar and the unfolded wide rail always show names (names are what the unfolded form is).
- Keyboard: Ctrl+1..9 stay "the nth visible tab". With Explore on, Ctrl+4 is Explore and Ctrl+5 is Library for people who have Shorts on (Ctrl+4 used to be Library). Turn Explore off to get the old numbers back.
- The preview is drawn by hand (not the real bar) so the page has one real bar only; screen readers get one line ("Preview of the navigation bar with N tabs").
- The settings are `look.` keys, so a saved Look includes them.

## 5. Known issues / unverified assumptions
- With names off there is no hover or long-press hint on the icons (screen readers do announce them). Adding tooltips would need wrapping each bar item, which Material's bar layout may not allow; not tried.
- The Explore icon is the existing compass; Explore and its selected (filled) form look different only slightly on some themes.
- With Explore hidden and the page opened by a "Browse Explore" button, no tab is highlighted until you leave it.
- Five tabs on a narrow phone: tested down to 320 dp in the desktop test harness, not on a device.

## 6. Manual test checklist
1. Open the app: the bottom bar shows Home, Shorts, Subs, Explore, Library. Tap Explore: Gaming opens first, pills have icons.
2. Settings > Customize > Navigation bar: a preview of the bar, then the five tabs (three say Always shown, Shorts shows Shown/Hidden and opens the Shorts settings, Explore has a switch), then "Names under the icons".
3. Switch Explore off: it leaves the real bar at once and the preview; an Undo toast appears; Undo brings it back.
4. Switch names off: the bar becomes icons only; the tabs still work; with TalkBack on, each icon is read with its name.
5. On desktop: with the window wide, the side bar still lists names unfolded; fold it and the names disappear when "Names under the icons" is off. Ctrl+1..5 open the tabs in order; with Explore off, Ctrl+4 is Library.
6. Home: the Filter button now has a slider icon.
7. Signed out: Home shows Continue watching (if any), then the sign-in card, then the other shelves.
