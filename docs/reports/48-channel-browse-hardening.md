# Task 48: Channel Browse Hardening (Task 47 Review Findings)

**Date**: 2026-11-01  
**Status**: Completed  
**Branch**: main  

---

## 1. Summary

Hardened the native YouTube channel browse implementation following findings from task 47 review and addressing the user request for uniform fixed height posts in channel home shelves. Key enhancements include:
- **Error classification & Tier memory protection**: HTTP 400/404 on continuation tokens now maps to `ExtractorError.TokenExpired` (reason `TOKEN_EXPIRED`), preventing tier health memory poisoning. Operations in `AccountOp.CHANNELS` do not record routes into playback memory, ensuring channel continuation failures never degrade video stream playback.
- **Tier token isolation**: Continuation tokens are strictly prefixed with originating tier tags (`n1:` for native browse, `np:` for NewPipe). Incoming foreign tokens immediately yield `ExtractorError.TokenNotMine` and stop fallback forwarding.
- **Continuation retry replacement**: Removed the internal page 1 retry loop inside `ChannelBrowse.tab`. The UI (`ChannelScreen` / `ChannelStore`) handles token expiration by re-fetching page 1 with the currently selected sort/filter, replacing the items list (no duplicates), resetting scroll to top, and rendering a dismissible "Refreshed" indicator banner.
- **Empty parse detection & logging**: Genuinely empty tabs (with recognized empty state messages or 0 uploads) return `Success(empty)`. Responses with unrecognized layouts or broken trees return `ExtractorError.ParseFailure` with top-level renderer keys logged to Diagnostics (rate-limited to 1 note per renderer type per hour).
- **NewPipe fallback correctness**: Replaced global search delegation in `NewPipeExtractor.channelSearch` with typed `TierUnavailable` (informing the user that in-channel search is unavailable on fallback). Unsupported tabs (Courses, Posts) return `TierUnavailable` instead of empty success. About metadata is tagged `isPartial = true` with a UI badge `Partial info (fallback mode)`.
- **Dynamic tab rendering**: Tabs render strictly what the channel returns in server order. Channels lacking Shorts, Playlists, or Courses no longer show synthetic tabs. `ChannelTabKind.UNKNOWN` tabs render their title and browse with server parameters.
- **Shelf post height constraint**: Shelf post cards in horizontal scroll rows on channel home page render with a uniform fixed height (`Box(width = 280.dp, height = 200.dp)`), post text capped at `maxLines = 3, overflow = TextOverflow.Ellipsis`, and compact attachments.
- **Fixture trimming & leak audit**: Trimmed channel JSON fixtures from ~12 MB down to 1.1 MB by stripping `responseContext`, `frameworkUpdates`, `topbar`, `trackingParams`, and excess items while preserving test schemas. Added a strict fixture leak audit test ensuring no visitorData, account cookies, or emails exist in fixtures.
- **Android SelfTestActivity**: Added channel flows (`channelLanding`, Videos sorted Oldest, paging 3 pages, Shorts tab) to `SelfTestActivity.kt`.

---

## 2. Fault Matrix

| Fault Condition | Trigger / HTTP Code | Classification | Fallback Behavior | User-Visible Result |
| :--- | :--- | :--- | :--- | :--- |
| **Rate Limited (429)** | HTTP 429 from YouTube browse API | `ExtractorError.RateLimited` | Forwarded to NewPipe tier | NewPipe serves tab if available; otherwise displays "Rate limited" error banner with retry button. |
| **Blocked (403)** | HTTP 403 / bot detection | `ExtractorError.Blocked` | Forwarded to NewPipe tier | Falls back to NewPipe tier. Tier memory tracks tier health. |
| **Token Expired (400 on token)** | HTTP 400/404 on continuation browse | `ExtractorError.TokenExpired` | Stopped (no fallback to other tiers; tier memory NOT poisoned) | Feed reloads from page 1 using active sort filter, replaces list, resets scroll to top, displays "Refreshed" notice. |
| **Foreign Token** | Continuation token without tier prefix (`np:` sent to native, `n1:` sent to NewPipe) | `ExtractorError.TokenNotMine` | Stopped immediately; never forwarded across tiers | Feed refreshes from page 1 without duplicate appends. |
| **Server Error (5xx)** | HTTP 500..599 from YouTube | `ExtractorError.TierUnavailable` | Forwarded to NewPipe tier | NewPipe tier serves request; if all tiers fail, displays "Service unavailable" error row with retry button. |
| **Offline** | Network disconnect / DNS failure | `ExtractorError.Offline` | Tier attempts logged; stops on network loss | Offline error card with "Check connection" and retry button. |
| **Timeout** | Socket timeout / >30s read | `ExtractorError.Timeout` | Forwarded to subsequent tier | Shows loading timeout indicator with retry option. |
| **Truncated JSON** | Partial response / invalid JSON | `ExtractorError.ParseFailure` | Forwarded to NewPipe tier | Falls back to NewPipe; logs syntax error to Diagnostics. |
| **Changed JSON Shape** | Empty items/shelves without recognized empty-state text | `ExtractorError.ParseFailure` | Forwarded to NewPipe tier | Falls back to NewPipe; logs top-level renderer keys to Diagnostics (rate-limited 1/hr). |

