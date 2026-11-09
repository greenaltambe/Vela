# 99: Volume boost, take-a-break reminder, daily Shorts limit

## 1. Summary
- **Volume boost** (Android): the player's Settings sheet has "Volume boost" with Off, +3, +6 and +9 dB, also in Settings > Playback & quality. It uses Android's `LoudnessEnhancer` on the player's audio session; it applies to every video, is remembered, and is rebuilt when the audio session changes. A device that refuses the effect plays at the normal level. Desktop has no row (it already has its own volume slider and the system mixer).
- **Take a break reminder:** Settings > Playback: Off, 15 min, 30 min, 1 h, 1.5 h. After that much *playing* time without a 5-minute pause, a dialog offers "Pause and rest" or "Keep watching" (asks again after 15 more minutes). Time paused does not count; a pause of 5 minutes or more is treated as a break and starts the count over.
- **Daily Shorts limit:** Settings > Shorts: No limit, 15 min, 30 min, 1 h. Time in the Shorts player is counted per local day (kept across app restarts); at the limit the Short pauses and a dialog offers "Leave Shorts" (Home) or "5 more minutes".

## 2. Verification
| Check | Result | Verified-by |
|---|---|---|
| Break timer: fires once at the threshold, paused time not counted, long pause resets, snooze, a sleeping phone not counted as watching, Off | `ViewingLimitsTest` (9) | running |
| Daily usage: adds up, yesterday forgotten, day changes at *local* midnight, forgiving time never below zero | `ViewingLimitsTest` | running |
| Boost remembered and handed to the player on every session | `PlaybackSessionTest` | running |
| `allTests` and build | pass | running |
| Boost on the phone: chose +6 dB; Android's audio system then listed a "Loudness Enhancer" effect owned by Vela's process; back to Off and the effect was gone; no crash | seen via `dumpsys media.audio_flinger` | running (phone) |
| Whether it is really louder to the ear | not measurable by me | not verified |
| Break reminder on the phone: with the threshold shortened for testing (15 "minutes" = 15 seconds, removed afterwards) the dialog appeared when 15 s of playing time was reached | seen | running (phone, test timing) |
| Daily Shorts limit on the phone, same shortened test: the Short paused, the dialog appeared, "Leave Shorts" went Home | seen | running (phone, test timing) |
| "Pause and rest", "Keep watching", "5 more minutes" buttons | "Pause and rest" and "Leave Shorts" tapped and worked; "Keep watching" and "5 more minutes" not tapped | partly verified |
| Real-length timings, a break across the app being closed, Shorts counted while in PiP, desktop | not run | not verified |

## 3. Files changed
New: `viewing/ViewingLimits.kt`, tests `viewing/ViewingLimitsTest.kt`. Changed: `core/settings/Settings.kt`, `SettingCopy.kt`, `SettingsPages.kt` (3 settings, enums `VolumeBoost`, `BreakReminder`, `ShortsLimit`), `player/PlayerController.kt` (`canBoost`, `setBoost`), `player/PlaybackSession.kt` (`boost`, `setBoost`), `androidApp/.../Media3PlayerController.kt` (the effect), `ui/player/PlayerView.kt` + `ui/screens/VideoScreen.kt` (boost row and sheet), `ui/AppShell.kt` (the counting loop and both dialogs), `AppServices.kt` (`shortsUsage`), test `PlaybackSessionTest.kt` (fake controller).

## 4. Decisions
- The break reminder only asks; it never stops audio that is playing in the background, so music is not cut by a dialog. The Shorts limit does pause (it is the point of the limit).
- Boost steps stop at +9 dB with a warning in the sheet; loud videos can distort.
- The limit counts only while a Short is actually playing, not while paused or scrolling.
- "5 more minutes" gives back 5 minutes of the day's count (not a flat extra), so it can be used again after the next 5.
- The counts are by local day using the phone's clock offset.

## 5. Known issues
- **My mistakes while testing, all reverted:** (1) a stray tap started a download of "Me at the zoo" (18 s) and I deleted it again ("Download deleted"); (2) earlier, while reading the Settings list, a scroll swipe changed "Caption size" to Small; I set it back to Normal. All the settings I touched (break reminder, Shorts limit, boost, caption size/background) are back at their defaults, confirmed in the database.
- One earlier attempt at the break test showed no dialog; the cause was my own tap outside the dialog (a tap outside counts as "Keep watching" and snoozes it) plus a video that had already ended, not a bug (the clean rerun fired on time and the logged counter followed the playing state).
- No notification-shade or lock-screen reminder; the dialog shows only while the app is open.
- Boost applies per device audio session; some Bluetooth or special outputs may ignore it.

## 6. Manual test checklist
1. Play a quiet video, open the player's Settings > Volume boost > +6 dB; compare. If sound distorts, pick a lower step.
2. Settings > Playback > Take a break reminder: 15 minutes; watch for the dialog; "Keep watching" should ask again 15 minutes later.
3. Settings > Shorts > Daily limit: 15 minutes; scroll Shorts until it stops; try "5 more minutes".

## 7. Design and usability pass (after your request)
Checked the new sheets, dialogs and rows against the rest of the app and Material 3 Expressive. Fixed:
- **Cut-off descriptions:** the shared player menu row clipped every description to one line, so "Use the creator's style" read "Colours, bold and position, such as a …". Rows can now take more lines (`supportingLines`); seen on the phone, full text.
- **Boost warning:** was always-visible grey fine print. It now appears only at +6 dB and above, as a caution card in the error container colour with an icon (animated in); at Off or +3 dB the sheet is just the choice and one plain sentence. Seen on the phone at +6 dB.
- **Shorts limit dialog was a trap:** Back and a tap outside did nothing. Both now mean "I am done" and leave Shorts, the same as the "Leave Shorts" button.
- **Dialogs now give a haptic** when they appear (the player's heavy tick), like the rest of the player's controls.
- Already consistent and left alone: the dialogs use the app's standard Material alert style with an icon, a title and text buttons (same as "Delete this download?" and sign-out); the settings rows use the standard dropdown/segmented controls; the caption sheet uses the same chip grid, preview card and switch row as the speed and sleep sheets.
- Not verified: the dialog haptics (cannot feel it), Back on the Shorts limit dialog (not pressed on the phone).
- Not done on purpose: the Volume boost setting is still listed on desktop although it only works on Android (its description says so); hiding platform-only settings needs a platform flag that settings do not have yet.
- Your settings are back at defaults (boost Off, caption Normal/Shade/creator on, break and Shorts limit Off).
