# Task 47: native channel browse (server-side sort, all tabs, Posts, Courses, About) with fallback to NewPipe

Read CLAUDE.md first. Do not commit. Report `docs/reports/47-channel-tabs-native-browse.md` (VERIFIED / NOT VERIFIED table), terminal reply 5 lines max.
Inspiration (read-only, GPL-3.0, we do not copy code; if any structure is reused keep attribution in THIRD_PARTY_NOTICES): https://github.com/Flow-Tube/Flow ,
`app/src/main/java/io/github/aedev/flow/innertube/pages/channel/*` and `.../pages/renderer/{FeedItemParsers,CommunityPostsPage}.kt`.
Principle: YouTube's own web response tells the client which tabs, sorts and filters exist and hands over a continuation token for each. We replay those tokens; we do not hardcode sorts.

## A. Probe first (anonymous, throwaway visitor only, paced, record fixtures with identifiers scrubbed)
1. POST https://www.youtube.com/youtubei/v1/browse with the WEB client (clientVersion taken from NewPipe's YoutubeParsingHelper.getClientVersion() with a pinned fallback,
   visitorData from our existing source, utcOffsetMinutes = device offset) for a channel id: landing response => header, `twoColumnBrowseResultsRenderer.tabs[]`
   (tabRenderer / expandableTabRenderer; each has `endpoint.browseEndpoint.params` and a URL whose last segment identifies the tab: featured, videos, shorts, streams,
   shows, podcasts, playlists, courses, community/posts, releases, store, search). Identify tabs by URL segment, NEVER by localised title.
2. Tab request = browse(browseId=channelId, params=<tab params from the response>). Known params (fallback only for deep links): videos EgZ2aWRlb3PyBgQKAjoA, shorts EgZzaG9ydHPyBgUKA5oBAA==,
   streams EgdzdHJlYW1z8gYECgJ6AA==, playlists EglwbGF5bGlzdHPyBgoKCEIGCgIQaCIA, courses Egdjb3Vyc2Vz8gYFCgPCAQA=, posts EgVwb3N0c_IGBAoCSgA=, search EgZzZWFyY2jyBgQKAloA.
   Verify each against the live response and record which still work today (they are protobuf; take the params from the response when present).
3. Sort and filters: in the tab response find `chipBarViewModel.chips[].chipViewModel` (text, selected, `tapCommand...continuationCommand.token`); the sort control may be a chip that
   opens a sheet (`showSheetCommand -> sheetViewModel -> listViewModel.listItems[].listItemViewModel` with title, isSelected, `rendererContext...continuationCommand.token`);
   Playlists uses `sortFilterSubMenuRenderer.subMenuItems[]` whose option carries browse `params` (a fresh browse, not a continuation). Write findings: which tabs have which control today
   (expected: Videos, Shorts, Live = Latest/Popular/Oldest; Playlists = date added/last updated; plus visibility chips such as "Members only").
4. Paging: items live in one of `richGridRenderer.contents`, `reloadContinuationItemsCommand.continuationItems` (sort switch), `appendContinuationItemsAction.continuationItems` (next page),
   `richGridContinuation.contents`, `gridRenderer.items`, `sectionListRenderer` shelves; the next-page token is the `continuationItemRenderer` INSIDE THAT LIST, not the first one found in the
   document (the header's description/attribution have their own). A sort chip token and a page token are the same request type.
5. Item renderers to support: `lockupViewModel` (videos, playlists, courses, podcasts), `richItemRenderer` wrapping `videoRenderer`/`gridVideoRenderer`, `shortsLockupViewModel`, `reelItemRenderer`,
   `playlistRenderer`/`gridPlaylistRenderer`, `channelRenderer`, `backstagePostThreadRenderer` / `postRenderer` (Posts tab: text, `backstageAttachment` image/poll/video/playlist, vote count,
   published time, reply browse params), course = playlist row whose thumbnail overlay `thumbnailOverlayBottomPanelRenderer.icon.iconType == "COURSE"`. Unknown renderers are skipped, counted and
   logged (type name only) so a YouTube change is visible in Diagnostics instead of an empty screen.
6. About: the landing response only has a short description; the full panel (links, country, joined date, total views, video count) is a separate continuation token found in the header.

## B. Implementation (inside the existing extractor architecture)
1. New `ChannelBrowse` in the innertube module (or the alt-client module): `landing(channelId)`, `tab(channelId, tabDescriptor)`, `continuation(token)`, `about(token)`; models `ChannelTab(kind, title, params, selected)`,
   `ChannelFilterGroup(isDropdown, options[label, selected, continuation|params])`, `ChannelTabPage(items|shelves, filters, nextToken)`. Tolerant parsers (walk by key, never fixed paths), unit-tested on the recorded fixtures.
2. Wire into the Extractor interface as optional capabilities (`channelTabs`, `channelTab(sort/filter)`), served by this tier first and by NewPipe's channel tab extractors as the FALLBACK
   (no sort chips then: the UI hides the sort control and says nothing scary). Go through PacedExtractor, TimeoutExtractor, FaultInjector and the health canary (add canary: a known channel's
   Videos tab sorted Oldest returns a first item older than the first item of Latest, and Posts/Courses parse on known channels). Errors map into ExtractorError as today.
3. Cache per (channel, tab, sort) with the usual TTL; keep the sort selection per channel tab in memory during the visit; remember the last sort per tab type optionally in settings.
   Tokens expire: on a 400/invalid token refetch the tab once, then show the retry row.
4. Never send account cookies for this (anonymous browse); the account tier is not involved. No PO token is needed for browse; if the probe shows one is required today, STOP and report.

## C. UI
1. Channel page: tab strip built from the response (only tabs the channel really has, in its order), localised labels as sent. Sort control = dropdown (Latest / Popular / Oldest as offered), extra chips for other
   filters; changing sort replaces the list and scrolls to top with the same shimmer + undo-free behaviour; paging continues with the new token. Honest empty states per tab.
2. Posts tab: post cards (text with link/hashtag handling, image attachment, poll results read-only, attached video/playlist card, likes, relative time) and a post detail sheet with comments if the probe shows
   `FEpost_detail` browse works anonymously; otherwise text-only with "open on YouTube".
3. Courses and Shows/Podcasts/Releases tabs as playlist grids (course badge), opening the playlist page; Store tab only shows a link-out row.
4. In-channel search (`search` tab params + query) replacing any local filter if the probe shows it works.
5. About sheet with links, country, join date, totals; desktop and phone layouts, keyboard focus (task 26 style), tokens/motion from the design system.

## D. Verification
Unit (fixtures) + live probes on 3 channels (large, small, one with Posts and Courses); on phone and desktop screenshots; sort Oldest on a channel with >500 videos returns its truly oldest video first;
paging 5 pages; fallback test (force the tier to fail: NewPipe tab still shows, no sort control); full suite after the last change; leak audit unaffected; NOT VERIFIED list explicit.
