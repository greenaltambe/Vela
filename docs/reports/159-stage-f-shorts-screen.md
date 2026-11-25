# 159 Stage F: the Shorts tab shows YouTube's Shorts

## 1. Summary
The Shorts tab now shows your account's recommended Shorts when signed in, YouTube's general feed when not, and falls back to the old channel mix if both fail. A Short tapped in a YouTube row on Home plays the rest of the row and goes on as YouTube would. Blocked ones are passed over, the next one's title and channel are fetched ahead, and a change of account or region starts the feed afresh. This completes the plan.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:compileDebugKotlin` | pass; two further UI tests were added afterwards and that class run on its own | run (the Android app compile is compile-only) |
| `ShortsSourceOrderTest`, 13: source order for each failure, token and seed isolation, one new sequence, bounded following, no repeats, one request at a time | pass | run |
| `ShortsFeedUiTest`, 14: signed in, signed out, both fallbacks, look-ahead once, blocked skipped forwards and back, blocked found while playing, Home row, opened from a channel, region change (also with a late answer), sign-in while open, Shorts hidden, retry | pass | run (desktop, fake player) |
| Those UI tests with hiding, look-ahead, the YouTube feed or the restart removed in turn | the matching tests fail | run |
| Live, `gl=IN`: the new supply over the real sources, 6 batches | account: 59 Shorts, none twice. Account made to fail: switched once to anonymous, 46, none twice | run (live, read-only) |
| Daily limit and break reminder | code not touched; no new test | existing suite only |
| On a phone or desktop with real playback | NOT RUN | needs the owner |

## 3. Files created/changed
- New: `shared/.../shorts/ShortsFeed.kt`, `ShortsSourceOrderTest.kt`, `desktopApp/.../ShortsFeedUiTest.kt`.
- Changed: `ShortsScreen.kt`, `Navigator.kt` (`fromYouTubeRow`), `AppShell.kt`, `HomeScreen.kt`, `LiveShortsFeedTest.kt`, plan status.

## 4. Decisions the spec didn't dictate
- Only the Shorts tab and Shorts opened from a YouTube row use YouTube's feed. From a channel, search, Subscriptions or the signed-out shelf, behaviour is as before.
- The Short you tap is never hidden, even if a rule would match it.
- Hiding covers blocked channels and words, the per-channel Shorts setting and keyword rules. Watched Shorts are not removed.
- Swiping back past a hidden Short goes further back; from the first page it goes forward.
- With Shorts hidden in settings, the page says so and fetches nothing.
- Each switch to another source is noted in Diagnostics under "shorts feed".

## 5. Known issues / unverified assumptions
- A Short whose channel is not known yet can start before a channel rule stops it, with a moment of sound.
- Swiping faster than the look-ahead causes a second lookup for that Short.
- The channel-mix fallback still includes channels that are live-streaming (as before).
- Whether your watching teaches YouTube's recommendations depends on "Tell YouTube what I watch" (off by default) and is unverified for Shorts.
- Token lifetime is unmeasured; a refused one starts a new sequence.
- Real swiping, loading times, battery and Android behaviour are untested.

## 6. Manual test checklist
1. Signed in, open Shorts: compare the kind of Shorts with youtube.com/shorts for your account.
2. Swipe through 30 or more: no repeats, no stall, titles and channels appear.
3. Sign out: the tab still fills. Sign back in while on it: it changes to yours.
4. Tap the second Short of a Home row: that one, then the rest of the row, then new ones.
5. Block a channel from a Short's menu or the blocklist: its Shorts are passed over, both swiping up and down.
6. Open a Short from a channel page: it still goes through that channel's Shorts.
7. Change the region while using Shorts: the feed reloads.
8. Airplane mode: a message with "Try again" that works once you are back online.
9. Set a short daily limit: the dialog still appears. Set Shorts to Hide: the tab and rows go.
10. Check Diagnostics for unexpected "shorts feed" lines.
