# Backlog: fixes and features (not yet implemented)

## Fixes

### 1. "/" does not open search on the search results page
- On the search results page the `/` shortcut does not focus or open the search field. It should work the same as on
  the other screens.
- To investigate: where the `/` key handler is registered and whether the results screen swallows or never receives it
  (focus owner, key-event consumption).

## Features

### 2. Toast / notification when a video is added to a playlist
- Adding a video to a playlist currently gives no feedback. Show a toast/snackbar (like the one shown when the mini
  player is closed), e.g. "Added to <playlist name>", ideally with an Undo action.
- Reuse the existing toast mechanism used for the mini player close; cover both desktop and Android.

## Investigations

### 3. Playlist sync with the YouTube account
- The playlist sync behaviour with the YouTube account needs more investigation (see task 84, `docs/tasks/84-playlist-sync.md`).
- Scope is not yet defined; start by reproducing and measuring what happens, then decide what to fix.

### 4. Overflow when a synced playlist is full
- Designed in `docs/tasks/84-playlist-sync.md` ("Overflow when YouTube's playlist size limit is hit"): leftovers stay local, the user can move them to another synced playlist or have a new one created.
