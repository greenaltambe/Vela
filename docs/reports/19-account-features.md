# Task 19: account features

## 1. Summary

Done and proven live on the throwaway account: the write layer (subscribe/unsubscribe, like/dislike/remove, Watch Later and playlist add/remove, playlist create/rename/delete) as a thin governor-owned client, each call a `WRITE` through the account's governor and audit log; plus reads of the account's playlists. In the app: optimistic subscribe, like and "Watch later (YouTube)" on the video page with rollback and a visible error; Home "Recommended" with a fallback to Trending; a Library "Sync account playlists" row that mirrors account playlists locally (add-only); downloads are now per account; a top-bar account switcher; leak tests. **Not done:** watch-history reporting, profile (channel) selection, merged history view, playlist reorder, playlist create/rename/delete in the UI, per-account SponsorBlock/DeArrow user data. The Android UI is compile-only.

## 2. Verification

| Check | Result | Verified by |
|---|---|---|
| Write calls: right endpoints and bodies (subscribe, unsubscribe, like/dislike/removelike, `edit_playlist` add/remove/rename, `playlist/create` private, `playlist/delete`), all audited as WRITE; a refusal or `STATUS_FAILED` is an error, not a silent success; the governor's write budget stops the 4th write before it is sent | pass (4 tests) | running (fake transport) |
| Live, throwaway account (`VELA_TEST_COOKIES`), each a round trip that restores the state it found: subscribe/unsubscribe (original=true → flipped=false → restored=true), like/unlike (NONE → LIKE → NONE), Watch Later add/remove (0 → 1 → 0), playlist create → add → rename → read → delete, playlists list ("Watch later", "Liked videos") | all pass; audit counts printed: 2 WRITE + 3 BROWSE per round trip, 4 WRITE + 6 BROWSE for the playlist one | running (real network) |
| App layer UI (Compose): subscribe is optimistic and calls the account; like calls `rate`; a refused subscribe rolls the local change back and shows the reason; signed out = local only, no account call | pass (3 tests) | running (desktop UI) |
| Home: signed-in shows the account feed ("Recommended"); a failed or empty feed falls back to Trending with a note; signed out or the Trending setting never asks the account | pass (3 UI tests) | running |
| Home feed parser | passes on a **hand-built fixture** of the documented shapes (`videoRenderer`, `richItemRenderer`, video lockups); **the throwaway account's real Home feed was empty** (a "feed nudge" and no videos), so no real recommended feed was ever parsed | running; real-feed shape NOT verified |
| Subscriptions listing: "All subscriptions" page, not the suggestions page; stops on repeated pages; keeps a partial list on a 5xx; live: empty account → 0 (the old page gave 120), subscribed → listed (size 1) | pass (unit + live) | running (fake transport, real network) |
| Default budget (6 browse/min) live: subscriptions + playlists + Watch Later + Home in one run, no refusals (5 ok calls, 7 s on the empty account; a multi-page list waits ~1 minute per window) | pass; a long list with many pages was NOT measured | running (real network) |
| Governor patience: waits through the window with the injected sleeper, no audited refusals; too little patience, no patience or a kill switch refuse at once | pass | running |
| Library sync: adds only, no duplicates, account rename renames the mirror, a failed read changes nothing, a viewer-removed item returns on the next sync, mirrors are per account | pass (3 unit + 2 UI tests) | running |
| Per-account downloads: B sees none of A's downloads (list, find, completed bytes, pending work), the same video can exist in both accounts, switching back finds A's rows | pass | running (JVM) |
| Switcher: the top-bar menu lists the accounts (active ticked), choosing another switches, and the other account's subscription is invisible and returns on switching back | pass | running (desktop UI) |
| Multi-account guard: A exhausts its write budget, switching to B and back neither refills A nor shares B's budget | pass | running |
| Android (UI, Library sync, switcher, Home) | compiles; nothing run on the phone | compile-only |
| `./gradlew allTests test :androidApp:assembleDebug :androidApp:assembleRelease --continue` | green (no failures printed). The first full run had one timeout in `LayoutInvariantsTest.lightLayoutHoldsAtEverySize` ("test body did not complete in 1 m") while other Gradle tasks ran at the same time; alone it takes 18 s, `:desktopApp:test` alone passes, and the repeat full run was clean, so I treat it as load-related, not proven | running |

## 3. Files created/changed

- **Writes/reads client**: `innertube-auth/.../InnertubeClient.kt` (writes, `playlists`, `playlistVideos`, `rating`, `isSubscribed`), `InnertubeParsers.kt` (playlists, playlist videos, feed videos, `apiError`, `rating`, `subscribed`), `InnertubeAccountActions.kt`, `InnertubeAccountReads.kt`.
- **Shared**: `auth/Accounts.kt` (`AccountActions`, `AccountReads`, `ActionResult`, `VideoRating`, `AccountPlaylist`), `auth/AccountLibrarySync.kt`, `data/LibraryRepositories.kt` (`PlaylistRepository.mirror`, `isMirror`), `Playlist.sq` (`renameMirror`), `DownloadRepository` (account id as a provider), `DownloadManager.reload`, `Settings.homeSource`, `AppServices` (`actions`, `reads`).
- **UI**: `VideoScreen` (optimistic subscribe/like, Watch later), `HomeScreen`, `LibraryScreens` (sync row, mirror labels), `AppShell` (account entries in the top-bar menu, downloads reload on switch).
- **Wiring**: desktop `main.kt`, `DesktopDownloads.kt`; Android `PlaybackRuntime.kt`, `MainActivity.kt`, `AndroidDownloads.kt`.
- **Tests**: `InnertubeWritesTest`, `LiveWritesTest` (liveTest), `AccountActionsUiTest`, `HomeSourceUiTest`, `AccountSwitcherUiTest`, `LibrarySyncUiTest`, `AccountLibrarySyncTest`, `DownloadsPerAccountTest`, `MultiAccountGuardTest`, `UiHarness` (actions/reads).
- Not committed. Two temporary live probe tests were deleted after use.

