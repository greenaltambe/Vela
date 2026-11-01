# 47 Native channel browse (server-side sort, dynamic tabs, Posts, Courses, About) & solid color themes

## 1. Summary
Implemented native YouTube channel browse engine using anonymous InnerTube browse requests (`WEB` client) with automatic server-side sorting, dynamic tabs, shelf rendering, community posts, course playlists, vertical Shorts parsing (`shortsLockupViewModel` & `reelShelfRenderer`), and fallback to NewPipe. Replaced channel pill buttons with `ScrollableTabRow` and `HorizontalPager` with swipe animations between tabs. Added collapsible channel header on scroll, guaranteed Shorts, Playlist & Course tabs, 9:16 vertical Short cards, relative release age across cards and subscription feeds, and fixed video title truncation. Added a normal solid accent color theme mode (`look.colorMode = SOLID`) and custom hex color support (`look.customColor`) adhering strictly to the design token system.

## 2. Verification
RUN = executed in workspace. COMPILE-ONLY = verified by compiler and static tests (agent cannot run Android target runtime).

| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` (KMP shared, desktop tests, module unit tests) | PASS | RUN |
| `ChannelBrowseTest` (14 tests verifying 9 scrubbed live fixtures: landing, tabs, sort Oldest, courses, posts, about, search, pagination, retry, error handling, reelItemRenderer, and shortsLockupViewModel) | PASS | RUN |
| `LookBuilderTest` (solid color mode, custom hex colors, design token lint `noScreenSpellsARawColourRadiusOrFontSize`) | PASS | RUN |
| `CredentialLeakAuditTest` (ensuring no auth tokens or cookies sent or leaked) | PASS | RUN |
| `ExtractorHealthTest` (channel tabs capability and Oldest sort canary check) | PASS | RUN |
| `WrappersForwardEverythingTest` (Stub, Paced, Timeout, Captcha, FallbackChain forwarding) | PASS | RUN |
| `ChannelTabsNativeUiTest` (Compose Desktop UI: dynamic tabs, swipe navigation, course badge/lessons, posts, shorts tab 9:16 short cards, and fallback hiding sort controls) | PASS | RUN |
| `ScreenStatesTest` & `LayoutInvariantsTest` (all 104 desktop tests passed) | PASS | RUN |
| Android application compilation (`:androidApp:compileDebugKotlin`) | PASS | RUN (compile-only) |
| Physical Android device touch swipe animations | UNVERIFIED | COMPILE-ONLY (requires physical device) |

## 3. Files created/changed
- **Look & Settings (Solid Color & Hex Theme)**:
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/core/settings/LookSettings.kt`: Added `ColorMode` (`GRADIENT`, `SOLID`), `colorMode`, and `customColor` settings.
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/ui/look/LookTokens.kt`: Added `isSolid: Boolean` to `AccentTokens`.
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/ui/look/LookBuilder.kt`: Resolved solid color palettes and custom hex color parsing into uniform gradient stops.
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/ui/screens/CustomizeScreens.kt`: Routed settings under Color page with `SettingRow`.
  - `shared/src/jvmTest/kotlin/com/greenaltambe/vela/ui/look/LookBuilderTest.kt`: Added unit tests for solid themes and token lint compliance.
- **Native ChannelBrowse Engine & Fixtures**:
  - `innertube-auth/src/main/kotlin/com/greenaltambe/vela/innertube/ChannelBrowse.kt`: Pure tolerant InnerTube browse parser with 15-minute in-memory caching, single retry on 400, multi-part metadata parsing for relative upload age (`publishedText`), duration badges, clean query stripping, modern `shortsLockupViewModel` extraction (`onTap.innertubeCommand.reelWatchEndpoint`, `entityId`, `overlayMetadata`), and `reelShelfRenderer` shelf parsing.
  - `innertube-auth/src/test/kotlin/com/greenaltambe/vela/innertube/ChannelBrowseTest.kt`: Unit tests for all tab variants, pagination, sort, error handling, reelItemRenderer, and modern shortsLockupViewModel parsing.
  - `innertube-auth/src/test/resources/fixtures/channel/*.json`: 9 scrubbed anonymous InnerTube browse fixtures.
  - `innertube-auth/src/test/kotlin/com/greenaltambe/vela/innertube/CredentialLeakAuditTest.kt`: Configured fixture exclusion for leak audit.
- **Extractor Architecture & Wiring**:
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/extractor/Models.kt`: Added models for `ChannelTab`, `ChannelFilterGroup`, `ChannelTabPage`, `ChannelPost`, `CourseBadge`, attachments, shelves, and known tab protobuf tokens in `ChannelTabParams` (including `SHORTS`).
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/extractor/Extractor.kt`: Added channel browse capability flags and methods (`channelShorts`).
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/extractor/ExtractorResult.kt`: Added non-inline `map` utility.
  - `innertube-auth/src/main/kotlin/com/greenaltambe/vela/innertube/AltClient.kt`: Implemented `AltClientExtractor` delegation to `ChannelBrowse` (including `channelShorts`).
  - `extractor-newpipe/src/main/kotlin/com/greenaltambe/vela/extractor/newpipe/NewPipeExtractor.kt`: Added fallback implementation for channel tabs.
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/extractor/chain/FallbackChain.kt`: Forwarded channel browse methods through fallback chain.
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/extractor/chain/PacedExtractor.kt`, `TimeoutExtractor.kt`, `Captcha.kt`, `StubExtractor.kt`: Forwarding wrappers.
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/health/ExtractorHealth.kt`: Canary check for channel tabs and oldest sorting.
  - `androidApp/.../PlaybackRuntime.kt` & `desktopApp/.../DesktopWiring.kt`: Instantiated `ChannelBrowse` with dynamic NewPipe clientVersion.
- **Feed & Database Repositories**:
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/db/LibraryRepositories.kt`: Mapped `publishedAt` to `uploadedAt` on `VideoSummary` in `FeedRepository.entries()`, exposing relative release age in subscription feeds.
- **UI Screen, Store, Components & Test Harness**:
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/ui/screens/ChannelStore.kt`: Updated `ChannelState` to maintain per-tab feeds, filter selections, pagination tokens, and scroll state.
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/ui/screens/ChannelScreen.kt`: Collapsible channel header within a clipped Box preventing bleed into the top bar or status bar; fixed scaffolding top bar remains in place while channel banner and metadata scroll upwards; `ScrollableTabRow` smoothly moves up and pins directly below the top bar; `HorizontalPager` with `Modifier.weight(1f)` occupies the full remaining viewport to eliminate bottom clipping; guaranteed `Shorts`, `Playlists` and `Courses` tabs; 2-column vertical `ShortCard` grid in Shorts tab; horizontal scrolling Shorts shelf on Home/Landing tab; server-side sort chips/dropdowns, course cards, community post cards, about sheet, channel search, and error retry state.
  - `shared/src/commonMain/kotlin/com/greenaltambe/vela/ui/components/Components.kt`: Displayed `views · relativeAge` on `VideoCard`; added inner padding `padding(horizontal = Vela.xs)` and expanded `maxLines = 3` to prevent title cutoff in curved card containers; created `ShortCard` with 9:16 vertical aspect ratio, `SHORT` pill badge, views/age overlay, and title.
  - `desktopApp/build.gradle.kts`: Configured isolated Skiko data directory and user home in `/tmp` to allow seamless test execution in sandboxed environments.
  - `desktopApp/src/test/kotlin/com/greenaltambe/vela/ui/ChannelTabsNativeUiTest.kt`: Desktop Compose UI tests for channel tabs, swipe animations, Shorts tab rendering, and channel list scrolling with pinned tabs below top bar.
  - `desktopApp/src/test/kotlin/com/greenaltambe/vela/ui/ScreenStatesTest.kt` & `UiHarness.kt`: Added channel landing failure error handling and theme consistency.

## 4. Decisions the spec didn't dictate
- Tab navigation UX: Selected tab state and `HorizontalPager` page are bidirectionally synchronized with smooth animations (`animateScrollToPage`).
- Collapsible channel header & fixed top bar: The scaffolding top bar (`VelaTopBar`) remains fixed in place. The channel header (banner, avatar, title, buttons) is contained in a clipped Box whose height smoothly collapses towards 0 as the list is scrolled, pinning `ScrollableTabRow` directly beneath `VelaTopBar`. The root column and header box enforce `.clipToBounds()`, preventing header content from ever overlapping or drawing behind the status bar or top bar. `HorizontalPager` dynamically takes the exact remaining screen height (`weight(1f)`), preventing bottom items from being clipped offscreen.
- Guaranteed tabs: Regardless of whether YouTube's initial browse response omits them, `Shorts`, `Playlists` and `Courses` tabs are always injected into the channel's tab navigation bar using predefined protobuf parameter tokens (`ChannelTabParams.SHORTS`, `ChannelTabParams.PLAYLISTS`, `ChannelTabParams.COURSES`).
- Shorts parsing & display: YouTube's Web client encodes modern Shorts as `shortsLockupViewModel` (with video ID under `onTap.innertubeCommand.reelWatchEndpoint.videoId` or `entityId`, and title/viewcount under `overlayMetadata`) and shelves under `reelShelfRenderer`. The parser was enhanced to support both legacy `reelItemRenderer` and modern `shortsLockupViewModel`. Shorts display as 9:16 vertical cards with a `SHORT` pill badge in a 2-column grid in the tab, or as horizontal shelves on the landing tab.
- Relative release dates: Extracted relative upload age from lockup metadata (`metadataParts`) and database entries, formatting it as `views · relativeAge` (e.g. `6.2M views · 4 days ago`) across video cards.
- Title display & card padding: Added horizontal inset padding inside `VideoCard` and `CourseOrPlaylistCard` and expanded `maxLines = 3` so titles are fully legible and not clipped by the rounded card border.
- Filter presentation: Tabs with sort/filter options render a compact horizontal filter chip bar at the top of the tab content; when the fallback extractor (NewPipe) is active, the filter bar is cleanly omitted without error banners.
- Course badge styling: Badges use `Vela.colors.accentPrimary` pill styling with lesson counts on playlists identified as courses via `thumbnailOverlayBottomPanelRenderer`.
- Custom color parsing: Valid `#RRGGBB` or `#AARRGGBB` hex strings override palette presets; invalid inputs fall back gracefully to the selected palette color without crashing.
- Bytecode inlining across modules: Made `ExtractorResult.map` a non-inline function to prevent cross-JVM compilation errors between `:shared` (JVM 21) and `:innertube-auth` (JVM 11).

## 5. Known issues / unverified assumptions
- Gestural swipe responsiveness on low-end Android touch screens cannot be measured in the headless desktop test runner.
- YouTube may periodically introduce new lockup view models; unknown renderers are logged with warning metrics for diagnostics rather than crashing the parser.

## 6. Manual test checklist
1. Open **Settings > Look & feel > Color**: verify the "Color mode" setting toggles between Gradient and Solid. When Solid is chosen, verify accent highlights and buttons use uniform solid colors.
2. In the same Color menu, enter a custom hex color (e.g. `#E91E63`) in "Custom color": verify the theme updates to the custom hue immediately.
3. Open any channel page (e.g. Kurzgesagt): verify dynamic tabs (`Home`, `Videos`, `Shorts`, `Playlists`, `Community/Posts`, `Courses`, `About`) render in a scrollable tab strip instead of pill buttons.
4. Swipe horizontally between tabs: verify smooth transitions and that the indicator tracks the active tab.
5. In any tab, scroll downwards: verify the channel header collapses smoothly and the tab strip stays pinned at the top, leaving full screen height to scroll through items.
6. Open the `Shorts` tab: verify vertical 9:16 Short cards render in a 2-column grid with `SHORT` badges, view counts, and titles. On the `Home` tab, verify Shorts shelves render as horizontal scrolling carousels.
7. In the `Videos` tab, tap the sort chip (e.g. "Oldest"): verify YouTube returns the channel's oldest videos first.
8. Verify relative release age (e.g. "4 days ago") is clearly visible on video cards in the channel page, subscription feed, and home feed alongside view count.
9. Verify long video titles wrap up to 3 lines without getting cut off at the curved container edges.
10. Open the `Playlists` and `Courses` tabs on channel pages: verify playlists and courses load properly with course badges and lesson counts.
11. Open the `Community` / `Posts` tab: verify text formatting, relative dates, likes, and image/video attachments render cleanly.
