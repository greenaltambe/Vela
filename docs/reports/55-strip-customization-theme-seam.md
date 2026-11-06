# 55 Strip customization, theme seam (phase 1 of 5)

## 1. Summary
The customization system is gone: palettes, colour modes and custom hex, fonts, text size, density, corner radius, icon packs, glass, reduce motion, performance mode, Pure black, and all bar / tab / mini-player settings. The app is now one plain Material 3 Expressive theme in red, light and dark (Theme: System / Light / Dark is the only appearance switch), with the progress-bar style kept. A new `ui/theme/` package is the seam for bringing customization back: `Appearance` + `AppearanceSource` + `AppTheme`. About 1,700 `Vela.*` call sites were moved to `MaterialTheme.*`, `Spacing`, `ExtraColors` and `Accent` by a codemod. Components themselves are still the old custom ones; icons, nav and the redesign are phases 2 to 4.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests` (shared) | pass | running |
| `:desktopApp:test` | 94 tests, 0 failures (17 tests removed with the features they covered, 2 added) | running |
| `:androidApp:compileDebugKotlin`, `:androidApp:installDebug` | pass | compile / install on the phone |
| New `RedSchemeContrastTest` (every text role >= 4.5:1, light and dark) | pass | running |
| New `ThemeSourceScanTest` (no raw colour / radius / font size outside `ui/theme`, `ui/look`, `ui/debug`) | pass | running |
| New `LookSettingsTest` (live settings, legacy keys, default appearance) | pass | running |
| `generateThirdPartyNotices` | regenerated (Lucide removed) | running |
| Home on the phone: red scheme, light mode | seen | running on device |
| Dark mode, Customize hub, other screens on the phone | not looked at | not verified |

## 3. Files created/changed
- New `ui/theme/`: `Appearance.kt` (`Appearance`, `AppearanceSource`, `DefaultAppearanceSource`, `MotionTokens`, `LocalProgressStyle`), `AppTheme.kt` (`MaterialExpressiveTheme`, expressive motion), `RedColorSchemes.kt`, `ExtraColors.kt` (SponsorBlock markers, media colours), `Spacing.kt` (`SpacingTokens`, `Spacing`), `Accent.kt`.
- Deleted: `ui/look/{Palettes,Contrast,Effects,LookBuilder,LookTokens}.kt`, `ui/VelaTheme.kt`, `core/settings/Look.kt` (LookStore), the hand-drawn icon packs and the Rounded / Sharp / Two-tone / Lucide packs (`VelaIcons.kt` is now a thin set over `MaterialOutlined` / `MaterialFilled`), Customize pages colour / text / icons / glass / tabs / stubs, the Tabs checklist, the performance-mode row in Settings, the corner glow in `AppShell`.
- Settings: `LookSettings.kt` keeps `theme`, `progressStyle`, `home.*`, `nav.railCollapsed`; `Settings.look` / `performanceMode` removed; `LegacySettingKeys` added; `Backup.kt` no longer warns about legacy keys.
- Migrated by codemod (about 40 files): everything that used `Vela.colors/shapes/spacing/wake/motion`; `Vela` now only exposes `icons` until phase 2.
- Nav: `NavConfig.of` returns fixed values (docked bar, labels, card mini player); tabs fixed to Home, Shorts, Subs, Library.
- Build: Lucide dependency removed (`gradle/libs.versions.toml`, `shared/build.gradle.kts`), `THIRD_PARTY_NOTICES.md`.
- Tests: `NavigationUiTest`, `SettingsUiTest`, `MotionTest`, `LayoutInvariantsTest`, `UiHarness`, `ScreenshotsTest`, `NavConfigTest`, `SettingsPagesTest`, `LayoutLogicTest`, debug `SelfTestActivity`; removed `LookBuilderTest`, `LookTokensTest`, `LookStoreTest`, `ContrastTest`, `IconPacksTest`.

## 4. Decisions the spec didn't dictate
- `AppearanceSource.resolve(settings)` takes only the settings (the plan had a `systemDark` argument): `Appearance` holds the theme mode and both colour schemes, and `AppTheme` picks one.
- The red palette is hand-authored from the Material red tonal palette (primary #BB1614 light, #FFB4AA dark), error stays a standard red; `surface` and `surfaceVariant` map to `surfaceContainerLow` / `surfaceContainerHigh` so cards and placeholders keep their old raised look.
- Type is Material's default scale; only `labelSmall` is lifted to 12 sp (the layout test's floor).
- `ui/look/` keeps `Motion.kt`, `VelaMark.kt`, `ComponentIds.kt` and the icon vectors for now; the raw-styling scan allows that folder until phases 2 and 5 shrink it.
- Gradients are one colour: `Accent.colors` gives two equal stops so gradient APIs still work and a later look can bring a real gradient back.
- Reduce motion and performance mode are gone as settings; animation speed no longer changes. System "remove animations" is not read yet.
- Shorts remains a tab (default Shorts mode), so the bar has four tabs; tests updated.
- `railCollapsed` stays a stored setting for now (the rail is rebuilt in phase 3).
- Old settings rows stay in the database unused; restoring an old backup does not warn about them.

## 5. Known issues / unverified assumptions
- Nothing was checked in dark mode or on screens other than Home on the phone.
- The in-app picture-in-picture mini-player look is no longer reachable (hard-wired off); its code is removed in phase 3.
- Pure black is gone; AMOLED users get the standard dark red scheme (`#1A1110`).
- `MaterialExpressiveTheme` and `MotionScheme.expressive()` are alpha APIs in this version; they compiled and ran on the phone and desktop tests.
- Layout test change: controls drawn under 24 dp high are ignored (they are slivers cut by the list edge; one search card hit this after the type scale changed).

## 6. Manual test checklist
1. Open the app: red accent, no pink wash over the page, chips / buttons / bar in Material style.
2. Settings > Customize Vela: only Theme (System / Light / Dark), Progress bar and Home. Switch Theme to Dark and Light; check Home, a video page with comments, Search and Settings in both.
3. Progress bar page: pick Wave, open a video, check the seek bar uses it and the red accent.
4. Settings: no Performance mode row anywhere.
5. Restore an old backup (if you have one): no "settings this version does not know" warning for the old look keys.
6. Search for "corner" or "palette" in Settings search: no result.
7. Landscape and a tablet / desktop window: rail still shows Home, Shorts, Subs, Library and collapses.
