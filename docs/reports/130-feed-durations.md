# 130 Feed durations

## 1. Summary
Videos from subscriptions (Home shelf "New from your subscriptions" and the Subs page) showed no duration pill. The source is the channel RSS feed, which has no duration. `FeedService.refresh` now looks up the duration of the newest entries that lack one (`videoInfo`, at most 30 per refresh, `concurrency` at a time), and `mergeFeed` keeps a duration a cached copy already has.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `:shared:jvmTest` FeedServiceTest, NewUploadNotificationTest | pass | running |
| Duration pill appears on Home shelf / Subs page | not checked | not run (needs real network/UI) |

## 3. Files created/changed
- `shared/src/commonMain/kotlin/com/greenaltambe/vela/feed/FeedService.kt` (`withDurations`, `MAX_DURATION_LOOKUPS`)
- `shared/src/commonMain/kotlin/com/greenaltambe/vela/feed/FeedMerge.kt` (keep cached duration)
- `docs/reports/130-feed-durations.md`

## 4. Decisions the spec didn't dictate
- Cap of 30 lookups per refresh, newest first, so a large subscription list does not trigger hundreds of extractions; older entries fill in over later refreshes.
- Live entries are skipped; failed lookups are left without a duration and retried next refresh.

## 5. Known issues / unverified assumptions
- The first refresh is slower by the lookups (bounded by the cap).
- Not measured: real per-lookup latency.
- Shorts are still not flagged by the feed (unchanged).

## 6. Manual test checklist
1. Pull to refresh on the Subs page; confirm duration pills appear on the newest videos.
2. Open Home; confirm the "New from your subscriptions" shelf shows durations.
3. Refresh again; confirm durations stay and older videos gain them gradually.
