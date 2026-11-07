# 77 Chapters, Save and Share sheets, subtitles with downloads, small fixes

## 1. Summary
Chapters panel: rows are a segmented stack with a timestamp pill, title and length; the chapter that is playing is expanded in the primary container with "Now playing", Material's wavy progress bar showing how far into the chapter you are (flat while paused) and the time inside / length of the chapter; played chapters get a check; it opens at the current chapter and a "Now playing" button jumps back to it. Share sheet: three big square action tiles (Share, Copy link, Browser), the address shown, and a "Start at 12:34" switch. Save sheet: unchanged list plus "New playlist" (name, Create saves the video into it). Also: Download has a Subtitles switch (setting "Save subtitles with downloads"), the Subscribe button has no bell icon any more and the bell beside it animates in, the mini player's play / pause icon swaps with a pop.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests`, `:desktopApp:test`, `:androidApp:installDebug` | pass (new `ChapterProgressTest`) | running |
| Phone: chapters panel with the wavy "how far into the chapter" bar moving (2:47 to 2:51 over 4 s), description sheet, Subscribe without icon | seen | running on device |
| Phone: Share and Save sheets, subtitle download and offline captions, bell animation, mini player swap | not looked at / not run | not verified |
| Subtitles: saved as WebVTT text in the download's stored info (manual tracks, else the first automatic one, at most 8), shown through the captions menu when the downloaded file plays offline | compile and unit-suite only | not verified on a device |

## 3. Files created/changed
- `ui/player/ChaptersSheet.kt`, `library/Timestamps.kt` (`chapterEndSeconds`, `chapterProgress`), `VideoScreen.kt` (durations, live position, subtitles on offline start, bell animation).
- `ui/screens/{ShareSheet,SaveSheet}.kt`.
- Subtitles: `Settings.downloadsSubtitles`, `DownloadRequest.subtitles`, `StoredPlan.subtitles`, `StoredInfo.subtitles` / `StoredSubtitle`, `OfflineCollector`, `PlaybackSession.startLocal(captions)`, `ui/platform/PlatformUi.kt` (`OfflineSubtitles`), `AppShell.kt` (text fetcher), the download dialog switch.
- `components/SubscribeButton.kt`, `ChannelScreen.kt`, `search/SearchParts.kt`, `player/PlayerSheet.kt`.

## 4. Decisions the spec didn't dictate
- Subtitles are kept inside the app's database with the download (not as .vtt files in your downloads folder); a follow-up could also write sidecar files.
- The chapters panel stays an inline panel under the video (a modal sheet would cover the video).

## 5. Known issues / unverified assumptions
- Tapping the chapter pill on the player did nothing in my one attempt on the phone while the controls were fading; opening Chapters from the description sheet works. Not investigated further.
- Existing downloads have no subtitles; only new downloads with the switch on.

## 6. Manual test checklist
1. Open a chaptered video, open Chapters: the current row has the wavy bar moving; tap another row to jump; pause: the wave flattens.
2. Share: tiles, switch the time off and see the address change; Copy link shows the toast.
3. Save to: New playlist, name, Create.
4. Download with Subtitles on, then play it offline: captions menu lists the languages.
5. Subscribe: no icon on the button; the bell slides in; the mini player play / pause icon pops.

## Addendum: messages on the video page, download button states
- The snackbar was drawn inside the page area, underneath the video page (which covers it), so no message was ever visible. With the video page open it is now drawn above the page, at the bottom edge. The inline status line above the like / dislike buttons is gone; its messages (queued, saved to the account, could not rate...) use the snackbar.
- The Download button follows the download: idle (split button), queued / downloading (a wavy circular loader with the percentage; tapping pauses, the right-hand button cancels), paused (resume), failed (retry), finished: "Saved"; tapping it asks "Delete this download?" and removes it.
- Verified: `allTests`, `:desktopApp:test` pass. Not verified on the phone: the phone was in use for something else when I tried, so I stopped touching it (two taps and a swipe I sent landed in another app; nothing was typed or sent by me).

## Addendum 2: tested on the phone
- Saved: tapping it opened "Delete this download?"; Delete removed the file and showed the "Download deleted" snackbar (the snackbar is now visible over the video page). A bug found here is fixed: the button stayed "Saved" after the delete because the page read the download once; it now follows the downloads list.
- Idle: split button "Download | v". Tap: "Download queued" snackbar, the button becomes "Downloading 76%" with a wavy loader and an X (cancel) on its right; then "Finishing" while the video and audio are merged (3 minutes for a 177 MB file on this phone), then "Saved" without reopening the page.
- Seen on the way: `files/downloads-partial` on the phone holds about 1.9 GB of leftover partial files from earlier downloads (for example `rH3zE7VlIMs.video.video.webm`, 1.5 GB, for a finished 38 MB download). Not investigated; they are not cleaned up after a download finishes or is removed in some case.

## Addendum 3: leftover partial downloads
- Cleaned on the phone: `downloads-partial` went from 1.8 GB to empty (four orphan partial files, for videos that were finished or removed). The finished files in `Movies` (285 MB) were not touched.
- Fix so it does not build up again: at startup, before anything is scheduled, the download manager deletes working files that no unfinished download owns (`removeOrphanedWorkingFiles`; queued, paused and failed downloads keep theirs so they can resume). New `DownloadStorage.workingFiles()` (file storage lists its working folder). Test `workingFilesNoDownloadOwnsAreRemovedAtStartup`.
- Cause of the leftovers is not known (a download removed or interrupted in a way that skipped the delete); this makes them harmless, not impossible.
- Two files in `Movies` ("Git and GitHub - Full Course" 38 MB, "I am done with Golang" 1 MB, both 08:36) are far smaller than their partial files were (1.5 GB, 197 MB): they look like cut-off merges from an earlier test; delete them from your downloads if you do not want them.
