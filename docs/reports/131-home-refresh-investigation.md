# 131 Home refresh investigation (signed-in feed does not change)

## 1. Summary
Hypothesis: repeated refreshes of the signed-in Home feed return the same videos because Vela sends `FEwhat_to_watch` with no `visitorData`.
A live test (`LiveHomeRefreshTest`) measured the overlap of video ids between back-to-back requests with and without `visitorData`.
Result: the hypothesis is not supported. YouTube reshuffles about 20-35% of the 31 videos on every request in all three variants.
No app code was changed.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| A: bundled client, no visitorData, 4 requests | 23, 25, 26 of 31 same as the previous request | running (live, signed-in account) |
| B: visitorData from the first response reused | 21, 20, 25 of 31 same as the previous | running (live) |
| C: visitorData of the previous response chained | 21, 28, 18 of 31 same as the previous | running (live) |
| Response carries `responseContext.visitorData` | yes on a request without it; no when one was already sent | running (live) |

## 3. Files created/changed
- `innertube-auth/src/liveTest/kotlin/com/greenaltambe/vela/innertube/LiveHomeRefreshTest.kt` (new, prints overlap, asserts nothing)
- Cookies file moved by the owner's request from `~/Downloads` to `~/.vela-test/cookies.txt` (mode 600, directory 700, outside the repo).

## 4. Decisions the spec didn't dictate
- The test only reads (BROWSE `FEwhat_to_watch`); 12 requests in total per run, through the governor.

## 5. Known issues / unverified assumptions
- The cookies are the owner's real session, not a throwaway account. The test is read-only.
- Overlap was measured on the whole list, not by position. The top rows may change less than the rest.
- Not measured: what the website returns on repeated reloads for the same account, so "YouTube changes more" is unverified.
- Not measured: whether the app shows the new list after a pull (UI path was only read: `HomeScreen.load(false)` replaces the list; a revisit within 30 minutes shows the stored list, `HomeFeedStore.MAX_AGE_MS`).
- Run was one sample of four requests per variant; the spread (18-28 of 31) is large.

## 6. Manual test checklist
1. On Home, pull to refresh three times in a row and note whether the top 10 videos change.
2. Compare with youtube.com in a browser, same account, reloading three times.
3. Leave Home, come back within a minute: the list should be unchanged (stored for 30 minutes). Pull to refresh to get a new one.
4. If the top rows barely change but the lower ones do, say so: the next step is a positional measurement.

## Addendum: mobile findings (measured, live, same account)
- Top rows are stickier: across 3 comparisons the top 3 stayed in the same position 3, 3, 2 times; the top 10 kept the same position 4, 4, 5 times, though 9, 9, 7 of the same videos stayed in the top 10 (reordered). The reshuffle is in the lower rows.
- Home has a continuation: the first page gives 22 videos plus a token; 5 further pages gave 23-24 new videos each (141 in all, more still offered).
- Vela uses only page one. `InnertubeAccountReads.recommended()` returns a plain list and drops the token; `HomeScreen` then loads Trending after it (`load(more = true)` with no continuation, so Trending page one). Trending includes live streams, which explains the live streams at the bottom.
- Not fixed. Fix direction: carry the continuation through `AccountReads` and `HomeFeedStore`, page the recommended feed, and keep Trending only as the fallback when the account feed fails or is empty. Top-row stickiness is YouTube's own and not a Vela bug as far as measured.
