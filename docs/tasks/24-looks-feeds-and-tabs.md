# Task 24: Looks, feed/card styling, Home sections, tabs and mini player

Prerequisite: task 23 done (tokens, motion, nav, profile menu, settings layout). Read docs/design/DESIGN.md (sections 2, 5, 6, 10).
Do not commit. Report `docs/reports/24-looks-feeds-and-tabs.md`, screenshots in `docs/reports/24-screens/` (phone + desktop).
Terminal reply 5 lines max. Same verification table format. Measure janky frames for every new animated surface (budget in brief).

## A. Looks (UI on top of the task 23 groundwork)
1. Looks page: "Save as a Look" (name), preset cards with two live mini previews each, "..." menu (rename, duplicate, export JSON,
   share via system share/save file, delete). Presets shipped: Vela, Paper, Ink, Neon, Calm (design in brief section 2; all must pass
   the contrast test). Picking a Look applies in one transaction with one Undo toast; "You're on your own mix of settings" shown when
   the current settings match no Look.
2. Import: open a JSON file (Android document picker / Linux file dialog), preview what it changes, refuse foreign/newer-schema
   files with a clear message, ignore unknown keys, never apply behaviour keys (account safety, tier order, downloads, SponsorBlock).
   Round-trip tests (export -> import = identical), tamper tests.
3. Day/night Looks: optionally switch Look by system dark/light or by time schedule.
4. Looks are included in Backup and restore (task 20 format v3, old files still upgrade).

## B. Feeds and cards
1. Card styles: Standard (big thumbnail), Compact list, Magazine grid, Minimal (title only) plus columns Auto/1-4 on wide screens;
   details under each video toggles (channel, avatar, views, age, duration badge, progress bar); thumbnail shape from task 23;
   per-surface choice (Home, Subscriptions, Search, Channel, History, Library) with a "same everywhere" shortcut.
2. Card swipe actions (Watch later / hide channel) behind the setting, off by default; long-press menu stays.
3. Subscriptions page upgrades: channel strip at the top (avatars, unseen dot), grouping by day, "mark all seen", and the performance
   fixes from task 23 kept (re-measure).

## C. Home sections and tiles
1. Home = greeting header (date in small caps, large light greeting by time of day with the account name), a "Continue watching" pill,
   quick-access tiles (Watch later, Downloads, Subscriptions, History; user picks/reorders up to 8), then reorderable sections:
   Continue watching, Subscriptions, Recommended, Trending, Watch later, Downloads, Shorts shelf, Watch stats card.
   "Customize Home" page: reorder/hide with arrows + drag, "add new sections automatically" toggle, top-of-Home options.
2. Watch stats card (local data only, from history): watch time this week/month/year, top channels, streak, with >= 5 visual styles
   (Quiet, Ledger, Wash, Ring, Wake stripe) chosen in settings and previewed live; tap opens a Stats page (period chips, top channels,
   top videos, day-of-week bars). Nothing leaves the device.
3. Library page: tiles with coloured icon tiles (Playlists, Watch later, History, Downloads, Liked, Local files if any), reorder/hide
   ("10 of 11 shown"), grid/list per kind, open-Library-on setting.

## D. Tabs and mini player
1. Tabs & mini player settings page per brief: bar style (floating/docked/minimal), labels, height, background, shrink on scroll,
   swipe between tabs, tab checklist with order + "open on", mini player style (card/slim/pill), joined to bar, swipe to skip,
   progress style (line/fill/none), spacing, title size, card size, hide status bar (Android).
2. Linux rail mirrors the tab list; rail width/labels settings; window title/status integration kept.
3. Everything above animates via the Motion tokens and honours Reduce motion / Performance mode.

## E. Closing
Layout-invariant tests (phone/tablet/desktop/font scale 1.3), contrast tests for every preset Look, full suite after the last code
change, on-device self-test, janky-frame table, screenshots, owner checklist. NOT VERIFIED list stays explicit.
