# 82 Start-up ship animation, subscribe button on the search card

## 1. Summary
A start-up screen: on a red sea (same red as the Android 12+ system splash) the logo's ship sails in fast from the left, rides three layers of animated waves (it rocks and bobs) in the middle while the app loads, then, once Home has loaded, speeds off to the right with its bow dipping and the sea fades out. It waits at least about 1.3 s after arriving and at most 5 s in total, so a failed load never keeps it on screen. Separately, the Subscribe button on the search "All" page's top-channel card was invisible once subscribed (a tonal button on a tonal card); the card is now a low surface with an outline, so Subscribed and the bell read clearly.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `StartupSplashTest`: waits while loading, leaves after ready, leaves at the cap if nothing loads, shows a beat even when already ready | pass | running (test clock) |
| Renders of arriving / idle / leaving at 360 x 800 (scratch, not kept) | looked at (waterline and sea depth adjusted after the first look) | running |
| Full `allTests`, `:desktopApp:test`, `:androidApp:installDebug` | `allTests` and `:desktopApp:test` pass, 0 failures; the phone was not connected, so `installDebug` did not run | running |
| On the phone: the animation itself and the search card | not looked at | not verified |

## 3. Files created/changed
- New `ui/look/StartupSplash.kt`; `VelaMark.kt` (`Mark` shared, internal).
- `ui/AppShell.kt` (`startupAnimation`, the home store is created in the outer shell so the splash can see when Home loaded), `App.kt`, `MainActivity.kt`, `desktopApp/.../main.kt` (pass `startupAnimation = true`).
- `ui/search/SearchParts.kt`: `TopChannelCard` colours.
- Test: `StartupSplashTest.kt`.

## 4. Decisions the spec didn't dictate
- Off by default in `App(...)`, on in the two real entry points, so UI tests never wait behind a full-screen overlay.
- "Ready" means Home has finished loading (or the app opened on another page, e.g. from a notification).
- Red sea rather than the page colour: it continues the system splash on Android 12+ without a flash; older Android shows the light window for a moment first.
- The system splash itself cannot animate, so a short cut from its centred icon to the ship arriving is unavoidable.

## 5. Known issues / unverified assumptions
- Frame rate of the waves on a real phone not measured (three path fills per frame, light).
- No "reduce motion" switch exists in the app, so the animation always plays.

## 6. Manual test checklist
1. Force-stop and open the app: ship slides in from the left, rocks on waves, then sails right as Home appears.
2. Open the app with the network off: the ship leaves after about 5 s.
3. Search a channel name, All tab: the top channel card shows Subscribe; subscribe, the "Subscribed" button and the bell are visible and tidy.

## Addendum: shorter and smoother (from a screen recording of the first version)
The recording showed the ship waiting about 5 s (Home's feed was slow) and the sea popping in and fading out. Now: the sea rises from the bottom while the ship sails in (0.5 s), it stays at least 0.8 s and at most 2.8 s from the start (Home's skeleton cards show if the feed is not there yet), then it sails off (0.45 s) and the sea drains down the screen while the sky clears (0.4 s): about 2.2 s when the app is ready, 3.6 s at worst. The bow lifts while the ship comes in and leaves (speed), level in between with the swell rocking it. `StartupSplashTest` times updated and passing. Checked on the phone only that it still starts and hands over to Home (screenshots were too slow to see the motion): please watch it once.
