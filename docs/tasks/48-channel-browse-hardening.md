# Task 48: harden native channel browse (task 47 review findings)

Read CLAUDE.md. Do not commit. Report `docs/reports/48-channel-browse-hardening.md` (VERIFIED / NOT VERIFIED table), terminal reply 5 lines max.
Goal: a channel page never shows a silent empty list, never loses the user's sort/position, and a bad channel token can never affect video playback.
Every fix gets a test that FAILS on the old code (write the test first, show it failing in the report).

## 1. Error classification (tier memory poisoning)
`ChannelBrowse.executeBrowse` maps every non-2xx to `Blocked`, and `FallbackChain` remembers Blocked/RateLimited/SignIn/Captcha per TIER (not per operation) for 15 minutes,
so one stale continuation token (HTTP 400) can make the chain skip the alt client for streams too once NewPipe answers. Fix:
- 400 / 404 on a continuation = new `ExtractorError`/reason `TOKEN_EXPIRED` (or `Unplayable`), NOT a tier-health reason. 5xx = `Network`/`TierUnavailable`, not Blocked. 403 and 429 stay Blocked / RateLimited.
- Scope tier memory by operation family (playback vs channel browse) OR do not record routes for channel-browse operations at all. Test: after a channel-token 400 followed by a NewPipe success, `streams()` still tries the alt client.

## 2. Tokens never cross tiers
Native continuation tokens and NewPipe page tokens are different things. Prefix or wrap tokens with the tier that issued them (`n1:` native, `np:` NewPipe), and have each tier return a typed
`TokenNotMine` failure that the chain does NOT treat as a tier failure and does NOT forward to the other tier; the UI then reloads the tab from page 1 keeping the selected sort. Test both directions.
NewPipe `channelTabByContinuation` must return a failure (not an empty `Success`) for a token it cannot parse.

## 3. Continuation retry must not append page 1
`ChannelBrowse.tab(..., continuation)` retries a failed continuation by fetching page 1 of the default sort and returns it as if it were the next page; the UI appends it (duplicates, lost sort).
Replace with: return `TOKEN_EXPIRED`; the store re-requests the tab with the currently selected sort option, REPLACES the list, and restores scroll to the top with a small "Refreshed" note. `continuation()` gets the same handling. Test: expired token during paging => list replaced once, no duplicates, sort preserved.

## 4. Empty parse is not success
`parseTabPage` returns an empty page as Success when YouTube changes its JSON. For a first page of Videos/Shorts/Live/Playlists, if the response has no items, no shelves, no filters and no recognised "this channel has no ..." empty-state message,
return `ParseFailure` so the chain falls to NewPipe, and log the top-level renderer keys (names only) in Diagnostics. Genuinely empty tabs (empty-state text present, or landing says 0 videos) stay Success/Empty. Add fixtures: a truncated/changed-shape response must produce ParseFailure; a real empty channel must produce Empty.
Unknown-renderer logging: rate-limit to one NOTE per renderer type per hour so Diagnostics is not flooded.

## 5. NewPipe fallback correctness
- `channelSearch` in the NewPipe fallback runs a GLOBAL search and ignores the channel: wrong results shown as in-channel results. Either implement channel-scoped filtering (search then filter by channelId, labelled as limited) or return `notSupported` so the UI hides in-channel search on the fallback.
- Fallback for COURSES, POSTS, SHOWS, PODCASTS, RELEASES returns `Success(empty)`: return `notSupported`/`Unavailable` so the UI says "Not available right now" instead of "No items found".
- `channelAbout` fallback: label as partial.

## 6. Tabs: show only what exists
`ChannelScreen` injects Shorts, Playlists and Courses tabs for every channel using hard-coded params. Show only the tabs in the landing response (task 47 spec), keep the hard-coded params only as a fallback when the landing response has NO tab list (parse failure). Test: channel without Courses shows no Courses tab; landing with unknown tab shows it via its params without crashing.

## 7. Smaller items
- Use the current UTC offset (`TimeZone.getDefault().getOffset(now)`), not `rawOffset` (wrong in DST).
- `hl/gl` hard-coded en/US while NewPipe follows `Settings.contentRegion`: pass the region (keep `hl=en` for parsing subscriber text, or parse counts locale-independently) and test with a non-US region.
- Tab cache: add an explicit refresh path (pull-to-refresh / retry bypasses the 15 minute cache) and a test.
- Fixtures total ~12 MB (landing 6.9 MB): trim each to the minimal subtree the parser needs (keep the shape, drop unrelated sections) and keep them scrubbed; replace the blanket leak-audit exclusion for fixtures with a narrow check (no visitorData/cookies/emails in fixtures).
- Do NOT leave regenerated screenshots in the tree: the tests rewrote ~50 PNGs under docs/reports/12-screens and 23-screens (earlier reports' evidence). Restore them (`git checkout -- docs/reports/12-screens docs/reports/23-screens`) and make the screenshot tests write to build/ unless a `-Pscreens=docs` flag is given.
- Android: run the self-test on the phone (channel landing, Videos sorted Oldest, paging 3 pages, Shorts tab, tab swipe) and add the channel flows to `SelfTestActivity`; kill the network mid-paging and verify the retry row, then recovery.

## 8. Closing
Full suite after the last change, Android compile + on-device channel self-test, a short "fault matrix" table in the report (429, 403, 400-on-token, 5xx, offline, timeout, truncated JSON, changed JSON shape) with the user-visible result for each, NOT VERIFIED list.
