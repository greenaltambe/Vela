# 70 Video page, player controls (first pass), downloads on mobile data

## 1. Summary
Downloads: when downloads wait for Wi-Fi, the Downloads page now shows a banner with "Use mobile data" (turns Wi-Fi-only off) and "Download settings" (opens the download settings and highlights the Wi-Fi-only switch); a "Download settings" button is also always in the storage card. Video page: the title block is a tonal card (larger title, views and age, "…more", first line of the description) and the comments card has the larger expressive corners. Player controls: buttons morph from a disc to a rounded square while pressed; the menu choice chips (quality, speed steps, captions, sleep timer) and the comments sort are the app's expressive toggle chips (check on the chosen one). Not done: Download as a split button, menu rows as segmented list items, comments / description / chapters panels as sheets with Material drag handles, the YouTube inbox.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests`, `:desktopApp:test`, `:androidApp:installDebug` | pass, 0 failures (`NotificationsUiTest` fixed to read the unmerged dot) | running |
| Phone: video page (title card, comments card, controls, wavy seek bar) | seen | running on device |
| Phone: Downloads banner with a download blocked by Wi-Fi-only, both buttons | not triggered | not verified |
| Phone: pressed button morph, menu sheets with the new chips, comments sort | not looked at | not verified |

## 3. Files created/changed
- `ui/screens/DownloadsScreen.kt` (waiting banner, settings buttons), `ui/screens/VideoScreen.kt` (title card, comments card), `ui/player/PlayerControls.kt` (morphing press shape, `ChoiceChip` uses `ExpressiveChip`), `ui/screens/CommentsSheet.kt` (sort chips), `desktopApp/.../NotificationsUiTest.kt`.

## 4. Decisions the spec didn't dictate
- "Use mobile data" changes the global Wi-Fi-only setting (it is not a one-time override); the settings page is one tap away to turn it back on.
- The press morph keeps the existing squeeze, halo and haptic; only the disc shape changes.
- The description teaser shows only when the video details loaded.

## 5. Known issues / unverified assumptions
- Menu rows (`PlayerMenuRow`), the three panels' containers and drag handles, and Download as a split button are still the earlier look.
- With many chips (quality grid) the expressive chip is wider than the old one; check the 4-column quality grid on a narrow phone.

## 6. Manual test checklist
1. Start a download on mobile data with Wi-Fi-only on: Downloads shows the banner; "Use mobile data" starts it; "Download settings" opens the page with the switch highlighted.
2. Open a video: tonal title card, description line, comments card; tap each.
3. Player: press and hold a control button (it squares off), open Settings > Quality / Speed / Captions: chips with a check on the chosen one.
4. Comments: Top / Newest / Timed chips.
