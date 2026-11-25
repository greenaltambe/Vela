# 156 Stage C: YouTube's groups on Home, and the switch

## 1. Summary
Signed in, YouTube's groups now show on Home and in chips as their own titled rows, where YouTube had them; their videos are no longer mixed into the feed. New switch "YouTube's groups on Home" (Settings > Home, on by default, synced and backed up) hides every group except rows of Shorts, which follow the Shorts setting. Vela's own Shorts shelf now shows only when signed out.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:compileDebugKotlin` | pass | run (the Android app compile is compile-only) |
| New: `HomeListTest` (6), 8 more in `HomeLayoutTest`, a sync-and-backup test of the switch, a reads test | pass | run |
| New `HomeGroupsUiTest` (9): order on screen, switch without reload, Shorts setting, no app Shorts shelf, chips, a chip of hidden rows, a page of rows only, short rows, opening a Short | pass | run (desktop) |
| Live, signed in, `gl=IN`, three pages of All and six chips: no video in two groups; every group placed inside the list; no shelf on a group's place; switch off leaves no non-Shorts row | pass | run (live, read-only) |
| Same run, seen | All: two Shorts rows at places 1 and 4. Gaming chip: four rows of 10 to 12 | run (live) |
| Screen layout | one rendered desktop image at phone width | run, still image |
| The app on a phone or desktop with a real account | NOT RUN | needs the owner |

## 3. Files created/changed
- New: `shared/.../home/HomeList.kt`, `HomeListTest.kt`, `desktopApp/.../HomeGroupsUiTest.kt`, `innertube-auth/.../liveTest/LiveHomeListTest.kt`.
- `shared`: `HomeLayout.kt` (which rows show, where), `HomeScreen.kt`, `HomeFeedStore.kt`, `Accounts.kt` (`HomePage.groups`), `Settings.kt`, `SettingCopy.kt`, `SettingsPages.kt`, `HomeShelves.kt` (Shorts shelf description); `HomeLayoutTest.kt`, `BackupTest.kt`.
- `innertube-auth`: `InnertubeAccountReads.kt` uses the new parser and notes odd responses; `HomeReadsTest.kt`.
- Wiring: `PlaybackRuntime.kt`, desktop `main.kt`. Plan status, this report.

## 4. Decisions the spec didn't dictate
- A chip that holds only hidden rows stays in the chip row and says that your settings hide them.
- A group drops videos already in the feed or in an earlier group, judged when its page arrives, so rows do not shrink later.
- A group's place is the video it followed; if filters remove it, the nearest earlier one.
- Rows have no "See all".
- "Not interested" and "Hide this channel" work on cards inside a row, as in other shelves.
- Odd Home responses (unknown items, flat fallback) appear in Diagnostics under "account feed".

## 5. Known issues / unverified assumptions
- A later page may repeat in the feed a video that an earlier group already showed.
- If the loaded list is shorter than a shelf's place, shelf and group can meet at the end until more loads.
- Rows may appear a moment after the first videos on a first load (they are filtered off the main thread); not observed.
- Shorts rows have no per-item menu, and name no channel, so channel blocking cannot clear them.
- Opening a Short from a row continues through that row, then the old channel mix. YouTube's own continuation comes in stages E and F; the wait for the title before playback is removed in stage D.
- Signed in, switching the "Shorts" shelf in Customize now has no effect.

## 6. Manual test checklist
1. Open Home signed in: Shorts rows appear among the videos, with no second app Shorts shelf.
2. Open a topic chip such as Gaming: the game rows have their own titles.
3. Settings > Home: switch "YouTube's groups on Home" off. The game rows go, Shorts rows stay, and no row's videos turn up loose. Switch it on again.
4. Set Shorts to Hide: Shorts rows go, other rows stay.
5. Scroll far enough for several pages: no row appears twice, scrolling stays smooth.
6. Turn on the Filter (Unwatched): rows go; turn it off: they return.
7. Tap a Short in a row, swipe up: the next one is the next in that row.
8. Sync to your other device and check the switch arrives.
9. When "Most hyped" appears: it should be a titled row, and should vanish with the switch off. Tell me if not, with a Diagnostics line.
