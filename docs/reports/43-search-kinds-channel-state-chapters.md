# 43 Search channels/playlists, channel tab state, chapters sheet

## 1. Summary
Search returned only videos because the NewPipe tier hard-coded the `VIDEOS` search filter (nothing was filtered afterwards). Added "Channels" and "Playlists" pills to the search filter row that run a channel/playlist search. The channel page now keeps its open tab, loaded items and scroll in a shell-level store, so going back from a playlist restores the tab with no reload. The chapters sheet is capped to the space below the video in portrait.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `:shared`, `:extractor-newpipe`, `:androidApp` compile | pass | compile |
| `./gradlew allTests` | pass | running (offline) |
| Channel/playlist search against real YouTube | not run | unverified |
| Tab restore, chapters sheet bound on device | not run | compile-only |

## 3. Files created/changed
- Extractor: `Models.kt` (ChannelRef), `Extractor.kt` (searchChannels, searchPlaylists), wrappers `PacedExtractor`, `TimeoutExtractor`, `Captcha`, `FallbackChain`, `StubExtractor`; `NewPipeExtractor.kt`, `ItemMapping.kt`.
- UI: `SearchScreen.kt`, `ChannelScreen.kt`, new `ChannelStore.kt`, `AppShell.kt` (provides store), `PlayerView.kt`, `ChaptersSheet.kt`.

## 4. Decisions the spec didn't dictate
- Pills pick a result type (Videos / Channels / Playlists); the default "All" stays videos only. Video filters (Shorts, length, sort) hide while a type pill is on.
- Channel/playlist search is NewPipe-only; yt-dlp tier answers "not supported" and the chain moves on.
- Channel state kept for the last 6 channels, 30 min; sort resets to Latest when switching tabs.
- Sheet max height = window height minus the player's bottom edge minus handle; fullscreen is unaffected.

## 5. Known issues / unverified assumptions
- If NewPipe is down, channel/playlist search fails (no yt-dlp fallback).
- Channel search results aren't cached across leaving the search page.
- Sheet height arithmetic (36 dp handle + 40 dp title) is an estimate; may leave a small gap.

## 6. Manual test checklist
1. Search a name, tap Channels: channel rows appear; tap one to open it. Tap Playlists: playlists appear; tap one to open it.
2. Tap the "Videos" pill: back to video results with the other filters.
3. Open a channel, Playlists tab, open a playlist, press back: still on Playlists, no loader, same scroll.
4. Same with Videos tab after opening a video.
5. Portrait, open a video with chapters, tap chapters: the sheet stays below the video, list scrolls inside.
6. Fullscreen/landscape chapters still work.

## Addendum: playlist badge
Playlist rows (search and channel Playlists tab) show a corner badge on the thumbnail: the existing `playlist` icon plus the video count. It uses `Vela.icons.playlist`, which every icon pack already defines (Lucide, Material Sharp/Two-Tone, both custom sets), so no new variants were needed. Compile-only; check it in each icon pack (Settings > Look).

## Addendum: Home chips
Home has a chip row (in the list header): All (your feed), topic pills (Music, Gaming, News, Live, Sports, Podcasts, Movies, Learning, Cooking, Tech) that run a search for the topic in place of the feed, then a length pill and Shorts, which narrow what is loaded. Pull-to-refresh and paging follow the selected topic. Setting: Settings > Feed > "Show topic and filter chips on Home" (`home.chips`, default on). Topic/filter selection is not remembered when you leave Home. Compile + offline tests only; topic searches are plain searches, not YouTube's own category feeds.
Manual: 7. Home: tap Music, results change; tap All, feed returns. 8. Turn the setting off: chips disappear.

