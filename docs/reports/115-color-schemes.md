# 115 Livelier colours and a Color scheme setting

## 1. Summary
New setting Customize > Color scheme with six schemes: Vela red (the default), Sakura pink (the cute one: rose accent, lavender tertiary, blush surfaces), Ocean, Forest, Twilight and Sunset. Each has a light and a dark version. The dark surfaces of every scheme now carry the scheme's tint (the old dark theme was a low-chroma brown-grey, which is what looked dull), and the default Vela red dark theme got a brighter accent, a richer primary container and a redder page.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `allTests` incl. `RedSchemeContrastTest` now covering every scheme, light, dark and AMOLED: all text/background pairs at least 4.5:1 | pass | verified-by-running |
| Desktop + Android compile | pass | compile-only |
| How the schemes actually look | not run | not verified |

## 3. Files created/changed
- Created: `ui/theme/Palettes.kt` (the six schemes; neutrals are built from each scheme's tint), this report
- Changed: `core/settings/LookSettings.kt` (`look.colorScheme`, `ColorPalette`), `SettingCopy.kt`, `ui/theme/Appearance.kt` (palette-aware appearance; AMOLED now darkens the scheme's own containers instead of fixed warm greys), `ui/theme/RedColorSchemes.kt` (livelier dark scheme), `ui/screens/CustomizeScreens.kt` (swatch picker on the Customize hub), tests (`RedSchemeContrastTest`, `LookSettingsTest`)

## 4. Decisions the spec didn't dictate
- Setting key is `look.colorScheme` (`look.palette` is a known legacy key kept out of use).
- The picker shows each scheme as a card (accent, secondary, tertiary dots on the scheme's own surface) in the current light/dark mode; choosing one has an Undo toast like other Customize changes. It is part of a Look, so Look export/restore carries it.
- Light themes were left as they were for Vela red; only the dark Vela red was changed.
- AMOLED keeps working with every scheme: black page, containers pulled toward black from the scheme's own tint.

## 5. Known issues / unverified assumptions
- Colour taste is subjective and I could not see the result: if a dark scheme still feels flat or too tinted, say which and I will adjust the numbers in `Palettes.kt` / `RedColorSchemes.kt`.
- Colours drawn over video (markers, scrims) are deliberately not themed.

## 6. Manual test checklist
1. Settings > Customize: the Color scheme row of cards; tap Sakura pink and watch the whole app recolour; Undo restores.
2. Check each scheme in dark, in light, and with "Pure black in dark theme" on.
3. Default (Vela red) in dark: is it livelier than before? Compare Home cards, chips, bottom bar.
