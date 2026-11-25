# Plan: YouTube groups on Home, YouTube's Shorts feed, region

Background and measurements: reports 150, 151, 152. Status: all six stages built; device testing is the owner's.

## 1. What "works every time" can mean

YouTube's responses are undocumented and can change or fail. The promise is therefore: **every failure has a defined result, that result is tested,
Home and Shorts never crash or sit empty while another source can answer, and a change at YouTube shows up in Diagnostics.**

## 2. Invariants (each gets a test)

1. A video inside a YouTube group is never in the main feed.
2. Every top-level Home item is accounted for: video, group, ad (dropped), next-page token, or "unknown" (counted and logged). Nothing vanishes silently.
3. One bad item costs that item only, never the page.
4. A next-page token is only sent to the source that issued it.
5. An answer for a previous account, region, chip or list is discarded.
6. At most one feed request per list is in flight.
7. No retry or follow-up loop is unbounded.
8. A setting change shows at once, without a reload (region excepted, which reloads).
9. Starting a Short never waits for its title.
10. Account traffic goes only through the governor; the kill switch and breaker are obeyed.

## 3. Stages

Each stage leaves the app working, passes its gate, and gets its own report. You commit between stages.

Gate: `./gradlew allTests test :androidApp:compileDebugKotlin` (plain `test` is what runs `innertube-auth` and the other JVM modules), plus the stage's live check where it has one.

### Stage A: region (small, first, so later live checks use your country). DONE, report 154
- `InnertubeAccountReads` takes `region: () -> String`; each call uses `web.copy(gl = region)`, blank meaning `US`. Wired in `PlaybackRuntime.kt` and desktop `main.kt`; also wire its `onFailure` to Diagnostics.
- `HomeFeedStore.forRegion` beside `forAccount`: a change empties feed, chips and shelves. Setting description reworded.
- Tests: request body carries the region; changing it reloads Home once.

### Stage B: Home parser (no visible change yet). DONE, report 155
- New `HomeFeedParser` in `innertube-auth`, one pass over the top-level list (first page, appended page, chip reload).
- Output: feed videos, groups (`SHORTS` or `SUGGESTED`, title, subtitle, items, anchor = id of the feed video before it), next token, counts.
- Classify by container: any `richSectionRenderer` is a group; all items Shorts makes it `SHORTS`, otherwise `SUGGESTED`. No playable item: dropped and counted.
- Reuse the existing video and Shorts item parsers; set `isShort` from shape; keep a Short's `sequenceParams`.
- Fallback to the current flat parse when the list is missing, or when the walk yields nothing but the flat parse finds videos. Logged with the reason.
- `videos()` is untouched (history and other callers).
- Fixtures (hand-built, no account data): first page, appended page, chip reload, game shelf, Shorts shelf, mixed shelf, unknown section, games-only shelf, ad, a token inside a shelf, broken item, missing list, empty page.
- Live check: All and every chip; assert unknown = 0 or print the unknown kinds; no group id in the feed. Prints counts only.

### Stage C: groups on Home and the switch. DONE, report 156
- `HomePage.groups` (default empty, so existing fakes compile). `HomeFeedStore` keeps groups with each list; paging appends; de-duplication against feed and earlier groups.
- A pure function places everything: `(videos shown, YouTube groups, Vela slots, settings, filter) -> injections`. Rules: anchor on the video before, walking back if filtered out; stable keys; a Vela shelf that would touch a group moves down; groups under 3 items hidden; local Filter hides groups; chips show them.
- Switch `home.youtubeGroups` (default on) applied in that function only, so toggling is instant. Schema, Home page, copy, summary.
- Shorts groups: Shorts setting only. Signed in, Vela's Shorts shelf is skipped.
- "Empty page" now means no visible videos and no visible groups; follow at most 3 further pages.
- Group videos go through the same filters, DeArrow prefetch and card numbers as other shelves.
- Tests: placement function (table-driven), switch, Shorts setting, chips, Filter, paging, account change mid-load, sync and backup round trip of the new key.

### Stage D: playback no longer waits for a Short's title. DONE, report 157
- `PlaybackSession.start` gets `awaitInfo` (default true: everything else unchanged). False: streams load at once; the lookup runs beside it and applies only if the same video is still loaded.
- It updates title, channel name and channel id, publishes them (`MediaInfo` gains the channel id), and fills the history row whichever came first.
- Tests with a held-back lookup: playback starts; late answer applies; stale answer ignored; history row complete in both orders; lookup failure harmless.

### Stage E: Shorts data. DONE, report 158
- `ShortsFeedParser`: entries to (id, thumbnail), ads dropped, next token; masked and full answers parse alike.
- Account: `AccountReads.shorts(seed, continuation)`. `InnertubeClient.call` gains extra headers for the field mask. Waits at most 15 s on the governor.
- Anonymous: `Extractor.shortsFeed(seed, continuation)`, implemented by the alt client with the region; forwarded by timeout, pacing, chain (`AccountOp.NEVER`) and fault wrappers; added to `WrappersForwardEverythingTest`.
- Start: the seedless answer's first video plays first; a tapped Home Short is put first and continues from its `sequenceParams`.
- Live check, both paths: 3 pages, distinct ids, token present, answer size. Also measure whether a mask works on the seedless answer.
- The health screen gets an anonymous Shorts check, so breakage is visible without opening the tab.

### Stage F: Shorts screen. DONE, report 159
- One `ShortsFeed` object owns the source order (account, anonymous, today's channel mix), the token, de-duplication and the generation number. The screen only asks for "more".
- A source is kept once chosen; a switch drops its token. The account is tried again at the next fresh load.
- Look one Short ahead: fetch its title and channel before it is shown, and pass it to playback as a complete hint, so no second lookup happens, per-channel settings apply from the start, and blocked ones are known early.
- A blocked Short is never removed under the pager: it shows as hidden and the pager moves on in the direction of travel.
- Tests: source order for each failure, token isolation, reseed once, duplicates-only page, blocked skip forwards and back, account and region change mid-load, tapped Home Short, Shorts hidden fetches nothing, daily limit unchanged.

## 4. Failure table

| what happens | result |
|---|---|
| Home layout not recognised | flat list as today, Diagnostics line |
| New kind of group | treated as a suggested group, hidden by the switch |
| Page with only hidden groups | next page fetched, at most 3 times |
| Later Home page fails | list kept, "Load more" retries |
| Account: signed out, re-login, kill switch, breaker, budget over 15 s | Home as today (message and Retry); Shorts use the anonymous feed |
| Shorts continuation refused | one new sequence, then the next source |
| Shorts page empty or all seen | follow at most 3, then the next source |
| All Shorts sources fail | message with "Try again" |
| Title lookup fails | the Short plays; channel rules cannot apply to it |
| A Short cannot play | the player's error, swipe continues |
| Offline | cached list if there is one, otherwise today's offline message |

## 5. Cost

| action | requests |
|---|---|
| Home groups | none extra |
| Shorts start | 1 seed, then 1 page |
| Shorts watching | about 1 page per 8 to 13 Shorts, fetched when 3 remain |
| Each Short | streams plus one title lookup (done one ahead) |
| Shorts hidden or tab not opened | none |

A masked Shorts page measured 3 to 4 thousand characters against up to 2.2 million. Parsing stays off the main thread. At most one Short's lookup is wasted when you leave.

## 6. Not covered

"Most hyped" has never been seen. Android cannot be run by the agent: Android behaviour is compile-only until you test it. Token lifetime is unmeasured. Two requests per Short are not merged. Blocking a channel cannot clear its Shorts from a Home row.
