# 27 - Icon packs, Home thumbnails, channel banners

## 1. Summary

- Icon pack is now one choice that restyles every icon in the app. The icon set grew from 27 to 51 names; the leftover text glyphs (`›`, `‹`, `↑`, `↓`, `✓` in menus) are real icons now, and the settings tiles use fitting icons (palette, shield, lock, folder, cloud, bolt, ...).
- 8 packs: Vela Filled / Line / Duotone (hand-drawn, as before, extended to the new names) plus ready-made Material Rounded / Outlined / Sharp / Two-tone (Material Icons Extended, Apache-2.0) and Lucide (ISC). Rounded is the soft, elegant one; Lucide is the fine rounded line set.
- Home thumbnails: the Home recommended feed arrives as YouTube `lockupViewModel` items and the parser never read their picture. It does now, and the video card falls back to `i.ytimg.com/vi/<id>/mqdefault.jpg` when a feed gives no picture.
- Channel banners did not exist anywhere (no field, no UI). `ChannelPage.bannerUrl` is filled by both extractors and shown at the top of the channel page.
- DeArrow was already off by default (`modules.dearrow.enabled` = false, generated thumbnails = false); a test now pins that.

## 2. Verification

| check | result | verified-by |
|---|---|---|
| `:shared:compileKotlinJvm` with the new icon libraries | pass | running |
| `./gradlew allTests` (shared tests, incl. `DeArrowDefaultTest`) | pass | running |
| `:desktopApp:test` (all UI tests incl. `IconPacksTest`: every one of 8 packs has all 51 icons, Vela packs stay on the grid, Line/Duotone rules) | pass | running |
| `:androidApp:compileDebugKotlin` with the two new dependencies | pass | compile-only |
| Home thumbnails now show on a real signed-in Home feed | not verified (needs a signed-in account on a device) | unverified |
| Channel banner shown on real channels | not verified (needs network on a device) | unverified |

## 3. Files created/changed

Icons
- `gradle/libs.versions.toml`, `shared/build.gradle.kts`: `material-icons-extended` 1.7.3, `icons-lucide-cmp` 2.2.1.
- `core/settings/LookSettings.kt`: `IconStyle` gains ROUNDED, OUTLINED, SHARP, TWO_TONE, LUCIDE; labels, keywords, description.
- `ui/look/VelaIcons.kt`: `VelaIconSet` is now name-keyed over `IconNames.all` (51); new filled and line paths for the 24 new icons.
- `ui/look/LibraryIconPacks.kt`, `MaterialRounded.kt`, `MaterialOutlined.kt`, `MaterialSharp.kt`, `MaterialTwoTone.kt`, `LucideIcons.kt` (new): name -> library icon maps.
- `ui/components/Components.kt`, `ui/screens/SettingsHost.kt`, `CustomizeScreens.kt`, `LibraryScreens.kt`, `Common.kt`: glyphs replaced by icons; tile icons.
- `desktopApp/.../IconPacksTest.kt`: covers all packs; contact sheets for every pack.

Thumbnails and banners
- `innertube-auth/.../InnertubeParsers.kt`: lockup thumbnails, `//` URLs.
- `ui/components/Components.kt`: card thumbnail fallback from the video id.
- `extractor/Models.kt` (`ChannelPage.bannerUrl`), `extractor-newpipe/.../ItemMapping.kt`, `extractor-ytdlp/.../YtDlpMapping.kt`, `ui/screens/ChannelScreen.kt`.

Tests
- `shared/src/commonTest/.../core/DeArrowDefaultTest.kt` (new).

## 4. Decisions the spec didn't dictate

- Ready-made packs come as designed: the Look's icon weight and "rounded ends" options only affect Vela's Line/Duotone/Filled packs (stated in the setting's description).
- Chose Material Icons Extended + Lucide over the many-library route (FontAwesome etc.): permissive licences, one dependency covers four packs, and Lucide gives the thin elegant option. Feather was dropped (its artifact had no usable JVM classes).
- Default pack stays Filled.
- Banner is shown on the channel page only; subscriptions rows have no banner stored.
- Account-menu rows that are plain strings (`✓ name`, `Switch account ▾`) are still text; the menu API takes strings only.

## 5. Known issues / unverified assumptions

- Material Icons Extended is large: debug APKs grow noticeably (release has `isMinifyEnabled = false` in `androidApp/build.gradle.kts`, so release grows too). Enabling R8 for release would trim it; not changed here.
- Mapping choices are closest-match (e.g. subscriptions = Lucide `MonitorPlay`, downloaded = `CircleCheck`); look at the contact sheets in `docs/reports/23-screens/icons-*.png`.
- The thumbnail cause was found by reading the parser (lockup items carried no picture); I could not run a signed-in Home feed. If Home is still blank on a device, the next suspect is Coil's loader on Android (`ImageLoaderSetup.kt`).
- yt-dlp banner is read from the `banner*` thumbnail id; not measured against a live channel.

## 6. Manual test checklist

1. Settings > Customize > Icons and shapes: tap each of the 8 packs; check the bottom tabs, mini player, player controls, settings tiles, video-row menu and the channel/list chevrons all change together.
2. Pick Rounded, then Lucide; confirm nothing falls back to text arrows or chevrons (rail collapse toggle, settings rows, tab reorder arrows, playlist reorder arrows).
3. Home (signed in, recommended source): thumbnails show on every card.
4. Home (signed out, trending): thumbnails still show.
5. Open a channel with a banner: banner appears above the avatar row; open one without: no gap or crash.
6. Settings > Extras: DeArrow is off on a fresh install (clear app data first).
