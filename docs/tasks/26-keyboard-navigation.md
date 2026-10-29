# Task 26: complete keyboard navigation (desktop first, Android hardware keyboard as a bonus)

Prerequisite: tasks 23-25 (rail, top bar, cards, player, gestures page, settings schema). Do not commit.
Report `docs/reports/26-keyboard-navigation.md`; screenshots in `docs/reports/26-screens/`. Terminal reply 5 lines max.
Goal: the desktop app is fully usable without a mouse: every screen reachable, every action available, nothing traps focus.

## A. Focus model
1. One focus system for the whole app: visible focus ring (accent + corner radius from the Look tokens; animated move; instant under
   Reduce motion), focus order by region (rail/sidebar, top bar, content, mini player), `Tab`/`Shift+Tab` hop between regions and
   within controls, arrows/`hjkl` move spatially between cards and rows (grid-aware: left from the first column goes to the rail,
   right from the rail returns to the last focused content item), `Enter` activates, `Esc`/`Backspace`/`Alt+Left` go back.
2. Focus memory: returning to a screen restores the previously focused item and scroll position; opening a dialog/sheet traps focus
   inside it and restores it on close; the mini player and the video surface are focus targets.
3. Scroll-into-view with margin as focus moves; works with lazy lists (focus an item that is not composed yet by scrolling to it).
4. Mouse/keyboard coexist: mouse movement never steals focus; any key press shows the ring; ring hidden after mouse use.
5. Text fields: single-key shortcuts disabled while one is focused; `Esc` leaves the field.

## B. Contexts and default bindings (all data-driven; see table, implement exactly, then show in the cheat sheet)
Browse: h j k l / arrows move; Enter open; Shift+Enter queue/mini player; gg top, G bottom, Ctrl+d/Ctrl+u half page, count prefix
(e.g. 5j); `/` search; Ctrl+k command palette; r refresh; Esc/Backspace/Alt+Left back, Alt+Right forward; `?` cheat sheet.
Card actions on the focused card: w Watch later, a add to playlist, d download, s subscribe/unsubscribe, o open channel, x hide/not
interested, y copy link, m menu (every action shows the undo toast).
Player: Space or k play/pause; j/l seek -/+10 s; h/Left and Right seek 5 s; Shift+those 30 s; Up/Down volume; m mute; 0-9 jump to
0-90 %; < > speed; , . frame step when paused; f fullscreen; t theatre; i mini player; c captions; q quality; [ ] chapters;
Shift+N/Shift+P next/previous; b skip SponsorBlock segment; e comments/details; Backspace/Esc exit fullscreen then close the
video; Tab moves into the controls under the video, Esc back to the video.
Global: g h Home, g s Subscriptions, g l Library, g d Downloads, g y History, g w Watch later, g , Settings; 1-9 nth tab;
Ctrl+Tab/Ctrl+Shift+Tab cycle tabs; Ctrl+, Settings; Ctrl+Shift+A profile menu.
"Vim keys" switch (default ON on Linux): off frees hjkl and single-key actions; arrows and Tab always work.
Everything is in the shortcut table used by the Controls & gestures page: rebindable, conflict detection, reset, searchable,
included in backup. The cheat sheet (`?`) is generated from the same table per context.

## C. Command palette (Ctrl+k)
Fuzzy search over: pages, settings (from the schema, with their current value), actions on the focused item, recent channels,
recent searches, Looks (switch Look), accounts (switch account). Enter runs it, results show their shortcut, Esc closes.

## D. Reachability audit and tests
1. A test per screen that walks the whole UI by keys only (Compose Desktop key events): every focusable element reachable, no focus
   trap, Esc/back always leaves, ring visible, focus restored. Screens: Home, Subscriptions, Shorts, Library (+ each sub-page),
   Search, Channel, video page, player (fullscreen + windowed), comments, playlists, downloads, history, profile menu, every settings
   page and dialog, Customize pages, captcha banner, backup restore dialog, Health.
2. Binding tests: conflicts, rebinding, vim-keys off, text-field suppression, count prefixes, g-prefix timeout (1 s) and cancel.
3. Real-session checks on the owner's Linux session (GNOME/Wayland): keys work with a non-US layout (use key CODES for navigation
   where layout-independent, characters for mnemonic actions; test with at least a second layout), IME composition does not trigger
   shortcuts, and media keys still reach MPRIS.
4. Screen-reader semantics where Compose Desktop supports them (labels/roles on custom controls); report what is not possible.

## E. Android hardware keyboard / gamepad bonus
Same focus system, same table (subset: no global hotkeys); verify with a Bluetooth or adb-injected key events on the phone.

## F. Closing
Full suite after the last code change; layout/contrast tests still green with the focus ring; report what is NOT VERIFIED
(layouts, IMEs) and the owner checklist (a 5-minute keyboard-only tour).
