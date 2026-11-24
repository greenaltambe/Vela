# 133 Feed overhaul, task 2: Home read with chips and paging

## 1. Summary
`AccountReads` has a new `home(chip, continuation)` that returns a `HomePage` (videos, the next-page token, and on the first unfiltered page the
chips). It covers the unfiltered feed, a chip's first page, and the next page of either. `InnertubeAccountReads` implements it with the task 1
parser. `recommended()` is unchanged, and fakes that only know it keep working through a default `home`. Nothing in the UI calls it yet.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `HomeReadsTest` (4): first page + token + chips; next page via continuation with no chips and no new Home request; chip feed with its own paging token; signed-out page gives null | pass | running (offline, fake transport through the real reads class and governor) |
| `./gradlew allTests` | pass (BUILD SUCCESSFUL) | running (offline) |
| Live soak, signed-in: unfiltered Home, 10 pages | 236 videos, 0 repeats, still offered more | running (live) |
| Live soak: the Music chip | ended after 6 pages with 165 videos and 7 repeated ids | running (live) |

## 3. Files created/changed
- `shared/.../auth/Accounts.kt`: `HomePage`, `AccountReads.home` (default implementation).
- `innertube-auth/.../InnertubeAccountReads.kt`: `home`.
- `innertube-auth/src/test/.../HomeReadsTest.kt` (new).
- `innertube-auth/src/liveTest/.../LiveHomePagingTest.kt` (new; prints counts only).
- `docs/reports/133-feed-home-read-paging.md`.

## 4. Decisions the spec didn't dictate
- Patience for `home` is 15 s (same as `recommended()`), so a slow governor window ends in a null that the UI turns into Retry instead of a long spinner.
- `chips` is filled only on the first unfiltered page; the caller keeps them. The signed-out check applies to that first page only.
- When both `chip` and `continuation` are given, `continuation` wins (a chip's later pages use the continuation the chip's page returned).

## 5. Known issues / unverified assumptions
- Chips can repeat videos across pages (7 of 165 on the Music chip); the page list already de-duplicates by video id, so the UI must keep using it.
- Some chips end early (Music: 6 pages); the UI must treat a missing continuation as the end, not as a failure.
- The chip token's lifetime is unmeasured (plan item M2).
- Not yet measured: whether a chip's response ever carries the chip bar again.

## 6. Manual test checklist
1. Nothing user-visible changed in this task; open Home on Android and desktop and confirm it loads as before.
