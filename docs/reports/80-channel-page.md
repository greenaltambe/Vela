# 80 Channel page, Material 3 Expressive (phase 1 of 2)

## 1. Summary
The channel page is one scrolling list: header (banner, shaped picture, name, Subscribe / bell, auto-download, description teaser), a pinned `PrimaryScrollableTabRow`, then the open tab's rows. The hand-built collapsing header (manual offset, `draggable`, rebuilt nested-scroll) and the `HorizontalPager` are gone, so the header cannot overlap the tabs or videos. The shell's plain top bar is replaced by a small bar (back, name that fades in once the header has scrolled away, menu: About / Share / Open in browser). Page colour is `background` throughout. Posts and comments are phase 2 (task 81).

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `ChannelTabsNativeUiTest` (all tags kept, tabs pinned after scroll, load more with expired token) | pass | running |
| New `headerTabsAndVideosNeverOverlapWhileScrolling` (tabs under bar, list under bar over 4 scrolls) | pass | running |
| Desktop renders 360 x 800 and 1280 x 800 (scratch screenshots, not kept) | looked at | running |
| Full `allTests`, `:desktopApp:test`, `:androidApp:installDebug` | pass, 0 failures; installed on the phone | running |
| Real channel on the phone | not looked at | not verified |

## 3. Files created/changed
- `ui/screens/ChannelScreen.kt`: new structure (`ChannelTopBar`, `ChannelHeader`, `loadTab`, `channelTabItems`), `ShortCard` and playlist badge on theme colours, shelf posts no longer a fixed 200 dp.
- `ui/screens/ChannelStore.kt`: remembers the list's scroll position.
- `ui/AppShell.kt`: Channel added to the routes that draw their own bar.
- `desktopApp/src/test/.../ChannelTabsNativeUiTest.kt`: overlap test.

## 4. Decisions the spec didn't dictate
- One list with a sticky tab row instead of a pager: nested vertical scrolling inside a pager is what made the old header lag. Swiping sideways still changes tab (drag detector on the list; shelves consume their own drags). Each tab starts at its top when chosen.
- Phone video cards keep the full-width thumbnail like Home (consistency); wide windows use the horizontal row.
- Paging loads by itself when the footer scrolls into view, except right after a "Refreshed" restart (avoids a loop on an expired token); the Load more button stays as the manual way.
- Content is capped at 900 dp and centred on wide windows.

## 5. Known issues / unverified assumptions
- Not looked at on the phone with a real channel, nor in dark mode.
- The Shorts tab is still two-per-row chunks, not `ShortsGrid` (a lazy grid cannot sit inside the single list).
- Post cards and post comments are unchanged until task 81.

## 6. Manual test checklist
1. Open a channel from a video and from Search: header, tabs, videos with no overlap while scrolling.
2. Scroll down: the name appears in the bar; tabs stay pinned; scroll up again.
3. Switch tabs by tap and by swiping sideways; Shorts, Playlists, Community look tidy.
4. Subscribe: the bell slides in; menu (three dots): About, Share, Open in browser.
5. Reach the end of Videos: more load by themselves.
