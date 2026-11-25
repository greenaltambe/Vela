# 150 Home: YouTube's grouped shelves merged into the feed (investigation)

## 1. Summary
Confirmed. YouTube sends groups as `richShelfRenderer` (inside `richSectionRenderer`), but `InnertubeParsers.videos()` collects every video lockup anywhere in the response, so a group's videos are poured into the feed and its title is lost. "Most hyped" did not appear in 40 live pages, so that one group is not seen first-hand; any group of this kind is flattened the same way. No app code changed.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| Gaming chip, first page (3 loads) | 45 to 47 of the 62 to 66 videos Vela would list came from four shelves ("More from this game") | run (live, signed in, read-only) |
| Unfiltered Home, first page: shelves seen | "Shorts" (usually two, 9 each) and "YouTube Playables"; neither reaches the list today | run (live) |
| Unfiltered Home, later pages | no shelves in the pages sampled | run (live) |
| 10 chips' first pages (`gl=IN`) | shelves only on Gaming; "Top live games" plus four game shelves | run (live) |
| "Most hyped" or any text with "hype" | none in 40 pages, `gl=US` and `gl=IN` | run (live) |
| Cause in the parser | `videos()` uses `findAll("videoRenderer")` and `findAll("lockupViewModel")` over the whole response | code read |

Shelf shapes seen:

| title | items | has videos | today in Vela |
|---|---|---|---|
| game name (subtitle "More from this game") | `lockupViewModel` | yes | merged into the feed, untitled |
| Shorts (menu "Show fewer Shorts") | `shortsLockupViewModel` | shorts | dropped (not parsed) |
| YouTube Playables | `miniGameCardViewModel` | no | dropped |
| Top live games | `gameCardRenderer` | no | dropped |

## 3. Files created/changed
- This report only. Temporary live probes were written, run and deleted; the raw responses (personal data) were kept in the scratchpad and deleted.

## 4. Decisions the spec didn't dictate
Proposed design, nothing built:
- **Parse (`innertube-auth`)**: a Home-only parser that walks the top-level grid items in order. Grid items go to `videos`; each `richShelfRenderer` with videos becomes a shelf (title, subtitle, videos, the id of the video before it). `videos()` stays as it is for history and the other callers.
- **Model (`shared`)**: `HomePage.shelves`; kept in `HomeFeedStore` beside each list (All and each chip) and appended on paging.
- **Show**: reuse the existing list injections and `VideoCarousel`, placed where YouTube put them, with the same filters as other shelves (blocklist, hide watched, keywords, DeArrow; not drawn under three videos). Vela's own shelves use fixed slots, so two carousels could end up adjacent and need a spacing rule.
- **Hide**: a switch `home.youtubeShelves`, "YouTube's groups on Home", default on, on the Home settings page. Off: shelves are discarded, including their videos. A `home.` key in the settings schema syncs and backs up with no extra work (`SettingsDomain` excludes neither).
- **Generic handling** by renderer, not by title, so "Most hyped" is covered if it uses this renderer.
- Games and Playables shelves stay dropped: Vela cannot play them.

Open questions for the owner:
1. Shorts shelves: show YouTube's own where it puts them, or keep dropping them since Vela has its own Shorts shelf? If shown, they must obey the Shorts setting.
2. One switch for all groups, or also "hide this group" per title?
3. Should the switch apply to chip feeds too (recommended)? With it off, the Gaming chip's first page drops to 17 to 19 videos and relies on paging.

## 5. Known issues / unverified assumptions
- "Most hyped" is unseen: its renderer is an assumption. If you see it again, a screenshot and whether it was on Android or desktop would settle it.
- Possible reasons it did not appear (none verified): shown only occasionally, or to some client types; Vela asks as the web client.
- Side finding: the account Home always asks with `gl=US`; "Region for content" is not passed to `InnertubeAccountReads` on either platform. `gl=IN` showed no different shelf kinds in this sample.
- Shorts items use a different shape and would need their own parser if question 1 is "show".
- Hidden shelves still cost nothing extra in requests, but whether any shelf has its own "show more" paging was not examined.
- The probes used the real session in `~/.vela-test/cookies.txt`, browse reads only.

## 6. Manual test checklist
1. When "Most hyped" shows again, note the platform, the chip that was selected, and screenshot the YouTube app or website showing it.
2. Open the Gaming chip in Vela and on youtube.com: the website groups "More from this game", Vela shows one long list.
3. Answer the three questions in section 4.
