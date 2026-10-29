# 34 – Progress bar styles, seek/hold gestures, pull-down reveal, movable PiP, Home cache

## 1. Summary
Seek bar: palette gradient everywhere, six styles (Hairline, Hairline + dot, Bold, Bold + dot, Spikes, Wave) chosen on Customize > Progress bar with live previews. Player: double-tap left/right half seeks (step is a setting, 5-30 s), hold the picture or the space bar for fast playback (speed is a setting, default 2x), skip buttons use the same step and sit higher in the portrait player so pause is not next to the seek bar. Video page: pulling down now shrinks only the picture toward the mini player's spot and fades the rest, revealing a picture of the screen the video was opened from. Swiping up on the mini card opens the video; the picture-in-picture mini player can be dragged anywhere. Home keeps its feed and scroll position for 30 minutes instead of reloading on every visit.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| Progress bar page lists the styles with previews (Hairline, Hairline + dot, Bold seen) | seen | phone |
| Pull-down: picture shrinks, details fade, previous screen visible behind; PiP appears playing | seen | phone screenshots |
| PiP dragged from bottom-right to the middle-left, keeps playing | seen | phone |
| Double-tap seek, hold-for-2x (touch and space), seek badge, Wave/Spikes look, swipe up on the mini card | not exercised | compile-only |
| Home not reloading when returning; scroll position kept | not exercised | compile-only |
| Seek bar vs pause spacing in portrait | not checked on device | compile-only |

## 3. Files changed
- Settings/look: `LookSettings.kt` (ProgressStyle, `look.progressStyle`), `Settings.kt` (`playback.seekSeconds`, `playback.holdSpeed`), `LookTokens.kt`, `LookBuilder.kt`
- Seek bar: `ui/components/Scrubber.kt` (shared drawing, gradient, styles, wave phase), `ui/screens/CustomizeScreens.kt` (Progress bar page)
- Player: `ui/player/PlayerView.kt` (double-tap, hold, badges, spacing), `PlayerGestures.kt`, `player/PlaybackSession.kt` (`holdSpeed`), `ui/AppShell.kt` (space bar tap/hold, previous-screen picture, PiP overlay, transitions)
- Video page / mini: `ui/screens/VideoScreen.kt`, `ui/components/Components.kt` (mini card swipe up, gradient lines), `ui/nav/PageReveal.kt`, `ui/nav/PipPosition.kt`
- Home: `ui/screens/HomeFeedStore.kt`, `HomeScreen.kt`, `Lists.kt` (scroll memory)

## 4. Decisions
- The previous screen behind a pulled-down video is a bitmap captured as the video opens (a recording alone pointed at parts of the old screen that no longer exist).
- The shrink target is approximate (the mini card's thumbnail or the PiP's current spot).
- Home cache: 30 minutes, per account; pull to refresh, a failed/empty feed or an account change fetch again. Other screens (Subscriptions, Library...) are unchanged and may still reload.
- The picture-in-picture mini player is drawn over the whole window, so it no longer reserves space above the bar.

## 5. Known issues
- The bitmap behind the pull-down is the screen as it was when the video opened; if that screen was animating it may be a frame mid-way.
- A tap right after the page rebuilds could still show the old skeleton on screens other than Home.
- PiP position resets when the app restarts.

## 6. Manual test checklist
1. Customize > Progress bar: tap each style; open a video and check the seek bar (wave wiggles up to the current point, still after it).
2. Double-tap left/right of the picture: jumps by the Settings > Playback "Double-tap seek" amount. Hold the picture (or the space bar on Linux): 2x with a badge; release: normal speed.
3. Pull the picture down: it shrinks into the mini player while the screen it came from fades in behind; release early: springs back.
4. With the regular mini card, swipe up on it: the video opens. With picture-in-picture, drag it around; tap to open.
5. Switch tabs and back to Home: no skeleton, same scroll position.
6. Portrait player: press pause several times: no accidental seeking.
