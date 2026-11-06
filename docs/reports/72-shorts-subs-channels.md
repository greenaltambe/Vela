# 72 Player buttons, Downloads card, Shorts, Subscriptions, All channels

## 1. Summary
Player buttons are now Material 3 Expressive icon buttons (they morph from a circle to a rounded square when pressed, on the scrim disc), except the two buttons with a long-press action, which keep the custom button. The Downloads storage card is rebuilt: used space as a large number, a wavy bar, the folder path, and chips for Settings / Automatic / Folder. The Shorts player has expressive toggle buttons on its action rail (filled when chosen), a scrim channel pill, a wavy progress bar, a scrim back button and a scalloped play marker. Subscriptions has Notifications and "N channels" chips above the channel row; its error and empty states are tonal and expressive. All channels is one segmented list with a bell and menu on each row, search, an Import chip, and a "notifications for every channel" card (All / Personalized / None for all at once). "…more" on the video page now comes after the description line.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests`, `:desktopApp:test`, `:androidApp:installDebug` | pass, 0 failures | running |
| Phone: Downloads card, Subscriptions page, All channels (before the duplicate-bar fix), Shorts player | seen | running on device |
| Phone: All channels after removing the duplicate app bar, row bells, bulk chips; player buttons pressed; Shorts action rail pressed; "…more" position | not looked at | not verified |

## 3. Files created/changed
- `ui/player/PlayerControls.kt` (`PlayerIconButton`), `ui/screens/DownloadsScreen.kt` (storage card), `ui/screens/ShortsScreen.kt`, `ui/screens/SubscriptionsScreen.kt` (header chips, `AllChannelsScreen`), `ui/screens/ChannelBell.kt` (`connected`, `onChanged`), `ui/components/States.kt` (`ErrorRow`, `EmptyState` action), `ui/AppShell.kt` (no shell bar on All channels), `ui/screens/VideoScreen.kt` ("…more" last), `Subscription.sq` + `LibraryRepositories.kt` (`setAllNotifyLevels`).

## 4. Decisions the spec didn't dictate
- The previous / next lean on press (`pressShift`) and the halo are gone from buttons using Material's icon button; the haptic stays.
- "Every channel" chips show a check only when all channels share that level.
- Shorts has no Subscribe button (the channel pill opens the channel).

## 5. Known issues / unverified assumptions
- Shorts pager gestures, comments panel and buffering were not changed; only the overlay look.
- Imported channels start at Personalized.

## 6. Manual test checklist
1. Player: press play, previous, next, captions, settings: shape morphs; long-press ones still work.
2. Downloads: new card; Settings, Automatic, Folder chips work.
3. Shorts: like / dislike / comments / mute / share fill when chosen; progress bar is wavy; back button (when opened from a card).
4. Subscriptions: Notifications chip opens the feed; "N channels" opens All channels: bell per row, menu Unsubscribe, bulk chips, search, Import.
5. Video page: "…more" under the description line.
