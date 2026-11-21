# 116 Desktop: playback diagnosis, system theme, window size/place, tabs and multi-window

## 1. Summary
Videos: on this machine libmpv is not installed (`rpm -q mpv-libs` says so; `vela --smoke-test` reports "libmpv: not loaded"), so the desktop app runs but cannot play anything. That is the cause here; I cannot install it for you (needs sudo). The app now says so on every window with the exact install command, instead of a line on a video page. System theme: Compose reports light on Linux whatever the desktop says; the app now asks the desktop (settings portal, then gsettings) and follows it live. Window: it always opened at 1100x800 in the window manager's default place; size, position and maximized state are now remembered. New: browser-style tabs and multiple windows (reorder, close, drag out to a new window, drag onto another window's tab row, Ctrl shortcuts).

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `allTests`, `:desktopApp:test` (new `WorkspaceTest`: tab open/close/select, reorder, tear-off, move between windows, window-geometry encoding, install-hint text) | pass | verified-by-running |
| Android compile | pass | compile-only |
| `./gradlew :desktopApp:run` for 60 s on this Wayland/GNOME session: starts, no exception, and writes the window geometry (`desktop.window` = 1100,800,48,80,0 in vela.db) | ok | verified-by-running |
| System theme probe on this machine: desktop is dark (`gsettings` says prefer-dark); `SystemTheme.isDark()` returned true | ok | verified-by-running |
| Playback itself (libmpv missing here) | not testable | not verified |
| Dragging tabs (reorder / to another window / out into a window), shortcuts, shared mini player across tabs: logic is unit-tested, the mouse interaction was never driven | not run | not verified |

## 3. Files created/changed
- Created (desktopApp): `Workspace.kt` (windows, tabs and the operations on them), `TabStrip.kt`, `DesktopWindow.kt` (one window, shortcuts), `WindowGeometry.kt`, `SystemTheme.kt`, `PlayerMissingBanner.kt`, `src/test/.../WorkspaceTest.kt`
- Created (shared): `ui/nav/SharedNowPlaying.kt`
- Changed: `desktopApp/.../main.kt` (workspace instead of one window; theme polling; links open in a new tab), `desktopApp/build.gradle.kts` (material3), `shared/.../AppTheme.kt` (`LocalSystemDark`), `AppShell.kt` (public `routeTitle`, shared mini player across tabs), `PlayerSheetState.kt` (`adopt`)
- Created: this report

