# Next: Home is the account feed only; Trending lives in Explore

> Superseded by `feed-overhaul.md` (Home feed, YouTube chips, custom feed). Kept for history.

Status: agreed direction, not started. Do together with the Home feed fixes below.

## Findings (report 131, measured on a signed-in account)
- Home (`FEwhat_to_watch`) offers a continuation: the first page has about 22 videos, and 5 more pages gave 23-24 new videos each.
  Vela reads only page one (`InnertubeAccountReads.recommended()` returns a list and drops the token).
- At the end of that page `HomeScreen` appends Trending (`load(more = true)` without a continuation), which is where the live
  streams at the bottom of Home come from.
- YouTube keeps the top rows of Home stable and reshuffles the lower ones (top 10: same position 4-5 times out of 10 between requests).

## Direction
1. Carry the continuation through `AccountReads` and `HomeFeedStore`; page the recommended feed without a limit.
2. Never append Trending to a signed-in Home. If the account feed fails or is empty, say so in one line and offer "Open Explore".
3. Signed out (or Home source set to Trending): Home shows Trending as its feed, labelled as Trending.
4. Explore keeps Trending, Live, Music and the other topic pills. Live streams live there.
5. Home filter chips (All, Unwatched, Under 10 min, Live, Music) stay. They only filter what is loaded, so with paging they should
   keep loading more pages while a filter matches little (the existing `MAX_FILTER_PAGES` logic).
6. Subs is unchanged.

## Open points
- Wording of the empty / failed note, and whether the old setting "Home source: Recommended / Trending" stays.
- Whether refresh should also reorder or only reload (YouTube itself keeps the top rows stable, so this is not a Vela bug).

## Decisions after discussion
- The owner does not want two sources mixed on one page: each page shows one thing and says what it is.
- Signed out: Home shows Trending as its content, with a plain label ("Trending"), because an empty page with a button is a dead end.
- Signed in, account feed fails: an error state with Retry (and a quiet "Browse Explore" link), never silent Trending.
- Signed in, empty feed (new account): a short explanation, plus the link to Explore.
- Proposed: drop the "Home source: Recommended / Trending" setting (Explore has Trending); map any saved value to the default.

## YouTube's own Home chips (measured, live, signed-in)
The Home response carries `feedFilterChipBarRenderer` with 21 `chipCloudChipRenderer` chips: "Custom feed", All, Gaming, Music, Podcasts,
Live, News, Recently uploaded, Watched, New to you, and personalised topics (they came from the account's history). Each chip is normally a
continuation that returns that filtered feed, so these can be shown as Home chips with YouTube's own ranking. See `later-custom-feeds.md`.
