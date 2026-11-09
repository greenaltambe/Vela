# Report 83: watch activity with the YouTube account

## 1. Summary

Phases 1-3 are built: feed items carry the account's watched percentage, "Hide watched" uses a progress threshold (default 90 %) over this device's history, the item's own percentage and the synced account history, and the account's history can be read into a per-account table by setting. Phase 0 (tracking-URL spike) was NOT run: `VELA_TEST_COOKIES` (throwaway account) is not set in this environment, so Phase 4 (reporting playback to the account) was not built, as the task prescribes. Nothing Android-runtime was run; the protocol shapes for the progress bar are hand-built, not recorded.

## 2. Verification

| Check | Result | Verified by |
| --- | --- | --- |
| Parser: classic `percentDurationWatched`, lockup `startPercent`, null without progress, clamp | passed (`WatchedPercentParseTest`) | running (offline, hand-built JSON) |
| Hide watched: item `watchedPercent=100` hidden with setting on / shown off, quick path, threshold 95 keeps 90, 1 = played at all, local + account progress | passed (`FeedStepsTest`) | running |
| Migration 8 -> 9 (table `AccountWatched`), older versions still migrate | passed (`JvmDatabaseFactoryTest`, helper updated) | running |
| Account sync: paging, incremental stop, max entries, account isolation, deleted with account, failure keeps data, OFF / local account make no request, schedule by setting and time, ON_START once, `localOnly` does not stop reading | passed (`AccountHistoryServiceTest`) | running |
| Whole offline suite: `allTests`, `:innertube-auth:test`, `:desktopApp:test` (settings pages/orphans, `HistoryMergeUiTest` with unchanged expectations) | BUILD SUCCESSFUL | running |
| Android: `AccountHistoryWorker`, `MainActivity` start trigger (`:androidApp:compileDebugKotlin`) | compiles | compile-only |
| Real `FEwhat_to_watch` / `FEhistory` progress paths | NOT VERIFIED | not verified (no real signed-in feed seen) |
| Phase 0 spike (A/B/C tracking URL sources) | NOT VERIFIED | not run (no cookies) |
| Phase 4 reporter, `CallKind.REPORT`, final ping | NOT BUILT | n/a |

## 3. Files created/changed

Created
- `shared/.../sqldelight/.../AccountWatched.sq`, `8.sqm` (table + migration)
- `shared/.../auth/AccountHistoryService.kt` (sync, status, schedule rules)
- `androidApp/.../playback/AccountHistoryWorker.kt`
- Tests: `innertube-auth/.../WatchedPercentParseTest.kt`, `shared/src/jvmTest/.../AccountHistoryServiceTest.kt`

Changed
- `extractor/Models.kt` (`VideoSummary.watchedPercent`), `InnertubeParsers.kt` (both shapes), `InnertubeAccountReads.kt` (`historyPage`), `auth/Accounts.kt` (`AccountReads.historyPage`, `HistoryPage`, account removal clears `AccountWatched`)
- `core/settings`: `Settings.kt` (`feed.watchedThresholdPercent`, `history.accountSync`, `AccountHistorySync`), `SettingCopy.kt`, `SettingsPages.kt` (pages, section, privacy summary)
- `feed/FeedSteps.kt` (`WatchedLookup` with threshold, `HideWatched`), `FeedStepsTest.kt`
- `data/LibraryRepositories.kt` (`HistoryLibrary.watchedPercents`, `AccountWatchedRepository`), `WatchHistory.sq` (`watchedProgress`)
- `AppServices.kt` (`accountWatched`, `accountHistory`, `watchedLookup()`), `Common.kt`, `SubscriptionsScreen.kt`, `Lists.kt` (bar from `watchedPercent`), `HistoryScreen.kt`
- `desktopApp/.../main.kt` (timer), `androidApp/.../MainActivity.kt` (schedule + start trigger)
- `JvmDatabaseFactoryTest.kt` (migration helper), `docs/SPEC.md` section 11

## 4. Decisions the spec didn't dictate

- Local progress = max(furthest position, watched time) / duration per video; a video with no recorded duration counts as 100 % (today's "played at all"). With the default threshold 90 a video played only a little is therefore no longer hidden (was hidden before); set 1 to get the old behaviour.
- A video in the synced account history with unknown percent counts as watched (as the task says).
- FEhistory lists no time per entry, so `seenAt` = time of the read minus the list index (keeps order). Because of that, merge de-duplication is by video id only (a video watched on this device is listed once, in the local part); a time window would compare a real time with an invented one.
- Incremental stop: a whole page is stored, then paging stops if that page contained an already stored video (so recent re-watches refresh their percent); cap 200 entries, 20 pages.
- "Only when I ask" (MANUAL) is the default; the History menu entry is "Update from YouTube account" and is hidden when the setting is Off. The result line and the failure line "Could not read your YouTube history right now." are inline cards in the list, not toasts. `HistoryMergeUiTest` needed no change.
- ON_START runs once per process. Desktop: timer every 30 min checks "due"; Android: 6-hour WorkManager job plus the start check.
- `AccountWatched` is not in backups and not touched by "clear cache".

## 5. Known issues / unverified assumptions

**Findings (task hand-back block):**
- Source A/B/C for tracking URLs: NOT DETERMINED (spike not run, no throwaway cookies).
- Real parameter set for playback/watchtime pings: NOT DETERMINED.
- Whether the account history shows a Vela-played video, and whether a final ping changes the percentage: NOT DETERMINED.
- Repeated-recommendations problem: Vela still does not tell YouTube what is watched, so Home "Recommended" will keep repeating until Phase 4 exists. What is fixed is hiding/marking what the account already watched.

Other:
- Progress paths are UNVERIFIED against a real response: lockup `thumbnailOverlayProgressBarViewModel.startPercent` (searched anywhere below the lockup) and classic `thumbnailOverlayResumePlaybackRenderer.percentDurationWatched`. No scrubbed real fixture was added. If YouTube uses another name, `watchedPercent` stays null and nothing breaks.
- Account history items may not carry a progress figure at all; then every account-history entry counts as watched (threshold-independent).
- `historyPage` follows continuation tokens as the playlist reads do; an account whose FEhistory returns a token after the list ended is bounded by the 20-page cap.
- Android parts are compile-only.

## 6. Manual test checklist

1. Settings > Subscriptions & feed filters: "Counts as watched at" slider (default 90) sits under "Hide watched videos". Turn "Hide watched videos" on.
2. Signed in, Settings > Privacy: "Read my YouTube history" = "Only when I ask". Open History > menu > "Update from YouTube account": a "From your YouTube account" section appears with a bar on partly watched entries; the line "N on your account, M also watched here." shows.
3. Switch the setting to "When the app starts", restart, check the History section updates with no tap. Set it to Off: the menu entry disappears and no account request is made (Diagnostics > account audit shows no new BROWSE).
4. Watch a video on the official site for a few minutes, update, open Home with hide watched on at threshold 1: it should be gone; at 90 it stays until you reach 90 %.
5. Turn the network off and run the update: the card "Could not read your YouTube history right now." appears and the stored list stays.
6. Please tell me whether Home cards from your account show progress bars (this confirms the lockup path); if not, send a scrubbed `FEwhat_to_watch` overlay snippet.
7. To enable the spike and Phase 4: export `VELA_TEST_COOKIES` for the throwaway account and ask for Phase 0 again.
