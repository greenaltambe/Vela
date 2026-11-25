# 151 Shorts sources, and how to build Home's groups (investigation and design)

## 1. Summary
The Shorts tab does not show YouTube's recommended Shorts, signed in or out: it mixes Shorts from your subscriptions, from channels seen on Home and from `trending()` channels (which are live streams, task 138). YouTube's own Shorts feed answers both signed in and signed out. Signed-out Home gets no videos, Shorts or groups from YouTube. Design below; no app code changed.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| Shorts tab source (`ShortsScreen.kt`, `ShortsSource`) | channels' Shorts tabs only; no recommendation call anywhere in the code | code read |
| Signed out Shorts tab | local subscriptions plus `trending()` channels; empty message if neither gives anything | code read, not run in the app |
| YouTube Shorts feed (`reel/reel_item_watch`, then `reel/reel_watch_sequence`), signed out | 8 pages, 66 distinct Shorts (4 repeats), 1.1 to 2.1 s a page | run (live) |
| Same, signed in | 8 pages, 79 distinct, no repeats, 1.5 to 2.8 s a page, plus one 50 s wait | run (live, read-only) |
| Ads in those feeds | 0 of 149 entries | run (live) |
| Title and channel inside the feed | 44 of 149 entries; every entry had id and thumbnail | run (live) |
| Response size, signed out | pages ran from about 130 KB to 2 MB unpacked; one request measured 127 KB (29 KB gzipped) plain and 3 KB (1 KB) with a field mask, id and thumbnail kept | run (live) |
| Response size, signed in | 380 KB to 2.2 MB unpacked, about 98 % unused prefetch data | run (live); field mask not tried signed in |
| `disablePlayerResponse` on the sequence call | no real saving (29 to 27 KB) | run (live) |
| Region | `gl=IN` answers the same way | run (live) |
| Seeding from a Home Short (`sequenceParams`) | returns a sequence (11 entries) | run (live, signed in) |
| Seeding from a bare video id | no sequence token with the request tried | run (live) |
| Home Shorts shelf items | 18 of 18 have id, title and `sequenceParams`; 0 of 18 name a channel | run (live) |
| Shorts outside "Shorts" shelves | none: 0 of 388 ordinary video items open as a Short (5 are under a minute) | run (live, 16 Home and chip pages) |
| Signed-out Home, `gl=US` and `IN` | 0 videos, 0 Shorts, 0 shelves, only YouTube's "sign in" nudge | run (live) |

## 3. Files created/changed
This report only. Temporary live probes were written, run and deleted; raw responses were deleted.

## 4. Decisions the spec didn't dictate (the proposed design)

### Rules
| thing | which setting decides |
|---|---|
| A group made only of Shorts | the Shorts setting, nothing else |
| Any other group (games, "Most hyped", unknown kinds) | the new switch; a Short inside it also obeys the Shorts setting |
| A Short in the ordinary feed | the Shorts setting |
| Games and Playables cards | never shown (not videos) |

The switch: `home.youtubeGroups`, "YouTube's groups on Home", default on, applies to All and to every chip. As a `home.` schema key it syncs and backs up without further work.

### Parsing, for reliability
- Walk only the top-level list (first page and continuation pages). A `richItemRenderer` is a feed video; any `richSectionRenderer` is a group, whatever is inside, so a new kind such as "Most hyped" cannot leak into the feed.
- Take the next-page token from the top-level list only (today the first one found anywhere is used).
- Mark `isShort` from the item's shape (`shortsLockupViewModel` or `reelWatchEndpoint`); today Home never sets it.
- If the expected list is missing altogether, fall back to today's flat parse and note it in Diagnostics: a layout change then looks like today, not like an empty Home.
- Separate Home parser; `videos()` stays for history and the rest. Offline tests on hand-built fixtures, one per shape above.

### Model and screen
- `HomePage` gains `groups` (title, subtitle, videos, id of the feed video before it). Feed paging and de-duplication are unchanged; a group drops videos already listed.
- Kept in `HomeFeedStore` beside each list (All and each chip), appended on paging.
- Drawn through the existing list injections: `VideoCarousel` or `ShortsCarousel`, same filters as other shelves. If the anchor video is filtered out, use the one before it.
- Vela's own shelf slots move down when one would land next to a YouTube group.
- No extra requests: groups come in pages already fetched.

### Shorts tab
- Source order: YouTube's feed (account when signed in, anonymous otherwise), falling back to today's channel mix when it fails, is refused by the budget, or returns nothing.
- A Home Short opens the player on that Short and continues with its `sequenceParams`. Opened from a channel: unchanged.
- Ask with a field mask for id, thumbnail and continuation; parse tolerantly so a full answer still works if the mask is ignored.
- Title and channel come from the lookup playback does for a video it knows nothing about (corrected in task 152: that is a second request per Short, and playback waits for it today).
- Channel rules (blocklist, per-channel Shorts setting) can only be applied after that lookup: a blocked Short is skipped then. In Home's Shorts shelf, which names no channel, only the global Shorts setting, title keywords, blocked words, watched and "Not interested" can apply.
- Efficiency: a page of 8 to 15 covers a few minutes, so under one call a minute while watching; fetch the next when 3 remain (as now). Account calls share a budget of 6 browse calls a minute with Home and sync and run one at a time, so the Shorts read should wait at most 15 s, then fall back.
- Anonymous calls go through the same pacing as other anonymous traffic.

### Suggested order of work
1. Parser and fixtures. 2. Groups on Home, switch, sync test. 3. Shorts feed and fallback. 4. Region.

### Open questions for the owner
1. Signed in, with YouTube's Shorts shelves shown, drop Vela's own "Shorts" shelf there (recommended; it stays signed out)?
2. Signed out, should the Shorts tab use YouTube's anonymous feed first (recommended), or your subscriptions first?
3. Send "Region for content" with account Home and Shorts (recommended)? Today both clients ignore it and say `US`.

## 5. Known issues / unverified assumptions
- "Most hyped" is still unseen (task 150); the container rule is meant to cover it.
- Not measured: whether the signed-in Shorts list is personalised the way the website's is (it differed between two sessions, as did the signed-out one).
- Whether recommendations learn from watching in Vela: only possible with "Tell YouTube what I watch" (off by default), and that reporting is unverified for Shorts.
- Signed-out continuity: keeping `visitorData` between sessions might make the anonymous feed less random; not measured.
- The 50 s wait is consistent with the account's per-minute budget but was not confirmed as that.
- Field masks and these endpoints are undocumented and can change.
- Playlist or other non-video items inside a group were not seen; they would be skipped.
- Live probes used the real session in `~/.vela-test/cookies.txt`, reads only, no watch reporting.

## 6. Manual test checklist
1. Compare the first ten Shorts in Vela with those on youtube.com/shorts for the same account: they should be unrelated today.
2. Sign out and open the Shorts tab: note whether it fills, and from whom.
3. Answer the three questions in section 4.
