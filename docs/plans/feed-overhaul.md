# Feed overhaul: Home = your feed with YouTube's chips; Explore = Trending and topics

Status: planned, not started. Supersedes `next-home-feed-split.md` and the YouTube part of `later-custom-feeds.md` (those stay as history).
Source of every number below: live tests on the owner's signed-in account, 2026-11-24 (reports 131 and the chip test
`LiveChipFollowTest`). Nothing here was guessed without saying so; unmeasured items are marked **UNVERIFIED**.

## 1. Problems today
1. Signed-in Home reads only page one of `FEwhat_to_watch` (about 22 videos). `InnertubeAccountReads.recommended()` returns a plain list
   and drops the continuation token.
2. At the end of that page `HomeScreen` calls `load(more = true)`, which has no continuation, so it appends Trending page one. Trending
   contains live streams. Result: Home ends in content that is not the user's feed, and there is no endless scroll.
3. The top rows of Home barely change between refreshes. This is YouTube's behaviour, not Vela's (section 2).
4. Home's filter chips (All, Unwatched, Under 10 min, Live, Music) only filter what is already loaded, so "Live" and "Music" can be empty
   while YouTube has plenty.
5. Trending and the account feed are mixed on one page; the owner wants one source per page, clearly named.

## 2. What was measured
| Fact | Measurement |
|---|---|
| Home refresh variance | Whole list: 20-35% of 31 videos change per request. With or without `visitorData`: no difference (hypothesis refuted). |
| Home refresh, by position | Top 3 kept their position in 3, 3, 2 of 3 comparisons; top 10 kept position 4, 4, 5 times (same videos 9, 9, 7 of 10, reordered). YouTube keeps the top stable. |
| Home paging | Page one 22 videos + token; 5 further pages gave 23-24 new videos each (141 videos), more still offered. |
| Home chip bar | `feedFilterChipBarRenderer` with 21 `chipCloudChipRenderer` in the first Home response. Chip labels are personalised and rotate between requests (e.g. "Calculus", "History" appeared in one request, others in another). |
| Fixed chips | "Custom feed" (`STYLE_AI_MODE_CHIP`, `ATTRIBUTE_FILTER_TYPE_CUSTOM_FEED_CREATE_FEED`), "All" (selected), "Gaming", "Music", "Podcasts", "Live", "Recently uploaded", "Watched", "New to you" (`ATTRIBUTE_FILTER_TYPE_EXPLORE`), plus about ten topic chips from the account's history. |
| Chip mechanics | Each chip has `navigationEndpoint.continuationCommand.token` (a BROWSE continuation, about 600 characters). Following it with the normal `continuation` call returns that chip's feed. No extra parameters needed. |
| Chip results (19 chips followed) | 18 of 19 answers had 17-66 videos and nearly no overlap with All (0-3 of the same videos; "Recently uploaded" 9, "Watched" 2). Paging: 14 chips offered a second page of 22-33 new videos with more after; "Gaming" (66), "Podcasts" (18), "Live" (17), "Watched" (18) offered no next page. |
| Chip failures | "Mixes" returned 0 videos (its items are probably playlists/mixes, a different renderer). |
| Chip latency | Typically 1.0-2.3 s. Two calls took about 50 s: the account governor's BROWSE budget made them wait. 20 chips in a row therefore must never be prefetched. |
| Live detection | The "Live" chip returned 17 videos but `VideoSummary.isLive` was true for 0 of them: the parser misses the live marker on this response shape (**bug to fix**, needed for the Live badge and filters). |
| Custom feed form | The Home response holds `feedCustomizationFormViewModel` with only an experimental-AI disclaimer and a loading message. The form is drawn by the web client. **No submit command or endpoint is in the response**, so creating a custom feed cannot be done from this data. |
| Web browse of old pages | `browse` with `FEexplore`, `FEtrending`, `FElive` answers HTTP 400 for this account on the web client. `FEsubscriptions` works (2.2 MB) but carries no chips. So Explore cannot be rebuilt from those pages. |
| Subs | `FEsubscriptions` has no chip bar; Vela's own Subs (RSS plus groups) stays as it is. |

