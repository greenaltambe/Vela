# 141 Frosted bottom bar; preview and Glass removed (see the addenda: later rebuilt on Haze)

## 1. Summary
The Navigation bar page no longer has the preview, and the Glass option is gone: "Bottom bar background" (Solid / Translucent / Glass) became one switch,
"Translucent bottom bar". When it is on, the page scrolls under the bar and shows through it blurred, with a light tint on top, as YouTube's bar does. Where
the platform cannot blur (Android 11 and older) the bar is only tinted, and more opaque.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `FrostedBarUiTest`: black and white stripes under a frosted bar are blurred to grey in the pixels behind it, while the stripes above it stay sharp | pass | running (desktop, Skia; pixels read from the rendered image) |
| Page runs under the bar when translucent (and ends above it when solid); last card scrolls clear; Library clears the bar; the mini player stays attached | pass | running (desktop Compose UI tests) |
| The Navigation bar page: no preview, a "Translucent bottom bar" switch, an "Animate the tab icons" switch; the hub summary says "translucent" | pass | running |
| `NavConfigTest`, `LookSettingsTest` (the old key is a legacy key), `./gradlew allTests`, the full `:desktopApp:test` | pass | running |
| `:androidApp:compileDebugKotlin` | pass | compile-only |
| The blur on Android 12+ (RenderEffect) and how it looks over real thumbnails | not checked | needs the owner |

## 3. Files created/changed
- `ui/nav/FrostedBar.kt` (new): `BarBackdrop`, `recordsBackdrop` (on the page) and `frostedBackdrop` (on the bar): the page is recorded into a graphics layer and the part under the bar is drawn again blurred (radius 22 dp) behind the bar's tint.
- `ui/AppShell.kt`: records the page when the bar is translucent and the platform can blur; the bar's tint is 72 % opaque with blur, 92 % without.
- `ui/nav/AppNavigation.kt`: `backdrop` parameter. `ui/platform/PlatformUi.kt` (+ android and jvm actuals): `backdropBlurSupported()` (Android 12+, desktop).
- `core/settings/LookSettings.kt`: `look.nav.translucent` (boolean, off) replaces `look.nav.barOpacity` (now a legacy key, ignored); `NavBarOpacity` removed. `NavConfig.kt`, `SettingCopy.kt`.
- `ui/screens/CustomizeScreens.kt`: preview removed; the two switches.
- Tests: `FrostedBarUiTest.kt` (new), `NavCustomizationUiTest.kt`, `NavConfigTest.kt`, `LookSettingsTest.kt`.

## 4. Decisions the spec didn't dictate
- One switch, not a scale: the viewer asked for one translucent look with blur and no Glass.
- Anyone who had picked Translucent or Glass in the previous build starts with Solid again (the key changed); switching it on takes one tap.
- Recording the page costs a little drawing work each frame, so it only runs while the switch is on.

## 5. Known issues / unverified assumptions
- On Android 11 and older there is no blur: the bar is a 92 % tint over the page.
- The blur radius (22 dp) and the tint (72 %) are chosen by value, not by eye.
- Pages other than Home, Explore and Subs end above the bar, as before.
- A video playing under the bar (an embedded player surface) may not be captured by the recording on Android; thumbnails and text are.

## 6. Manual test checklist
1. Customize > Navigation bar: no preview. A "Translucent bottom bar" switch and an "Animate the tab icons" switch.
2. Switch translucent on, open Home or Explore on the phone, scroll: the thumbnails slide under the bar and look blurred through it. Compare with YouTube.
3. Scroll to the very end of a list: the last video lifts clear of the bar.
4. Check legibility of the icons and names over a bright thumbnail; if the tint is too light or too heavy, tell me which.
5. Play a video and minimise it: the mini player sits on the bar.
6. Switch translucent off: the bar is solid again and the page ends above it.

## Addendum: blurred halo above the bar (found on the phone)
- Symptom: a blurred strip of the page showed just above the bar, outside its tint.
- Cause: a blur spreads past the edge of the layer it blurs; the blurred layer was drawn behind the bar without clipping, so the spread showed above the bar where nothing tinted it.
- Fix: the blurred layer is clipped to the bar's bounds (`clipRect` in `frostedBackdrop`).
- Test: `FrostedBarUiTest` now also checks the row just above the bar is still sharp stripes. Verified that this check fails with the clip removed and passes with it.
- Not verified: the result on the phone (Android's blur renders the same layers, but only your phone can confirm).

## Addendum 2: blur gone on the phone, text and accent hard to see (report 142 changes)
- Symptom (second phone screenshot): the page's text showed sharp through the bar (no blur), the bar's names were faint, and the selected tab's pale pill almost vanished on the tint.
- Cause of the missing blur: not confirmed. The hand-built blur worked in the desktop test but its Android behaviour could not be checked; after the clip fix it no longer showed on the phone.
- Fix: the hand-built layer code was replaced by the Haze library (`dev.chrisbanes.haze:haze` and `haze-blur` 2.1.0, Apache-2.0), which is built for this on Android 12+ and desktop and draws a tinted scrim on older Android. It is built against Compose Multiplatform 1.12.2, so `composeMultiplatform` moved from 1.12.1 to 1.12.2 (patch release). `THIRD_PARTY_NOTICES.md` regenerated.
- Legibility: with blur the tint is 62 % over a 28 dp blur; without blur 94 %. Names and icons are full strength (`onSurface`), the selected tab is a solid accent pill with a light icon, and a hairline marks the bar's top edge. Solid bars are unchanged.
- Verified: the stripes test now runs through Haze (stripes under the bar blurred, row just above it sharp); `allTests`, all desktop UI tests, `:androidApp:assembleDebug`, `:desktopApp:smokeTest` pass.
- Not verified: the result on the phone.

## Addendum 3: no selection pill on the translucent bar
- Symptom: with the Classic colour scheme the accent pill is near black and the selected icon disappeared into it; other schemes would differ again.
- Change (owner's call): in translucent mode the pill is off. The chosen tab is told by its filled icon and a bold name at full strength; the other tabs are the same colour at 74 %. The solid bar keeps Material's pill. The icon pop and hop still run.
- Verified: `allTests`, all desktop UI tests, `:androidApp:compileDebugKotlin` pass (selection state is unchanged, only its drawing). Not verified: how it reads on the phone in each colour scheme.
