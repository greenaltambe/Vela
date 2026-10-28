# Task 18: account features (what task 13 did not finish)

Follow CLAUDE.md (report docs/reports/18-account-features.md; terminal reply max 5 lines + anything the owner must do). Read docs/reports/13-accounts.md and 14-reliability.md. Everything that carries credentials goes through the AccountGovernor and the audit log; budgets and breaker rules apply to every new call. Default for activity reporting stays OFF. Live tests use the throwaway account via VELA_TEST_COOKIES and must be harmless round trips that restore the original state. Commit nothing.

## A. Writes (innertube-auth, thin client, governor-owned)
- Subscribe/unsubscribe, like/dislike/remove rating, add/remove video to a playlist and Watch Later, create/rename/delete playlist, reorder if feasible. Optimistic UI update with rollback and a clear error; local-only accounts keep working locally.
- Watch-history reporting through the playback tracking URLs (videostats playback + watchtime) from the player response, ONLY when `account.reportActivity` is on, `history.localOnly` is off and the playback went through an allowed path; throttled and batched; never for offline/downloaded playback unless its own setting is on; never for the Linux yt-dlp route unless you can report correctly from that stream info (otherwise skip with a Diagnostics note).
- Profile selection after sign-in (channel switcher), as Grayjay does: evaluate and implement if the data is available so history and recommendations apply to the chosen profile.

## B. Reads and sync
- Library shows the account's playlists, Watch Later, Liked, next to local ones; playlist contents with continuation; incremental sync that never deletes locally without an explicit action; conflict rules written down in the report.
- Merged history view (local + account), with source labels, de-duplicated by video and time window.
- Home source setting: Trending / Recommended (FEwhat_to_watch, only when signed in, fallback to Trending on failure, signed-out = Trending), default Recommended when signed in.
- Subscriptions: signed-in feed uses OUR RSS engine for the account's channels; unsubscribing and subscribing update the local table and the account.

## C. Per-account data and switching
- Fix everything still bound to the LOCAL account: DownloadRepository/Downloads, DeArrow and SponsorBlock user data, search history, UI state; downloads of other accounts must not leak between accounts but files remain valid.
- Settings > Account card per docs/SPEC.md section 7 (avatar, name, masked email, switch, add, sign out, safety status) and an account switcher in the top bar/profile menu; switching resubscribes every observer; tests for leaks between accounts.
- Multi-account guard: the governor budgets are per account; switching quickly cannot bypass them.

## D. Verification
Offline tests with recorded, scrubbed fixtures; live round trips on the throwaway account (subscribe/unsubscribe, like/unlike, Watch Later add/remove, playlist create/delete, a reported watch of one video that is removed afterwards if the API allows) with the audit log showing every call; leak tests for per-account data; Compose UI tests for the switcher and Library; screenshots reviewed. Manual checklist for phone and Linux.
