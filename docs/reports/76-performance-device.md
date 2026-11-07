# 76 Performance, measured on the phone

## 1. Summary
The player's picture is now measured once at its full size and drawn scaled during the minimise / maximise morph (the video, controls and surface are no longer re-measured every frame). In the debug build minimise / maximise janky frames fell from about 18 % to about 13 % (p90 23 ms to 15 ms, p99 57 ms to 40 ms). In a release-like build with the baseline profile compiled (`assembleMeasure`, `cmd package compile -m speed-profile`) everything is smooth: Home scroll 0.6 % janky, tab switching 3.7 %, minimise / maximise 6.5 % (p99 20-24 ms). So most of what you felt is the debug build; install the measure or a release build to judge.

## 2. Verification (phone, `dumpsys gfxinfo`, same swipe / tap script, 3 minimise-maximise cycles)
| Action | Debug before | Debug after | Release-like (measure + profile) |
|---|---|---|---|
| Minimise / maximise | 17-19 % janky, p90 23 ms, p99 57-81 ms | 12-13.5 % janky, p90 15-20 ms, p99 40-57 ms | 6.3-6.7 % janky, p90 12 ms, p99 20-24 ms |
| Home scroll (12 swipes) | 2.7-12 % | not rerun | 0.55-0.77 %, p99 15-16 ms |
| Tab switching | 5.6-6.3 %, p99 200-250 ms | not rerun | 3.65 %, p99 73 ms |
| Open a video | 2.5 % | not rerun | 1.2 %, p99 38 ms |
| Video picture while dragging and when collapsed (looked at screenshots) | | fine, scaled, not broken | |
| `allTests`, `:desktopApp:test` | pass | | |

## 3. Files created/changed
- `ui/screens/VideoScreen.kt` (picture measured at full size, scaled with a layer; corners follow the scale).

## 4. Decisions the spec didn't dictate
- Not done on purpose: a shared shimmer transition (one always-running animation would redraw every frame even with no placeholder on screen, which costs more than the per-placeholder ones) and per-card press effects (the cards no longer have one; only the first screenful animates in).
- The phone now has the measure build installed (release code, debug key, your data kept). `./gradlew :androidApp:installDebug` replaces it again.

## 5. Known issues / unverified assumptions
- Desktop (Skia) morph not timed; only the desktop tests ran.
- The baseline profile in the APK was not measured separately from the forced `speed-profile` compile.

## 6. Manual test checklist
1. Use the measure build for a day: scrolling, tabs, minimise / maximise should feel smooth.
2. Drag the player slowly: the picture should shrink smoothly with no wrong-size or black frames, also in fullscreen and on a tablet / desktop window.
