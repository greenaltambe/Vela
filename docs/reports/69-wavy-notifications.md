# 69 Wavy progress default, notifications (phase 1)

## 1. Summary
The seek bar has a new "Wavy (Material)" style, drawn in the existing scrubber (so buffered range, SponsorBlock markers and chapter gaps stay), and it is the default; the old wave is "Wave (classic)". There is now a local notification feed: a bell in the top bar (with an unread dot) opens a Notifications page; new uploads of subscribed channels, finished / failed downloads and health alerts are recorded. A bell button next to Subscribe (video page, channel page, search channel card) sets All / Personalized / None per channel on this device. Not done in this task: the YouTube account inbox (phase 1b), and the video page, player controls and comments redesign (phases 2 and 3).

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests`, `:desktopApp:test`, `:androidApp:installDebug` | pass, 0 failures (new: `NotificationRepositoryTest`, `NotificationsUiTest`, migration test for version 8; `SettingsPagesTest` summary text updated) | running |
| Phone: bell in the top bar, Notifications page (empty state), bell next to Subscribed, wavy mini-player bar | seen | running on device |
| Phone: the Wavy style in the full-screen seek bar, scrubbing with chapters / SponsorBlock markers | not looked at | not verified |
| Phone: upload rows and system notification (needs a new video from a subscribed channel), the 3-hour background worker | not triggered | not verified (worker compile-only) |
| Phone: bell menu choices, light mode, wide window | not tried | not verified |

## 3. Files created/changed
- Progress: `core/settings/LookSettings.kt` (`EXPRESSIVE` first in the list, default), `ui/components/Scrubber.kt` (wave, gap, pill handle), `ui/theme/Appearance.kt`, `ui/screens/CustomizeScreens.kt` (preview animates), `SettingsPages.kt` (summary).
- Data: `7.sqm` (migration: `Subscription.notifyLevel`, `NotificationItem`), `NotificationItem.sq`, `Subscription.sq`, `data/NotificationRepository.kt` (+ `NotifyLevel`, `NotificationKind`), `data/RecordingDownloadNotifier.kt`, `LibraryRepositories.kt` (notify level), `feed/FeedService.kt` (records new uploads, at most 20 per refresh, first fill seeds silently), `AppServices.kt` (`notifications`, `notificationsRevision`, `onNewUploads`).
- UI: `ui/screens/NotificationsScreen.kt`, `ui/screens/ChannelBell.kt`, `VelaTopBar` (bell + unread dot), `Route.Notifications`, `AppShell.kt`, icons (`notificationsActive`, `notificationsOff`), settings page "Notifications" (`notify.background`, `notify.system`, `notify.downloads`).
- Android: `playback/UploadNotifier.kt` (channel "New videos"), `playback/FeedRefreshWorker.kt` (every 3 h), wiring in `MainActivity`, `PlaybackRuntime`, `AndroidDownloads`; desktop `main.kt` records download results.

## 4. Decisions the spec didn't dictate
- PERSONALIZED records feed rows without a system notification (the app has no personalization signal); ALL also raises a system notification; NONE records nothing.
- The desktop volume slider keeps its fixed look (the plan said the desktop overlay should read the style; that slider is a volume control, not the seek bar).
- Re-subscribing resets a channel's level to Personalized (the table replaces the row; SQLDelight's dialect here has no upsert).
- Marking a row read when opened; read rows get a menu with Remove; "Mark all read" is the check icon.

## 5. Known issues / unverified assumptions
- The bell level is local only; it does not change YouTube's own notifications.
- Upload detection uses the same anonymous recent-uploads fetch as the Subscriptions feed; a first refresh after subscribing only fills the cache.
- The notification page has no pull-to-refresh yet.

## 6. Manual test checklist
1. Settings > Customize > Progress bar: "Wavy (Material)" is first and selected; play a video, scrub, check buffered / markers / chapters.
2. Tap the bell next to search: Notifications page; subscribe to a channel, set its bell to All, wait for a new upload (or Subscriptions pull-to-refresh after it posts): row appears, system notification shows, dot on the bell.
3. Download a short video: a "Download finished" row appears.
4. Open a video of a channel you subscribe to: bell menu All / Personalized / None; also on the channel page and in Search for a channel name.
5. Settings > Notifications: toggles for background checks, system notifications, download results.
