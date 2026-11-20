# 108 Home polish, hide-on-scroll, sync from account

## 1. Summary
(1) The "N hidden by your filters or blocklist" text on Home is now a tonal pill with an eye icon. (2) Two new settings hide Home's filter chips and the bottom bar while scrolling down, and bring them back on scroll up, with spring/slide/fade animation. (3) The Channels page (Import / Manage) got a "Sync from your account" card with an expressive button.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `allTests`, `:desktopApp:test` (incl. LookSettingsTest with the new keys, customize coverage test) | pass | verified-by-running |
| Desktop + Android compile | pass | compile-only |
| Animations, scroll behaviour, mini-player following the bar, sync result | not run | not verified |

## 3. Files created/changed
- Created: `ui/nav/ScrollChrome.kt`, `ui/screens/SyncFromAccountCard.kt`, this report
- Changed: `LookSettings.kt`, `SettingCopy.kt`, `NavConfig.kt`, `AppShell.kt`, `HomeScreen.kt`, `CustomizeScreens.kt`, `SubscriptionsScreen.kt`, `LookSettingsTest.kt`

## 4. Decisions the spec didn't dictate
- Settings: Customize > Home > "Hide the filter chips while scrolling" (`look.home.hideChipsOnScroll`); Customize hub > Layout > "Hide the bottom bar while scrolling" (`look.nav.hideBarOnScroll`). Both default off, both part of a Look.
- One shared scroll tracker around the page: hides after ~48 px of downward scrolling, shows after ~24 px upward, and always on a new page. Horizontal carousels are ignored.
- The hidden-count pill hides together with the chips; the feed note (errors etc.) stays.
- Bottom bar: wide-window rail is unaffected. With a video minimised, the mini player sits above the bar and should drop down as the bar slides away (position is observable state).
- Sync card: signed in = "Sync now" (adds missing channels, never removes, same call as Accounts > Sync); signed out = same card with "Sign in to sync" opening Accounts. It sits at the top of the Channels page, which the Subscriptions empty state's "Import or manage" also opens.

## 5. Known issues / unverified assumptions
- Not run on a device: the feel of the animations, and whether the shrinking list causes any scroll jump on Home, are untested.
- Hide-on-scroll applies to every page that shows the bar, not only Home.

## 6. Manual test checklist
1. Home: the "N hidden by your filters or blocklist" pill looks tidy under the chips (use a blocklisted channel to trigger it).
2. Customize > Home: turn on "Hide the filter chips while scrolling". Scroll Home down: chips and pill slide away; scroll up: they return.
3. Customize hub > Layout: turn on "Hide the bottom bar while scrolling". Scroll Home/Subscriptions/Library down: bar slides away; up: returns. Switching tabs always shows it.
4. Play a video, minimise it, scroll: the mini player follows the bar down and back up without jumping.
5. Subscriptions > Import or manage: the Sync card is at the top. Signed in: press Sync now, watch the loader, then the result line and updated list. Signed out: "Sign in to sync" opens Accounts.
6. Turn both settings off: everything stays put.

## Follow-up (after 109)
- The "N hidden by your filters or blocklist" pill is removed from Home. After a pull-to-refresh (or the desktop refresh button / Ctrl+R) that finishes, the app's snackbar toast says "N hidden by your filters or blocklist" once (only if N > 0) and disappears by itself after about 5 s.
- Home's filter chips (All, Unwatched, Under 10 min ...) auto-hide on scroll by default now (`look.home.hideChipsOnScroll` default true; turn it off in Customize > Home). Existing installs that never touched the setting get the new default.
- Verified: `allTests`, desktop and Android compile pass; not run on a device. Test: refresh Home with a blocklisted channel in the feed and watch for the toast; scroll Home down and up and watch the chips.
