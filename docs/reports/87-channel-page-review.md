# 87: Channel page and tabs: design and usability review

## 1. Summary

Review, then fixes (section 7). I opened four different channels on the phone (dark theme, 1272x2800 at 3.5 px/dp) and went through every tab each one offered: Kurzgesagt (8 tabs), Taylor Swift (artist), NASA (live and podcasts), and Adele - Topic (auto-generated music channel).
Two serious problems: auto-generated "Topic" channels are a dead end (one empty Home tab although the channel has videos), and some tabs (Store, Podcasts) show "Could not load: not supported" with a Retry that cannot work. There are also smaller bugs (a doubled word in About, a misleading empty state in channel Search, tab labels with no selected/unselected contrast) and layout issues (the header uses 62% of the screen).

## 2. Verification

| Check | Result | Verified-by |
|---|---|---|
| Kurzgesagt: Home, Videos, Shorts, Shows, Playlists(tab seen in row), Posts, Store, Search, About sheet | Opened and screenshotted (Playlists tab itself not opened) | running (phone) |
| Taylor Swift: header, Home shelves | Seen | running |
| NASA: header, Home, Live tab, Podcasts tab | Seen | running |
| Adele - Topic: header, Home, overflow menu | Seen | running |
| Tab label colours | Selected and unselected tab text are both 0xFFFFB4AA (10.9:1 each), so only the 3 dp underline tells them apart | running (pixels sampled) |
| Header height | Tab bar sits at 62% of screen height (Taylor Swift) and 63% (Kurzgesagt), so first content starts there | running (screenshot) |
| Causes in code | `ChannelScreen.kt:231-249` (Tab without content colours), `:941-942` (empty messages), `:835` ("Joined $it") | code reading |
| Landscape, tablet/desktop layout, light theme, a channel with a members-only/Join button, a channel with no banner or no avatar | Not seen | not verified |
| Swipe between tabs vs. horizontal shelves (code at `ChannelScreen.kt:215-220` changes tab on a 140 px sideways drag) | Not tested | not verified |

## 3. Files created/changed

- Created: this report. No source changes. Screenshots stay in the session scratchpad.
- Phone: the debug build from task 86 is still installed.

## 4. Decisions the spec didn't dictate (proposals)

Severity: **B** = bug or dead end, **U** = usability, **D** = design.

