# 86: Video list and app-wide design/usability review

## 1. Summary

Review, then fixes for the confirmed items (section 7). I ran the debug build on a physical phone (1272x2800, 3.5 px/dp, dark theme) and went through Home, Search, Library, Subscriptions, Shorts, the watch page and the profile menu, then confirmed causes in code and by pixel measurement.
Both of your concerns are confirmed: thumbnails are over-rounded and cut at the screen edge, and the LIVE/SHORT badges fail contrast (1.7:1, minimum 4.5:1). Shorts has two clipping bugs. Details and proposed fixes below, ranked.

## 2. Verification

| Check | Result | Verified-by |
|---|---|---|
| LIVE badge contrast | White text (255,255,255) on pink (255,180,171) = **1.7:1**, fails 4.5:1 | running (pixels sampled from a device screenshot) |
| Cause of badge colours | Dark theme: `error` = 0xFFFFB4AB, `primary` = 0xFFFFB4AA; badge text hardcoded `ExtraColors.onMedia` = white (`theme/ExtraColors.kt:15`, `theme/RedColorSchemes.kt:35,41`). `onError`/`onPrimary` exist (dark red) and are not used. Light theme is fine (red 0xFFBA1A1A with white). | code reading |
| Corner cut-off at screen edge | Visible in a 3x zoom of the Home screenshot: the thumbnail's top corners curve away at x=0 | running |
| Touch targets (Home: avatar, 3-dot, tabs, search/bell/profile) | All at least 48 dp (168 px), so the earlier 3-dot concern is withdrawn | running (uiautomator dump) |
| Shorts clipping, watch-page clipping, empty states | Seen on device | running |
| Compact HORIZONTAL row, light theme, tablet/desktop, Settings pages | Not seen | not verified |
| App runs and builds | `:androidApp:assembleDebug` OK | running |

## 3. Files created/changed

- Created: this report. No source changes. Screenshots stay in the session scratchpad (not in the repo).
- On your phone: debug build installed; I declined the Android notification-permission prompt that Vela showed on first Shorts open (changeable in Android settings).

## 4. Findings (confirmed, most important first)

**What "dark wedge" means:** `VideoCard` makes the thumbnail 16 dp rounded (`MaterialTheme.shapes.large`) and `bleed()` then stretches it to the screen edges. A rounded corner at the very edge of the screen leaves a small dark curved cut-out where the picture should reach the edge, instead of a clean edge or a clean floating rounded image. It is the screen edge cutting through a rounded shape.

Video list (shared by Home, Search, Subs, channel, playlists, related):
1. **Over-rounded, edge-clipped thumbnails.** Fix: full-bleed thumbnails 0 dp; inset ones (tablet/desktop, shelves) 8 dp. Compact row: outer 12 dp, inner 8 dp.
2. **LIVE/SHORT badges unreadable in dark theme (1.7:1).** Fix: use `onError`/`onPrimary` for the text, or a dark scrim (e.g. 0xCC000000) with white text and a red dot for LIVE. Either gives well over 4.5:1.
3. **Badges are full pills over a rectangular frame.** Use about 4 dp corners.
4. **Heavy title.** All-caps titles in `bodyLarge` SemiBold look loud and cut mid-phrase; use a smaller weight/size and a 40 dp avatar. The avatar's left edge (about 26 dp from the screen edge) matches nothing else on the page (chips/title use 16 dp).
5. **Density.** One card is about a quarter of the screen, so about 2.5 items are visible. Phones have no compact option (`Lists.kt` uses the row style only on wide windows).

Other screens:
6. **Shorts, action rail overflows (bug).** Six buttons in the right rail; the bottom one (fullscreen) is cut off by the black strip under the video on this phone (`ShortsScreen.kt:285`, a `Column` with `Alignment.BottomEnd` and no scroll/limit).
7. **Shorts, caption cut off (bug).** The title "Ou paka mouri! #shorts" is half-hidden behind the black "49K views" strip, so the caption and the views line fight for the same space.
8. **Shorts, counts on busy video.** "845" sits on the picture with no scrim and overlapped the title text of the video.
9. **Watch page, player buttons clipped.** The back, CC and settings circles sit flush against the video's edges so their circles are cut by the screen edge.
10. **Watch page, action row hides actions.** Like / dislike / Download fill the width and the next button is cut off at the right; Share and Save are only reachable by scrolling sideways with no hint beyond a fade.
11. **Watch page, "Comments" card is a bare label.** No count, preview or arrow, so it does not read as tappable.
12. **Wavy (expressive) progress bar on live streams.** It shows a full bar for a stream that has no end, on the watch page and in the mini player. On busy video its thin pink line is also low contrast. Usable but decorative; a plain "LIVE" state would be clearer.
13. **Live streams show "views".** "2.8K views · 41 minutes ago" for a live stream; for live this is probably the concurrent audience, "watching". Unverified: needs a look at what the extractor returns.
14. **Empty states are bare.** Subscriptions: an "i" icon, one sentence, one button. Library "Watch later 0 videos" is a large tile for nothing. Low severity.
15. **Library tiles truncate.** "Downlo..." is cut in the Downloads shortcut because the title shares a narrow tile with a 3-line subtitle.

### Light theme (seen on device, colours sampled from screenshots)

Fine in light: LIVE badge (white on 0xFFBA1A1A, about 6.5:1), title and meta text (about 8.9:1), Subscribe button, filled Download button.
Problems, all the same cause: surfaces are tinted pale pink on a near-white pink page, so containers and controls have almost no edge.

