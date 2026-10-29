# 37 – Shorts feed, comments sheet, Home paging, watched bar

## 1. Summary
New Shorts tab (an endless full-screen pager of Shorts) next to Home; it, the Shorts filter in search and the Shorts option in the tab list all disappear/disable when Shorts are set to Hide. Comments are a bottom sheet in the app's look with replies as threads (a palette-gradient line down their side). Cards in Home and elsewhere show views and how long ago when the source says so (the account's Home feed is now parsed for both). The watched bar on thumbnails uses the palette gradient. Home keeps loading after the account's recommended feed ends (it carries on with Trending). The subscriptions page no longer refetches every channel on every visit.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| Live: channel Shorts tabs give ~48 Shorts per page in 0.3-0.7 s each (search for "#shorts" gives ordinary videos, so it cannot be the source) | measured | network (temporary probes, removed) |
| Phone: Shorts tab in the bar, a Short plays full screen with channel picture, name, title, views, mute/share/open; swiping up plays the next | seen | phone screenshots |
| Comments sheet and threads, search Shorts filter and hiding with Shorts off, views and age in cards, watched bar gradient, Home loading past recommended, subscriptions not refetching | not exercised | compile-only |

## 3. Files changed
`Settings.kt` (`shortsEnabled()`), `LookSettings.kt` (NavTab.SHORTS, default tabs), `NavConfig.kt`, `Navigator.kt` (Route.Shorts), `AppShell.kt`, `Components.kt`, `CustomizeScreens.kt`, `ShortsScreen.kt` (new), `SearchFilters.kt`, `SearchScreen.kt`, `CommentsSheet.kt` (new), `VideoScreen.kt`, `HomeScreen.kt`, `FeedService.kt`, `SubscriptionsScreen.kt`, `Models.kt` (`publishedText`), `ItemMapping.kt`, `InnertubeParsers.kt` (views, age, avatars)

## 4. Decisions
- The Shorts feed is built from the Shorts tabs of channels you subscribe to plus channels seen on Home (and Trending as a last resort), a few at a time, mixed. Without subscriptions or a loaded Home it says so.
- Leaving the Shorts page stops its playback (nothing else shows it).
- Shorts is on unless the global Shorts setting is Hide; Separate tab counts as on.
- An existing saved tab layout does not get Shorts added by itself (new installs and the default do); turn it on in Customize > Tabs.

## 5. Known issues
- "Open on" still lists Shorts even when they are hidden.
- Collaboration videos from the account's feed may lack a channel picture.
- The Shorts feed does not use your watch history or account recommendations.

## 6. Manual test checklist
1. Customize > Tabs: switch Shorts on; open it, swipe through a few; tap to pause; mute; Open as a video.
2. Settings > Shorts > Hide: the tab leaves the bar and its switch is disabled; search has no Shorts chip. Set it back to Show.
3. Search something: tap the Shorts chip to show only Shorts.
4. Video page > Comments: sheet opens; open a comment's replies: a gradient line runs down the thread.
5. Home: scroll to the end of the recommendations; more videos load by themselves.
