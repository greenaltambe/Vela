# 164 Playlist reader: both page layouts

## 1. Summary
Stage 1 of `docs/plans/youtube-playlists.md`. The account playlist reader now reads both of YouTube's layouts and their further pages: Liked videos went from 0 videos to 104. A page it cannot understand is a failed sync, no longer an empty playlist, and a list with anything unread never removes videos. Under "Unreleased" in the changelog.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:compileDebugKotlin` (before one more test was added; production code unchanged since, module tests rerun) | pass | run (the Android app compile is compile-only) |
| New `PlaylistPageTest`, 7 tests, made from the measured shapes | pass | run |
| Channel name rules removed | 2 of the new tests fail | run |
| Live, Watch later (old layout) | 28 videos, complete; channel name and id on 25 | run (live, read-only) |
| Live, Liked videos (new layout, two pages) | 104, complete; channel name and id on 104 (the old lockup parser named 90 of the first 100). Before: 0 | run (live, read-only) |
| Live, public playlist | 44 of 44, complete. Before: 0 | run (live, read-only) |
| Live, long public list stopped after two pages | 200, marked incomplete | run (live, read-only) |
| Live shapes: new layout's next page | inside the list; later pages under `appendContinuationItemsAction` | run (live, read-only) |
| Live shapes: the continuation beside the list on a public playlist | answers with nothing; no longer followed | run (live, read-only) |
| Live, history, subscriptions, playlist overview | the app still finds their next-page token | run (live, read-only) |
| Library > Sync with YouTube in the app, desktop or phone | NOT RUN | needs the owner |

## 3. Files created/changed
- Reader: new `innertube-auth/.../PlaylistPageParser.kt`; `InnertubeClient.kt`, `InnertubeParsers.kt`, `InnertubeAccountReads.kt`.
- Service: `shared/.../auth/PlaylistService.kt`.
- Tests: new `PlaylistPageTest.kt`, new `LivePlaylistReadTest.kt`, `InnertubeWritesTest.kt`.
- `CHANGELOG.md`, the plan, this report.

## 4. Decisions the spec didn't dictate
- Only the first block of the page that holds videos is the playlist; anything after it is treated as suggestions.
- Any entry that cannot be read marks the list incomplete: new videos still come in, nothing is removed.
- A page that states "No videos" or "0 videos" is an empty playlist whatever else it carries.
- Videos brought in by a sync now keep their channel id, so the channel can be opened and hide rules can match it. Videos already stored are not updated.
- The sync adds a playlist's new videos in one database write instead of one per video.
- Corrected the plan: it said an empty read could send local videos again; the existing guard prevents that when any video is synced.

## 5. Known issues / unverified assumptions
- How a deleted or private video looks in the new layout was not seen. If it is an unknown shape, removals made on YouTube are not applied to that playlist.
- An empty playlist was not observed live.
- A playlist whose videos are all unavailable, but which states a count, reports a failed sync.
- The three Watch later entries without a channel were not examined.
- The stated count is read from English wording (the client asks for English).
- Large lists: one request per 100 videos at 6 a minute, so 3,000 likes take about 5 minutes on the first sync. While a sync runs, other playlist changes, local ones too, wait for it (existing behaviour, now reachable through Liked videos). Not measured with a large list.
- Reading stops at 60 pages (about 6,000 videos) and marks the list incomplete.

## 6. Manual test checklist
1. Library > Sync with YouTube (desktop): Liked videos fills, newest like first, with channel names.
2. Compare its count with youtube.com.
3. Like a video on youtube.com, sync again: it appears at the top. Unlike it, sync: it goes.
4. Watch later still matches youtube.com.
5. During a sync, save a video to a playlist on this device: note whether it waits noticeably.