---

## 3. Verification Table

| Test / Verification Item | Target | Result | Evidence |
| :--- | :--- | :--- | :--- |
| Channel token failure does not poison playback streams tier memory | `FallbackChainChannelTierTest.kt` | **VERIFIED** | Failed on old code with `AssertionError`; passes with `TokenExpired` and `op != AccountOp.CHANNELS`. |
| TokenNotMine stops fallback immediately without forwarding | `FallbackChainChannelTierTest.kt` | **VERIFIED** | Failed on old code (forwarded to NewPipe); passes immediately. |
| Foreign continuation token returns `TokenNotMine` without network call | `ChannelBrowseTest.kt` | **VERIFIED** | Tested via `continuationWithForeignTokenReturnsTokenNotMine`. |
| HTTP 400 continuation returns `TokenExpired` without internal retry | `ChannelBrowseTest.kt` | **VERIFIED** | Tested via `http400ContinuationReturnsTokenExpiredWithoutInternalRetry`. |
| Empty parse on first page without recognized empty-state returns `ParseFailure` | `ChannelBrowseTest.kt` | **VERIFIED** | Tested via `emptyParseWithoutRecognisedEmptyStateReturnsParseFailure` (verifying top-level renderer keys). |
| Genuinely empty channel with recognized message returns `Success(empty)` | `ChannelBrowseTest.kt` | **VERIFIED** | Tested via `emptyParseWithRecognisedEmptyStateReturnsSuccessEmpty`. |
| Unknown renderer reporting is rate-limited to 1 note per renderer type per hour | `ChannelBrowseTest.kt` | **VERIFIED** | Tested via `unknownRendererReportingIsRateLimitedToOnePerHour`. |
| Refresh parameter clears tab cache | `ChannelBrowseTest.kt` | **VERIFIED** | Tested via `refreshParamBypassesAndClearsTabCache`. |
| Fixture leak audit verifies no visitorData, account cookies, or emails | `CredentialLeakAuditTest.kt` | **VERIFIED** | Failed on untrimmed fixtures; passes cleanly on trimmed fixtures. |
| Channel tabs display only server-returned tabs (no synthetic Shorts/Courses) | `ChannelTabsNativeUiTest.kt` | **VERIFIED** | `nativeChannelTabsRenderInTabStripAndSwipePager` verifies no synthetic Shorts when omitted. |
| Unknown tab kind renders title and handles interaction | `ChannelTabsNativeUiTest.kt` | **VERIFIED** | `channelWithUnknownTabRendersIt` verifies `channel.tab.unknown` and "Merch" title. |
| Continuation expiration refreshes page 1 and shows "Refreshed" banner | `ChannelTabsNativeUiTest.kt` | **VERIFIED** | `continuationExpiredRefreshesPage1AndShowsRefreshedNotice` verifies banner and page 1 re-fetch. |
| Shelf posts have fixed uniform height (200dp) with ellipsis | `ChannelTabsNativeUiTest.kt` | **VERIFIED** | `shelfPostHasFixedHeightInHorizontalRow` passes with 280dp x 200dp box and text truncation. |
| Android debug compilation with channel self-test flows | `androidApp:compileDebugKotlin` | **VERIFIED** | `./gradlew :androidApp:compileDebugKotlin` passed with code 0. |
| Android physical on-device self-test execution | Physical Android device via `adb` | **NOT VERIFIED** | No physical Android device connected in sandbox terminal; automated compilation and unit/UI tests verified. |

---

## 4. Files Created / Modified

