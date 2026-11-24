# 138 Feed overhaul, task 6: Explore pills with icons and real lists; signed-out Home

## 1. Summary
Measuring found that Vela's "Trending" was only live streams (YouTube removed Trending; NewPipe's default kiosk is "live"). Explore now has no
Trending pill: Gaming, Podcasts and Live use their real lists (NewPipe kiosks), the rest are searches, every pill has a fixed icon, Live is last
and Explore opens on Gaming. Signed-out Home no longer shows a stand-in feed: it keeps the shelves that work without an account and adds a
sign-in card (Sign in / Browse Explore).

## 2. Verification
| check | result | verified-by |
|---|---|---|
| Live, through Vela's own extractor: `kiosk("live")` 15 videos all flagged live, page 2 has 16; `kiosk("trending_gaming")` 50; `kiosk("trending_podcasts_episodes")` 100; all with thumbnails and channel names | pass | running (live, real YouTube) |
| `:desktopApp:test` (171 tests incl. new `ExplorePillsUiTest` (2), the signed-out Home test, and the screen-states test now covering Explore instead of signed-out Home) | pass | running (desktop Compose UI tests) |
| `./gradlew allTests` incl. `WrappersForwardEverythingTest` (every wrapper forwards `kiosk`) and the icon-set test | pass | running (offline) |
| `:androidApp:compileDebugKotlin` | pass | compile-only |
| How the pills and icons look on a phone | not checked | needs the owner |

## 3. Files created/changed
- `shared/.../extractor/Extractor.kt`: `kiosk(id, continuation)`; `trending()` documented as live-only. Forwarded by `StubExtractor`, `TimeoutExtractor`, `Captcha`, `PacedExtractor`, `FallbackChain` (account use: never), `AuthenticatedYtDlpExtractor`; implemented in `NewPipeExtractor`.
- `shared/.../ui/screens/ExploreScreen.kt`: `ExploreTopic` list with icon and optional kiosk; no Trending; opens on Gaming.
- `shared/.../ui/components/ExpressiveChip.kt` (optional leading icon), `ui/theme/AppIcons.kt`, `MaterialOutlined.kt` (ten topic icons).
- `shared/.../ui/screens/HomeScreen.kt`, `HomeShelvesUi.kt`: signed-out Home (no network call; `SignInCard`).
- Tests: `ExplorePillsUiTest.kt` (new), `HomeSourceUiTest.kt`, `ScreenStatesTest.kt`, `UiHarness.kt`, `WrappersForwardEverythingTest.kt`; live: `LiveExploreLiveTest.kt`, `LiveAnonymousHomeTest.kt` (counts only).
- `docs/plans/feed-overhaul.md` section 9.

## 4. Decisions the spec didn't dictate
- Owner decisions: no Trending pill; Live last and not the default; signed-out Home = shelves plus sign-in card.
- Music, News, Sports, Movies, Learning, Cooking, Tech stay searches (the Music and Movies kiosks answer HTTP 403).
- Gaming and Podcasts are one page each (the kiosks have no further pages); Live pages onward.
- The card says Home's content comes from the YouTube account, offers "Sign in" (Accounts page) and "Browse Explore".

## 5. Known issues / unverified assumptions
- Also affected, not changed here: `ShortsScreen` builds its "trending channels" from `trending()`, so those are live-stream channels. Needs its own decision.
- The Gaming and Podcasts lists are YouTube's "trending in that category" lists for the region NewPipe is set to (Settings > Region).
- Earlier tasks (3-5) ran `allTests` but only the Home UI tests of the desktop module; the full `:desktopApp:test` (171 tests) was first run now and passes.
- Icons are Material outlines (sports_esports, podcasts, music_note, newspaper, sports_soccer, movie, school, restaurant, memory, sensors); unseen on a device.

## 6. Manual test checklist
1. Open Explore: pills Gaming, Podcasts, Music, News, Sports, Movies, Learning, Cooking, Tech, Live, each with an icon; Gaming is selected and shows videos. No Trending pill.
2. Tap Live last: real live streams with LIVE badges; scroll down for more. Tap Podcasts: episodes.
3. Tap Music: results of a search for Music, as before.
4. Sign out (or use a build with no account): Home shows Continue watching / New from your subscriptions and the other shelves, then the card "Your feed comes from your YouTube account" with Sign in and Browse Explore. No videos list and no "Trending" note.
5. Tap "Sign in": the Accounts page opens. Tap "Browse Explore": Explore opens.
