# 163 YouTube playlist page and saving playlists (planning)

## 1. Summary
Planned in `docs/plans/youtube-playlists.md`: a playlist page with header and actions, a third kind of playlist ("Saved") with the same account box as new playlists, and five stages. Found on the way: Vela's account playlist reader returns nothing for Liked videos and for other people's playlists. No app code changed.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| Current playlist pages and service (`ChannelPlaylistScreen`, `PlaylistScreen`, `PlaylistService`) | the YouTube page is a bare list; library playlists have Play all, Shuffle, Download; two kinds only | code read |
| Live playlist page, public example, signed in and out | header has title, owner, count, views, description; buttons Play, Save, Share, More | run (live, read-only) |
| Save button, signed in | `like/like` and `like/removelike` with the playlist id; signed out it asks to sign in | read from the response; **never sent** |
| App's reader on that public playlist (44 videos) | 0 videos, "complete" | run (live) |
| App's reader on Liked videos (100 on page one, more on page two) | 0 videos, "complete" | run (live) |
| App's reader on Watch later | 28 of 28 (old layout) | run (live) |
| New next-page token | found under `continuationItemViewModel`, nested; page two answers when it is followed | run (live) |
| Account playlist list (`FEplaylist_aggregation`) | only Watch later and Liked videos on this account | run (live) |

## 3. Files created/changed
`docs/plans/youtube-playlists.md`, this report. Temporary probes written, run and deleted; raw responses deleted.

## 4. Decisions the spec didn't dictate (proposed)
- Owner's decision: saved playlists behave as on YouTube (follow the original, read-only). "Make a copy" dropped; "Add all to a playlist" gives an editable version.
- Kept beyond YouTube: device-only saves, and the cached list stays (marked unavailable) when the original disappears.
- The save box reuses the words and default of the new-playlist box.
- Saved lists use their own column, so existing write paths cannot touch someone else's playlist.
- A save removed on youtube.com is removed in Vela only on the playlist page's own "not saved" state, never on absence from a list.

## 5. Known issues / unverified assumptions
- Not verified: whether the account's playlist list includes playlists saved from others, and how your own playlist's page differs (this account has neither). If saved ones are listed, today's sync would import them as editable synced playlists.
- The save and remove requests have not been sent.
- How YouTube's library shows a saved playlist that was deleted or made private was not checked.
- The save button's "saved" state was only seen as not saved; the saved case is unverified (checklist 1).
- Whether your own playlists use the old or new layout is unknown.
- NewPipe's header fields (views in particular) were not checked.
- A very long list takes about one request per hundred videos before Shuffle or Download all can start.

## 6. Manual test checklist (before building)
1. On youtube.com, save the example playlist to your library, and create one playlist of your own with two videos. Then say so: a read-only check will show how both appear.
2. In Vela today, run Library > Sync with YouTube and open Liked videos: note whether it is empty or stale (it should be, per the finding).
3. Decide: may the agent test save and remove once on your account, or will you test those yourself after stage 5.
