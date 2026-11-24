# 142 Translucent top bar, Home chip strip and mini player (one switch)

## 1. Summary
The one switch, now called "Translucent bars" (Customize > Navigation bar), makes four things see-through with the same blur: the top bar, Home's chip strip, the
bottom bar (as before) and the collapsed mini player. The page runs under all of them; lists get matching room at the start and end so the first and last card
can be scrolled clear. Phones and narrow windows only (no desktop top bar); on Android 11 and older each is a nearly opaque tint instead of a blur.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| Top bar: solid page starts below it; translucent the page starts at the window top under it and the first card starts below the bar | pass | running (desktop Compose UI test, 400x800) |
| Home: the chip strip starts below the top bar, the list runs under both, the first list item starts below the strip; solid Home unchanged | pass | running |
| Bottom bar tests of report 141 (page under bar, last card clears it, mini player stays attached, Library clears it) | pass | running |
| The blur itself (stripes blurred behind a bar, sharp above it) | pass | running (`FrostedBarUiTest`, Haze on desktop) |
| `./gradlew allTests`, the full `:desktopApp:test`, `:androidApp:compileDebugKotlin` | pass | running / compile-only |
| The mini player's blur while collapsed and its fade-out as the page opens | not checked by pixels | needs the owner |
| How all four look on the phone, and the pull-to-refresh spinner under the top bar | not checked | needs the owner |

## 3. Files created/changed
- `ui/AppShell.kt`: the page area is one box; the top area (top bar, profile menu, banners) and the bottom area overlay it when translucent; both insets are measured; the backdrop is shared with the player sheet.
- `ui/nav/BarInset.kt` (`LocalTopBarInset`), `ui/screens/Lists.kt` (top inset in lists), `ui/components/RefreshBox.kt` (the pull spinner starts below the top bar).
- `ui/components/Components.kt`: `VelaTopBar` translucent mode. `ui/screens/HomeScreen.kt`: with a top inset Home lays its chip strip over the list, blurred by the list under it; the strip's height is held while it hides on scroll.
- `ui/player/PlayerSheet.kt`: the collapsed docked mini player is blurred (the floating window is not); `ui/nav/FrostedBar.kt`: `active` switch so the blur follows the sheet's progress.
- `core/settings/LookSettings.kt`, `SettingCopy.kt`: wording of the switch (same key `look.nav.translucent`, so the saved choice carries over).
- Tests: `NavCustomizationUiTest.kt` (three new).

## 4. Decisions the spec didn't dictate
- One switch for all four, as asked. Which pages run under the bars is unchanged: Home, Explore and Subs; other pages are padded by the shell and end below the top bar and above the bottom bar.
- The top bar's tint is the page colour (62 % with blur, 94 % without), the bottom bar's the bar colour; the chip strip uses the page colour so it reads as part of the top.
- On the pages that show the shell's top bar only; the video page, search, channel and settings keep their own bars.

## 5. Known issues / unverified assumptions
- A tall banner (account warning) shown under the top bar becomes part of the see-through top area and the list starts below it.
- While the chip strip is hidden by scrolling, the list keeps its top room (no jump) so a gap shows only when you scroll back to the very top.
- Pull-to-refresh was moved down by the top inset but not seen.
- The mini player's blur fades with the page opening by Haze's style following the progress; this was reasoned from Haze's documentation and not seen.

## 6. Manual test checklist
1. Customize > Navigation bar: the switch is called "Translucent bars". Turn it on.
2. Home: videos slide under the top bar (mark, search, bell, avatar) and under the chip strip, both blurred; the first video starts below the chips; scrolling down hides the chips and the list does not jump.
3. Explore and Subs: the same under the top bar; Library, Downloads, channel pages are as before.
4. Pull down on Home: the spinner appears below the top bar.
5. Play a video and minimise it: the mini player is blurred over the page; open it: the page opens solid with no leftover tint; minimise again.
6. Bottom bar as in report 141. Switch the setting off: everything is solid again.
7. Check legibility in Classic and one other colour scheme.

## Addendum: one frosted surface per pair (from the phone screenshots)
- Symptom: the top bar and the chip strip looked clearly different (pastel blur against a flat grey band), and the mini player and the bottom bar slightly different, with a visible seam between each pair. Each piece had its own blur of its own slice of the page.
- Change: each pair is now one frosted surface with one blur and one tint. The top area (top bar, banners and Home's chip strip) is a single frosted block; the bottom area (the mini player's room and the bottom bar) is another. Home publishes its chip strip into the shell's top area (`TopExtras`) so the strip is drawn inside that block; the top bar and the bottom bar paint no background of their own, and the mini player draws none while collapsed. One hairline marks the edge of each block facing the page.
- Verified: the three layout tests for the top bar and the strip, the existing bottom-bar and mini-player tests, `allTests`, the full `:desktopApp:test` and the Android compile pass. Not verified: how it looks on the phone, in particular whether the seam is gone.
- Unchanged: the strip's room is still held while it slides away on scroll; wide windows with the desktop top bar are not affected.

## Addendum 2: the strip stays while the mini player is swiped away
- Symptom: while the docked mini player was being swiped away (it had faded), the frosted strip it sat in stayed at full strength until the video was closed.
- Change: the part of the bottom frosted block that is the mini player's strip, and the hairline above it, now fade with the swipe (`swipeAlpha`), as a mask on the one blur, so the rest of the block (behind the bottom bar) stays frosted and there is still a single surface.
- Verified: `FrostedBarUiTest` renders stripes under a frosted area, fades its top half and checks that half shows the page sharp again while the other half stays blurred; `allTests`, the full `:desktopApp:test` and the Android compile pass. Not verified: the swipe itself on the phone (the test changes the fade value directly).
