# 154 Stage A: Home follows "Region for content"

## 1. Summary
The signed-in Home (feed, chips, next pages) now asks YouTube for the region set in Settings > Home instead of always `US`. Changing the region starts Home afresh: old list, chips, shelves, scroll position and answers still on their way are dropped. Account feed failures now appear in Diagnostics on both platforms.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:compileDebugKotlin` | pass | run (the Android app compile is compile-only) |
| `HomeReadsTest`, 3 new: region on every Home request, read at that moment, blank means `US`; history requests unchanged; no region given keeps the client's own | pass | run |
| `HomeSourceUiTest`, 3 new: one load per region and the old list gone; a late old-region answer is not shown; the new list starts at the top | pass | run |
| Same UI tests with the region reset removed | all 3 fail | run |
| Same with the scroll position merely reset in place | the scroll test fails | run |
| A signed-in request with `gl=IN` is answered | done in tasks 150 to 152, not repeated with this class | run then |
| The app on a phone or desktop with a real account | NOT RUN | needs the owner |

## 3. Files created/changed
- `innertube-auth`: `InnertubeAccountReads.kt`, `HomeReadsTest.kt`.
- `shared`: `HomeScreen.kt`, `HomeFeedStore.kt`, `Settings.kt` and `SettingCopy.kt` (description).
- Wiring: `PlaybackRuntime.kt`, desktop `main.kt`.
- `desktopApp`: `HomeSourceUiTest.kt`.
- `docs/plans/home-groups-shorts.md` (status, corrected gate command), this report.

## 4. Decisions the spec didn't dictate
- Only Home asks for the region. History, playlists, subscriptions and actions keep the bundled client.
- Home is rebuilt as a whole on a region change, not cleared field by field, so later additions (groups) are covered too.
- The plan's gate command was wrong: it skipped `innertube-auth` tests. Corrected.

## 5. Known issues / unverified assumptions
- The Shorts tab does not react to a region change yet (stage F).
- The language stays English whatever the region.
- The region is per device, not synced.
- How much the signed-in feed differs between regions is unmeasured; your account's history probably weighs more than the region.

## 6. Manual test checklist
1. Settings > Home > Region: set your country. Go to Home: it loads again, from the top.
2. Change it to another country and back; each time Home reloads and never shows the previous list first.
3. Open a chip, then change the region: Home returns to All with fresh chips.
4. Leave the region alone and switch tabs: Home comes back as you left it, without reloading.
5. With the network off, pull to refresh, then check Diagnostics for an "account feed" line.
