# 134 Feed overhaul, task 3: signed-in Home pages the account feed and never shows Trending

## 1. Summary
Signed-in Home now loads the account feed with `AccountReads.home` and pages it by its own continuation, so the list keeps loading as you
scroll and ends where YouTube's list ends. Trending is no longer appended to it. When the feed fails, Home says so with Retry and a
"Browse Explore" button; a new account with no recommendations gets a message and the same way out. Signed-out Home (and the old Home
source = Trending setting) still shows Trending, now with a one-line label. The chip row is not part of this task (task 4).

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `HomeSourceUiTest` (8, five new or rewritten): feed failure shows the message, Retry area and Explore link and no Trending; empty account message; paging asks `null` then `next-1` and stops, no Trending after the last page; signed-out label; existing tests unchanged | pass | running (desktop Compose UI tests with a fake `AccountReads`) |
| `./gradlew allTests` | pass | running (offline) |
| `:androidApp:compileDebugKotlin` | pass | compile-only (shared code, not run on a device) |
| Real account, endless scroll on a phone | not checked | needs the owner (checklist below) |

## 3. Files created/changed
- `shared/.../ui/screens/HomeScreen.kt`: `load()` split into the account-feed path (paged, no Trending) and the Trending path (signed out or setting);
  `canMore` and `onEndReached` now follow the continuation only; `feedFailed` flag and the "Browse Explore" button.
- `desktopApp/src/test/.../HomeSourceUiTest.kt`: fake `Reads.home` with pages; the old "falls back to Trending with a note" test replaced by the new ones.
- `docs/reports/134-feed-home-page-state.md`.

## 4. Decisions the spec didn't dictate
- Failure messages: "Your feed could not be loaded right now." (Retry plus "Browse Explore"), "Your account has no recommendations yet." (button "Browse Explore").
  A refresh that fails with a list already showing keeps the list and puts "Could not refresh your feed. Showing what was loaded before." in the note strip.
  A later page that fails keeps the list; the existing "Load more" button retries.
- A first page with no videos but a continuation is followed up to three times before the feed is called empty.
- Signed-out label: "Trending now. Sign in to see your own feed."; with the Trending setting while signed in: "Trending now (your Home source setting)."
- "Browse Explore" uses `selectTab(Route.Explore)`.

## 5. Known issues / unverified assumptions
- The old setting "Home source" still exists (removed in task 5); with it set to Trending, signed-in Home shows Trending and says so.
- The cached Home (30 minutes) from before this change is shown once on the first visit after updating; pull to refresh to start paging.
- The note strip text is not yet translated or in the settings copy (the app has no other translations).
- Real-device scrolling behaviour (how early the next page loads on a phone) is unmeasured.

## 6. Manual test checklist
1. Signed in, open Home and scroll far down (about 10 screens). More videos should keep loading and no live streams or Trending videos should appear at the end.
2. Pull to refresh; the list reloads (the top rows will mostly stay, which is YouTube's own ranking).
3. Turn on airplane mode, pull to refresh with a list showing: the list stays and a note says it could not refresh.
4. Sign out of the account: Home shows Trending with "Trending now. Sign in to see your own feed."
5. With a signed-in account that fails to load the feed (airplane mode before opening the app): the error, Retry, and "Browse Explore" appear; Explore opens.
