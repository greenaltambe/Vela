# Task 21: gap filling (last task before customisation)

Read CLAUDE.md and docs/SPEC.md first. Do not commit. Write the report to `docs/reports/21-gap-filling.md` with the usual
verification table (item / check / result / verified by: running on phone, running on desktop, unit only, not run).
Terminal reply: 5 lines max, only instructions for the owner.
Rule for this task: every item ends as VERIFIED (say how) or NOT VERIFIED (say why). No "should work".
Phone: OPPO CPH2707, Android 16, adb as in task 17 Stage 0. If the phone is not connected, do all desktop/unit work first,
then list in the report the exact phone steps left, and say so in the terminal reply.

## A. Re-verify on the phone (everything that was compile-only or changed after the last phone run)
1. Self-test `--es run all` with the final build; no crash in logcat.
2. Live stream (find a live one via Trending/search): LIVE badge, "-m:ss behind live", Go live button, DVR seek back 60 s,
   seek past the end lands on the live edge (not stuck in BUFFERING). Also an ENDED live stream (VOD of a past stream): plays, seeks.
3. Live chat panel on a live stream: nothing requested until opened, messages appear, closing stops polling.
4. Automatic downloads: Rules screen opens, add a rule, "Check now" works, the WorkManager job is registered
   (`adb shell dumpsys jobscheduler` or WorkManager inspector output).
5. Backup/restore: create via the system document picker, restore via the picker, preview shown, restore twice = no change.
6. Process death: play video, set speed + quality cap, `adb shell am kill`, reopen: both restored for that video only.
7. Captcha flow: you cannot get a real captcha. Launch the captcha activity from a debug-only entry in SelfTestActivity
   with a harmless test page, confirm it opens, shows the page, returns "Done", and that the cookie store accepts and
   expires an entry. Say plainly that the real unblock is unproven.
8. Screenshots (phone size) of every screen touched here, saved in docs/reports/21-screens/.

## B. Screen-states audit, remaining screens
Extend the 5x3 audit (dead network, captive-portal HTML, hang) to: Shorts, Channels list, Comments, Related, Playlist page,
Channel tabs (videos/playlists/about), and the local-only screens Library, History, Downloads, Playlists, Watch later
(these need EMPTY, populated, and large (2000 items) states instead of network failures; check scroll stays smooth and
nothing loads all rows at once). Fix what fails. Screenshots at phone and desktop sizes.

## C. Linux
1. MPRIS in the real GNOME session: run `dbus-monitor --session "sender='org.mpris.MediaPlayer2.vela'"` while playing,
   pausing and seeking, and report whether PropertiesChanged signals are emitted with the right fields. Ask the owner in
   the terminal reply to press the media keys and look at the GNOME widget (you cannot see it); put the result row as
   "owner to confirm".
2. Close the window / SIGTERM while a download is mid-flight: the partial file is kept, resumes on next start, no orphan
   yt-dlp/ffmpeg/mpv. A component throwing mid-download (fault injection): the download is marked failed with a reason and the app stays up.
3. Ended live stream in libmpv: plays and seeks.

## D. Live chat replay
Measure the replay endpoint on an ended stream (real probe, record request/response shape). If it works, sync replay
messages to the playhead (show messages with offset <= position, handle seeking backwards/forwards, no re-fetch storms).
If it does not work anonymously, say so and make the panel say "chat replay unavailable" quietly.

## E. Profiles (brand accounts), carefully
1. Storage + UI: an account's profile list (from `account/accounts_list`), a selector in the account card, the chosen
   profile stored per account, and `X-Goog-PageId` sent ONLY when a non-default profile is chosen.
2. The page-id rule is unverified with one profile. Do not guess silently: gate the selector behind a clearly labelled
   "Experimental" switch, default OFF, and make the first request after selecting a profile a read-only check
   (e.g. the account's own name via accounts_list) that, if it returns an error or the wrong identity, reverts the selection and
   reports it. All calls still go through AccountGovernor. Never write (like/subscribe) as an unverified profile.
3. Unit tests for the header rule, revert-on-failure, and governor use. Leak audit still green.

## F. Watch-history reporting: make the product honest
Decision stays: not implemented (needs own /player tracking URLs, tied to the parked Android age-restricted work).
Check what the `account.reportActivity` setting does today. If it does nothing, remove it from the UI (or show it disabled with
the text "Not available: history stays on this device") so no setting lies. Confirm local history always works and that the
merged-history view labels account entries as before. Record the decision in docs/SPEC.md status notes.

## G. Smaller verifications
1. SponsorBlock segment submission: verify the request shape against the official API docs and with a fake server test.
   Do NOT submit anything to the real SponsorBlock server.
2. Home "Recommended": keep the fallback; document the one thing unknown (real feed shape) in the report. No new probing
   of the owner's real account; throwaway account only.
3. Dependencies freshness check (NewPipeExtractor, yt-dlp, remote-config) still run clean; Diagnostics shows all tiers.

## H. Closing
1. Full suite: `./gradlew allTests test :androidApp:assembleDebug :androidApp:assembleRelease --continue` green, and run it
   AFTER the last code change (report 20 did not).
2. Credential-leak audit green; `git status` shows no cookies/secret files untracked-but-unignored.
3. Update the "Core complete?" table from report 20 with new statuses. Anything still not VERIFIED goes in a short
   "Known gaps accepted" list for the owner to sign off.
4. Manual checklist for the owner: only what you truly could not do.
