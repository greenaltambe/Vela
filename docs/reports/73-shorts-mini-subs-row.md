# 73-74 Mini player on Shorts, channel strip, description sheet, performance

## 1. Summary
The mini player now goes away when you open Shorts (the Shorts player takes over the one playback session, so the mini player had nothing to show) and does not come back afterwards. Subscriptions lost its two chips; the channels row is a new "Following" strip with a tonal "View all" button, avatars cut to Material shapes, a primary ring on channels with a new video you have not seen in Notifications, and an "All" tile. The video description sheet is redesigned (tonal stat tiles in three accent roles, a tonal description card with a "Show more" button, one segmented list for chapters / transcript / channel, expressive tag chips). Performance: the player minimise / maximise janky frames dropped from 21.9 % to 10.2 % (p90 32 ms to 12 ms) in the debug build; other numbers below.

## 2. Verification (debug build, `dumpsys gfxinfo`, same swipe / tap script)
| Action | Before: janky / p90 / p99 | After: janky / p90 / p99 |
|---|---|---|
| Minimise / maximise the player (2 cycles) | 21.9 % / 32 ms / 81 ms | **10.2 % / 12 ms / 73 ms** |
| Home scroll | 7.8 % / 14 ms / 34 ms | 18.9 % cold, 12.1 % then 2.7 % warm (p99 25 ms): dominated by first image / page loads, not repeatable to one number |
| Tab switching | 6.3 % / 10 ms / 200 ms | 5.6 % / 8 ms / 250 ms (one slow first load per tab remains) |
| Open a video | 2.5 % / 15 ms / 81 ms | 2.6 % / 12 ms / 77 ms |
| `allTests`, `:desktopApp:test` (new test: mini player goes away on Shorts) | pass | running |
| Release-like (`assembleMeasure`) numbers, baseline profile | not done | not verified |
| Phone: Shorts without mini player, channel strip, description sheet | not looked at after the last build | not verified |

## 3. Files created/changed
- Mini player: `ui/player/PlayerSheetState.kt` (`releaseForShorts`; `isExpanded` / `isCollapsed` are derived so composition is not invalidated every frame), `ui/AppShell.kt`, test in `NavigationUiTest.kt`.
- Channel strip: new `ui/components/ChannelStrip.kt`, `SubscriptionsScreen.kt`, `NotificationRepository.unreadUploadChannels`.
- Description: `ui/screens/DescriptionSheet.kt`.
- Performance: `PlayerSheet.kt` (mini player reads only "playing" in composition; wave and position are read in draw, wave stops when paused), `Scrubber.kt` (`rememberWavePhase` returns a State read at draw time; `animate` flag, off while paused), `PlayerView.kt`, `VideoScreen.kt` (player state rounded to 2 s for the page; saved-state query off the main thread), `AppShell.kt` (tab switch is a quick cross-fade, no slide; unread dot off the main thread).

## 4. Decisions the spec didn't dictate
- Opening Shorts stops the playing video and clears the mini player; it is not resumed.
- The strip's ring marks channels with unread UPLOAD rows in Notifications (read them there to clear it).

## 5. Known issues / unverified assumptions
- The debug build is still about 3x slower than a release-like build; judge feel on `assembleMeasure`.
- Not done: the player's drag still re-measures the page at the changing size (the biggest remaining morph cost), shared shimmer transition, per-card press feedback cost, baseline profile.

## 6. Manual test checklist
1. Play a video, minimise it, open the Shorts tab: no mini player; leave Shorts: no stale mini player.
2. Subscriptions: no chips; "Following" strip, "View all" button, "All" tile; a ring on a channel with an unseen new video.
3. Video page: tap the title card: new description sheet (tiles, "Show more", list of chapters / transcript / channel, tags).
4. Minimise and maximise the player repeatedly: smoother than before.
