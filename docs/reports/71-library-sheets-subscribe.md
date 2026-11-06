# 71 Library pages, sheets, subscribe pair, description links, wave fix

## 1. Summary
History, Downloads and a playlist now share one page frame (collapsing large top bar with back, same width and spacing, rows as one segmented stack) instead of three different layouts; Library's tiles use the same extra-large corners. All eight bottom sheets go through one `VelaSheet` (Material modal sheet, extra-large top corners, tonal container, Material drag handle, opens full height); the comments, description and chapters panels use Material's drag handle. Subscribe and the bell are a connected pair like Like / Dislike (flat inner sides). The description preview showed `<a href>` tags: descriptions are now converted to plain text (links keep their real address). The wavy progress bar no longer pinches at chapter gaps: the wave swells in once at the start and eases out before the handle.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests`, `:desktopApp:test`, `:androidApp:installDebug` | pass, 0 failures (new `DescriptionTextTest`) | running |
| Phone: video page with the connected Subscribed + bell pair, plain-text description preview, wavy bar with a handle | seen | running on device |
| Phone: History, Downloads, playlist pages in the new frame; all sheets; Downloads banner | not looked at | not verified |
| Wave motion over a long play with chapters | one still frame only | not verified |

## 3. Files created/changed
- New `extractor/DescriptionText.kt` (`plainDescription`), used by `StreamMapping.kt`, `VideoScreen.kt`, `DescriptionSheet.kt`; new `ui/components/VelaSheet.kt` (`VelaSheet`, `SheetHandle`).
- Sheets switched to `VelaSheet`: `Common.kt`, `PlayerControls.kt`, `ChannelScreen.kt` (2), `DownloadRulesScreen.kt` (2), `SettingItem.kt`, `SearchParts.kt`; handles in `CommentsSheet.kt`, `DescriptionSheet.kt`, `ChaptersSheet.kt`.
- Library: `LibraryScreens.kt` (History and Playlist on `SettingsScaffold`, segmented rows, extra-large tiles), `DownloadsScreen.kt`, `AppShell.kt` (no shell top bar on those routes).
- Subscribe: `SubscribeButton.kt` (toggle button with `position`), `ChannelBell.kt`, `VideoScreen.kt`, `ChannelScreen.kt`, `SearchParts.kt`.
- Wave: `Scrubber.kt`.

## 4. Decisions the spec didn't dictate
- Library home stays a tab page with the app bar (it is a root); its tiles took the shared corners.
- The History title action "Clear all" moved into the top bar.
- Links in descriptions keep the real address when YouTube's visible text was cut with "…".

## 5. Known issues / unverified assumptions
- Not done from the earlier list: Download as a split button, player menu rows as segmented list items (the rows keep their custom look), comments / description / chapters as true modal sheets (they stay inline panels with Material's handle).
- Old cached video details with HTML are converted when shown, so no migration was needed.
- Playlist video rows and Download rows now share the 2 dp stack gap; swipe-to-delete backgrounds take the row's shape.

## 6. Manual test checklist
1. Open History, Downloads and a playlist from Library: same bar, width, spacing; Clear all in History's bar; Download settings button on Downloads.
2. Open every sheet (video menu, quality, filters, channel About, a settings choice, add rule): same corners, handle, container.
3. Video page: Subscribed + bell look like Like + Dislike; description preview has no tags; open the description sheet.
4. Play a chaptered video with Wavy progress: smooth wave, no pinching at chapter gaps.

## Addendum: remaining items (same task)
- Download on the video page is now a split button: the left part starts a download at once with the download settings (and says "Download queued"), the arrow opens the quality / audio-only sheet (`video.download`, `video.download.options`).
- Player menu rows (quality, speed, captions, audio, sleep timer, share, save) are Material selectable list items: the chosen row fills with the primary container with a check; entrance stagger and haptic kept (`player.menu.row`).
- Comments, description and chapters stay inline panels under the video (a modal sheet would dim and cover the playing video); they use the same corners-and-handle language as the modal sheets.
- Verification: `allTests` and `:desktopApp:test` pass, installed on the phone. The split button and the new menu rows were not looked at on the phone: the phone was in use for something else when I tried, so I stopped touching it.
