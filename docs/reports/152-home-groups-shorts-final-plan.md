# 152 Home groups, Shorts and region: gap check and final plan

## 1. Summary
Owner decisions: signed in, show YouTube's Shorts shelves instead of Vela's; the Shorts tab uses YouTube's feed, signed out too; Home and Shorts follow "Region for content"; one switch for the other groups, chips included. A second pass found gaps the design in task 151 missed; they are folded in below. This report replaces 151's design section. No app code changed.

"YouTube's anonymous feed" means the Shorts that youtube.com/shorts serves a visitor who is not signed in: no account or cookies are sent, so it is not built around you. Two separate sessions shared no Shorts.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| Field mask, signed in | pages of 8 to 13 Shorts in 2.9 to 4.4 thousand characters (380 KB to 2.2 MB without), 29 of 29 distinct, paging works | run (live, read-only, `gl=IN`) |
| Seedless first answer | 294 KB; names a first video that the next pages do not repeat | run (live) |
| Home Short's `sequenceParams` | all 18 differ; the sequence for a tapped one does not contain it | run (live, one tap tried) |
| Starting a Short: streams only, against streams plus title/channel lookup | 1.31 to 1.41 s against 1.34 to 1.52 s; two requests instead of one | run (live, NewPipe, desktop, 7 Shorts) |
| `needsInfo` when title or channel id is blank | playback waits for both answers | code read |
| `InnertubeAccountReads` on both platforms | built without the region; always `US` | code read |
| `content.region` | in the never-sync list; its text says it matters "when signed out" | code read |
| Alt-client tier | not wrapped in the anonymous pacing | code read |

## 3. Files created/changed
This report; one corrected sentence in report 151. Temporary live probes were written, run and deleted.

## 4. Decisions the spec didn't dictate (final plan)

### Step 1: Home parser (`innertube-auth`)
- Walk the top-level list only: feed items are videos, every `richSectionRenderer` is a group, the next-page token comes from the top level.
- A group made only of Shorts is a Shorts group; any other group with videos is a suggested group. Games and Playables are dropped.
- Set `isShort` from the item's shape. Reuse the existing Shorts item parser (title, views, thumbnail) and keep `sequenceParams`.
- No top-level list found: today's flat parse, plus a Diagnostics note.

### Step 2: Home screen and setting
- `HomePage.groups`; stored per list (All and each chip), appended on paging, de-duplicated against the feed and earlier groups.
- Groups show in chip views too (today every shelf is removed there, so they need their own path) and are hidden while the local Filter is active, like other shelves.
- A page with groups but no feed videos is not "empty"; with groups off, such a page makes Home fetch the next one.
- Switch `home.youtubeGroups` (default on): settings schema, Home settings page, copy and summary; syncs and backs up; round-trip test.
- Shorts groups obey only the Shorts setting. Signed in, Vela's own Shorts shelf is not shown; its Customize hint says it is for signed-out Home. Signed out it stays.
- Vela's other shelves move down when one would touch a YouTube group. Groups under three videos after filtering are not drawn.
- Tapping a Shorts item opens it, then the rest of its shelf, then its own sequence.

### Step 3: Shorts tab
- Order: account feed (signed in), else YouTube's anonymous feed, else today's channel mix. A signed-in failure (re-login needed, budget, kill switch) drops to anonymous, never to an empty page.
- Account calls: new read in `AccountReads`, waiting at most 15 s. Anonymous calls: new `Extractor.shortsFeed`, implemented in the alt client, forwarded by the timeout, pacing and chain wrappers (the forwarding test covers it), and paced explicitly.
- Field mask for id, thumbnail, ad marker and continuation; drop ad entries; parse a full answer the same way. Use the first video the seedless answer names. Prepend a tapped Short. A refused continuation: start a new sequence once.
- **Do not make a Short wait for its title.** Start playback on streams alone and fill title, channel name and channel id when the lookup returns; update the list item, the menus ("Go to channel") and the history row in either order.
- After that lookup apply the blocklist, title keywords and per-channel Shorts setting; skip a Short that fails.
- Unchanged: daily limit, break reminder, Shorts setting (hidden: nothing is fetched), Shorts opened from a channel.

### Step 4: Region
- Pass the region to account Home, chips and Shorts on both platforms, and to the anonymous Shorts feed. Other account calls are left alone.
- Changing it empties the Home, shelf and Shorts caches. Reword the setting's description.
- It stays per device (not synced), as today. Say so if you want it synced.

### Tests and checks
Offline fixtures (hand-built, no account data) for: each group kind, an unknown section, the missing-list fallback, top-level token, Shorts pages with an ad and with a full unmasked answer; UI tests for the switch, chips, Shorts setting and the fallback order; one live test per platform-independent read. Android is compile-only.

## 5. Known issues / unverified assumptions
- "Most hyped" has never been seen; the container rule should cover it.
- Per-channel settings that apply at the first moment of playback (speed, SponsorBlock overrides) get no channel id for a Short whose channel is not yet known. This is accepted.
- Home's Shorts items name no channel: blocking a channel cannot remove them from the shelf (it is applied when one plays).
- Two lookups per Short remain. Merging them in the extractor would halve requests; not in this plan.
- Not measured: how long sequence tokens live, a field mask on the seedless answer, whether watch reporting trains Shorts recommendations, Android timings.
- The endpoints and field masks are undocumented.

## 6. Manual test checklist
Before building:
1. Settings > Home > Region for content: note whether it shows your country (the default is United States).
2. Reply "build", or say what to change.