## 4. Decisions the spec didn't dictate

- **How state is verified live** (and a bug this found): the throwaway account has NO real subscriptions. `FEsubscriptions`, which the app used since task 13 to list subscriptions, shows ~120 SUGGESTED channels for such an account (that is where "122, 122, 123" came from, varying between reads), so the sync would have imported suggestions as subscriptions. It now reads `FEchannels` ("All subscriptions"): 0 for the empty account, and 1 (the right channel) while subscribed, measured both ways. That page keeps offering a continuation after the list ends (it repeated the same channel on 12 pages) and answers HTTP 500 for the empty account's continuation, so the listing stops after two pages with nothing new and keeps what it has on a 5xx. State is verified with the channel page's `subscriptionStateEntity` (not the `subscribed` flags in the button templates, which both appear) and the watch page's `likeStatus` are used. The Liked-videos list (`VLLL`) read 0 videos while the like was in effect, so likes are verified by `likeStatus`, not by that list.
- **Conflict rules for synced playlists** (also in the code comment): sync only adds; a video removed on the account stays in the local mirror; a video the viewer deletes from a mirror returns on the next sync; mirrors are never deleted and cannot be renamed or deleted locally; nothing local is pushed by sync (account writes happen directly: Save > "Watch later (YouTube)"). Mirrors are keyed by `systemKey = "yt:<id>"`, so no schema migration; Watch Later and Liked are named "(YouTube)" to stay apart from the local Watch later.
- **Home**: default source Recommended; it is used only when signed in, only the first page (no continuation), and falls back to Trending on failure or an empty feed, with a note.
- **Downloads per account**: the repository follows the active account; a transfer of the previous account runs to its end, new scheduling follows the active account's rows.
- **Switcher** lives in the existing top-bar overflow menu (shown when there is more than one account); the Accounts screen from task 13 is the account card (avatar, masked e-mail, sign out, remove); I did not add a separate card in Settings.
- **Test-account state**: while probing I subscribed the throwaway account to `UC_x5XG1OV2P6uZZ5FSM9Ttw`; it is now unsubscribed again, which matches the account's apparent original state (no subscriptions). The live round trip restores whatever it finds.
- **Governor patience (found by you on a device)**: sync and listing calls hit "browse budget used up" because the default is 6 browse calls a minute (session checks count too) and a listing needs several. Listings (subscriptions, playlists, playlist videos) now WAIT for the window to reopen (up to 70 s per call) instead of failing, Home waits up to 15 s and then falls back to Trending. Interactive or non-listing calls, a kill switch and an open breaker are still refused at once, and only a final refusal is audited. The default budget is unchanged (a protection for the account); it can be raised in Settings > Account.

## 5. Known issues / unverified assumptions

- **Suggested channels may already be in your local Subscriptions.** Any subscription sync run before this fix on an account with few or no subscriptions copied YouTube's suggestions (~120 channels) into that account's local table, and sync never deletes. They stay until you remove them (Subscriptions screen); nothing was removed automatically.
- **Empty Home feed**: your explanation (a new account with no watch history) fits what the response contained (a feed-nudge block and an empty grid), but I cannot prove it; a real recommended feed has still not been parsed.

- **Not done (and why)**: **watch-history reporting** (needs the player response's playback tracking URLs, which no tier hands to the app; NewPipe does not expose them, and the Linux yt-dlp route cannot report correctly; it stays off); **profile/channel selection** (not evaluated: needs the account menu's channel-switch data, which was not captured); **merged history view**; **playlist reorder**; **create/rename/delete playlist from the UI** (the client and `AccountActions` support it and the live test proves it; only the screens are missing); **per-account SponsorBlock userId / DeArrow user data** (still global settings); the signed-in feed "uses our RSS engine" already (per-account `FeedService`) and subscribe/unsubscribe now update both sides from the video page, but not from the channel page.
- The like button's selected state starts unselected: the viewer's existing rating is not read when the page opens, so a video already liked shows as not liked until toggled (the next toggle would send LIKE again, which is idempotent).
- Compose screenshots were not generated or reviewed for the new UI; Android was not run.
- Real recommended-feed shape unverified (see above).
- The write calls use the web client's endpoints as of 2026-10-28; they are expected to change like any scraped API, and a failure shows as a clear error and a rollback.
- Test counts of the final run were not captured (quiet output): only the absence of failures. The `LayoutInvariantsTest` timeout under load may come back on a busy machine.

## 6. Manual test checklist for the user

1. Sign in on Linux with a Google account (Accounts screen), open a video: Subscribe, Like and Save > "Watch later (YouTube)" each succeed; check youtube.com in a browser. Turn the network off and press Subscribe: the button goes back and the red message says why.
2. Home: signed in shows "Recommended" (or "Trending" with a note if the account has no recommendations); Settings > Home shows the source setting (Trending / Recommended).
3. Library > "Sync account playlists": your Watch later, Liked videos and playlists appear as "YouTube account · N videos".
4. Add a second account; the top-bar menu (three dots) shows both; switch, and confirm subscriptions, history, playlists and downloads are the other account's; switch back and nothing was lost.
5. Settings > Diagnostics / Account activity: the account calls appear in the audit log as writes with `ok`; the budget (10 writes/hour by default) stops the 11th write with a clear message.
6. Phone: the same checks (compile-only so far), especially the top-bar menu on a narrow screen.
7. Decide which of the not-done items to schedule (history reporting needs a plan for where to get the tracking URLs).
