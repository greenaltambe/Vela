# 167 Saved playlists, on this device

## 1. Summary
Stage 4 of `docs/plans/youtube-playlists.md`. A YouTube playlist has a Save button; a saved one is in Library as the owner's: it can be played, downloaded, shared and copied, not edited. It is re-read when opened or on library sync, and the copy stays if the original disappears. Backup and device sync carry it. Nothing is sent to your account yet. Under "Unreleased" in the changelog.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:compileDebugKotlin` on the final code, 2,134 tests | pass | run (the Android app compile is compile-only) |
| New `SavedPlaylistTest`, 10 tests: save, refusals with no account write, whole / partial / empty / gone / offline reads, stale reading, library sync, backup, device sync | pass | run (stand-in sources) |
| `PlaylistPageUiTest`, 4 new: save and remove, no button where it does not apply, the saved page in Library, gone original | pass | run (desktop, stand-in data) |
| Database 12 to 13: existing playlists unchanged, saved ones can be added | pass | run |
| Three safeguards removed (sync import skip, sync refusal on a saved list, backup filter) | 3 tests fail; code restored and passes | run |
| What the fallback source says about a playlist that does not exist | "unreadable page" for one id, "does not exist" for another | run (live, read-only) |
| Rendered image of a saved playlist | checked | run, still image |
| Save and refresh in the app against real YouTube | NOT RUN | needs the owner |
| On a phone, including the database upgrade | NOT RUN | needs the owner |

## 3. Files created/changed
- Data: new `12.sqm`; `Playlist.sq`, `LibraryRepositories.kt`.
- Logic: `PlaylistService.kt`, `RemotePlaylist.kt`, `FallbackChain.kt`, `AppServices.kt`.
- Backup and sync: `Backup.kt`, `SyncDomains.kt`, `SyncService.kt`.
- Screens: `ChannelPlaylistScreen.kt`, `PlaylistScreen.kt`, `LibraryScreens.kt`, `PlaylistUi.kt`, `PlaylistHeader.kt`.
- Tests: new `SavedPlaylistTest.kt`; `PlaylistPageUiTest.kt`, `JvmDatabaseFactoryTest.kt`.
- `CHANGELOG.md`, the plan, this report.

## 4. Decisions the spec didn't dictate
- Saving asks in a dialog like New playlist (the plan said sheet); the account box joins it in stage 5.
- Opening a saved playlist re-reads it only when the copy is more than 10 minutes old.
- A read that gets only part adds new videos and removes none.
- "No longer on YouTube" is set only when the playlist is reported missing or unreadable, not on connection trouble, and clears at the next good read.
- Library sync no longer imports an account playlist that is saved here as one of your own.
- Backup and device sync carry which playlist it is, not its videos; it follows the "Playlists" sync choice.

## 5. Known issues / unverified assumptions
- A playlist of someone else's already synced as your own (likely "The Rust Lang Book") shows no Save button until stage 5, or until you do checklist 1.
- Signed in, a public playlist of your own that Vela does not have yet can be saved, and is then read-only. Stage 5.
- A restored or device-synced saved playlist has no videos until it is opened or the library synced.
- Saved playlists are refreshed by library sync only when signed in; the Android background sync does not refresh them.
- If YouTube changed so that every read of a playlist failed as unreadable, saved ones would be labelled gone until a read succeeds. Nothing is deleted.
- Installing an older build over this one after the database upgrade was not tried.

## 6. Manual test checklist
1. If "The Rust Lang Book" shows as "Synced with YouTube": its ⋮ > Sync settings > untick > Apply, then ⋮ > Delete. Your YouTube library is not changed.
2. Search it, press Save, confirm: the button fills, and Library shows it as "Saved".
3. Open it from Library: owner, videos, no "Remove" or "Move" in a video's menu, no "Sync settings".
4. Library > Sync with YouTube: it stays one entry, still "Saved".
5. Offline, open it from Library: the videos are listed.
6. ⋮ in the header > Add all to a playlist > New playlist: you get an editable copy.
7. Press the filled bookmark, Remove: it leaves Library, and youtube.com still has it saved.
8. On the phone, install over the current build: your playlists are all still there.
