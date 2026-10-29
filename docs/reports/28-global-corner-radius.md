# 28 – Global corner radius

## 1. Summary
Removed the "Thumbnail shape" and "Button shape" settings. The single "Corner radius" setting (Appearance) now drives every rounded element: thumbnails, buttons, cards, sheets, tabs, segmented controls and the floating bar/mini player. Range is 0–16 dp (was 0–32), default 12 dp (was 24). Only genuinely circular things (avatars, colour swatches, switch, active-tab marker, shimmer dots) stay fully round.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| `:androidApp:compileDebugKotlin` | pass | compile-only |
| Look on device / regenerated screenshots | not done | n/a |

## 3. Files created/changed
- Settings: `core/settings/LookSettings.kt` (removed thumbShape, buttonShape and their enums; corners range/default/info)
- Look: `ui/look/LookBuilder.kt`, `LookTokens.kt` (ShapeTokens removed), `LocalLook.kt` (thumbnail/button shapes = medium radius), `Motion.kt`
- UI: `ui/components/Components.kt`, `ui/nav/BottomChrome.kt` (bar radius follows setting), `ui/screens/ModuleWidgets.kt`, `ui/screens/CustomizeScreens.kt` (thumbnail picker removed)
- Tests updated: `LookBuilderTest`, `LookStoreTest`, `SettingsUiTest`

## 4. Decisions the spec didn't dictate
- Radii: small = 0.5×, medium = 1×, large = 1.5× the setting (default 6/12/18 dp, max 8/16/24).
- Button shape setting also removed, so buttons follow the global radius.
- Floating bar is no longer a full pill; it uses the large radius.

## 5. Known issues / unverified assumptions
- Saved values above 16 (old range) or old thumbShape/buttonShape keys: expected to fall back to default/be ignored; covered only by the existing out-of-range store test.
- `docs/SPEC.md` and `docs/reports/12-screens` images not updated.

## 6. Manual test checklist
1. Install the build; open Customize > Icons & shapes: no "Thumbnail shape" picker.
2. Settings > Appearance > Corner radius: drag 0 → 16; thumbnails, buttons, cards, tab highlight and bar all change together.
3. Check that nothing looks extreme at 16 and the bar still looks right at 0.
