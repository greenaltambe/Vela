# 135 Separate application id for the debug build

## 1. Summary
The Android debug build now installs as `com.greenaltambe.vela.debug`, labelled "Vela Debug", so it sits next to the release app (`com.greenaltambe.vela`)
instead of replacing it. The release build is unchanged.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `:androidApp:assembleDebug` | pass | running (build) |
| APK package and label (`aapt2 dump badging`) | `com.greenaltambe.vela.debug`, "Vela Debug" | running (inspected the built APK) |
| Install next to the release app on a phone | not checked | needs the owner |
| Release APK id unchanged | not rebuilt; the change is in the `debug` and `measure` build types only | reasoning, not run |

## 3. Files created/changed
- `androidApp/build.gradle.kts`: `debug { applicationIdSuffix = ".debug" }`; the `measure` type uses the same suffix (it exists to replace the debug app).
- `androidApp/src/debug/res/values/strings.xml` (new): `app_name` = "Vela Debug".
- `androidApp/src/debug/AndroidManifest.xml`: the comment's `adb` command updated to the new id.

## 4. Decisions the spec didn't dictate
- The Kotlin namespace stays `com.greenaltambe.vela` (only the installed id changes), so no code or class names moved.
- The label is "Vela Debug" so the two icons can be told apart.

## 5. Known issues / unverified assumptions
- The debug app has its own data: sign in and settings start empty, once.
- The first install of the new debug id does not remove the old debug app that used the release id (if one is still installed over the release one, it was the release app that got replaced; reinstall the release APK once).
- Debug and release are signed with different keys, which is fine now that the ids differ.
- Only the Android side is covered; the code does not hard-code the package name anywhere else (checked by search).

## 6. Manual test checklist
1. Install the release app from its APK as usual.
2. Run the debug build from Android Studio: it should install as a second app "Vela Debug" and leave the release app and its data alone.
3. Sign in once in the debug app; the release app should still be signed in.
4. `adb shell am start -n com.greenaltambe.vela.debug/com.greenaltambe.vela.debug.SelfTestActivity` still opens the self-test screen.
