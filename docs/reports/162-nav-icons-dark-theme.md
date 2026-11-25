# 162 Navigation icons invisible in the dark theme

## 1. Summary
The icons of the bottom bar and the side rail were drawn black in every theme, so they vanished on the dark one. `AnimatedNavIcon` defaulted its tint to "unspecified", which draws an icon in its own black; it now takes the colour of the item around it. Under "Unreleased" in the changelog.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| Reproduced in rendered dark-theme images, solid and translucent bar | black icons | run (desktop) |
| New `NavIconColorUiTest` (bar and folded rail; dark and light; animation on and off; solid and translucent), without the fix | both tests fail | run |
| The same with the fix | pass | run |
| `./gradlew allTests test :androidApp:compileDebugKotlin` (before a last correction to the new test, whose class was then run again) | pass | run (the Android app compile is compile-only) |
| Look after the fix, dark and light | rendered images checked | run, still images |
| On a phone | NOT RUN | needs the owner |

## 3. Files created/changed
`ui/nav/NavIcon.kt`, new `desktopApp/.../NavIconColorUiTest.kt`, `CHANGELOG.md`, this report.

## 4. Decisions the spec didn't dictate
The desktop side bar already passed its own colour and is unchanged.

## 5. Known issues / unverified assumptions
- The fault came in with the icon animation in 0.6.1-beta.5, so beta.5 and beta.6 both have it.
- Not changed, seen while reading code only: Android's own status and system navigation buttons take their light or dark look from the system theme at app start, not from Vela's Theme setting, and are not updated if the system theme changes while the app is open.

## 6. Manual test checklist
1. Dark theme: all bottom bar icons are visible, chosen and not chosen.
2. Switch to light: they turn dark.
3. Turn "Translucent bars" and "Animate the tab icons" on and off: icons stay visible.
4. In landscape (side rail): the same.
5. Look at the phone's own back and home buttons with Vela set to Dark while the system is light, and say if they are hard to see.
