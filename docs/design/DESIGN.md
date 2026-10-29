# Vela design brief (the "fun part")

Owner reference: a polished customisable music app (screens reviewed in chat on 2026-10-28). We copy its STRUCTURE and FEEL
(how settings are organised, how deep customisation goes, how alive the motion is), NOT its branding, name, signature rainbow,
icons, copy or exact visuals. Vela gets its own identity (below). Never use the reference app's name or assets anywhere.

## 1. Feel
Playful, alive, a little cheeky, never cluttered. Big thin titles, rounded surfaces, one signature accent, small delightful motion
on every interaction. Defaults are good-looking out of the box; everything is changeable. Reliability work from tasks 1-22 must
not regress: player, resolve chain, downloads and account safety are untouched by styling.

## 2. Vela identity (defaults, all changeable via tokens)
- Name/mark: Vela (sail / constellation). Signature accent = the "wake": a thin 2 px gradient stroke (teal -> indigo -> magenta)
  used ONLY for "selected" (chips, segmented pills, active tab marker, toggles-on, progress). Never a whole-surface gradient.
- Type: rounded geometric sans for UI (bundle one OFL font with Latin + a fallback for CJK/Devanagari; titles light weight, large).
- Shapes: generous corner radius (default 24 dp cards, 999 pills), surfaces one step lighter than background, no hard borders.
- Default Looks shipped (names are ours): "Vela" (dark, wake), "Paper" (warm light, serif titles, ink instead of colour),
  "Ink" (pure black AMOLED, thin icons), "Neon" (mono type, neon accent), "Calm" (low-contrast, no motion extras, no glass).

## 3. Information architecture
- Bottom nav on phone (floating pill, tab list user-configurable): Home, Subscriptions, Shorts, Library (+ optional Search,
  Downloads). Linux/wide: left rail (collapsible to icons) with the same list. Settings is NEVER in the nav.
- Top bar: avatar button in the top corner (owner asked for top-left; make the side configurable: left/right, default left on phone
  and Linux) opens the Profile menu: account card (name, handle, switch account, add account, sign out), Settings, Customize Vela,
  Downloads, History, Backup & restore, Health & diagnostics, About. Only a few lines each; no deep menus.
- Page titles: large thin title that collapses into a small centered title in the top bar on scroll; round icon buttons for
  page actions in the title row (sync, edit, layout, filter).

## 4. Settings structure (generated from SettingsSchema; same keys, scopes, search and backup as today)
Top of Settings: account card, settings search, Performance mode switch (with info), then grouped cards with small-caps headers.
Each row = coloured rounded icon tile + title + LIVE one-line summary of current values + chevron.
- MAKE IT YOURS: Customize Vela (opens the Customize hub).
- WATCHING: Playback & quality, Gestures & controls, SponsorBlock & extras (RYD, DeArrow), Captions.
- YOUR FEED: Home, Subscriptions & feed filters, Shorts, Search.
- ACCOUNT & SAFETY: Accounts, Account safety (governor, tier order, age-restricted), Privacy.
- OFFLINE: Downloads, Automatic downloads, Storage.
- DEVICE: Notifications, Background play & PiP, Linux integration (MPRIS, tray) / Android integration.
- MAINTENANCE: Backup & restore, Updates (yt-dlp, remote config), Health & diagnostics, Rebuild caches, About/licences.
Rules: every page ends with "Reset this page"; every change shows an undo toast ("Corners -> Round · Undo"); every page has an
info (i) where a setting is non-obvious; search results jump to the setting and highlight it; each control uses only these kinds:
segmented pills (2-4), chips (long lists), toggles with a one-line explanation, sliders with a live word, option cards with a tiny
drawing, reorderable checklists (arrows + drag handle).

## 5. Customize hub (the Customize page)
- Live preview strip at top: mini Home, mini Video page, mini Player; updates instantly as settings change.
- Theme: System/Light/Dark + Pure black. THE WHOLE LOOK: Looks, Color, Text, Icons & shapes, Glass & backgrounds.
  EACH PART: Tabs & mini player, Home & Subscriptions, Feeds & cards, Video page & Player. Then "Customize by tapping" switch and
  "Reset look & layout to defaults".
- Looks: "Save as a Look", preset cards each with two mini previews, "..." menu (rename, duplicate, export JSON, share, delete),
  picking one changes everything at once with one Undo. A Look = a named bundle of look/layout settings keys (not behaviour keys such
  as account safety, tier order, downloads). Import/export as a JSON file; schema versioned; unknown keys ignored; refuse foreign files.
