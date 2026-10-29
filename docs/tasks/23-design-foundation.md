# Task 23: design foundation (subscriptions lag, tokens, motion, navigation, profile menu, settings reorganisation)

Read CLAUDE.md, docs/SPEC.md and docs/design/DESIGN.md (the brief; sections 1-4, 6 and 10 matter most here). Do not commit.
Report: `docs/reports/23-design-foundation.md` (usual verification table, VERIFIED how / NOT VERIFIED why, screenshots in
`docs/reports/23-screens/` at phone and desktop size). Terminal reply 5 lines max, only owner instructions.
Phone: OPPO CPH2707 over adb. Reliability must not regress: run the on-device self-test at the end.
Also: update the first-launch behaviour from task 22: on a fresh Android install the bundled yt-dlp is too old for age-restricted
videos, so make the first launch update yt-dlp automatically (same verified/rollback updater, Wi-Fi or any network but never in the
background without the app open; show the result in Health). Keep it off the critical startup path.

## A. Fix first: the Subscriptions screen lags while loading
Measure BEFORE changing anything and write the numbers in the report.
1. Reproduce on the phone with the owner's real subscription count (use the throwaway account plus a synthetic 300 subscriptions /
   5000 FeedItem rows dataset in a debug build if the real count is small). Capture: `dumpsys gfxinfo` (janky %, 90/95/99th
   percentile frame times) during navigation to Subscriptions and first scroll; a method/system trace (Perfetto via adb or
   `Debug.startMethodTracing`) of the navigation; StrictMode violations; main-thread time in the first 1.5 s.
2. Find the cause(s) from the trace, not by guessing. Likely suspects to check: DB or feed merge on the main thread, mapping/sorting
   thousands of rows in composition, N+1 queries per row (channel avatar, watched state, SponsorBlock/DeArrow lookups), image decoding
   at full size, no stable keys / no contentType in lazy lists, recomposition of the whole list on each state tick, a network call on
   the critical path before anything is drawn, feed filters applied on every frame.
3. Fix: do work off the main thread, one query (join) instead of per-row queries, paginate/limit the first page (e.g. 30) and load
   more on scroll, show cached rows immediately and refresh in the background (stale-while-revalidate), shimmer skeleton instead of
   blocking, stable keys + contentType, `derivedStateOf`/immutable models to cut recomposition, thumbnails decoded at display size
   with a memory cache cap. Apply the same review to Home, Search results, Channel and History (same list code) and fix what shows up.
4. AFTER numbers on the phone for Subscriptions, Home, Search, Channel, History: target <= 3% janky frames, no frame > 100 ms,
   first content visible < 300 ms with cached data. Add a regression guard: a JVM/Compose test that fails if a list screen
   queries once per row (count DB calls for 200 items) and a debug `FrameStats` helper reusable by later tasks (also for Linux:
   log frame times in a debug overlay).

## B. Design tokens and theming layer (everything later builds on this)
1. `VelaTokens` (extend the existing `LookTokens`): colours (surface levels, accent, wake gradient, content colours), type scale +
   font family slot, corner radii (small/medium/large/pill) driven by one "corners" setting, spacing/density scale, icon style +
   weight + rounded flag, elevation/glass parameters, motion parameters. All UI reads tokens only; sweep the existing screens so no
   hard-coded colours, radii, paddings or font sizes remain (add a lint-style test that greps for raw `Color(0x`, `.dp` radii
   in screens outside the theme package, with an allow-list).
2. Settings keys (with schema, labels, summaries, keywords, defaults, reset): theme (system/light/dark), pure black, accent colour,
   accent source (pick / thumbnail), corner radius, density (compact/comfortable/roomy), text size (80-130 %), font choice (bundle 4
   OFL fonts to start: UI default, a clean grotesque, an editorial serif, a mono; plus System), icon style, weight, rounded,
   thumbnail shape (rounded/square/squircle/circle), button shape, glass on/off (frosted only for now), reduce motion, performance
   mode. Performance mode forces glass off, motion reduced, no blur, no stagger.
3. Looks groundwork: a `Look` data class = map of look-keys -> values + name + schema version. Applying a Look is one transaction
   with one Undo. (The Looks UI comes in task 24; build and unit-test apply/undo/serialise now, plus the "look keys vs behaviour keys"
   split so a Look can never change account safety, tier order, downloads or SponsorBlock behaviour.)
4. Fonts: bundle only what is needed for Latin, and keep a system fallback chain for CJK/Devanagari so titles never render as boxes.
   Verify with Japanese, Hindi and emoji titles on the phone and on Linux.
