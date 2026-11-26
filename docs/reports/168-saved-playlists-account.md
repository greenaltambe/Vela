# 168 Saved playlists and the YouTube account

## 1. Summary
Stage 5, the last of `docs/plans/youtube-playlists.md`. Save has the "Sync with my YouTube account" box; Remove can take the playlist out of your YouTube library too. Library sync tells your own playlists from other people's: yours sync both ways, others come in as saved, and ones brought in earlier as yours are converted. Under "Unreleased" in the changelog.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:compileDebugKotlin`, 2,149 tests, on the final app code | pass | run (the Android app compile is compile-only) |
| New `SavedPlaylistAccountTest`, 11 tests: save, refusal, held-back writes, box, removal both ways, import, conversion, merge, removal made on youtube.com | pass | run (stand-in account) |
| Save and remove use the like endpoints with the playlist as target; each counts as a write | pass | run (stand-in transport) |
| A signed-out playlist page is refused, not read as "not saved" | pass | run |
| Dialogs signed in, own channel's playlist has no Save | pass | run (desktop, stand-in data); images checked |
| Three safeguards removed (own-channel check, removal only on the page's word, no return after removal) | 3 tests fail; code restored and passes | run |
| Live: each list in your library (your two-video one, Liked, Watch later, the saved one) | yours: cannot be saved, owned by you; the other: can be saved, saved, not yours | run (live, read-only) |
| Live: save and remove sent by the app | NOT SENT in this task; the same requests were accepted in report 165 | needs the owner |
| On a phone | NOT RUN | needs the owner |

`LivePlaylistReadTest` gained the ownership check.

## 3. Files created/changed
- Account: `Accounts.kt`, `InnertubeClient.kt`, `InnertubeAccountActions.kt`, `InnertubeAccountReads.kt`.
- Logic and data: `PlaylistService.kt`, `LibraryRepositories.kt`, `Playlist.sq`, `Backup.kt`, `SyncDomains.kt`.
- Screens: `PlaylistUi.kt`, `ChannelPlaylistScreen.kt`, `PlaylistScreen.kt`, `LibraryScreens.kt`.
- Tests: new `SavedPlaylistAccountTest.kt`; `SavedPlaylistTest.kt`, `PlaylistPageUiTest.kt`, `PlaylistPageTest.kt`, `InnertubeWritesTest.kt`, `PlaylistCallsGuardTest.kt`, `LivePlaylistReadTest.kt`.
- `CHANGELOG.md`, the plan, this report.

## 4. Decisions the spec didn't dictate
- Unticking the box in Sync settings leaves your YouTube library as it is, as it does for your own playlists.
- A playlist kept on this device only stays so even when your account also has it; removing it then does not bring it back.
- A list counts as someone else's only when its page offers "save" and its channel is not yours. When the page cannot tell, it is treated as before; a new one that cannot be read waits for the next sync.
- When a converted list held videos you had added that YouTube never took, they are kept in "<name> (your additions)".
- A save held back by the hourly limit and then refused loses its "on YouTube" mark. A held-back removal is tried three times.
- Backup and device sync keep the "on YouTube" mark for Google accounts.

## 5. Known issues / unverified assumptions
- A playlist you stopped syncing and deleted (report 167, step 1) will not come back by itself at a sync; save it again from search.
- Playlists saved before this version stay "on this device"; tick the box in Sync settings to link them.
- If a held-back removal is refused three times, the playlist stays on youtube.com and does not return to Vela.
- Albums, podcasts or courses in the account's library were not seen.
- The Android background sync does not refresh saved copies.

## 6. Manual test checklist
1. Library > Sync with YouTube: your two-video playlist shows "Synced with YouTube"; "The Rust Lang Book", if it was there as synced, now shows "Saved, on YouTube".
2. On youtube.com save another public playlist, sync in Vela: it appears as saved.
3. Remove it on youtube.com, sync again: it leaves Vela.
4. In Vela, search a playlist, Save with the box ticked: it appears in your YouTube library.
5. Press the filled bookmark and Remove with "Also remove it…" ticked: it leaves your YouTube library.
6. Save one with the box unticked: it is not on youtube.com, and a sync keeps it.
7. Open your two-video playlist from your channel: no Save button; ⋮ has "Open in your library".
8. On the phone, install over the current build and check your playlists are all there.
