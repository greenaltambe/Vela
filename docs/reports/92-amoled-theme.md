# 92: Pure black (AMOLED) theme

## 1. Summary
New switch Settings > Customize Vela > Look > "Pure black in dark theme" (`look.amoled`, off by default). While the dark theme is on, the page and plain surfaces are true black; cards, chips and sheets use containers a few steps darker than before so they keep an edge. Text and accent colours are unchanged. It does nothing in light theme.

## 2. Verification
| Check | Result | Verified-by |
|---|---|---|
| Scheme maths: black background/surface, accent and text kept, containers differ from the page, light and plain dark untouched | `AmoledSchemeTest` (3) | running |
| `allTests` (incl. the test pinning the appearance keys, updated for the new key), `assembleDebug` | pass | running |
| Switch visible in Customize; Home with it on: background pixel (0,0,0), cards (22,15,14); chips outlined and readable | seen | running (phone) |
| Light theme, tablet/desktop | not checked (unchanged code path) | not verified |
| Every screen in AMOLED (Library, watch page, sheets) | only Customize and Home looked at | not verified |

## 3. Files changed
`core/settings/LookSettings.kt` (+`amoled`), `ui/theme/Appearance.kt` (+`amoled`, `asAmoled()`), `ui/screens/CustomizeScreens.kt` (shows it), tests `AmoledSchemeTest`, `LookSettingsTest` (key list).

## 4. Decisions
- A separate switch rather than a fourth Theme choice, so "System/Light/Dark" keep working and AMOLED follows the system dark mode too.
- Containers are not pure black (Material guidance for OLED dark: keep elevation cues).

## 5. Known issues
- I left the switch ON on your phone while testing; turn it off in Customize if you prefer the normal dark look.

## 6. Manual test checklist
1. Customize Vela > turn on "Pure black in dark theme" with the system in dark mode: pages go black.
2. Look through Library, the watch page, comments and sheets for any panel that looks lost against the black.
