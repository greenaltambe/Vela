# 121 Remove from Continue watching

## 1. Summary
Each Continue watching card now has a "..." menu with "Remove from Continue watching" (with Undo). The removal is stored apart from the history, so history, stats and "watched" marks are untouched. The video returns to the shelf when it is watched again later and left unfinished, until removed again.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| New `LibraryRepositoriesTest.removedFromContinueWatchingStaysOffUntilWatchedAgain` (remove, re-watch, re-remove, undo, delete from history, unknown video) | pass | running |
| `:desktopApp:compileKotlin`, `:androidApp:compileDebugKotlin` | pass | compile-only for Android |
| `./gradlew allTests` | first run: 7 failures, all the migration test helper (it did not drop the new table); fixed in `JvmDatabaseFactoryTest.kt`; `:shared:jvmTest` re-run passes. Other modules' tests not re-run after the fix | running |
| Menu, toast and shelf refresh in the real UI | untested | not run |

## 3. Files created/changed
- New: `shared/.../db/10.sqm` (migration 10 -> 11), `shared/.../db/ContinueDismissed.sq`
- Changed: `data/LibraryRepositories.kt` (`dismissContinue`, `restoreContinue`, filter in `continueWatching`, cleanup in `delete`/`deleteVideo`/`clear`), `ui/screens/Common.kt` (menu item), `ui/screens/HomeScreen.kt` (menu on the carousel, refresh)
- Tests: `LibraryRepositoriesTest.kt`, `JvmDatabaseFactoryTest.kt` (old-version helper drops the new table)

## 4. Decisions the spec didn't dictate
- Marker = the video's latest watch time at removal; shown again only if its latest watch is newer. Independent of the device clock and of how many sessions exist.
- Deleting the video from History, or clearing history, also forgets the removal.
- Stored per account, per device: not in backups and not synced (re-fetching/sync would not know it).
- Only the Home shelf has the action; "Recently watched" in Library does not (it is a history view).

## 5. Known issues / unverified assumptions
- Continuing to watch the same video in the session that was open at removal bumps its watch time, so it reappears. Intended ("started again").
- Another device's synced watch of the video newer than the removal also brings it back.
- The undo toast and menu use existing components; look not checked on screen.

## 6. Manual test checklist
1. Watch a video 20-30 s, leave it; it appears in Continue watching on Home.
2. Card "..." -> "Remove from Continue watching": card disappears, toast offers Undo; Undo brings it back.
3. Remove it, restart the app: still gone; it is still in History.
4. Open it from Search, watch 20 s, leave: it is in Continue watching again.
5. Remove it again: gone again. Delete it from History, watch again: shows up as new.
