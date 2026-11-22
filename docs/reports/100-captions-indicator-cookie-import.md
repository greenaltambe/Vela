# 100 Captions indicator, cookies.txt import crash

## 1. Summary
Fullscreen captions button now shows a bar under the icon when captions are on (colour alone was easy to miss over video).
Desktop cookies.txt import no longer lets an exception escape: any failure becomes a message on the Accounts screen. The root cause of the crash was not reproduced (the log has no stack trace), so the fix is defensive.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `:shared:compileKotlinJvm`, `:androidApp:compileDebugKotlin` | pass | compile-only |
| Captions indicator on Android | not run | compile-only |
| Import crash fixed | not run | compile-only |

## 3. Files created/changed
- shared/.../ui/player/PlayerView.kt (captions on-indicator, a11y label "Captions on/off")
- shared/.../ui/screens/AccountsScreen.kt (catch Throwable around importCookies)
- shared/.../jvmMain/ui/platform/PlatformUi.jvm.kt (file dialog failures return null; title "Choose a file")

## 4. Decisions the spec didn't dictate
Indicator is a small primary-coloured bar plus the existing tint, in the shared button (so desktop gets it too).

## 5. Known issues / unverified assumptions
Real crash cause unknown. Likely an exception from the credential store/DB/network after the file is read. The message shown now will reveal it.

## 6. Manual test checklist
1. Android: play a video with captions, go fullscreen, tap CC: bar + coloured icon appear; tap again: both vanish.
2. Desktop: Accounts > Import cookies.txt, pick a file: no crash; success or an error message appears.
3. If an error message appears, send me its text.

## Update: crash on Continue (file dialog never opens)
Likely cause: `rememberTextFilePicker` (and the folder picker) showed the AWT FileDialog from `Dispatchers.IO`, not the AWT event thread; the native dialog can kill the process. Now both run via `EventQueue.invokeAndWait` (the backup picker already used the Swing thread). Verified: `:shared:compileKotlinJvm`, `:desktopApp:compileKotlin` (compile-only). Test: Accounts > Import cookies.txt > tick boxes > Continue: the file dialog opens. If it still crashes, run the app from a terminal and send the output.