| # | Sev | Finding | Evidence | Proposal |
|---|---|---|---|---|
| 1 | B | **Topic channels are a dead end.** Adele - Topic shows one tab (Home) reading "No content found on this tab." The same channel's videos show in search. | a2/a3 screenshots | When the landing has only an empty Home, load the Videos tab (or the `ChannelItem` list the old `legacy` path already fetches, `ChannelScreen.kt:107-114`) and show it; if still empty say "Nothing to show here. Open in browser". |
| 2 | B | **"Could not load: not supported" on Store and Podcasts**, with a Retry button that can never succeed. | Kurzgesagt Store, NASA Podcasts | Hide tabs whose kind the extractor does not support (STORE, PODCASTS, SHOWS fallbacks), or show a calm "Not available in Vela" with "Open in browser" instead of an error and Retry. |
| 3 | B | **About sheet says "Joined Joined Jul 10, 2013".** `Text("Joined $it")` at `ChannelScreen.kt:835` and the extractor text already starts with "Joined". | k_more screenshot | Strip the prefix once (`removePrefix("Joined ")`) in the UI. |
| 4 | B | **Channel Search tab shows "No results found on this channel." before anything is typed.** | k_search2 | Before a query, show a hint ("Type to search this channel"); only say "No results" after a search ran. Drop the extra filled search button or the leading icon (two search icons side by side). |
| 5 | U | **Tab labels do not show which tab is selected.** All tab text is the same pale pink; only a small underline differs. | pixel sample | Pass `selectedContentColor = primary` and `unselectedContentColor = onSurfaceVariant` (Material's own pairing) to `Tab`. |
| 6 | U | **The header uses 62% of the screen.** Banner, avatar, name, subscribers, a Subscribe row, and the description card all stack before the tabs; the first shelf shows only a title and a thumbnail edge. | k1, t2, nasa1 | Put Subscribe (and a bell) on the same row as the name block or full width directly under it; shrink the banner to about 16:5 on phones; collapse the description to one line. Target: tabs at or above 45%. |
| 7 | U | **No bell/notification control, handle (@name) or video count on the channel page.** The bell exists in Search and Subscriptions (`ChannelBell`) but not here. | header screenshots | Add the bell next to Subscribe once subscribed; show "@handle" and the video count under the subscriber count when the extractor has them. |
| 8 | U | **Subscribe sits alone on its own row**, leaving the right 70% of the row empty. | k1 | See 6. |
| 9 | U | **Channel's own shelves repeat the channel name on every card** (Home tab: "Taylor Swift", "Kurzgesagt - In a Nutshell" under each title). | t3, k2 | Pass `showChannelName = false` (or hide the line) on the channel's own page; show it only for videos by other channels (collab shelves). |
| 10 | U | **Shorts tab marks every item SHORT**, which overlaps the video's own text, and the view-count pill is a full pill (not the 4 dp shape used elsewhere). | k_shorts | Do not draw the SHORT badge inside Shorts lists; use `shapes.extraSmall` for the views pill; keep titles aligned with the thumbnail edge (they are 5 dp inset). |
| 11 | U | **Shows tab cards** have the title in the top right and the remaining space empty. | k_shows | Center the text vertically or add the first video's title/last updated line. Minor. |
| 12 | U | **Switching to an error or short tab jumps the page to the header** (content too short to stay scrolled). | Store, Podcasts | Keep the tab bar pinned: give the tab body a minimum height so the sticky tabs stay under the top bar. |
| 13 | D | **Banner corners are very round** (about 28 dp) and the avatar is a scalloped "cookie" that cuts into faces and logos (Adele, NASA). Recognisability matters more than the shape for a channel icon. | all headers | Banner: `shapes.medium` (12 dp) or square; avatar: circle for photos, keep cookie only for the empty "L" placeholder. |
| 14 | D | **Shelf titles are cut to one line** ("Taylor Swift - The Life of a Showgirl: The ..."). | t2 | Allow two lines for shelf titles. |
| 15 | D | **Live tab uses "139 watching", but the Home and Search lists use "views" for live streams.** The channel tab is right. | nasa_live vs Home | Use the same wording everywhere a stream is live (finding 13 of task 86). |
| 16 | D | **Overflow menu (About, Share, Open in browser)** reaches nearly the right edge and has no icon or divider; fine but About is also reachable from the description's "More". | a_menu | No change needed; mention only. |

What works well: tabs adapt to what each channel offers (Kurzgesagt 8 tabs, NASA with Live and Podcasts), the sticky tab bar with the channel name works, the About sheet is complete (links, stats, country), Posts render images and counts, tab swipe affordance is clear because the row scrolls.

## 5. Known issues / unverified assumptions

- Only dark theme was seen on channels; light theme is expected to have the same layout issues plus the weak surface edges from task 86.
- The Playlists tab and the Taylor Swift Videos/Shorts/Live tabs were not opened; channels with no banner/avatar were not found.
- Whether tab-swipe collides with horizontal scrolling of shelves is untested.
- The Topic-channel cause (extractor returns one empty tab vs. UI filtering) was not traced; proposal 1 may need an extractor change.

## 6. Manual test checklist for the user

1. Open Adele - Topic (search "adele - topic", Go to channel) and see whether you get the same dead end.
2. On any big channel open Store or Podcasts; do you get the red "not supported" banner?
3. Open About on any channel and look for "Joined Joined".
4. Open the channel's Search tab: does "No results found" appear before you type?
5. Look at the tab bar: can you tell the selected tab without relying on the underline?
6. Reply with the finding numbers to fix. Suggested first batch: 1, 2, 3, 4, 5, 6, 9, 10.

## 7. Fixes applied (after your confirmation)

You confirmed: the Topic dead end (it showed on one Adele - Topic channel), Store not working, "Joined Joined", "No results found" before typing, and that the tabs cannot be told apart without the underline.

**Correction to finding 7:** the notification bell does exist on the channel page; it appears next to Subscribe once you are subscribed (`ChannelHeader`). Handle (@name) and video count are not in the data model (`ChannelPage`), so they need an extractor change and were not done.

| Finding | Change | Verified-by |
|---|---|---|
| 1 Topic dead end | Measured first: for Adele - Topic (UCwwW33K...) NewPipe returns 26 videos, 9 playlists, 56.6K subscribers, while the native tier can answer "Home, empty" as a success, so the fallback chain never moves on. Now: (a) when a native tab page is empty, the older per-kind call (`channelVideos/Shorts/Live/Playlists`, served by the fallback tiers) is asked as a second source; (b) if the channel has no Videos tab and its first tab is empty, a Videos and a Playlists tab are added and Videos opens once; (c) if everything is still empty the empty state offers "Open in browser" | measured on NewPipe (live probe, removed afterwards); the UI path compiled and ran; the dead-end itself did not reproduce again on the phone (this id loaded fully, twice), so (a)-(c) are **not seen working in the failing case** |
| 2 Store / Podcasts "not supported" | Store tab no longer offered (the About sheet lists the shop link). Any tab whose answer is "not supported" or "not available on fallback" shows "Vela can't show this tab yet." with Open in browser. Network, rate-limit, 403 and 5xx errors keep the red banner and Retry (rule in `isTabUnsupported`, unit-tested) | Store gone and NASA Podcasts state seen on the phone; unit test 4/4 |
| 3 "Joined Joined" | Prefix stripped once | seen on the phone |
| 4 Search tab | Before a search: "Search this channel's videos, playlists and posts."; after: "No results for "x"."; one search icon (the button), not two | hint and single icon seen; the "No results for" text not triggered |
| 5 Tab colours | `Tab` gets `primary` for the open tab and `onSurfaceVariant` for the rest | seen on the phone (dark theme) |
| 6, 8 Header height | Subscribe (and the bell) moved into the name block; tabs now start at 52-59% of the screen height (was 62-63%) | measured on the phone |
| 9 Channel name on every card | `VideoCard(showChannelName)`; hidden for the channel's own videos, kept for another channel's | compiled; seen on the Home tab of Adele - Topic |
| 10 Shorts tab | SHORT badge removed from the channel's Short cards, small thumbnail corners, 4 dp views chip | compiled; not seen after the change |
| 11 Shows/playlist card | Text centred beside the picture, 8 dp thumbnail | compiled; not seen after the change |
| 12 Page jump on a short tab | A filler item keeps the pinned tab row in place | seen on NASA Podcasts (no jump) |
| 13 Banner corners | 16 dp (`shapes.large`); the cookie avatar was kept | seen |
| 14 Shelf titles | `SectionHeader` allows two lines (affects every shelf header in the app) | compiled |

### Reliability notes
- The fallback chain and tier order are untouched. Everything is in the UI layer on top of it, and the second source is the same `Extractor` API the chain already routes (a failure there counts as "nothing").
- A next-page token from the second source is a NewPipe token; the chain already routes tokens to the tier that owns them (`TokenNotMine`).
- `./gradlew allTests` passes; new test `ChannelTabUnsupportedTest` (4 tests).

### Manual checklist
1. Open `https://www.youtube.com/channel/UCwwW33Kn3mr48ZLGQ4wdUdQ` (Adele - Topic) and any Topic channel you can find: is there content? If you ever see the empty tab again, it should now open Videos by itself or offer the browser.
2. NASA > Podcasts: calm message and the browser button; Kurzgesagt: no Store tab.
3. Subscribe to a channel and see Subscribe + bell in the header; check the layout on a long channel name.
4. Light theme: tab colours and the new header.

## 8. Second round (your four concerns)

| Concern | Cause (found by measuring) | Fix | Verified-by |
|---|---|---|---|
| Sort chips work but show nothing selected | The chip's selected state compared option objects with `==` (a data class holding label, selected flag and token). A reload returns new objects with new tokens, so the clicked option never equalled any of them | Compare by label (also the dropdown's label) | seen on Kurzgesagt Videos: tapping Popular selects it and reorders the list |
| Handle and video count | The native header's `metadataParts` carries `@adele-topic` and `71 videos` (seen in the real response) | `ChannelPage.handle` and `.videoCount`; parsed in `ChannelBrowse.parseHeader`; shown as "@handle" and "56.6K subscribers \u2022 71 videos". Absent for sources that do not give them (the fallback tiers), so nothing is shown then | seen on Adele - Topic and Kurzgesagt; 2 unit tests |
| Topic channels have no content | Reproduced with "Palash Muchhal - Topic" (NewPipe also returns 0 videos for it). The raw response has an "Albums & Singles" shelf of `lockupViewModel`s of type `LOCKUP_CONTENT_TYPE_ALBUM`; only `...PLAYLIST` was recognised, so every album was dropped and the tab looked empty. Album ids (`OLAK5uy...`) are ordinary playlist ids | Albums parse as playlists (in grids and in shelves); an unknown lockup type in a shelf is now reported to the diagnostics log instead of dropped silently. My earlier "second source" fallback stays, and its auto-open now goes to the Videos tab (it went to the last tab, Playlists, by mistake) | Palash now lists its 12 albums; an album opens with its track; 2 parse unit tests with fixtures built from the live shape |
| No animation when swiping tabs | The tab index changed instantly | The tab's rows slide in from the side they lie on and fade up on a spring; while a finger swipes, the content follows it (with extra resistance at the first and last tab) and settles back if the swipe is too short. Header and pinned tab row stay still. Tapping a tab uses the same slide | mid-drag frame seen (content shifted and faded, tab row fixed) and the tab switch on release; the slide-in itself is too fast to capture (screen recording is blocked on this phone) |

Also: header spacing tightened (16 dp to 8 dp between header parts) so the added handle line does not push the tabs further down.

Tests: `allTests`, `:innertube-auth:test`, `:extractor-newpipe:test`, `:extractor-ytdlp:test` pass; new: `TopicChannelParseTest` (4), `ChannelTabUnsupportedTest` (4).

Known limits: a Topic channel with nothing in its Home shelf and no Videos still shows the empty state with "Open in browser"; the handle is only shown where the native tier served the page; the animation timing (spring, 18% of the width) is by feel and not yet reviewed by you.

Manual checks: 1. Palash Muchhal - Topic (`.../channel/UCSsBloROYdDWle9dlWZpq-g`): albums listed, one opens. 2. Any channel > Videos: tap Popular/Oldest and see the chip selected. 3. Swipe left/right on a Videos list: content follows the finger, the tab changes on release, and a short swipe springs back. 4. Tap between tabs: the new content slides in. 5. Check the handle and video count on a few channels, including one where a line is missing.
