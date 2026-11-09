# Task 84: one consistent playlist model, "Local" or "Synced with YouTube" (a checkbox, everywhere)

Read CLAUDE.md, docs/SPEC.md, docs/reports/19-account-features.md (write layer, conflict rules) and 83-watch-activity.md (for the sync-by-setting pattern: `AccountHistoryService`, `history.accountSync`, `AccountHistoryWorker`). Do not commit.
Report `docs/reports/84-playlist-sync.md` (fixed sections; Verification table separates VERIFIED-BY-RUNNING / compile-only / NOT VERIFIED). Terminal reply 5 lines max.
Every behaviour change gets a test that fails on the old code. Live checks use the throwaway account (`VELA_TEST_COOKIES`) and leave it as they found it; if the variable is not set, say so and mark those rows NOT VERIFIED.

## Why (what is inconsistent today, all verified in the code)

Playlists are one `Playlist` table, but three kinds hide inside `systemKey`: local (null), the built-in local Watch later (`watch_later`), and "mirrors" of account playlists (`yt:<id>`, incl. `yt:WL`, `yt:LL`). They behave differently in each screen:
1. Save sheet (`SaveSheet.kt`): a separate account-only "Watch later" row writes to YouTube and NOT to any local list, while the local "Watch later" and the mirror "Watch later (YouTube)" are other rows. Tapping a mirror row adds/removes LOCALLY only; YouTube never hears about it. `Common.kt` (~l.194) and `SaveManySheet` have the same split.
2. Playlist page (`PlaylistScreen.kt`): remove and move on a mirror are local only.
3. Library: rename/delete of a mirror DO go to the account, create has a checkbox ("Also create it on my YouTube account (private)", `library.onAccount`) that exists only at creation and can never be changed afterwards.
4. Sync (`AccountLibrarySync`) is add-only and manual: a video removed on YouTube stays here, a video removed here comes back, so a "mirror" is not a mirror.
5. Account writes queue in memory only (`InnertubeAccountActions.queueable`): a restart loses queued adds/removes.
6. Backup skips mirrors entirely ("they come back from the account"), which is wrong for anything added here that never reached the account.

## The model (the rules every screen must follow)