5. Pure black fix check (done in task 12) keeps working across all Looks; contrast test >= 4.5:1 over every default palette.

## C. Motion system (brief section 6)
1. `Motion` tokens (durations, springs, easings) and reusable modifiers/components: `pressable` (scale+dim), animated selection
   outline for segmented pills/chips, toggle morph, `appearOnce` staggered list entrance, shimmer skeleton, animated page title
   collapse, undo toast (`UndoHost`) with spring in/out, small like/subscribe/watch-later icon animations, seek thumb grow.
2. Wire them into the existing screens (Home, Subscriptions, Search, Library, Channel, video page, settings, dialogs). Navigation
   transitions: tab switch crossfade+slide; push/pop slide with predictive back on Android 16; mini player <-> full player drag driven.
3. Reduce motion and Performance mode respected everywhere (a test that renders key screens with both on and asserts no running
   infinite animations / no stagger delays). Animations pause when the app is not visible (no animation clock running in background).
4. Measure: gfxinfo janky % while exercising each animation on the phone, and a frame-time log on Linux; report numbers. Budget in the
   brief. If an effect misses the budget, simplify it rather than ship jank.

## D. Navigation and profile menu
1. Phone: floating pill bottom bar (labels per setting), configurable tab list (Home, Subscriptions, Shorts, Library, Search,
   Downloads) with order and "open on"; mini player card above it with progress line and swipe-to-skip off by default.
   Wide/Linux: left rail (collapsible) with the same tab list; keyboard focus and tooltips on the rail.
2. Top bar: avatar button (side configurable, default left) opening the Profile menu from the brief: account card (name, handle,
   avatar from the account, switch account, add account, sign out), Settings, Customize Vela, Downloads, History, Backup & restore,
   Health & diagnostics, About. Anonymous/local mode shows a "Local" avatar and "Sign in" entry. Remove Settings from any nav bar.
3. Large thin page titles with scroll-collapse and round icon buttons (task 12's title row restyled); search stays on its own tab or the
   search action per setting.
4. Glass bars must never show page text through them: bar background = blur + tint with a minimum opacity chosen so bar labels
   meet contrast; test with a screenshot test on text-heavy pages (phone screenshot of the owner's reference defect: labels colliding
   with page content behind the bar must not occur).

## E. Settings reorganisation (brief section 4)
1. Restructure the settings UI into the grouped layout: account card, search, Performance mode, groups with icon tiles + live
   summary subtitles + chevrons, "Reset this page" on every page, info (i) tooltips, undo toast on every change. Search jumps to
   and highlights the setting. All existing settings keep their keys, scopes, defaults and backups (migration not needed; add a test
   that every existing key is reachable from the new structure and none is orphaned).
2. Summaries are generated from current values (e.g. "720p · Normalised", "Floating bar · 4 tabs"), unit-tested.
3. Customize hub page skeleton with the live preview strip (mini Home/Video page/Player rendered from the real components at small
   scale, not screenshots), Theme + Pure black, and entries that exist so far: Color, Text, Icons & shapes, Glass & backgrounds
   (Looks, Tabs & mini player, Home & Subscriptions, Feeds & cards, Video page & Player are stubs labelled "next update" until task 24/25).
4. Build the Color, Text, Icons & shapes, Glass pages for the settings added in B with live previews and the rest per the brief
   (accent picker, softened vs exact, light-mode accent, font cards, text size/weight/spacing, icon style/weight/rounded, active-tab
   marker, thumbnail/avatar/button shapes, corner slider, glass + reduce motion).

## E2. Build for keyboard focus from the start (task 26 depends on it)
Every interactive component built or restyled here (rail items, top-bar buttons, cards, chips, segmented pills, toggles, sliders, tabs, menu rows, mini player, player controls) must be focusable with a visible focus state drawn from the tokens (ring colour = accent, corner radius = the component's), use real roles/labels, and activate on Enter/Space. Regions (rail, top bar, content, mini player) are separate focus groups. No behaviour yet beyond standard Tab order and activation; the full keyboard system comes in task 26.

## F. Closing
1. Layout-invariants tests at phone, tablet, desktop and font scale 1.3 for all new/changed pages (no mid-word wraps, no clipping).
2. Full suite `./gradlew allTests test :androidApp:assembleDebug :androidApp:assembleRelease --continue` after the last code change.
3. On-device self-test (all) green on the phone; before/after janky-frame table in the report; screenshots of every new page.
4. Report what is NOT done and the owner's manual checklist (what to look at: motion feel, contrast, the profile menu on Linux).