- `shared/src/commonMain/kotlin/com/greenaltambe/vela/extractor/ExtractorResult.kt` — Added `TOKEN_EXPIRED`, `TOKEN_NOT_MINE` to `FailureReason`, and `TokenExpired`, `TokenNotMine` to `ExtractorError`.
- `shared/src/commonMain/kotlin/com/greenaltambe/vela/extractor/chain/FallbackChain.kt` — Tier route memory guarded for `op != AccountOp.CHANNELS`; `TokenNotMine` stops fallback chain immediately.
- `shared/src/commonMain/kotlin/com/greenaltambe/vela/ui/screens/ChannelScreen.kt` — Area 6 server-only tabs, Area 3 continuation token refresh handling + "Refreshed" banner, uniform fixed 200dp shelf post card, Area 5 partial about badge.
- `shared/src/commonMain/kotlin/com/greenaltambe/vela/ui/screens/ChannelStore.kt` — Added `refreshedNotice` state flag to `ChannelTabFeed`.
- `innertube-auth/src/main/kotlin/com/greenaltambe/vela/innertube/ChannelBrowse.kt` — Native token wrapping (`n1:`), 400/404 mapping to `TokenExpired`, 5xx to `TierUnavailable`, empty parse detection with renderer keys, rate-limited unknown renderer reporting, UTC offset DST calculation, cache refresh.
- `innertube-auth/src/main/kotlin/com/greenaltambe/vela/innertube/AltClient.kt` — Region wiring to ChannelBrowse.
- `extractor-newpipe/src/main/kotlin/com/greenaltambe/vela/extractor/newpipe/NewPipeExtractor.kt` — Replaced global search with `TierUnavailable`, unsupported tabs return `TierUnavailable`, about tagged `isPartial = true`, token validation.
- `extractor-newpipe/src/main/kotlin/com/greenaltambe/vela/extractor/newpipe/Continuation.kt` — NewPipe token prefix (`np:`).
- `desktopApp/build.gradle.kts` & `UiHarness.kt` — Screenshots output to `build/screens` by default, preserving `docs/reports/` unless `-Pscreens=docs`.
- `androidApp/src/debug/kotlin/com/greenaltambe/vela/debug/SelfTestActivity.kt` — Added `channelFlows` exercising channel landing, oldest videos, 3-page continuation, and Shorts tab.
- `shared/src/commonTest/kotlin/com/greenaltambe/vela/extractor/FallbackChainChannelTierTest.kt` — New unit tests verifying tier memory isolation and token routing.
- `innertube-auth/src/test/kotlin/com/greenaltambe/vela/innertube/ChannelBrowseTest.kt` — Tests for token expiration, foreign tokens, empty state detection, renderer rate limiting, cache refresh.
- `innertube-auth/src/test/kotlin/com/greenaltambe/vela/innertube/CredentialLeakAuditTest.kt` — Added `fixturesNeverCarryVisitorDataCookiesOrEmails`.
- `innertube-auth/src/test/resources/fixtures/channel/*.json` — Trimmed 9 channel JSON fixtures from 12 MB to 1.1 MB.
- `desktopApp/src/test/kotlin/com/greenaltambe/vela/ui/ChannelTabsNativeUiTest.kt` — UI tests for server-only tabs, unknown tab rendering, continuation token expiration refresh, shelf post fixed height.

---

## 5. Decisions

1. **Token prefix naming**: Used `n1:` for native tier tokens and `np:` for NewPipe tokens. This guarantees immediate rejection (`TokenNotMine`) when continuation tokens cross tiers during paging.
2. **Tier memory isolation**: Rather than complicating the `TierMemory` storage key with composite operations, we explicitly exclude `AccountOp.CHANNELS` from recording failed routes into playback tier memory. This prevents anonymous/alt channel browse errors from ever degrading video stream playback.
3. **Fixed height posts in horizontal shelves**: When post items appear inside `HScrollRow` shelves on channel home, they are enclosed in `Box(Modifier.width(280.dp).height(200.dp))` and passed `inHorizontalShelf = true` to clamp post text to 3 lines with ellipsis and constrain attachment height, preventing vertical layout shifts or uneven shelf scrolling.

---

## 6. Known Issues

- In fallback NewPipe mode, in-channel search is not supported by the upstream NewPipe extractor and intentionally returns `TierUnavailable` (as global search returns irrelevant videos from other channels).
- Physical Android device self-test execution was not performed as no physical device was connected to the development environment.

---

## 7. Manual Test Checklist

- [ ] Open a channel page on desktop or Android: verify only the tabs returned by YouTube are shown (e.g., channels without Courses do not display a Courses tab).
- [ ] On channel Home tab, scroll horizontally through community post shelves: verify cards have uniform 200dp height with 3-line truncated text.
- [ ] On Videos tab, select "Oldest" sort chip: verify items update to oldest videos.
- [ ] Scroll down and tap "Load more": simulate a stale session token and verify the feed replaces items cleanly from page 1 and displays the "Refreshed" banner without duplicate items.
- [ ] Open channel About sheet in fallback mode: verify the "Partial info (fallback mode)" badge is visible.