- Color: accent sources (Your pick, From the thumbnail of the playing video, From wallpaper on Android), numbered preview parts,
  "Softened vs Exactly as picked", separate accent for light mode, optional per-channel accent.
- Text: font cards with "Aa" previews (6-10 bundled OFL fonts + System + From a file), text size slider, weight, letter spacing.
- Icons & shapes: icon style (outline/duotone/filled), weight, rounded ends, active-tab marker (ink/accent/wake), icon packs (>=2),
  corner radius slider, thumbnail shape, avatar shape, button shape (pill/rounded/square), filled vs outlined play button,
  alternate app icons on Android (activity-alias) and window icon on Linux.
- Glass & backgrounds: Frosted glass, Liquid glass (optional, heavy, off by default), frosted pages, screen backgrounds (design or
  own photo), Reduce motion. Glass must NEVER let content text show through bars (legibility: min blur+tint so bar text meets contrast).
- Tabs & mini player: bar style (floating/docked/minimal), labels (always/selected/never), height, background, shrink on scroll,
  swipe between tabs, tab checklist with order and "open on", mini player style (card/slim/pill), joined to bar, swipe to skip, progress
  style, spacing (compact/comfortable/roomy), page-title size, card size, hide status bar (Android).
- Home & Subscriptions: section list (Continue watching, Subscriptions, Recommended, Trending, Watch later, Downloads, Shorts shelf,
  Watch stats card) reorderable/hideable, "Continue" line, quick tiles, stats card styles (>=5), per-kind grid/list, columns Auto/1-4,
  details under each video (channel, views, age, duration toggles), open Library on ..., Library tiles reorder.
- Video page & Player: layouts (Standard, Theatre, Minimal gesture-only, Audio-only art, Cassette/Vinyl for audio-only as a joke
  option), background (colour mesh from thumbnail, blurred thumbnail, theme, black), controls layout (which buttons, order),
  seek bar style, gestures (below).

## 6. Motion (applies everywhere, one system)
All animation comes from a single `Motion` token set (durations 90/160/240/360 ms, 2-3 spring specs: snappy, standard, bouncy;
easing curves), never ad-hoc numbers. Required:
press feedback on every tappable (scale ~0.96 + slight dim), selected-state outline that slides between options, toggle thumb morph,
tab switch (crossfade + slight slide), list items appear with a short staggered fade/slide on first load only, thumbnail -> video page
shared transition where Compose allows, mini player <-> full player expand/collapse driven by drag with velocity, undo toast
spring in/out, page title collapse, skeleton shimmer for loading (never a blank screen or spinner-only), pull-to-refresh with a small
brand animation, like/subscribe/watch-later button micro-animations, seek bar thumb grow on drag.
Rules: 60 fps on the OPPO phone (measure with `dumpsys gfxinfo` janky-frame % on Home, Subscriptions, Search, video page open,
mini-player drag; budget <= 3% janky frames during these) and smooth on Linux (frame times logged in a debug overlay);
`Reduce motion` shortens/removes everything non-essential; Performance mode disables glass, blur, mesh backgrounds, stagger and
shared transitions; animations never block input, are cancellable, and never run while the app is in the background.

## 7. Gestures (Controls & gestures page, all rebindable, defaults sane)
Player: double-tap left/right to seek (step configurable), swipe left side brightness, right side volume (Android), long-press for 2x
speed (release restores), swipe down to minimise, swipe up for details, pinch to fill/fit. Cards: swipe for Watch later / hide channel
(configurable, off by default on Linux). Tabs: swipe between tabs (toggle). Linux: keyboard shortcuts (space, j/k/l, arrows, f, m, c,
<, >) listed and rebindable in the same page; mouse wheel on the player adjusts volume.

## 8. Customize by tapping
A switch in Customize. When ON, a subtle outline appears on customisable parts; tapping the mini player, tab bar, a Home section, a
video card, the player controls or the top bar opens a bottom sheet (dialog on Linux) with that part's settings (the same controls as
the settings pages, not a second implementation) and a "More in Customize" link. A part's sheet can also reorder/hide that part.

## 9. Later (not in these tasks): prank mode (docs/plans/later-fun-ideas.md), Replay-style yearly recap, Chromecast, Flatpak.

## 10. Quality bars
- Layout invariants tests (no clipped/wrapped-mid-word labels) at phone, tablet, desktop sizes and with font scale 1.3.
- Contrast: all text >= 4.5:1 over any Look preset incl. glass.
- Every setting has a default, a label, a summary string, search keywords, and a reset path.
- Screenshots of every new page at phone and desktop sizes in docs/reports/NN-screens/.