## Addendum: Library and Downloads redesign
- Library: shortcut tiles (History, Downloads, Sync) with icon on the palette gradient; "Recently watched" horizontal row (from history, with progress bar, "See all" opens History); playlists as a grid of large cards (first video's picture as cover, else gradient + icon; name, count, cloud icon for account playlists; Rename/Delete in a corner ⋮ menu).
- Downloads: each row has a thumbnail, title, status; Pause/Resume/Retry/Play/Delete moved into a ⋮ menu; swipe left asks to delete (row snaps back, confirm dialog as before). Cause of the missing thumbnail: the list never displayed one, although a copy was saved after completion. It now shows the saved copy (offline-safe), else YouTube's picture for the id; the collector also falls back to YouTube's picture URL when the video lookup fails.
- Theme: only `Vela.colors`, `Vela.wake`, `Vela.*Shape` (corner radius setting) used; no fixed colours or radii. Fixed a failing settings test by listing `home.chips` on the Home settings page.
- Verification: `allTests` pass; compile-only on Android. Downloads already completed before this change have no saved copy if their video lookup failed, so they show YouTube's picture (needs network).
Manual: 9. Library: tiles, Recently watched row, playlist cards (rename/delete via ⋮). 10. Downloads: thumbnails visible; ⋮ menu; swipe left asks to delete. 11. Change theme/corner radius in Settings > Look: both screens follow.

## Addendum: Explore, playlist page, player buttons, comments, swipe fix
- Home chips and the `home.chips` setting removed. New **Explore** tab (`NavTab.EXPLORE`, `Route.Explore`, new `explore` icon in all 7 icon sets): Trending plus topic pills (Music, Gaming, News, Live, Sports, Podcasts, Movies, Learning, Cooking, Tech); each topic keeps its loaded videos and scroll. It is not in the default tab list: add it in Customize > tabs (max 5 tabs).
- Library playlist page: header card (cover, name, count, Play all, Download all); swipe a video away to remove it; per-video ⋮ menu has Move to top / up / down / Remove.
- Player: the -10/+10 buttons are now Previous / Next video (`skipPrevious`, `skipNext` icons in all 7 sets; double-tap still seeks). Previous = last video played (history of 30), or restarts the current one if none. Next = queue, else first related; disabled when neither exists.
- Comments sheet: comments are cards, Top/Newest pills, count chip, gradient "Pinned" pill, like icon with count, reply toggle pill, replies nested with the gradient line. Theme colours and radii only.
- Swipe fix: swipe-up to fullscreen only counts when the gesture began with the page at full size and was never pulled down; changing your mind mid-minimise now just restores the page.
- Verification: compile + `allTests` pass; nothing run on a device.
Manual: 12. Customize > tabs: enable Explore; try topics. 13. Open a playlist: swipe to remove, ⋮ to reorder. 14. Player: Previous/Next buttons (play two videos first); double-tap still seeks. 15. Open comments: look and replies. 16. Start dragging the video down, change your mind and drag up: it must restore, not go fullscreen; a fresh swipe up on the full-size video still goes fullscreen.

## Addendum: player controls layout, long titles
- Small portrait player (not fullscreen, not the desktop window): the menu buttons (mute, captions, audio, quality, speed, sleep) moved to the top bar beside Back; the bottom now holds only `time / duration` (left), the chapter name (right, tap opens the chapter list), the fullscreen button, and the seek bar with a gap above it. Previous / play / Next sit in the middle with clear space on both sides. Fullscreen and desktop keep the old layout. SponsorBlock's skip prompt moves down under the top bar in this layout.
- A video title cut at 3 lines now expands when tapped and folds back on a second tap.
- Compile + allTests only. Manual: 17. Portrait: top bar has the menu icons; bottom has time left, chapter right, seek bar below with space; no accidental hits between buttons and seek bar. 18. Tap a long title: it expands.

## Addendum: suggestion arrow
Each search suggestion has an arrow button (the up arrow turned 45°, the icon pack's own) that copies the suggestion plus a space into the field without searching. Manual: 19. Type two letters, tap the arrow on a suggestion: the field fills and new suggestions appear; tapping the row still searches.

## Addendum: flash after minimising the video page
Cause (by reading the code, not measured on a device): after the page is pulled down and closed, the picture of the screen behind it was dropped at once while the real screen was rebuilt and faded in from transparent, so the bare background (and any loader) showed for a moment. Fix: the picture now stays under the screen for 450 ms after a Back from the video page, so the real screen fades in over it. Manual: 20. Minimise a video from Home, Channel, Search, Library: no flash; the screen appears in place.

## Addendum: profile menu and settings pages
- Profile menu now holds only the account card and account actions (Sign in / Switch / Add / Sign out), then Settings and About, each with an icon. Removed the repeats: Customize Vela, Backup & restore and Health & diagnostics (all in Settings), Downloads and History (in the Library). `NavigationUiTest` updated to the new entries.
- Settings pages: settings are grouped into titled cards (SettingsPages.sectionsOf), e.g. SponsorBlock & extras = SponsorBlock / Return YouTube Dislike / DeArrow / Modules; Playback = Playing / Quality and data / Reliability; Downloads, Account safety, Feed filters and Updates are split too. Each block has a small header with a palette-gradient mark, a rounded card (global corner radius) and hairlines between rows; the "Open ..." link is a tile row.
- Opening a page: the cards are composed after the page's slide-in finishes (placeholders of the cards' size meanwhile), so the heavy composition no longer lands in the middle of the animation. Cause is inferred from the code, not profiled; if it still stutters, tell me which page.
- Not changed: the Customize sub-pages (palette, text, icons...) and the Accounts / Backup / Health screens keep their own layouts. If any of them still looks off against the theme, name it.
- Verification: compile + allTests pass; not run on a device. Manual: 21. Open the avatar menu: short list. 22. Settings > SponsorBlock & extras: four clear cards. 23. Open several pages: no stutter at the start of the slide. 24. Change corner radius / palette: cards follow.

## Addendum: content shifting after minimising
The previous fix put the old picture UNDER the rebuilt screen, so any shift while that screen settled (top bar and banners laid out again, list restoring its place, "hidden by your feed filters" lines appearing after filtering) was visible. The picture is now drawn OVER the screen for 260 ms and then fades out over 220 ms. I could not pin the exact source of the shift without a device (candidates above); this hides it whatever it is. Manual: 25. Minimise a video from Home, Channel, Library: the page stays still and fades to the live screen. If it still shifts after the fade, tell me the screen and which direction.

## Addendum: title clipping, channel icons
- Video title: the tap-to-expand title was clipped to a rounded shape, cutting the text at the corners; the clip is removed.
- Channel page: videos no longer show the channel picture (`showChannelAvatar = false` on its four lists).
- Missing channel pictures: `VideoCollection` fills a missing picture from your subscriptions (by channel id), which fixes the Subscriptions feed, History and playlists. The video page uses: the listing's picture, else the video details' (new `VideoInfo.channelAvatarUrl`, filled by the NewPipe tier), else your subscription's. Subscribing from the video page now stores that picture too.
- Limits: a channel you are not subscribed to, on a source with no uploader picture (yt-dlp tier), still shows the letter placeholder in lists; a downloaded video opened offline has no stored picture.
- Verified: compile + allTests; on device: 26. Library > open a video: channel picture appears (after details load). 27. Subscriptions feed cards have pictures. 28. Channel page cards have none. 29. Expand a long title: no clipped corners.

## Addendum: selected-tab mark
- New setting `look.nav.marker` "Gradient mark on the selected tab" (default on), in Customize > Tabs & mini player. Off removes the mark from the bottom bar and the side rail.
- Side rail: the horizontal line at the end of the row looked wrong; the mark is now a short upright gradient bar at the row's left edge (space kept for unselected rows so labels stay aligned). The bottom bar keeps the line under the icon.
- Verified: compile + allTests; not seen on a device. Manual: 30. Customize > Tabs & mini player: toggle the mark; bottom bar and (wide window / desktop) side rail both follow. 31. Desktop rail: the upright bar sits on the selected row.
