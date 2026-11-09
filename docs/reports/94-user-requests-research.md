# 94: What people ask for and complain about (YouTube and other clients)

## 1. Summary

I searched the web and read the issue trackers of NewPipe, SmartTube and LibreTube, then checked each finding against what Vela has now. Evidence is uneven: the NewPipe list is real tracker data, the rest is news and forum posts of mixed quality, and several sites refused to be read (section 5). Most requests are already covered by Vela. The gaps that show up in more than one place are: cast to TV, subscription groups/feed filters, a blocklist that works everywhere (and can be shared), repeat/loop, caption styling, volume boost, "take a break" limits, posting comments, and syncing between devices. Android TV is the biggest audience gap I can infer but not prove.

## 2. Verification

| Check | Result | Verified-by |
|---|---|---|
| NewPipe top open feature requests (first 20 of 816, tracker order for "most reactions") | Read from GitHub | reading the live tracker (via a fetch agent) |
| The numbers beside each NewPipe issue | Could not tell reactions from comments; they are not a ranking I can quote | not verified |
| SmartTube and LibreTube issue lists (first page of each) | Read; counts unreliable, sort order did not look like reactions | partly verified |
| FreeTube list | Not read (label filter returned nothing) | not verified |
| Android Authority poll and bug articles, TechRadar, ReVanced's patch list | Fetch refused (HTTP 403 / 451 / domain blocked); I used only the search-result summaries | not verified |
| Reddit | No direct threads found; only search summaries | not verified |
| "Vela has X" statements | Reports 88 to 93 (code search and phone) | code reading and earlier runs |

## 3. Files created/changed
Created this report only. No code changed.

## 4. Findings

### A. What people want, with where it came from, and Vela's state

| Theme | Evidence | Vela today |
|---|---|---|
| Control Shorts (hide, limit) | YouTube added a Shorts off/limit control in 2026 per several articles; long-running complaint ([tbreak](https://tbreak.com/turn-off-youtube-shorts-mobile/), [Tubefilter](https://www.tubefilter.com/2026/04/16/youtube-take-a-break-shorts-feed-time-limit/amp/)) | Have: hide, in feed, or own tab. **Missing:** a daily limit / "take a break" |
| SponsorBlock, DeArrow, dislikes, ad-free | Listed as the reasons people use alternatives ([search summaries](https://www.androidauthority.com/youtube-app-alternatives-poll-results-3631972)); NewPipe request #10288 asks for DeArrow | Have all three |
| Blacklist videos, channels and keywords | NewPipe #1815 (second on its list); LibreTube #8534 asks to block "slop" channels and share blocklists | Have keyword rules in feed filters and hide-channel on Home. **Missing:** one blocklist applied in Search, channel pages and Subscriptions, and shareable |
| Sort and filter | NewPipe #7729 (sort channel videos), #2251 (search filters), #8069 (subscription feed filter) | Have channel sort, search filters, feed filters. **Missing:** subscription groups |
| Cast / stream to TV | NewPipe #5060 | **Missing** (spec P4) |
| Sleep timer | NewPipe #2830 | Have |
| Sync between devices, cloud backup, self-hosted sync, automatic backups | NewPipe #5325, #1163, #8039; LibreTube #5592 | Have manual backup/restore and account sync. **Missing:** device-to-device sync, scheduled auto-backup |
| Bulk downloads (queue or playlist) | NewPipe #5482 | Have "Download all" on playlists and a queue |
| Play downloads as a cache / offline | NewPipe #10542 | Have offline library |
| Search inside one channel | NewPipe #5441 | Have |
| Pinch zoom, always-visible fullscreen button | NewPipe #1406, #4478 | Have (task 91, compact fullscreen button) |
| Material 3 design | NewPipe #7595 | Have |
| Remember player state: shuffle, repeat | NewPipe #1795 (open since 2018) | **Missing:** repeat/loop one, shuffle (queue exists) |
| Automate the background player (Tasker) | NewPipe #1214 | **Missing** |
| Post comments | NewPipe FAQ: "several requests" | **Missing** (read-only) |
| Watch Later and Add-to-queue buttons vanish; playlist drag-reorder broken; 5,000-item limit | News on YouTube's own site and app ([PiunikaWeb](https://piunikaweb.com/2026/03/27/youtube-hides-watch-later-add-to-queue-buttons-desktop/)) | Have Watch Later, queue, drag-reorder (task 89) |
| Forced/auto-launching Shorts or fullscreen; unwanted autoplay on launch | Reddit threads quoted in [Unilad Tech](https://www.uniladtech.com/social-media/youtube/youtube-users-furious-with-latest-update-410688-20250401) | Vela never does this (autoplay is off by default) |
| Block automatic translations / AI-dubbed audio | SmartTube #4689 and #6280 | Have audio-track choice and prefers original audio; **not checked:** translated titles |
| Hide members-only content | SmartTube #5806 | **Missing** |
| Volume boost for quiet videos | FreeTube user comment (search summary) | **Missing** (volume stops at 100%) |
| Caption size/style, caption accessibility | YouTube has them; accessibility reviews list player gaps ([accessible.org](https://accessible.org/youtube-video-player-accessibility/)); LibreTube #4685 screen-reader accessibility | **Missing** caption style; screen-reader support is partial (queue has actions, rest unchecked) |
| Reliability: "Sign in to confirm you're not a bot", infinite loading, subscriptions showing 3 videos, network switch pauses playback | SmartTube #4287, #6030, #4743; LibreTube #6451 | Addressed by the fallback chain and account fallback, but not proven against these cases |
| F-Droid build | SmartTube #5213 | Distribution decision for you (GitHub releases today) |
| Proxy settings | NewPipe #992 | Not checked |
| Android TV (leanback) apps are the most popular third-party YouTube clients | Inference from SmartTube's tracker size (784 open issues); no source states it | **Missing** |

### B. Suggested order (my judgement)
1. Repeat/loop and shuffle (small, asked since 2018).
2. One blocklist (channels + keywords) across Search, channels and Subscriptions, with export/import so it can be shared.
3. Subscription groups and a Subscriptions filter.
4. Caption style and size.
5. Volume boost beyond 100% (with a clear warning).
6. Cast to TV.
7. "Take a break" / daily Shorts limit.
8. Hide members-only; posting comments (needs the write path); scheduled auto-backup; cross-device sync.
9. Android TV as a separate project.

## 5. Known issues / unverified assumptions
- Search results were mostly news sites, blogs and aggregator pages. Some (marlvel.ai, honeypot/"counterfeit" ReVanced pages) look like auto-generated or unofficial and were ignored.
- Blocked or unread: Android Authority (403), TechRadar (blocked), revanced.app (blocked), GitHub's ReVanced code (451), FreeTube's tracker (label filter empty), Reddit (no direct threads). So the ReVanced picture is secondhand: ad removal, SponsorBlock, background play, PiP, AMOLED, layout tweaks.
- The numbers beside tracker issues are not usable as rankings.
- Date: sources run from 2023 to 2026; the YouTube Shorts-limit and Watch Later items are single reports.

## 6. Manual test checklist / what I need from you
1. Tell me which of the list in 4B to build next (my order is a suggestion).
2. If you know specific pain points from Reddit or your own use, say so: the research here is weakest on firsthand voices.
