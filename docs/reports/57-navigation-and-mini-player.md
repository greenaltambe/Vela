# 57 Navigation chrome and mini player (phase 3 of 5)

## 1. Summary
The bottom bar, side rail, top bar and mini player are now standard Material 3. Narrow windows get Material's expressive `ShortNavigationBar` (docked, system inset handled by it); wide windows get the expressive `WideNavigationRail` (collapsed to icons or expanded with labels, toggled by a menu button); every screen's top bar is a Material `TopAppBar`. The mini player is a standard 64 dp bar sitting on the navigation bar (a `LinearProgressIndicator` strip, thumbnail, title, play/pause, close). The shell keeps a 64 dp strip for it and the player sheet places itself there, so no sizes are hard-coded any more.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests` and `:desktopApp:test` (1,087 test cases, 0 failures) | pass | running |
| `NavigationUiTest` (tabs, labels, profile menu, logo and profile positions, docked bar below the page, mini player as wide as the bar and attached, wide rail collapses and navigates) | pass | running |
| Desktop screenshots: phone light and dark with mini player, 1200x800 with rail and mini player | seen, correct | running (test render) |
| `:androidApp:assembleDebug`, `:desktopApp:smokeTest` | pass | compile / running |
| Anything on the phone (gestures, drag to expand, back, insets) | not tested (phone away) | compile-only |

## 3. Files created/changed
- New `ui/nav/AppNavigation.kt` (`AppNavigationBar`, `AppNavigationRail`, `MiniPlayerSlotSpacer`); `ui/nav/MiniSlot.kt` now carries `left`, `top`, `width` and `MiniPlayerHeight`.
- `ui/AppShell.kt`: new layout (rail, then a column of top bar, banners, page, mini-player strip, bar). Removed: floating / minimal bar, scroll-shrink, `LocalChromeInset` page insets, the desktop mini-player inset guess, the old picture-in-picture position holder.
- `ui/player/PlayerSheet.kt`: the collapsed card is the strip's rectangle (full width, square corners, `surfaceContainer`, top divider, `LinearProgressIndicator`); expanded it fills the content area beside the rail (or the window on a phone and in fullscreen). In-app picture-in-picture look and its drag overlay removed.
- `ui/screens/VideoScreen.kt`: thumbnail position for the 64 dp bar; the page is laid out at the sheet's real width.
- `ui/components/Components.kt`: `VelaTopBar` on `TopAppBar`; deleted `BottomNav`, `SideRail`, `MiniPlayer`, `MiniState`, the nav marker. Deleted `ui/nav/BottomChrome.kt`, `PipPosition.kt`.
- `ui/nav/NavConfig.kt`: only `tabs` and `railCollapsed` left; enums `BarStyle`, `NavLabels`, `MiniLook`, `MiniPlacement` deleted.
- `LocalChromeInset` usages removed from Refresh, Shorts, Backup, Health, Accounts, Account safety screens and `Spacing.contentPadding`.
- Tests: `NavigationUiTest`, `NavConfigTest`.

## 4. Decisions the spec didn't dictate
- The top bar is static (no scroll-away `enterAlways` behaviour); adding it later is one parameter and a `nestedScroll`.
- Pages no longer run under a floating bar, so they need no bottom inset; the strip and the bar are laid out below them.
- The rail's collapsed choice is still a stored setting (`look.nav.railCollapsed`), not only UI state, so it survives restarts.
- The sheet's thumbnail is 64x36 dp with a 12 dp margin (was 8 dp / 10 dp).
- `LayoutInvariantsTest` still ignores controls drawn under 24 dp high (list edge slivers).

## 5. Known issues / unverified assumptions
- Not run on a device: the drag from the bar to the full video page, back handling with the sheet open, gesture-navigation insets, the Android 3-button bar, and landscape phones. The same code path (strip position) is exercised on desktop only.
- `ShortNavigationBar` and `WideNavigationRail` are experimental APIs in material3 1.12.0-alpha03; the opt-in is local to `AppNavigation.kt`.
- No in-app picture-in-picture card any more (the OS picture-in-picture is unchanged).
- Tab labels are clipped, not wrapped, on very narrow windows.

## 6. Manual test checklist
1. Phone: bottom bar is the plain Material bar with four tabs; selecting one fills its icon.
2. Play a video, collapse it: a 64 dp bar sits directly on the bar with a thin red progress strip; tap it to open, drag it up to open, swipe the page down to collapse; pause and close buttons work.
3. Rotate to landscape and open a video; return to portrait.
4. Tablet / desktop: rail on the left with a menu button that collapses it to icons; the mini player sits at the bottom of the content area, not under the rail.
5. Android back with the video page open collapses it; with it collapsed goes back a screen.
6. Gesture navigation vs three-button navigation: bar and mini player clear the system bar.
7. Settings > Customize no longer mentions tabs, bar style or mini player (already gone in phase 1).
