# 84: one playlist model, Local or Synced with YouTube (with the 5000-video overflow)

## 1. Summary
A playlist is now either LOCAL or SYNCED with one YouTube playlist, chosen by one box ("Sync with my YouTube account") in New playlist, the Save sheet's New playlist and a playlist's
"Sync settings". There is one Watch later (local until ticked; ticking merges both lists and adds everything in both places). Synced playlists are two-way, applied here first and written
through `PlaylistService` with rollback on a refused write and a database outbox for writes the hourly limit refused. YouTube's 5000-video limit is handled: what does not fit stays here as
"not on YouTube" and can be moved to another synced playlist or a new one. Everything runs offline-tested; nothing was run against the real account (no `VELA_TEST_COOKIES`).

Follow-up: the like button now also updates the local Liked videos list at once (`PlaylistService.noteRating`: a like goes to the top, a dislike or un-like removes it; local only, nothing sent; a no-op until Liked videos has been brought in by a sync). Test: `PlaylistServiceTest.theLikeButtonUpdatesTheLocalLikedListAtOnceAndWritesNothing`. The video page and Shorts call it after a non-failed rating. The Android UI part is compile-only.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew :desktopApp:test allTests` (all offline tests) | pass | verified by running |
| Migration 11 -> 12 with data (local list, local Watch later, `yt:WL`/`yt:PL`/`yt:LL` mirrors, second account), no loss, items pending until a sync confirms | pass (`JvmDatabaseFactoryTest`) | verified by running |
| `PlaylistServiceTest` (15): local never touches the network; link pushes in one request; 120 items in chunks 50/50/20 with the limit refusing mid-way and resuming after a "restart"; batch refused falls back to one by one; rollback on `Failed`; `Queued` persists; ADD+REMOVE cancel, RENAME keeps last; two accounts isolated; two-way sync incl. empty/cut-off/failed read guards; unlink keeps videos and never deletes on YouTube; one Watch later merge; overflow to a new / existing synced list; a "full" answer is not retried; Liked videos rejects every write; refused create makes nothing | pass | verified by running (fakes) |
| Guard test: no screen calls playlist writes or the account's playlist writes directly | pass | verified by running |
| UI: sync box absent for local profile, badge + delete wording for synced, overflow note and dialog | pass (`PlaylistSyncUiTest`) | verified by running |
| Batch `edit_playlist` request body (one request, one action per video, one write of budget) | pass (`InnertubeWritesTest`) | verified by running |
| Android compile (worker, screens) | pass | compile-only |
| Live: does batching work, how long a link takes, what YouTube answers when a playlist is full | NOT VERIFIED (`VELA_TEST_COOKIES` not set; `LiveWritesTest.addingSeveralVideosInOneRequestWorks` is written, never run) | not run |

## 3. Files created/changed
- Data: `11.sqm`, `Playlist.sq`, `PlaylistOp.sq` (outbox), `data/LibraryRepositories.kt` (LOCAL/SYNCED model, row states, `PlaylistOpRepository`).
- Logic: `auth/PlaylistService.kt` (new; replaces `AccountLibrarySync`, its test removed), `auth/Accounts.kt` (`addManyToPlaylist`, `playlistListing`), `AppServices.kt`.
- Account layer: `innertube-auth` `InnertubeClient` (batch add, paged listing up to 60 pages), `InnertubeAccountActions` (playlist writes no longer queue in memory), `InnertubeAccountReads`.
- UI: `ui/screens/PlaylistUi.kt` (box, badge, dialogs, overflow), `SaveSheet.kt`, `Common.kt`, `LibraryScreens.kt`, `PlaylistScreen.kt`; `backup/Backup.kt`, `sync/SyncDomains.kt`.
- Settings: `library.accountSync` (Off/Manual/On start/Every 6 h), `library.newPlaylistSyncDefault`; new "Playlists & YouTube" page. Desktop timer in `main.kt`, `PlaylistSyncWorker` on Android.
- Tests: `PlaylistServiceTest`, `PlaylistCallsGuardTest`, `PlaylistSyncUiTest`, `JvmDatabaseFactoryTest`, `BackupTest`, `InnertubeWritesTest`, `LiveWritesTest`; updated `AccountPlaylistsUiTest`, `LibrarySyncUiTest`.

## 4. Decisions the spec didn't dictate
- The limit is 5000 (from the owner); a "full" answer from YouTube is also recognised by wording ("full", "maximum", "too many", "limit" without "hourly") because its exact text is unmeasured.
- A synced-playlist write that YouTube refuses is rolled back (also when offline or signed out: the message says why). Only the hourly limit and cool-downs keep a write.
- "Sync account playlists" became "Sync with YouTube": it brings in account playlists with no local copy as synced ones, never auto-links Watch later, and does not bring back a playlist the viewer stopped syncing (remembered per account).
- Refused create-on-YouTube creates nothing locally. Delete of a synced playlist asks YouTube first; if only the limit refused it, it is hidden here and the delete waits in the outbox.
- Backup: LOCAL playlists as before; synced ones are not saved except videos that never reached YouTube (saved as "<name> (not yet on YouTube)").
- Sync with other devices (Sync feature) skips synced playlists (they come from YouTube); Watch later is skipped when synced.
- Order: YouTube's order wins; Move up/down/top are hidden for synced playlists. Liked videos is read-only (open and play).
- Playlist writes use their own outbox, so the old in-memory queue is no longer used for them (subscribe/like keep it).

## 5. Known issues / unverified assumptions
- Batch adding (many `ACTION_ADD_VIDEO` in one request) and YouTube's "full" reply are unverified; if batching is refused the service retries one by one, which under the default 10 writes/hour budget makes a large link slow (50 items about 5 hours). Run the live test and tell me the numbers.
- The sync listing reads up to 60 pages (about 6000 videos); a cut-off read never removes videos here.
- Failures: three in a row set the playlist to "Sync failed, tap to retry"; there is no automatic retry after that.
- Tokens such as `setVideoId` and reordering on YouTube were left out (out of scope).
- The Android UI and worker are compile-only.

## 6. Manual test checklist
1. Signed in with a Google account: Library > New playlist, tick "Sync with my YouTube account", create. Check youtube.com: a private playlist with that name exists.
2. Save any video to it from the card menu and from the video page's Save sheet: toast "Added to ...". Reload youtube.com: it is there. Remove it in the app: gone on youtube.com.
3. Add a video on youtube.com to that playlist, then Library > Sync with YouTube: it appears here. Remove it on youtube.com, sync: it disappears here.
4. Make a local playlist with 3 videos, open its menu > Sync settings, tick, Apply: the playlist appears on youtube.com with the 3 videos; untick: videos stay here, the YouTube one is untouched.
5. Watch later: still local after Sync with YouTube; tick Sync on it: both lists end up with the union.
6. Delete a synced playlist: the dialog says it also deletes on YouTube; the "stop syncing first" route keeps the YouTube copy.
7. Overflow needs 5000+ videos; instead check the wording only: on a synced playlist in the app nothing should say "did not fit" unless YouTube refused. If you can, report what YouTube says on a full playlist.
8. Settings > Playlists & YouTube: change the sync schedule; "New playlists sync with YouTube" makes the box start ticked.
9. On Android repeat 1, 2 and 4 (not run by the agent).
