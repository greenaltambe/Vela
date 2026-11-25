# 161 Region code beside the app's name

## 1. Summary
The code of the chosen "Region for content" (such as GB or IN) now shows small and raised after "Vela": on the phone on Home, in the desktop top bar on every page. It changes as soon as the setting does. Added to the 0.6.1-beta.6 changelog section.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:compileDebugKotlin` | pass | run (the Android app compile is compile-only) |
| `NavigationUiTest`, 2 new: code beside the name, raised, follows the setting, absent on other phone pages; present on desktop pages | pass | run (desktop) |
| Look, phone-width and desktop bars | rendered images checked | run, still images |
| On a phone | NOT RUN | needs the owner |

## 3. Files created/changed
`ui/components/Components.kt`, `ui/AppShell.kt`, `NavigationUiTest.kt`, `CHANGELOG.md`, this report.

## 4. Decisions the spec didn't dictate
- Shown for every region, including US (YouTube hides it there).
- On the phone only Home carries the app's name, so only Home shows the code.
- It is a label, not a button. Screen readers say "Region for content: <country>".

## 5. Known issues / unverified assumptions
None found.

## 6. Manual test checklist
1. Home on the phone: the code stands after "Vela".
2. Settings > Home > Region: choose another country, go back: the code changed.
3. Open Subs or Library on the phone: no code. On Linux: it stays beside the name.
4. Check it with translucent bars on, in dark theme.
