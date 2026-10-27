# Task 12: usable, responsive UI (phone first, then tablet/desktop)

Follow CLAUDE.md (report docs/reports/12-responsive-ui.md; terminal reply max 5 lines). Read docs/SPEC.md sections 5 and 7.
Context: the owner ran the Android app. Everything functions (trending, feed, playback, mini player) but the UI is nearly unusable on a phone. Look at the owner's three screenshots in docs/tasks/assets/12-phone-*.jpg (they are copied there). Concrete defects seen:
1. VideoCard uses a horizontal layout: the thumbnail takes ~70% of a 360dp-wide screen and the title/channel column collapses to ONE character per line.
2. The search text field + big Search button sit at the top of every screen, keep a stale query, and eat vertical space.
3. Bottom nav label "Subscriptions" is truncated ("Subscriptio"); icons are placeholders.
4. Video page: seek slider and volume slider render as a bar with a stray vertical line and overflow the right edge; speed chips wrap into uneven rows; the Back button sits beside the search field; all secondary controls are always visible and push the content down.
5. The player showed the PREVIOUS video's frame and "Resolving streams..." / old position (28:56 / 29:45) while the page below already showed the NEW video's title. Check this cannot get stuck (state must reset per video, status must follow the real session state) and fix if it can.
6. Margins/insets are inconsistent between cards, search field and tabs; mixed left edges.
Visual style stays plain/clean and token-driven: this task is about LAYOUT, usability and responsiveness, not final aesthetics (that comes later). Do not hardcode visuals; extend LookTokens where needed.

## A. Adaptive layout system
- Window size classes (COMPACT <600dp, MEDIUM 600-840, EXPANDED >840) exposed through a CompositionLocal computed from the real window/screen width (Android + desktop); all screens branch on it, not on ad-hoc dp checks. Design for 360x800dp as the primary target; must also work at 320dp and in landscape.
- Spacing scale and a standard content gutter (16dp compact, 24 medium, 32 expanded, from tokens); edge-to-edge with correct system bar/cutout/gesture insets on Android; predictive back; min touch target 48dp; text never below 12sp; every Text has a deliberate maxLines/overflow policy (titles 2 lines, meta 1 line).
- Bottom nav on COMPACT, rail on MEDIUM/EXPANDED; short labels that fit ("Home", "Subs", "Library", "Settings" or equivalents) and real icons (draw a small own icon set as ImageVectors behind the token `iconSet`, no big icon dependency).

## B. Components (reusable, named, token-driven)
- VideoCard variants: Vertical (thumbnail full width 16:9, title 2 lines, channel avatar + name + views/age, duration badge on the thumbnail, overflow menu) for COMPACT; Horizontal (fixed ~168dp thumbnail, text fills the rest) for lists on MEDIUM and for search/playlists/history rows; Grid (adaptive columns, min width ~280dp) on MEDIUM/EXPANDED Home/Subscriptions. Live badge, Short badge, watched progress bar, downloaded indicator.
- Proper top app bar per screen (title, search icon, overflow); tapping search opens a full search view (query field with clear, back, history list, results below) instead of the permanent field. Remove the stale-query behaviour.
- Mini player: thumbnail, title (1 line), play/pause and close as icon buttons, thin progress line, respects nav/insets; tap expands.
- Loading skeletons, empty states with a short message and action, and inline error rows with Retry for every list screen (Home, search, feed, channel, comments, downloads).

## C. Video page / player (phone first)
- COMPACT portrait: 16:9 player pinned at the top (below the status bar), everything else scrolls underneath (title, channel row, action row with Like count/RYD, Download, Save, Share, description collapsed with "more", chapters, comments, related). The Back arrow is a top-bar icon overlaid on the player, not a text button.
- Player controls as an overlay on the video that auto-hides after ~3s and toggles on tap: centre play/pause (+/-10s), seek bar with correct thumb, buffered track, chapter and SponsorBlock markers, elapsed/total, and a row of icon buttons: quality, speed, captions placeholder, sleep timer, fullscreen. Speed/quality/sleep/stats open as a bottom sheet or popup menu (never always-visible chip rows). Volume slider only on desktop (Android uses system volume); mute icon only.
- Fullscreen: on Android rotate-to-landscape or the button enters immersive fullscreen with the same overlay; leaving restores the page and scroll position; works with PiP. On desktop keep the existing real fullscreen.
- MEDIUM/EXPANDED: player left/top with the details/comments/related in a second column beside it (YouTube-style) when width allows.
- Implement a custom seek bar (own composable, not the stray-line Material slider as used now) with: drag/seek preview timestamp, tap to seek, accessibility semantics, marker ticks aligned exactly to the track, large touch target.

## D. Screen-by-screen pass
Home, Subscriptions (Feed / Shorts / Channels tabs with the Shorts tab as a grid), Library, Settings (generated screen: group headers, search field in a top bar, controls aligned and not clipped), Channel page, Downloads, Playlist, Diagnostics. Each must be usable at 360dp and good at 1280dp.

## E. Verification (you cannot see the device, so make the checking mechanical AND visual)
- Compose UI tests that render key screens at these sizes and capture screenshots with captureToImage(): 320x640, 360x800, 412x915, 800x1280 (tablet portrait), 1280x800 (desktop), plus landscape 800x360, in light and Pure Black, with realistic fake data (long titles, long channel names, large numbers, live/short/downloaded states, empty and error states). Save them under docs/reports/12-screens/ and LOOK at them yourself with the image reader; fix what is wrong and re-capture until none of the defects above remain. Mention in the report which screenshots you reviewed and what you changed because of them.
- Add layout assertions as tests so regressions are caught without eyes: title text width never below ~120dp at 320dp window; no text clipped horizontally (compare measured width to constraints); touch targets >= 48dp; nothing overlapping the system insets; bottom nav labels fully visible; seek bar thumb inside track bounds.
- Offline tests, liveTest, :androidApp:assembleDebug, :desktopApp:run + the Compose UI flow test updated for the new UI. State what is compile-only (real device insets/gestures/rotation/PiP).
- Manual checklist (Android phone + Linux) in the report, short and concrete, including: rotate while playing, fullscreen enter/leave, back gesture behaviour, tiny-text check, scroll position restore, mini player + nav.
