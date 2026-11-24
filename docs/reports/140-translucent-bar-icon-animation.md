# 140 Translucent bottom bar and animated tab icons

## 1. Summary
Customize > Navigation bar has two new settings: "Bottom bar background" (Solid, Translucent, Glass) and "Animate the tab icons" (on by default). With a see-through
bar the page runs underneath it and the bar is laid over the page's bottom edge; Home, Explore and Subs scroll under it and their last card can still be scrolled
clear of it. The tab icons now animate in the way of the Play Store bar: the outlined and filled forms cross-fade, the tapped icon hops and lands with a springy pop,
and the one you leave shrinks back.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| Solid bar: the page ends above the bar. Translucent: the page runs to the window's bottom edge, under the bar | pass | running (desktop Compose UI test, 400x800) |
| Glass bar: after scrolling Explore to its end the last card ends above the bar | pass | running |
| A page the shell pads itself (Library) stays clear of a translucent bar | pass | running |
| The mini player stays attached on top of a translucent bar | pass | running |
| Icon animation: every tab selects cleanly, two quick taps end on the second tab, switching animation off still navigates | pass | running (selection state; the motion itself was not seen) |
| The page offers the background choice and the animation switch; the Customize hub summary says translucent / glass | pass | running |
| `NavConfigTest` (defaults and steps of opacity), `LookSettingsTest` (key set); `./gradlew allTests`; full `:desktopApp:test` (all desktop UI tests) | pass | running |
| `:androidApp:compileDebugKotlin` | pass | compile-only |
| What the translucency and the animation look like on a phone | not checked | needs the owner |

## 3. Files created/changed
- `core/settings/LookSettings.kt`: `NavBarOpacity` (Solid 100 %, Translucent 82 %, Glass 58 %), `look.nav.barOpacity` (default Solid), `look.nav.animateIcons` (default on); `SettingCopy.kt`.
- `ui/nav/NavConfig.kt`: `barOpacity`, `animateIcons`.
- `ui/nav/NavIcon.kt` (new): `AnimatedNavIcon`; used by the bottom bar, the wide rail and the desktop side bar. `ui/nav/AppNavigation.kt`: bar background alpha and the animated icon.
- `ui/nav/BarInset.kt` (new): `LocalBarInset` and the pages that draw under the bar. `ui/AppShell.kt`: the see-through layout (page full height, bar and the mini player's room laid over its bottom edge); `ui/screens/Lists.kt`: lists add the inset to their bottom padding.
- `ui/screens/CustomizeScreens.kt`: the two settings on the Navigation bar page; the preview shows the background over coloured blocks so the effect is visible.
- Tests: `NavCustomizationUiTest.kt`, `NavConfigTest.kt`, `LookSettingsTest.kt`.

## 4. Decisions the spec didn't dictate
- Solid stays the default, so nothing changes until the viewer chooses.
- See-through applies to the bottom bar of phones and narrow windows only (like "hide the bar while scrolling"); the wide rail and the desktop side bar stay solid. The icon animation applies to all three.
- Only Home, Explore and Subs scroll under the bar (they share one list component that takes the inset). Every other page keeps its content above the bar, so no item can ever hide behind it.
- The bottom room is held while the bar slides away on scroll, so lists do not jump.
- No blur (see section 5): Translucent is 82 % opaque and Glass 58 %, a tint, so text behind the bar stays readable at the first and busy at the second.

## 5. Known issues / unverified assumptions
- There is no blur behind the bar (Compose has no cross-platform blur of what is behind a composable without an extra library); on Glass, busy thumbnails show through at full detail.
- The animation was tuned by values (spring damping 0.32 for the pop, 0.42 for the hop, 6 dp hop), not by eye; it may need adjusting after you see it.
- The tapped icon's pop and hop run on the icon only; the selection pill's own animation is Material's.
- With the bar hidden on scroll and the mini player on top, the held room still includes the bar's height.
- The system gesture area under a translucent bar was not examined on a device.

## 6. Manual test checklist
1. Settings > Customize > Navigation bar: the preview now sits over coloured blocks. Change "Bottom bar background" to Translucent, then Glass: the preview, and the real bar, show the page through it.
2. Home, Explore, Subs: scroll; videos slide under the bar. Scroll to the very end: the last video can be lifted fully above the bar.
3. Open Library, Downloads and a channel: their content ends above the bar as before.
4. Play a video and minimise it: the mini player sits right on top of the bar; the list's last item still clears both.
5. Tap each tab: the icon pops, the old one shrinks back; tap quickly between tabs and nothing sticks half-way. Switch "Animate the tab icons" off: icons just swap.
6. With "Hide the bottom bar while scrolling" on, scroll: the bar slides away and the list does not jump.
7. Wide window or desktop: the side bar stays solid; its icons animate too.