## 3. Target design

### 3.1 Pages and what each one is
- **Home** (signed in): the account feed, with YouTube's own chip row above it. One source per page; never Trending, never appended.
- **Home** (signed out): Trending as its content, with the plain label "Trending". Not a dead end, and honest about what it is.
- **Explore**: unchanged in purpose: Trending plus topic pills (Music, Gaming, News, Live, Sports, ...). Trending and Live live here.
- **Subs**: unchanged (RSS feed, groups, posts, durations).
- **Shorts**, **Library**, **Downloads**: unchanged.

### 3.2 The chip row on Home (one row, not two)
- Signed in: YouTube's chips, in YouTube's order, first chip "All". Tapping loads that chip's feed (section 4.3); the selected chip is kept
  while the app lives, per account.
- The current local filters (Unwatched, Under 10 min, Live, Music) would make a second row. Proposal: move the useful local ones
  (Unwatched, Under 10 min, hide Shorts) into a "Filter" button at the start of the chip row that opens a small sheet. They apply on top of
  whichever chip is selected (they are feed-pipeline steps, so they work on every chip's list). The local "Live" and "Music" filters go away
  because YouTube's chips do it properly. **Decision needed from the owner.**
- Signed out: no YouTube chips exist; Home shows Trending and the Filter button only.
- A chip that comes back empty (like "Mixes") is hidden for the rest of the session and shown as "Nothing here" if it was just tapped.
- Chip labels come from the viewing history, so they are personal: they never go into the diagnostics log or crash reports (the log
  already redacts account data; add the labels to that rule).

### 3.3 Custom feeds
1. **Show YouTube's custom feeds: MEASURED, works.** A custom feed made on youtube.com (owner's, 2026-11-24) came back in the Home chip
   bar as an ordinary chip: `uniqueId` `ATTRIBUTE_FILTER_TYPE_CUSTOM_FEED_<n>`, placed right after "All", with the same
   `navigationEndpoint.continuationCommand.token` as every other chip. Following it returned 24 videos in 4.3 s (0 of them also in All),
   and a second page of 24 new videos with more after. So task 4's chip row shows custom feeds with no extra work; only the creation
   stays in YouTube's own app or website. The chip also carries a three-dot menu with **Rename** (a form submit with `params`) and
   **Delete** (a `/youtubei/v1/feedback` call with a `feedbackToken`). Both are write actions: **not tested** (it would change the
   owner's account), and not planned for now.
2. **Create a custom feed inside Vela (LATER, parked by the owner)** needs the form's submit request, which is not in the Home response
   (measured again: the "Custom feed" chip only holds the form's disclaimer text). Options, in order: (a) read the
   web client's script to find the request (fragile, and the feature is marked experimental and AI-backed by YouTube); (b) capture the
   official mobile app's traffic with a proxy; (c) skip it. Not planned for the first versions. Revisit after M1.
3. **Local rule feeds** (keywords, channels, duration, live only) remain the fallback for signed-out users and for when YouTube changes
   its chips; they are the earlier idea in `later-custom-feeds.md` and are not part of this overhaul.
4. The "describe what you want" sentence-to-feed idea needs a language model; keep it out of scope; if ever added it is opt-in and off by
   default because the text leaves the device.

### 3.4 Empty and failure states (Home, signed in)
| Situation | What the page shows |
|---|---|
| Feed fails to load | Error with Retry and a small "Browse Explore" link. Never silent Trending. |
| Account has no recommendations (new account) | Short explanation and the Explore link. |
| A chip fails | The error appears in the list area only; the chip row stays; Retry reloads that chip. |
| Signed out | Trending with a "Trending" label. |
| Paging fails mid-list | The existing "Load more" button; the list is kept. |
| Signed in, account needs login again | The existing re-login prompt (auth state), then the feed. |

## 4. Technical plan

### 4.1 Data layer (`innertube-auth`, `:shared`)
- New parser pieces in `InnertubeParsers`:
  - `homeChips(page): List<HomeChip>` with `HomeChip(label, token?, selected, kind)`; `kind` from `uniqueId` / style
    (`CUSTOM_CREATE`, `ALL`, `NORMAL`). The "Custom feed" create chip is not shown as a normal chip (see 3.3); a created custom feed is.
  - Keep `videos(page)` and `continuationToken(page)`; verify they handle the chip continuation response shape (the test already got
    17-66 videos from it, so `videos` works; `continuationToken` also returned tokens, so it works too).
  - Fix live detection for the lockup shape (section 2, Live detection) and add a parser test with a fixture.
  - Chip items that are playlists or mixes: return nothing (today) and let the empty-chip rule hide the chip; optionally add a playlist card later.
- `AccountReads` gets one new method and keeps `recommended()` for existing callers and tests:
  `suspend fun home(chip: String? = null, continuation: String? = null): HomePage?`
  with `HomePage(videos, continuation, chips: List<HomeChip>)`. `chip` is the chip's token (null = All); `continuation` pages the current feed.
  `chips` is filled only on the first page of All. Existing fakes in tests keep `recommended()` working through a default implementation.
- `InnertubeAccountReads.home` runs through the same `run {}` wrapper (account governor, BROWSE, patience 15 s interactive). Chip taps use
  the interactive patience too; if the governor would make the user wait longer, return null quickly and let the UI show Retry rather than 50 s of spinner.
- No prefetching of chips. At most one chip request in flight; switching chips cancels the previous one.
- Token lifetime: **UNVERIFIED**. Chip tokens are tied to the response they came in. On a failure with a stale token, re-fetch Home (All) to get fresh chips, then retry once.

### 4.2 Home state (`HomeFeedStore`, `HomeScreen`)
- Store per account: chips (from the last All load), selected chip, and per chip a paged list + loadedAt + scroll memory, exactly like
  `ExploreStore.Entry`. Replace the single `videos / showing / note` fields.
- Remove Trending from the signed-in `load(more = true)` path. Trending is only the signed-out Home content.
- `showing` and `note` fields go; the page header is the chip row. The signed-out label is a small caption above the list.
- Refresh (pull): reloads the selected chip's first page (and chips, if it is All). Same 30 min freshness for a revisit as today.
- Infinite scroll: `onEndReached` pages with the stored continuation; stop and show nothing extra when `continuation == null`
  (for chips like Gaming/Podcasts/Live/Watched that end after one page).
- Dedupe by video id across pages (`PagedList` already does this by id).
- Feed pipeline: chip feeds go through the same steps as Home today (blocklist, dismissed videos and channels, hide Shorts, DeArrow
  titles, watched markers), so a rule applies to every chip. No special cases per chip.
- The shelves above the list (Continue watching, New from your subscriptions, ...) stay on All only, or all chips? Proposal: shelves
  only on All, so a topic chip is a clean list. **Decision needed from the owner.**

### 4.3 Explore
- Keep Trending (NewPipe kiosk) and topic pills (search). Signed-in "Live" and "Music" could use the Home chips of the same name for
  better results: optional, later, because the Home chip needs a signed-in account and Explore must work signed out.
- Check why the "Live" topic is a plain search for the word "Live" (it returns videos about the word, not live streams); use a live-only
  search filter if the extractor offers one. Separate small task.

### 4.4 Settings (one typed schema, per SPEC section 5)
- `home.source` (Recommended / Trending): remove from the UI; map any stored value to the default. Signed-out Home is always Trending.
- New: `home.youtubeChips` (on by default): show YouTube's chip row. When off, Home is the plain All feed.
- New (optional): `home.hiddenChips`: labels the user never wants to see (a "Hide this chip" long-press). Stored per account because the labels are personal.
- Existing `homeChips` (show local filters) and `homeShelves` keep their meaning; update the setting copy and keywords.
- Backup/restore and settings search read the schema, so nothing else to wire. Update `SettingCopy`.

### 4.5 Tests and verification
- Offline (`./gradlew allTests`): parser tests with **synthetic fixtures** (hand-written JSON with the same shape; no real labels,
  ids or tokens committed): chip bar, chip response, empty chip, live lockup, no-chip signed-out page. `FakeAccountReads` for the Home
  state tests: chip switch keeps each chip's list, refresh reloads only the selected chip, signed-out shows Trending, failure shows Retry
  and not Trending, end of feed (no continuation) stops paging, a filter applies to every chip.
- Desktop UI tests (existing harness, `HomeSourceUiTest` pattern): chip row renders, tap switches the list, Filter sheet.
- Live (`liveTest`, owner's cookies, never in `check`): `LiveChipFollowTest` (exists), `LiveHomeRefreshTest` (exists); add a paging soak
  test: follow 10 pages of All and one chip, assert no repeats and report counts.
- Android is compile-only for the agent; every UI item goes to the manual checklist.

## 5. Work order (each becomes a numbered task with its own report)
1. **Parser and live-flag fix**: `homeChips`, chip response handling, live detection bug, fixtures and tests. Offline only.
2. **`AccountReads.home` + continuation**, `InnertubeAccountReads`, fakes; keep `recommended()` working. Offline tests plus the live paging test.
3. **Home page state**: store per chip, endless scroll of the account feed, Trending removed from signed-in Home, empty/failure states,
   signed-out label. No chip UI yet (All only). This alone fixes problems 1 and 2.
4. **Chip row UI** (Android and desktop) with the Filter button moving the local filters, empty-chip hiding, per-chip store.
5. **Settings**: remove the Home source, add `home.youtubeChips` / hidden chips, copy and keywords.
6. **Explore touch-ups**: Live topic quality; optional use of Home chips when signed in.
7. **Custom feed management (parked)**: created feeds already show as chips after task 4; in-app create, rename and delete need the write requests, to be investigated later.
Tasks 1-3 are the core; 4-5 are the visible redesign; 6-7 are polish.

## 6. Risks
- YouTube changes the chip structure or removes the chips: the row is hidden when parsing finds none, and Home falls back to All.
- Governor budget: chip taps can wait long (two calls took 50 s). Mitigated by no prefetching, one request at a time, and a quick Retry.
- Chip tokens may expire (UNVERIFIED); handled by re-fetching Home once.
- Privacy: chip labels reflect watch history; keep them off logs, reports and committed fixtures.
- Account safety: all chip and paging calls are read-only BROWSE calls through the existing governor; no new write actions.

## 7. Owner decisions and measurements still open
- D1 (decided: yes, a "Filter" button) Move the local filters into a "Filter" button (proposal) or keep a second chip row?
- D2 (decided: All only) Shelves (Continue watching, New from subscriptions, ...)
- D3 (decided): the "Custom feed" create chip is hidden for now; creating, renaming and deleting custom feeds in Vela is parked (task 7).
- M1 (done): the owner's custom feed appears as a normal chip and loads and pages (section 3.3).
- M2 (agent, later): token lifetime of chips (follow a chip token after 10 and 30 minutes).

## 8. Decisions made by the owner (2026-11-24)
- Remove the "Home source" setting (Explore has Trending). Signed-out Home is always Trending.
- No "hide this chip" long-press for now.
- Work order is the agent's call; start with tasks 1-3.
- Explore pills get a leading icon (Home chips stay text only) so the two rows look different. Icons are fixed per topic. No "Explore" word on the pills.
- Explore's topic list stays fixed (as on YouTube).
- Same label on both pages is fine; the icon is the distinction.

## 9. Found during task 6 (measured 2026-11-24)
- YouTube has no Trending page any more. NewPipe's "Trending" kiosk fails ("Could not get Now or Videos trending tab") and its default kiosk is "live",
  so Vela's `trending()` returned live streams only (15 of 15 live). That is why live streams showed at the bottom of Home and in "Trending".
- Anonymous Home does not exist: `FEwhat_to_watch` without an account returns the sign-in page, 0 videos.
- NewPipe kiosks: live (15 per page, pages onward), trending_gaming (50, one page), trending_podcasts_episodes (100, one page) work;
  trending_music and trending_movies_and_shows answer HTTP 403.
- Decided with the owner: no Trending pill; Explore pills Gaming, Podcasts, Music, News, Sports, Movies, Learning, Cooking, Tech, Live (Live last, not
  the default); each with a fixed icon; signed-out Home = the shelves plus a sign-in card (no stand-in feed).
