# 59 App icon and splash (phase 5 of 5)

## 1. Summary
New launcher icon: white sails (a curved main sail and a jib) on a mast over one wave, on the brand red (#BB1614, the primary of the light scheme). The first attempt, a plain white play button on a red rounded square, was too close to YouTube's own logo, so it was dropped for the sailing boat (Vela means sail). The Android adaptive icon has a solid red background, a white foreground and a monochrome layer for themed icons. The in-app mark (`VelaMark`) is the same boat in the theme's colours. Android also gets a native theme (no white flash behind the first frame, dark variant) and, on Android 12+, a splash of the sail on red. Notifications get a one-colour sail icon.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests` + `:desktopApp:test` (1,086 test cases, 0 failures) | pass | running |
| `:androidApp:assembleDebug`, `:desktopApp:smokeTest` | pass | compile / running |
| Rendered launcher icon (1024 px PNG) | seen, reads as a sail boat on red | running (ImageMagick render) |
| Icon on the phone (home screen, round and themed variants), splash, notification icon, status-bar colour | not seen | compile-only |

## 3. Files created/changed
- `tools/make-icon.py` rewritten: one mark (`SAIL`, `JIB`, `MAST`, `WAVE`) drives everything; ImageMagick renders the PNGs.
- `docs/icon/vela-icon.svg`, `vela-icon-round.svg`, `vela-adaptive-foreground.svg` (sources), `vela-icon-1024.png`, `vela-icon.ico`, `docs/vela-icon-512.png`.
- `androidApp/src/main/res`: `drawable/ic_launcher_background.xml`, `drawable-v24/ic_launcher_foreground.xml`, `drawable/ic_launcher_monochrome.xml`, `drawable/ic_stat_vela.xml`, all `mipmap-*/ic_launcher*.png`, `values/colors.xml`, `values/themes.xml`, and the `-night`, `-v31`, `-night-v31` variants.
- `androidApp/src/main/AndroidManifest.xml`: application and the two login activities use `@style/Theme.Vela`.
- `HealthNotifier.kt`, `DownloadNotifications.kt`: the sail icon for the health alert and the automatic-downloads summary (the download-progress arrow, done and error icons stay the system's).
- `desktopApp/src/main/resources/icons/vela-{256,128,64,48}.png`.
- `ui/look/VelaMark.kt`: redrawn from the same path data.

## 4. Decisions the spec didn't dictate
- The mark is a boat, not a bare play triangle (brand reasons above); the play idea is only hinted by the main sail's shape.
- The window colour before the first frame is the light scheme's page colour (day) or its dark page colour (night); the Android 12+ splash is always red with the white sail.
- No `core-splashscreen` dependency: the system splash on 12+ and a plain window background before that.

## 5. Known issues / unverified assumptions
- Not checked on a device: the themed (monochrome) icon, the splash animation, and the transparent status and navigation bars set in the native theme next to `enableEdgeToEdge()`.
- Below Android 12 there is no splash screen, only the window colour.
- The Linux dock icon is the new PNG, but a desktop entry is not part of this repo.

## 6. Manual test checklist
1. Home screen: red icon with the white boat; long-press / themed icons on Android 13+ show the one-colour boat.
2. Cold start (day and night): no white flash; the Android 12+ splash shows the sail on red.
3. Top bar mark: red sails on any theme; the mast is dark on light, light on dark.
4. Trigger a download summary or health alert: the status-bar icon is the boat.
