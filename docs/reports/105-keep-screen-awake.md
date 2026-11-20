# 105 Keep screen awake during video

## 1. Summary
Nothing in the app kept the screen awake, so it blanked during playback. Android now sets `FLAG_KEEP_SCREEN_ON` while a video (not audio-only) is PLAYING and clears it when paused/stopped. Linux desktop now holds a `org.freedesktop.ScreenSaver` inhibit under the same condition.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `:desktopApp:compileKotlin`, `:androidApp:compileDebugKotlin` | pass | compile-only |
| org.freedesktop.ScreenSaver service present on this GNOME session | yes (gdbus introspect) | verified-by-running |
| Android flag behaviour, desktop inhibit behaviour | not run | not verified |

## 3. Files created/changed
- Changed: `androidApp/.../MainActivity.kt` (flag set in the existing player/session collector), `desktopApp/.../main.kt` (starts/closes inhibitor next to MPRIS)
- Created: `desktopApp/.../mpris/ScreenSaverInhibitor.kt`, this report

## 4. Decisions the spec didn't dictate
- Awake only for video playing; audio-only and paused let the screen sleep.
- Windows not handled (would need SetThreadExecutionState); the Linux path is skipped there.
- Android collector starts after first frame (idle handler), as the PiP one did.

## 5. Known issues / unverified assumptions
- Desktop: if no ScreenSaver service exists (some minimal WMs), the inhibit silently does nothing.
- Collector runs only once the idle handler fires; playback before that is not possible in practice.

## 6. Manual test checklist
1. Android: set screen timeout to 15 s, play a video; screen stays on past 15 s, also in fullscreen.
2. Pause; screen sleeps after the timeout. Resume; stays on again.
3. Play audio-only; screen sleeps normally.
4. Linux: set GNOME blank screen to 1 min, run the app, play a video >1 min; no blanking. Check `gdbus call --session --dest org.freedesktop.ScreenSaver --object-path /org/freedesktop/ScreenSaver --method org.gnome.ScreenSaver.GetActive` isn't needed; just watch it.
5. Pause on desktop; screen blanks after the timeout.
