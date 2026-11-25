# 166 Playlist page: header and whole-list actions

## 1. Summary
Stage 3 of `docs/plans/youtube-playlists.md`. A YouTube playlist now opens with a header (cover, title, owner, counts, description) and Play all, Shuffle, Download all, Share, plus Add all to a playlist, Open in browser and Refresh; library playlists use the same header. It stands beside the list on a wide window. No Save button yet and no account writes. Under "Unreleased" in the changelog.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:compileDebugKotlin` on the final code, 2,119 tests | pass | run (the Android app compile is compile-only) |
| New `RemotePlaylistTest`, 12 tests: paging, failed and expired pages, private list through the account, Play all queueing | pass | run (stand-in sources) |
| New `PlaylistPageUiTest`, 10 tests: wide and narrow layout, each action, partial read asks first, mix, library header, Share only for your own synced list | pass | run (desktop, stand-in data) |
| Queue check removed; ask before using a partial list removed | one test fails each; code restored, both classes pass again | run |
| Rendered images: wide, narrow, library, light and dark, with a test picture as cover | checked | run, still images |
| The page against real YouTube in the app | NOT RUN (its sources were checked live in report 165) | needs the owner |
| On a phone | NOT RUN | needs the owner |

## 3. Files created/changed
- Logic: new `shared/.../library/RemotePlaylist.kt`, new `ui/screens/PlaylistStore.kt`; `ui/nav/Navigator.kt`, `ui/AppShell.kt`.
- Screens: new `ui/components/PlaylistHeader.kt`, new `ui/look/Effects.kt`; `ChannelPlaylistScreen.kt`, `PlaylistScreen.kt`, `SaveSheet.kt`, `LibraryScaffold.kt`, `ComponentIds.kt`, icon files (Shuffle).
- Tests: new `RemotePlaylistTest.kt`, new `PlaylistPageUiTest.kt`.
- `CHANGELOG.md`, the plan, this report.

## 4. Decisions the spec didn't dictate
- The page is read without the account; the account is asked only when that fails (private lists). So the save state is not shown until stage 5.
- The last four playlists opened are kept for 30 minutes, with their scroll position.
- Play all keeps queueing while the queue is still that one; playing another video from a card does not stop it, clearing the queue or another Play all does.
- When part of a list cannot be read, the app says how much was read and offers "Try again", "Use these" or "Cancel".
- Mixes offer no Shuffle, Download all or Add all.
- Share appears on library playlists only when synced, not on Watch later or Liked videos.
- The sheet for saving several videos gained "New playlist" (also seen from History).
- The side-by-side layout starts at 840 dp of page width.

## 5. Known issues / unverified assumptions
- Saved playlists from others still sync in as "Synced with YouTube" (report 165); stage 5.
- A private playlist of yours was never available to try; it is read only after the two anonymous sources have failed, so it may open slowly.
- On Android older than 12 the cover behind the header is not blurred, only faint. Not seen on a phone.
- Very long lists: Shuffle and Download all need one request per 100 videos. Download all and Add all with thousands of videos were not tried.
- Adding a long list to a synced playlist uses account writes and may wait for the hourly limit (existing behaviour).
- Mixes were not opened against real YouTube.
- Two earlier sessions ended during this task: the system ran out of memory during builds and closed the terminal. The first interrupted test check left two lines changed in the source; they were restored and verified before the final test run.

## 6. Manual test checklist
1. Search "Rust book", open the playlist (desktop): header on the left with picture, owner, "44 videos" and views; list on the right.
2. Click the owner: the channel opens; go back: same place in the list.
3. Play all: the first video plays and the queue holds the rest.
4. Open a playlist with more than 100 videos, press Shuffle: a short "Reading the playlist" dialog, then a mixed queue of all of them.
5. Share: the pasted link opens the playlist in a browser.
6. ⋮ > Add all to a playlist > New playlist, not synced: it appears in Library with every video.
7. Download all on a short playlist: the download sheet shows the right number.
8. Narrow the window, or use the phone: the header sits above the list and every button fits.
9. Open a Library playlist: same header; "Share" only on a synced one.
10. Turn on a feed rule such as "hide watched": the page says how many videos are hidden.
