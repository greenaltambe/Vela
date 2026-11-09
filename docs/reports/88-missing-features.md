# 88: What a regular YouTube user might miss

## 1. Summary

Feature-gap analysis against the app as it is now. I listed what exists from the settings schema (`core/settings/Settings.kt`), the player menus, the screens folder and the manifest, then searched the code for each candidate gap; the phone was used for the channel and list screens only. Vela already covers a lot (downloads, SponsorBlock, DeArrow, RYD, background audio, PiP, captions, chapters, live chat, share/link intents, notifications bell, sleep timer). The gaps below are ranked by how often an everyday viewer would hit them.

## 2. Verification

| Check | Result | Verified-by |
|---|---|---|
| Items marked "absent" | Searched `shared/src` and `androidApp/src` (non-test) for the feature's names and the UI strings; zero matches unless noted | code search (a missing match proves nothing about the extractor, so each is "no UI"; none was tried live) |
| Items marked "present" | Found in settings keys, menus or manifest | code reading; channel/list/Shorts/watch screens also seen on the phone |
| Player, notifications, downloads behaviour end to end | Not re-tested in this task | not verified |
| Spec cross-check | `docs/SPEC.md` priorities P3 and P4 listed below where they are the gap | doc reading |

## 3. Files created/changed

- Created: this report. No source changes.

## 4. Findings

### A. Missing, and everyday users will notice

| # | Feature | State | Why it matters / note |
|---|---|---|---|
| 1 | **Add to queue / Play next / a visible queue** | Absent. A queue exists only as "Play all" from a playlist (`Navigator.queue`); no way to add one video, reorder, or see "Up next" | The most common "I'll watch this next" action. |
| 2 | **Seek-bar preview thumbnails** (storyboards) | Absent | Scrubbing a long video blind is hard. The extractor data exists on YouTube (storyboard spec) but is not used. |
| 3 | **Brightness and volume swipe in the player** | Absent. `PlayerGestures.kt` has only vertical swipe for fullscreen/sheet; double-tap seek and hold-for-speed exist. (Spec P3 lists it.) | Every video app has it. |
| 4 | **Zoom to fill / pinch zoom** | Absent; the Android player view is fixed to `resize_mode="fit"` | On tall phones a 16:9 video in fullscreen has bars; users pinch to fill. |
| 5 | **Write and like comments** | Absent (comments are read, sorted and replies opened). Needs the signed-in write path (`account.budget.writesPerHour` exists) | Heavy commenters will go back to YouTube. |
| 6 | **Channel page bell, @handle, video count** | Bell missing on the channel page (it exists in Search and Subscriptions); no handle or count in the header | See report 87, findings 7 and 8. |
| 7 | **Channel tabs that work for every channel** | Topic channels show an empty Home; Store and Podcasts show "not supported" | See report 87, findings 1 and 2. |
| 8 | **Pause watch history / Incognito switch** | Absent. History can be kept local (`history.localOnly`) but not paused | A one-tap "don't record this" is common. |
| 9 | **Mark as watched / remove from Continue watching** | Absent. "Hide watched" feed filter and "Remove from history" exist; no mark-as-watched on a card | Needed to clear feeds. |
| 10 | **Pure-black (AMOLED) theme** | Absent. Theme setting is System / Light / Dark only (`ThemeMode`) | Spec P3. A top request on OLED phones. |

### B. Missing, nice to have

| # | Feature | State | Note |
|---|---|---|---|
| 11 | Caption style (size, background) and auto-translate captions | Absent. Captions on/off and track choice exist | Accessibility. |
| 12 | Subscription groups or categories | Absent | Users with 100+ channels filter by topic. |
| 13 | Premiere and upcoming reminders ("Notify me") | Absent in UI | Models carry upcoming info; no reminder. |
| 14 | Chromecast / Cast to TV | Absent (spec P4, later) | Common on phones. |
| 15 | App language (UI translations) | Absent (no `values-xx` resources); only `content.region` for results | Non-English users get an English UI. |
| 16 | App shortcuts (long-press icon) and a home-screen widget | Absent | Convenience. |
| 17 | End screens and info cards | Absent | Rarely used by viewers; low. |
| 18 | "Your videos", purchases, YouTube Music/Premium-only features | Absent | Out of scope for a privacy-first client; mention only. |
| 19 | Hashtag pages | Unverified: hashtags may render as text in the description, but no tap target was found | Check manually. |

### C. Present (so you do not duplicate work)

Search with filters, suggestions and history; Home/Trending/Explore feeds; subscriptions feed with filters; Shorts with like/comments/mute/share; downloads queue, folder choice, rules, offline library; background play, PiP, sleep timer, speed with hold-for-2x, chapters, quality, captions, audio-track choice, SponsorBlock, DeArrow, RYD, live chat with Super Chats and memberships; playlists (create, save, sync with account), Watch later, history, notifications page; links from youtube.com and share-to-Vela intents; backup/restore; local profiles and account sign-in; keyboard shortcuts on desktop.

## 5. Known issues / unverified assumptions

- "Absent" means no UI or setting I could find; a feature may be partly present behind a screen I did not open (Settings pages were not walked on the phone).
- I did not run these against a signed-in account.
- Priorities (A/B) are my judgement of everyday use, not usage data.
- Spec lists AMOLED, brightness/volume gestures and Chromecast as P3/P4 (planned), so some of these are known and deferred, not oversights.

## 6. Manual test checklist for the user

1. Open a video and look for "Add to queue" / "Play next" in the card menu and the player (finding 1).
2. In fullscreen, swipe up/down on the left and right halves: does brightness or volume change (3)?
3. In fullscreen on a tall phone, pinch outward: does the picture fill the screen (4)?
4. Settings: look for AMOLED / pure black and for "pause history" (8, 10).
5. Tell me the order to build: suggested first five are 1, 3, 4, 10, 2.
