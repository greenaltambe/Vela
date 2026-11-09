# 101: Reddit research (what was possible)

## 1. Summary
**Reddit could not be read.** Web search refuses reddit.com and old.reddit.com ("domains not accessible to our user agent"), and page fetching refuses both hosts (policy block, tried 6 pages across r/youtube, r/NewPipe, r/Android, r/revancedapp, r/LibreTube). I did not try to get around that block. So there are no Reddit quotes, scores or thread titles in this report, and nothing in it should be taken as "what Reddit says".
What I did instead: searched Hacker News mirrors, Lemmy, forums and app directories, where the same audience talks about the same apps. These are anecdotes, mostly 2023-2024, and I label them as such. They add little to report 94 and mostly agree with it.

## 2. Verification
| Check | Result | Verified-by |
|---|---|---|
| Reddit web search | refused (HTTP 400, domain blocked) | tried |
| Reddit page fetch (6 pages) | refused (policy / domain block) | tried |
| Lemmy, forum and HN-mirror threads | read as search summaries only, not whole threads | search results |
| Claims below | each is one person's comment unless marked | not verified beyond the source |

## 3. Files created/changed
This report only.

## 4. What the substitute sources add (compared with report 94)
New or reinforced points:
- **Plugins / fast fixes.** One commenter prefers the app whose bypass updates arrive as fast as YouTube changes; another notes forks with SponsorBlock lag behind the main app's updates. For Vela this is the remote-config and yt-dlp update path: already designed for it, worth keeping prominent in the Health screen ([Lemmy](https://lemm.ee/post/11518139), [scribe.disroot.org](https://scribe.disroot.org/post/51904)).
- **Reliability is the first complaint about every client**, ahead of features: LibreTube "not working" for some, NewPipe "stagnant", forks "delaying updates" ([F-Droid forum](https://forum.f-droid.org/t/newpipe-clipious-or-libretube-which-is-better-and-why/26244)).
- **Live streams:** "live tabs should show previous streams" and "can't view live recordings for some channels" (Vela's channel Live tab and ended-stream replay exist; worth testing with real recordings).
- **Tablet layout:** LibreTube "is NOT optimised for tablets" ([GrapheneOS forum](https://discuss.grapheneos.org/d/14084-yattee-iphone-alternative-for-gos-that-works-in-mid-2024/31)); Vela has medium and expanded layouts, but I have not run it on a tablet.
- **Picture-in-picture quality:** one switcher left NewPipe/Tubular because PiP was clunky; Yattee is praised for automatic PiP. Vela has PiP; whether it enters automatically when leaving the app is worth checking.
- **Posting comments** again (a user wants to comment with their own account), and **podcast-style following of creators / Jellyfin integration** (new, niche).
- **Android TV:** SmartTube is the TV favourite; it is not a phone app, casting to it needs setup ([GitHub SmartTubeNext](https://github.com/Resshi/SmartTubeNext)). Reinforces "cast to TV" and "Android TV" as gaps.
- **Trust:** an unofficial SmartTube build had a security notice (the developer's machine was infected); people warn to install only from official links. For Vela: signed releases and a clear source of downloads matter (already in the release docs).

## 5. Known issues / unverified assumptions
- No Reddit data at all. If you want it, paste thread titles/comments here, export them, or allow reddit.com for the fetch tool, and I will redo this properly.
- Search summaries, not full threads; several sources are 2023-2024 and the apps have changed.

## 6. What I need from you
1. Tell me whether to build **cast to TV** next (the one planned gap left), or something from the list above (automatic PiP check, comment posting, hide members-only).
2. If you can, paste a few Reddit threads you care about.
