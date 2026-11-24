# 132 Feed overhaul, task 1: chip parser, feed continuation, live flag

## 1. Summary
First task of `docs/plans/feed-overhaul.md`. The Home response parser now reads YouTube's chip bar (`homeChips`), has a dedicated paging
token for a feed (`feedContinuation`, which ignores chip tokens), and flags live streams in the lockup shape (`isLive`, which was never
set there). Nothing in the app uses the new functions yet; no user-visible change.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `:innertube-auth:test` incl. new `HomeChipsParseTest` (5) and `LiveLockupParseTest` (2) | pass | running (offline, hand-built fixtures) |
| `./gradlew allTests` | pass (BUILD SUCCESSFUL) | running (offline) |
| Live, signed-in account: chips parsed | 21 chips: 1 create, 1 all, 1 custom feed, 18 topic; 1 selected, 1 without token | running (live) |
| Live: the Live chip's videos flagged live | 21 of 21 (before the fix: 0 of 17) | running (live) |
| Live: paging with `feedContinuation` | All page one gave a token; custom feed chip: 24 videos, then 24 new on page 2 | running (live) |

## 3. Files created/changed
- `shared/.../auth/Accounts.kt`: `HomeChipKind`, `HomeChip`.
- `innertube-auth/.../InnertubeParsers.kt`: `homeChips`, `feedContinuation`, live badge in the lockup parser.
- `innertube-auth/src/test/.../HomeChipsParseTest.kt` (new).
- `innertube-auth/src/liveTest/.../LiveChipFollowTest.kt`, `LiveHomeRefreshTest.kt` (new live measurements; print counts, no ids or tokens).
- `docs/plans/feed-overhaul.md` (owner decisions added, section 8).

## 4. Decisions the spec didn't dictate
- `continuationToken()` is kept for the other callers, but Home must not use it: it returns the first continuation command anywhere,
  and on Home that can be a chip's token. `feedContinuation()` reads the list's own continuation item.
- Chip kinds come from `uniqueId` (`CUSTOM_FEED_<n>`, `..._CREATE_FEED`); the selected chip without a token is "All". Other chip clouds
  (outside `feedFilterChipBarRenderer`) are not Home chips.
- `isLive` in lockups = a thumbnail badge whose style contains LIVE or whose text is "LIVE".

## 5. Known issues / unverified assumptions
- Live-stream viewer counts ("13 watching") are still not read in the lockup shape.
- Upcoming premieres are not flagged live.
- Chip tokens' lifetime is unmeasured (plan item M2).
- The chip bar may be absent from chip (continuation) responses; Home must keep the chips from the first All load. Not yet measured.

## 6. Manual test checklist
1. Nothing user-visible changed in this task. Open Home on Android and desktop and confirm it still loads and videos open as before.