R1. Every playlist is in exactly one MODE: LOCAL or SYNCED. SYNCED means linked to one YouTube playlist (`remoteId`). Nothing else decides behaviour (no more `systemKey` prefix tricks for this).
R2. A checkbox "Sync with my YouTube account" is the ONLY control for the mode, shown identically in: New playlist (dialog and the Save sheet's "New playlist"), and a playlist's options ("Sync settings" / edit, same wording, same checkbox). Hidden for the LOCAL account and when signed out; shown disabled with the reason ("Sign in to sync") when an account exists but is NEEDS_RELOGIN. Default for NEW playlists: unchecked (Local), remembered per device as a setting `library.newPlaylistSyncDefault` (default off).
R3. LOCAL playlists never touch the network, never appear in the account audit, and are included in backups.
R4. SYNCED playlists are two-way and behave the same from every entry point (Save sheet, Playlist page, selection actions, Watch later buttons, Play queue "save"): the change is applied locally first, then written to YouTube through `AccountActions` (governor, budgets, audit). `Failed` rolls the local change back and shows the message; `Queued` keeps it and it is written later; offline/NEEDS_RELOGIN behaves like `Queued` (kept, pending). Remote changes come in through sync (R7). Order: YouTube's order is the truth, "Move up/down/top" are hidden for SYNCED lists until reorder is implemented (out of scope, note it).
R5. Linking a LOCAL playlist (checkbox on): create a PRIVATE YouTube playlist, then push the existing items (batched, resumable, with a visible "Syncing 40/120" state), then it is SYNCED. Unlinking (checkbox off): ask nothing destructive; the local items stay as a LOCAL playlist, the YouTube playlist is left untouched (never deleted by unlinking), `remoteId` is cleared. Linking to an EXISTING YouTube playlist is not offered here (the "Sync account playlists" flow already brings those in as SYNCED).
R6. Delete: LOCAL = delete here. SYNCED = the dialog states plainly "Deletes it here and on your YouTube account"; to keep the YouTube copy, unlink first. Rename: SYNCED renames on YouTube too. System lists: Watch later can be linked like any other (it links to `WL`); Liked videos (`LL`) is SYNCED, read-only here (no add/remove/rename/delete/unlink, opening and playing only), because the like button owns it.
R7. Sync is automatic by setting, same pattern as task 83: new setting `library.accountSync` (choice OFF / MANUAL / ON_START / EVERY_6H, GLOBAL, section LIBRARY, default MANUAL) next to the existing "Sync account playlists" tile, which stays as the manual trigger. It also brings in account playlists that have no local copy yet as SYNCED lists.
R8. One Watch later. After this task there is a single "Watch later" list. It is LOCAL until the owner ticks the checkbox on it; the separate "Watch later (YouTube)" mirror and the account-only row in the Save sheet disappear (migration below). Auto-download rules keyed on `AutoDownloadRule.WATCH_LATER` must keep working unchanged.
R9. The same badge everywhere (Library card, Playlist page header, Save sheet row, selection sheets): a small cloud icon for SYNCED (with "Pending" / "Syncing" / "Sync failed, tap to retry" states), nothing for LOCAL. Supporting text: "On this device" vs "Synced with YouTube".

## Data model

Migration 9 -> 10 (`9.sqm`, follow the repo's migration pattern and update the migration helper test):
- `Playlist`: add `remoteId TEXT` (null = LOCAL), `syncState TEXT NOT NULL DEFAULT 'LOCAL'` (LOCAL, SYNCED, LINKING, ERROR), `lastSyncedAt INTEGER`. Index on (accountId, remoteId).
- `PlaylistItem`: add `rowState TEXT NOT NULL DEFAULT 'SYNCED'` (SYNCED, PENDING_ADD, PENDING_REMOVE; a PENDING_REMOVE row is a tombstone hidden from the UI), `setVideoId TEXT` (YouTube's id of the entry, returned by `playlistVideos`, kept for later reorder; do not rely on it for removal, `ACTION_REMOVE_VIDEO_BY_VIDEO_ID` works today).
- New table `PlaylistOp` (persistent outbox, replaces the in-memory queue for playlist writes): id, accountId, playlistId, kind (ADD, REMOVE, RENAME, DELETE, CREATE), videoId?, payload?, createdAt, attempts, lastError. Coalesce: an ADD followed by REMOVE of the same video cancels both; repeated RENAME keeps the last.
- Data migration of existing rows: `systemKey 'yt:<id>'` becomes `remoteId = <id>`, `syncState = SYNCED`, systemKey cleared (keep `watch_later` for the local one); mirror rows `rowState = SYNCED`. IMPORTANT, loss-proof first pull: items that exist in a migrated mirror but are NOT in the account list at the first pull are NOT deleted; they become PENDING_ADD (they were saved here on purpose). Cover with a test. The old "Watch later (YouTube)" mirror (`yt:WL`) is merged INTO the single local Watch later: items are unioned, the list becomes SYNCED to `WL` only if the account mirror existed (the owner opted into account sync by using it), otherwise stays LOCAL.
- `AccountManager.remove` must delete the new rows and ops of that account (extend the existing leak/cleanup tests). "Clear cache" never touches playlists or the outbox. Backup: LOCAL playlists as today; SYNCED playlists are NOT in the file but their PENDING items are not lost because the outbox is data, not cache (state this in the Backup screen text).

## Sync algorithm (per SYNCED playlist, no snapshot table needed)

Let R = remote video ids (full list incl. continuation), L = local rows.
1. Flush the outbox for this playlist first (ADD/REMOVE/RENAME in order; stop on `GovernorDenied` and retry after `until`; 3 hard failures set `syncState = ERROR` with the message, never loop).
2. For each remote v not in L (and no tombstone for v): insert as SYNCED at the remote position.
3. For each local row with `rowState = SYNCED` and v not in R: the account removed it, remove it locally. (Never for PENDING_ADD rows; never when the remote read failed or returned a suspiciously empty list while L is non-empty: treat empty-vs-non-empty as a failed read unless the account says the playlist is empty and count matches.)
4. PENDING_ADD rows not in R stay pending and are pushed in step 1 next time; PENDING_REMOVE tombstones for v not in R are dropped.
5. Order = remote order for SYNCED rows; PENDING_ADD rows go at the end.
6. A failed or signed-out read changes nothing (keep the existing rule). `AccountLibrarySync` is rewritten around this; keep its public entry point and its result type so the Library tile keeps working, and keep/adjust `AccountLibrarySyncTest` (update expectations that encoded "add-only" and say so in the report).

Batching (UNVERIFIED, measure): `browse/edit_playlist` takes an `actions` array, so many `ACTION_ADD_VIDEO` can go in one request. Add `AccountActions.addManyToPlaylist(playlistId, videoIds)` (chunks of 50, one WRITE per chunk). If a live run shows batching is rejected, fall back to one request per item through the outbox with the existing write budget and say in the report how long a 100-item link takes. Never exceed the governor budgets; progress is shown in the UI, and the work survives an app restart because it is the outbox.

## UI work (all screens use one helper so they cannot drift)

- A single `PlaylistActions` service in `:shared` that the UI calls: `create(name, synced)`, `setSynced(id, on)`, `add(id, videos)`, `remove(id, videoIds)`, `rename`, `delete`, each returning an `ActionResult` and applying R4. SaveSheet, SaveManySheet, Common.kt's menu, PlaylistScreen, LibraryScreens and the Watch later buttons go through it; no screen calls `services.playlists.add/remove` or `services.actions` for playlists directly any more (add a test that greps the UI package for those calls, so it stays true).
- SaveSheet: remove the account-only Watch later row; one row per playlist, badge per R9, check state = membership, tap toggles through `PlaylistActions`; per-row pending/failed state; "New playlist" has the R2 checkbox.
- Library: card badge, options menu gets "Sync with YouTube" (checkbox sheet/dialog per R2/R5), Delete dialog text per R6, `editable` rules per R6 (Liked videos read-only).
- PlaylistScreen: header badge + state line ("Synced 5 min ago" / "3 changes waiting"), hide Move actions when SYNCED, Retry action on ERROR.
- Settings: `library.accountSync`, `library.newPlaylistSyncDefault` in the schema with copy, pages and search keywords like every other setting (the settings-orphan tests must stay green).
- Android: a worker like `AccountHistoryWorker` for the schedule and for draining the outbox when the network or the budget window returns; desktop: timer in `main.kt`.

## Tests

Offline (fakes for `AccountActions`/`AccountReads`): each rule R1-R9 at least once; two accounts never see each other's playlists/ops; remove/add/rename/delete on SYNCED call the account once and roll back on `Failed`; `Queued` persists across a simulated restart (new repository over the same DB); ADD+REMOVE coalescing; sync algorithm steps 1-6 incl. the empty-read guard and the migration "no loss" case; link of a 120-item list in chunks with a mid-way `GovernorDenied` resuming; unlink keeps items and never calls delete; Liked videos rejects every write; migration 9 -> 10 from a DB that has a local list, local Watch later and `yt:WL`/`yt:LL`/`yt:PL...` mirrors; Backup round trip keeps LOCAL lists and loses nothing pending; Compose UI tests for the checkbox in the three places, the badge states and the delete text.
Live (liveTest, throwaway account, restores state): create synced, add two videos, remove one, rename, delete; link a local list of 3, then unlink; confirm each against `playlistVideos`/`playlists`.

## Out of scope

Reordering playlists on YouTube, playlist visibility other than PRIVATE, collaborative playlists, linking to an existing YouTube playlist by URL, syncing Liked videos writes (like button owns it), and the watch-history reporting from task 83 phase 4.

## Hand back

In the report, section 5 starts with: did batch edit_playlist work (chunk size, status), how long the link of N items took under the default write budget, and any place where the old add-only behaviour was relied on and changed. Section 6 gives the owner steps: create a synced list on the phone, add a video from Save to, see it on youtube.com; remove it on youtube.com, sync, see it vanish; untick sync, confirm the YouTube copy stays.
