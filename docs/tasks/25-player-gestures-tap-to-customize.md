# Task 25: video page and player layouts, gestures, customize-by-tapping, finishing touches

Prerequisite: tasks 23 and 24. Read docs/design/DESIGN.md (sections 5-8, 10). Do not commit. Report
`docs/reports/25-player-gestures-tap-to-customize.md`, screenshots in `docs/reports/25-screens/`. Terminal reply 5 lines max.
Playback reliability must not regress: run the on-device self-test (all) and the Linux playback checks at the end.

## A. Video page and player layouts
1. Layouts as option cards with drawings: Standard (video on top, details/comments/related below), Theatre (wide, details collapsed),
   Minimal (title + play, gestures do the rest), Audio-only art (cover art from the thumbnail, a turning-record variant and a
   cassette variant as playful options; audio-only plays audio stream only to save data).
2. Backgrounds: colour mesh from the thumbnail, blurred thumbnail, theme, black; motion (still/slow) and softness sliders; all off
   under Performance mode.
3. Controls: choose which buttons show and their order (like, dislike count, share, download, save, loop, speed, quality, captions,
   sleep timer, PiP, cast placeholder), seek bar styles (line, thick, wave), thumb grows on drag, chapters markers, SponsorBlock
   segment colours (existing), button shape/play button style from task 23.
4. Mini player <-> full player drag with velocity, shared thumbnail transition where possible, no hitching while the video surface
   moves (the old native use-after-free defect must stay fixed: add a stress test that toggles layouts/orientation/size repeatedly during
   playback on desktop and on the phone).

## B. Gestures (Controls & gestures settings page; all rebindable; defaults per brief section 7)
Android: double-tap seek (step 5-30 s), left-side brightness and right-side volume swipe, long-press 2x speed, swipe down minimise,
swipe up details, pinch fill/fit, lock controls. Linux: keyboard shortcuts (space, j/k/l, arrows, f, m, c, <, >, Esc) shown in a
list and rebindable, mouse-wheel volume, double-click fullscreen, middle-click pause. A gesture/shortcut map is data-driven (schema)
so settings, search and backup cover it. Conflict detection when rebinding. Unit tests for the mapping + UI tests; on-device checks
on the phone (volume/brightness do not leak outside the app after leaving).

## C. Customize by tapping
Switch in Customize (task 23 hub). When on: customisable parts get a subtle animated outline; tapping the mini player, tab bar/rail,
a Home section, a video card, the player controls, the top bar or the page title opens a bottom sheet (Linux: popover/dialog) with
that part's controls (reuse the settings controls, no duplicated implementations), a Reorder/Hide row where applicable, "More in
Customize" and Undo. Off = normal behaviour with zero overhead. Tests: each part opens the correct sheet; settings changed through
the sheet equal settings changed on the page.

## D. Per-channel and per-video rules (the layered overrides already exist)
UI to set per-channel overrides from the channel page and from a card's menu: default playback speed, quality cap, SponsorBlock
categories, accent colour, card style, hide Shorts, auto-download rule link. Show where a value comes from ("from this channel").
Reset per scope. Included in backup (already).

## E. Finishing touches
1. Alternate app icons (Android activity-alias, >= 4 variants) and the Linux window icon; app icon preview in Icons & shapes.
2. Onboarding (first launch only): 3 quick screens (pick Look, pick accent, sign in or stay local) that can be skipped; never
   blocks playback.
3. Empty states, error rows and loading skeletons restyled to the Look system with a bit of personality (short friendly copy).
4. "Reset look & layout to defaults" in Customize with confirmation + Undo.

## F. Closing
Layout-invariant, contrast and motion tests; janky-frame table for player drag, gestures and layout switches; full suite after the
last code change; self-test on the phone; screenshots; owner checklist; NOT VERIFIED list. Update docs/SPEC.md status and the
"Core complete?" table with a P3 table (done / partial / not done).