## 4. Decisions the spec didn't dictate
- One player, shared: a video keeps playing when you switch tabs or windows, and the mini player follows (a tab that comes to the front shows what is playing; closing it in one closes it in all). The last session's mini player is restored once, in the first tab.
- Tab = its own page stack (back history, current page). A tab that is not showing is not kept composed: coming back rebuilds the page from its stack (scroll position and loaded lists are not kept).
- Tab strip is Vela's own, under the native title bar. Tabs: click selects, cross or middle-click closes, "+" opens. Dragging: along the row reorders; released over another Vela window's tab row it moves there (at that slot); released well away from its row it opens in a new window at the pointer (not possible for a window's only tab). Closing a window closes all its tabs; closing the last window ends the app.
- Shortcuts: Ctrl+T new tab, Ctrl+W close, Ctrl+Tab / Ctrl+Shift+Tab (also Ctrl+PageDown/Up) switch, Ctrl+1..8 jump, Ctrl+9 last, Ctrl+Shift+N new window.
- A link given to a second launch of Vela opens in a new tab of the last used window.
- Window position is remembered only when it lies on a connected screen; the position of a window on Wayland may be ignored by the compositor (this session gave a position via XWayland).
- Theme polls the desktop every 3 s (cheap D-Bus read) rather than listening for the change signal; Windows and macOS keep Compose's own detection.
- The libmpv banner can be dismissed for the session and has a "Copy command" button. For Fedora it names RPM Fusion, because Fedora's own repositories do not ship mpv/libmpv.

## 5. Known issues / unverified assumptions
- Tabs are not restored after a restart (only the window geometry is).
- AWT coordinates are assumed to be the same unit as Compose dp (true on Linux without fractional scaling tricks); on unusual scaling the drop target may be off by the scale factor.
- Other desktop findings: yt-dlp is not installed here (the app falls back to NewPipe and the InnerTube client; install yt-dlp for the best coverage); Fedora's default ffmpeg has no H.264 decoder, which the app already works around by asking for other formats. I found nothing else wrong from reading the code; things only a person using the app will see (HiDPI, Wayland details) are not covered.
- Tabs open the same page of the app: there is no "Open in new tab" entry on videos and links yet.

## 6. Manual test checklist
1. Install libmpv (Fedora: RPM Fusion free + `sudo dnf install mpv-libs`), restart, play a video. Without it the red banner is at the top of every window; "Copy command" copies the line.
2. Set Settings > Customize > Theme to System and change the desktop between light and dark: the app follows within a few seconds.
3. Resize and move the window, close the app, start it again: same size and place; maximize, quit, start: still maximized.
4. Ctrl+T opens tabs; open different pages in each; drag a tab left/right to reorder; click the cross and middle-click to close.
5. Drag a tab down/away from the row: it becomes its own window under the pointer. Drag a tab from that window onto the first window's tab row: it joins at the slot under the pointer, and the empty window closes.
6. Play a video in one tab, switch to another tab/window: sound continues and its mini player shows the same video; close it there, it closes everywhere.
7. Ctrl+W on the last tab of the last window quits the app.

## Follow-up fixes (same task)

- Tab switch replayed the startup animation and reloaded Home: the animation now plays once (first tab only) and each tab keeps its own Home feed (`TabModel.homeFeed`). Scroll position of a switched-away tab is still lost.
- Videos opened in fullscreen: the "phone is sideways, open straight in fullscreen" rule saw every desktop window as landscape. It now skips desktop (`LocalVolumeSlider`).
- Verified: `allTests` and Android compile pass (compile/unit only; not driven by mouse).
- Manual: maximize, play, press F then Esc: still maximized; open a video from the mini player: not fullscreen; switch tabs: no animation, Home not reloaded.
- Customize page: the colour scheme row was a plain LazyRow, which a desktop mouse cannot scroll. It is now an HScrollRow (drag, wheel, arrows). Compile/unit tests only.
- Fullscreen vs window size (replaces the earlier workarounds): the video goes truly fullscreen (title bar gone, like a browser) through AWT `GraphicsDevice.fullScreenWindow` on the window, not through Compose's `WindowState.placement`, whose Maximized -> Fullscreen -> back round trip shrank and flashed the window. Measured with a plain AWT frame on this GNOME session: maximized stays maximized (state 6) after leaving fullscreen with no delay; a floating frame returns to its position (the app re-applies its saved bounds). Geometry saving is paused while fullscreen. A tab change, or closing the window, leaves fullscreen first. Only the AWT behaviour was measured; the app itself was not driven by hand.
- Manual: maximize, play, fullscreen (title bar hidden), Esc: back to maximized at once, no small flash; repeat from a floating window (same size and place); two windows: fullscreen in one leaves the other alone.
- Video page vs tab strip: confirmed by reading the code that the player sheet and the video page sized themselves from the whole window (`LocalWindowInfo.containerSize`), which on desktop includes the 40 dp tab strip above the app. The open page was therefore 40 dp taller than the area it sits in, so its bottom was cut off (and the mini-player / details heights were off by the same amount). Both now use the sheet's own measured area (`PlayerSheet` `areaSize`, `VideoScreen.pageHeightPx`). Not confirmed: the back button being partly behind the tab strip. The code puts the page below the strip, so I could not reproduce that by reading; compile and unit tests only, not seen on screen.
- Manual: open a video on desktop: the back button fully visible below the tab strip, the video's top edge not cut, the bottom of the page (description / controls) reaching the window bottom.

## Follow-up: video page panels on a wide window
- Problem: in the wide layout (window >= 1000 dp) the About / Comments / Chapters panels opened in the strip under the picture, which is short once the 16:9 picture takes its share of the left column.
- Change (`VideoScreen.kt`, wide branch only): the three panels now open over the related-videos column on the right, which has the window's full height. Their open/close flags, slide animation, close buttons and Back handling are unchanged; the phone layout is untouched.
- Verified: `allTests` and Android compile pass (compile/unit only; no UI test exists for these tags; not seen on screen).
- Manual: wide desktop window, open About, Comments and Chapters: each fills the right column top to bottom and scrolls; the video and details stay on the left; below 1000 dp the phone layout is as before.

## Follow-up: description selectable, social links as chips
- Selectable: the description text in the About panel is wrapped in `SelectionContainer`, so a part of it can be dragged over and copied (the copy button still copies all).
- Links lost: the panel ran the description through `plainDescription` first, which turned every named link ("TikTok: peacocktv") into plain text and dropped its address; only bare http links survived. It now parses the raw description (`parseDescription` keeps anchors and resolves YouTube's redirect links), `plainDescription` stays only for the copy button and the blank check.
- Chips: links to known networks (`library/SocialLinks.kt`: TikTok, Instagram, Facebook, X/Twitter, YouTube, Twitch, Discord, Reddit, GitHub, LinkedIn, Spotify, Patreon, Threads, Bluesky) render as a round badge, " / ", the account, on a tonal background, and open the address. `descriptionText()` in `LinkedText.kt`.
- Decision: the badges are a brand-coloured circle with a glyph or letter, not the real logos (the app has no brand icon assets and I did not draw trademarks). Real logos would need vector assets.
- Verified: `allTests` (new `SocialLinksTest`: network detection, handle, anchors keep their address) and Android compile pass. Not seen on screen; whether the extractor hands over HTML anchors for every video is assumed from the plain-text fallback working the same way.
- Manual: open About on a video with social links (e.g. the Monk one): chips with badges, clicking opens the browser; drag across a few words and Ctrl+C (long-press on Android) copies only that.

## Follow-up: video page shrank to the mini player on returning to its tab
- Cause: each tab's app (and its player sheet) is rebuilt when the tab is selected; the sheet adopted the shared video in its collapsed state, even when that tab's page stack was on the video page.
- Fix: `PlayerSheetState.adopt(video, expanded)`; `AppShell` passes `expanded = navigator.current is Route.Video`, so such a tab comes back with the video page open (snapped, no animation). Other tabs still show the mini player.
- Verified: `allTests` and Android compile pass; not driven by hand.
- Manual: play a video on a tab, open another tab (mini player there), return to the first tab: the video page is open, not the mini player, and the sound never stopped.

## Follow-up: "Classic" colour scheme
- New scheme `CLASSIC` ("Classic", second in the Customize > Color scheme row): white page / near-black (#0F0F0F) dark page, neutral grey cards, black-and-white primary (buttons, selection), grey secondary, red only as the tertiary role. (Corrected below: the seek bar follows the scheme's primary, not a fixed red, so Classic needed its own red for it.) Hand-written in `ui/theme/ClassicColorSchemes.kt`, wired in `Palettes.kt` and `LookSettings.kt`.
- Verified: `PaletteContrastTest` (all palettes, light, dark and AMOLED, 4.5:1) and `allTests` pass; not seen on screen.
- Decision: named "Classic", not "YouTube" (a brand name); values follow the neutrals in your screenshots (#0F0F0F page, #272727 cards), not a copy of any asset. Red appears less than in YouTube (no red tab underline); say if you want more.
- Manual: Customize > Color scheme > Classic, in light and dark: mostly black/white/grey, red seek bar.

## Follow-up: SponsorBlock "Jump to highlight" at the start
- The button was only offered within 5 s of the highlight point. It is now also offered during the first 10 s of the video (when the highlight is ahead), then disappears; accepting it marks the highlight handled. `SegmentEngine.openingWindowMs` (10 s), tested in `SegmentEngineTest.highlightIsOfferedAtTheStartOfTheVideoToo`.
- Unverified: playback that resumes after 10 s (history / restore) will not show the button at the start; it still shows near the point. The 10 s is my choice.
- Manual: open a video that has a SponsorBlock highlight (action "Ask" for Highlight): the button shows at the start for about 10 s; tapping it jumps there.

## Follow-up: red seek bar in Classic
- Correction: my earlier note that the seek bar "stays red" was wrong. `Accent.colors` is the scheme's primary, which in Classic is white/black.
- Change: `LocalSeekColor` (`theme/Accent.kt`), provided by `AppTheme` only for Classic (`ClassicColorSchemes.SeekRed`); `Accent.seek*` is used by the `Scrubber` and the watched bar on video cards. Other schemes unchanged (primary).
- Verified: `allTests` and Android compile; not seen on screen.
- Manual: Classic, play a video: the played part and the marker of the seek bar are red; cards' watched bar red; everything else neutral. Other schemes: seek bar in their accent as before.

## Follow-up: wide pages, Classic logo and badge, search bar, "/" shortcut, channel avatar
- Layout on a wide desktop window: Library is now left-aligned at the full width (it was a 900 dp column centred); list pages (History, Downloads, playlists) 760/900 -> 1040 dp, Channel 900 -> 1280 dp, Settings 720 -> 1000 dp (still centred). Judgement from your screenshots, not pixel-matched to YouTube; the same caps apply on Android tablets. Channel videos are still a list, not YouTube's 3-column grid.
- Classic: the Vela mark in the top bar and the unread dot on the bell are red (`LocalSeekColor`); the dot is still the error colour in the other schemes. The count is not shown (the app only has "unread or not").
- Desktop top bar: the search icon is a 360 dp search bar (`TopBarSearchField`); Enter or its icon opens the search page with the text. Android keeps the icon.
- Shortcut: `/` opens the search page from any page except Search. It only fires when the page holds the focus (not when a text field or button does), so typing a slash in a field is safe; on the video page it works regardless of the focused button. Does not focus the top-bar field: it opens the page.
- Video page: the channel avatar was placed at the top of the channel row, not centred against the name and Subscribe button (`ChannelRow`); now centred.
- Verified: `allTests` and Android compile; none of this was seen on screen or driven by key presses (no UI-test setup exists).
- Manual: Library/Channel/Settings on a wide window; Classic top bar logo + unread dot; type in the top-bar bar and press Enter; press `/` on Home and on a video page; open a video: the avatar is vertically centred.

## Follow-up: Classic start-up animation
- The sail-in splash painted itself in the scheme's primary (black/white in Classic); it now uses the Classic red with white foam (`LocalSeekColor`, `ExtraColors.onMedia`). Other schemes unchanged. Compile and tests only; not seen. Manual: Classic, restart the app: red sea, white ship.

## Follow-up: every tab has its own player
- Before: one libmpv player and one mini player shared by all tabs and windows (`SharedNowPlaying`). Now each tab owns a player (`TabPlayer`, `ui/nav/TabPlayer.kt`): created the first time the tab needs it (opening a video, Shorts, or restoring the last video), released when the tab or its window closes or the app quits. A video in one tab never shows in another tab's mini player, and another video can be opened there. Chosen: all tabs keep playing at once (like a browser; no auto-pause).
- Per-tab SponsorBlock: the addon follows one session, so each tab gets its own (`Modules.newSponsorBlock()`; segment cache shared). The video page, the player sheet and Shorts read the tab's player through `LocalTabPlayer`; Android never provides it and keeps `services.player`, unchanged.
- Switching tabs: the tab's app is rebuilt but its player and page stack live in the `TabModel`, so it comes back playing, with the video page open if its page stack is on it. Dragging a tab to another window keeps its player.
- Media keys (MPRIS) follow the tab in front; the screen stays awake while any tab plays (`TabPlayerBridge`). Only the first tab restores the last video after a restart and saves the snapshot (so tabs do not overwrite it).
- Verified: `allTests` (new: per-tab player lifecycle in `WorkspaceTest`, `PerTabSponsorBlockTest`), Android compile, `:desktopApp:run` starts. NOT verified: real playback (no libmpv on this machine), several mpv instances in one process, audio overlap, MPRIS switching.
- Not done: a "playing" marker on background tabs; freeing a tab's mpv when its video is closed (it is kept until the tab closes); each open player costs a decoder and an audio stream.
- Manual (needs libmpv): play a video in tab 1; open tab 2: no mini player there; play another: both keep playing; back to tab 1: its video page / position intact; close tab 1: its sound stops; media keys act on the tab in front; drag a playing tab to another window: keeps playing; quit: no stray audio.

## Follow-up: tab titles, and the video page after switching tabs
- Correction: my two earlier "comes back with the video page open" fixes tested `navigator.current is Route.Video`, but the open video page is the player sheet's state, not a route on the page stack, so they never applied. Now `Navigator.sheetWasExpanded` is saved when a tab's app leaves the screen (`AppShell` `onDispose`) and used when it comes back (`PlayerSheetState.adopt(..., expanded)`).
- Tab titles (`tabTitle(navigator, front)`): the video's title while its page is open, else the page: Home, Library, a channel's name, "Search: words", a Short's title, and so on. Window title: "<tab title> - Vela". A tab that is not showing uses its last saved state.
- Verified: `allTests`, Android compile, `:desktopApp:test`; not seen on screen.
- Manual: open a video page in tab 1, switch to tab 2 and back: the video page is open (not the mini player), the video kept playing; tab 1's label shows the video's title, tab 2's its own page.

## Follow-up: no refetch when switching tabs, and the desktop search bar
- Cause of the refetch: only the selected tab is composed, so a tab's shell (and the page caches it created: video page cache, search results, channel, explore, Shorts) was thrown away on every switch. They now live on the tab's `Navigator` (`videoPageCache`, `searchStore`, `channelStore`, `exploreStore`, `shortsFeed`), so coming back shows the loaded details, related videos, comments and results at once (cache ages out after 30 min as before). Scroll position of a switched-away page is still not kept.
- Search bar (desktop): the top-bar pill is now a button that opens the search page (which already has recent searches and as-you-type suggestions), instead of a bare text field that skipped them. "/" does the same. The page's field starts at the pill's width, right-aligned, and grows to the row's width in 220 ms (header 64 dp tall to match the top bar). The old `onSearchSubmit` parameter and the `nav.searchBar.field` / `.go` tags are gone.
- Verified: `allTests`, `:desktopApp:test`, Android and desktop compile. Not seen on screen: the grow animation is a width tween, not a shared-element transition, and the top bar itself is replaced by the search header without animation.
- Manual: (1) open a video, scroll to related, switch to another tab and back: related and comments are there without skeletons. (2) Click the top bar's search pill: the search page opens with the field focused, history listed; type: suggestions appear; Enter searches. (3) Search results, switch tabs and back: results show without reloading.

## Follow-up: black strip at the bottom after relaunching maximized
- Seen: a window restored as maximized had its content ~38 px short of the screen's bottom (black strip); minimise + restore fixed it, so the content is not re-laid out after the window opens maximized.
- Change (`DesktopWindow`): for a window that opens maximized, 400 ms after it is up the placement is set to Floating and, 120 ms later, back to Maximized, which is what minimise/restore did. Cost: a possible brief flash at start.
- Unverified: I cannot see the window here, so this is a workaround for a cause I have not measured (probably the content size taken before the maximize on GNOME/XWayland). If the strip stays, or the flash bothers, tell me.
- Manual: quit while maximized, relaunch: the window ends full height with no strip.

## Follow-up: vertical scroll no longer stops on horizontal rows
- Cause: `HScrollRow` and `HScrollableRow` turned a vertical wheel/touchpad delta into a sideways scroll while the pointer was over the row (and the row could still scroll), so the page stopped moving. They now react only when the gesture is mostly sideways (|dx| > |dy|); vertical goes to the page. Drag with the mouse and the round arrows are unchanged. This covers every row that uses these two components (home shelves, channel, customize, chips).
- Trade-off: a plain mouse wheel no longer scrolls a row sideways (use the arrows, drag, Shift+wheel if the system sends it as sideways, or a touchpad swipe).
- Verified: desktop compile only; not tried on a touchpad here. Rows that are not these two components (if any) are unchanged.
- Manual: scroll a long page vertically with the pointer over a shelf: it keeps going; swipe sideways over the shelf: it moves; arrows still work.