| Element | Colour vs what it sits on | Contrast |
|---|---|---|
| Unselected filter chips (Home, Search) | 0xFCEAE8 on 0xFFF8F7 | 1.11:1 (no outline; M3's own unselected chip has one) |
| Profile avatar blob (top right) | 0xF6E4E2 on 0xFFF8F7 | 1.17:1; also no red ring as in dark theme |
| Mini player | 0xFCEAE8 on the page / on the nav bar | 1.11:1 / 1.00:1, separated only by a hairline |
| Selected nav-bar indicator | 0xFFDAD5 on 0xFCEAE8 | 1.11:1 (the filled icon and bold label carry the state; that is M3's default look) |
| "Watch later" tile icon blob | 0xFFDAD5 on 0xF0DEDC | about 1.0:1, the shape vanishes (strong red in dark theme) |
| Library shortcut cards, description/comment cards | 0xFFF0EE on 0xFFF8F7 | 1.06:1 |

16. **Light theme: boundaries of controls are nearly invisible** (rows above). Fix: give unselected chips a 1 dp `outlineVariant` border, give the avatar and the Watch-later icon a stronger container colour (primary container with on-container glyph, or the red ring from dark theme), and put a top divider or one tonal step darker on the mini player. Cards of text content (1.06:1) are fine if the text is readable, which it is.
17. **Thumbnail corner cut-out is visible in light theme too**, on the Bob & Tom card: the picture's own blue frame is clipped by the corner curve at the screen edge.
18. **Library "Recently watched" carousel** (inset, 16 dp rounded) is fine in shape; it would also look tighter at 8-12 dp, so use the same token as finding 1.

## 5. Known issues / unverified assumptions

- Light theme was checked on Home, Search, Library and the watch page; Shorts is on black in both themes.
- The compact row, tablet/desktop layout, Settings, channel page, downloads and History were not seen (History and Downloads were empty).
- dp values come from pixel measurements at 3.5 px/dp, accurate to about 1 dp.
- Whether the Shorts overflow also happens on shorter or taller phones is untested; it likely depends on screen height.
- Task number 86 was chosen as the next free one.

## 6. Manual test checklist for the user

1. Home: zoom in on the left/right edge of a thumbnail corner and compare it with a thumbnail in Search; confirm the curved cut-out at the edge.
2. Find a LIVE item and read the badge; then tell me if you want the scrim style or the dark-red-text style.
3. Shorts: scroll the right rail to its bottom; is the fullscreen button reachable or cut? Is the caption fully visible?
4. Watch page: is the second row (Download and beyond) scrollable, and can you reach Share and Save?
5. Light theme: can you tell the profile button, an unselected chip and the mini player apart from the page at arm's length (findings 16-17)?
6. Reply with the finding numbers to fix. Suggested first batch: 1, 2, 3, 6, 7, 9, 10, 16.
6. Optional cleanup: `adb uninstall com.greenaltambe.vela`.

## 7. Fixes applied (after your confirmation)

Your answers: corner cut-out confirmed; badge readability bad; Shorts fullscreen and caption reachable but low visibility; watch-page second row scrolls fine (finding 10 withdrawn); light-theme surfaces distinguishable. Shorts "clipping" (6, 7) was a misread: the rail straddles the video's bottom edge but is fully visible; only visibility changed.

| Change | Where | Verified-by |
|---|---|---|
| Full-bleed thumbnails square; inset thumbnails 8 dp (`shapes.small`); row container 12 dp (concentric) | `components/Components.kt` (VideoCard), `States.kt` (skeleton matches) | running (phone, light theme) |
| LIVE / SHORT badges use `error`+`onError` / `primary`+`onPrimary`; duration badge is 80% scrim with white text; badge corners `shapes.extraSmall` (4 dp) | `Components.kt` | LIVE seen on phone in light; dark theme not re-checked (contrast of `onError` 0xFF690005 on 0xFFFFB4AB is above 7:1 by the colour values) |
| Phone card title `titleMedium` (no extra weight), avatar 40 dp, avatar/text aligned to the page gutter | `Components.kt`, `States.kt` | running |
| Unselected chips get Material's 1 dp `outlineVariant` border | `components/ExpressiveChip.kt` | running |
| Profile button gets a 1 dp `outlineVariant` ring | `screens/ProfileMenu.kt` | running |
| Library icon badge uses `primary`/`onPrimary` (was a container colour 1.0:1 against its tile in light theme) | `screens/LibraryScreens.kt` | compile-only (no playlist cover shown after the fix) |
| Shorts: button scrims 35% to 55%, dark gradient behind the caption and rail, shadow on the like/dislike counts | `screens/ShortsScreen.kt` | running |
| Player top row 4 dp inset so the back/CC/settings circles are not cut by the picture's edge | `player/PlayerView.kt` | compile-only (watch page not re-opened after the change) |

Offline tests: `./gradlew allTests` passes (the theme source scan caught a raw 4 dp and a raw black, both replaced with theme tokens). `assembleDebug` OK.

Not changed: mini player (already has a divider; you can tell it apart), live "views" wording (13), compact-list option (5), wavy bar on live streams (12), empty states (14), Downloads tile title truncation (15). Say if you want any of these.

### Extra manual checks
7. Dark theme: Home LIVE badge now dark red text on pale pink; Library "Watch later" badge; chip outlines not too heavy.
8. Watch page: back button and the CC/settings buttons sit slightly inside the picture's corners.
9. Shorts on a bright video: counts and caption readable.
