# 157 Stage D: Shorts start without waiting for their title

## 1. Summary
A Short whose listing lacks a channel or title (such as one tapped in a Home row) now starts as soon as its streams are ready. The lookup runs beside it and fills in the channel name, picture, "Go to channel" and the history row when it answers. Other videos behave as before.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:compileDebugKotlin` | pass | run (the Android app compile is compile-only) |
| `PlaybackSessionTest`, 9 new: default still waits; starts at once and fills in later; late answers after a swipe or stop dropped; history row complete in either order; failed lookup harmless; a re-resolve keeps the lookup; the channel's own speed applies late but not over the viewer's pick | pass | run |
| `HealHistoryTest` (2, real database): a row gets only what it lacks | pass | run |
| `ShortsLookUpUiTest`: the Short plays while the lookup is held, then shows its channel | pass | run (desktop, fake player) |
| The last two with the change undone | both fail | run |
| How much sooner a Short starts | not measured here; task 152 measured 1.31 to 1.41 s for streams alone against 1.34 to 1.52 s for both, on a desktop | measured then |
| On a phone | NOT RUN | needs the owner |

## 3. Files created/changed
- `shared`: `PlaybackSession.kt` (`start(awaitInfo)`, `MediaInfo.channelId` and `channelAvatarUrl`), `ShortsScreen.kt`, `WatchHistory.sq`, `WatchHistoryRepository.kt`.
- Tests: `PlaybackSessionTest.kt`, new `HealHistoryTest.kt`, new `ShortsLookUpUiTest.kt`.
- Plan status, this report.

## 4. Decisions the spec didn't dictate
- Only the Shorts player skips the wait.
- What a listing already says is never replaced by the lookup; it only fills gaps.
- The history query now fills a missing channel even when the row has a title. Before, such rows kept no channel for good.
- A channel's own playback speed is applied when the channel becomes known, unless the viewer has chosen a speed or is holding one.

## 5. Known issues / unverified assumptions
- The speed gain is probably small; the larger benefit is that the channel now appears at all for Home-row Shorts.
- Per-channel audio language and SponsorBlock overrides still miss a Short whose channel is unknown at the start (stage F's look-ahead is meant to fix this).
- Android's notification title is set when the video loads. Every Shorts listing today has a title; the untitled ones of YouTube's feed get theirs ahead of time in stage F.
- Channel rules (blocklist) are not yet applied after the lookup (stage F).

## 6. Manual test checklist
1. Tap a Short in a Home row: it starts, and a moment later the channel name appears bottom left.
2. Open the three-dot menu after that: "Go to channel" is there and opens the right channel.
3. Swipe quickly through several: no Short shows another's channel.
4. Open History: those Shorts have title and channel.
5. Set a speed for one channel, then open its Short from Home: the speed applies shortly after it starts.
6. Open ordinary videos and a pasted link: unchanged.
