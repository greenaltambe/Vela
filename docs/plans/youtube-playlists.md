# Plan: YouTube playlist page and saving playlists

Status: all five stages built (reports 164 to 168). Evidence: reports 163, 165 and 168.

## 1. Done: account playlist reading was broken for the newer page layout

`InnertubeParsers.playlistVideos` reads only `playlistVideoRenderer`. Liked videos and other people's playlists now arrive as `lockupViewModel` entries:
the app's reader returns **0 videos and says the list is complete**. The newer next-page token (`continuationItemViewModel`, nested command) is also not
followed. Watch later still uses the old layout, so it works today.

Effect: "Sync with YouTube" brought nothing into Liked videos; any playlist in the newer layout read as empty. The existing guard kept local videos.

Fix: one parser for both layouts and both token shapes. A read counts as failed, never as empty or complete, when the header promises videos and none were
read, or a next-page item cannot be followed.

## 2. The three kinds of playlist

| kind | who edits | where it lives | label in Library |
|---|---|---|---|
| Yours, on this device | you | Vela only | On this device |
| Yours, synced | you, both ways | Vela and YouTube | Synced with YouTube |
| **Saved (new)**: someone else's | its owner | follows the original | Saved, or Saved · on YouTube |

Decided by the owner: a saved playlist behaves as on YouTube. It is the owner's playlist: their changes show up, you cannot edit, rename or reorder it, and
removing it only removes your save. Vela keeps a copy of its list (for offline use and downloads) and refreshes it when opened and on library sync.
To get an editable version you use "Add all to a playlist".

## 3. Saving, with the same box as everywhere else

Tapping Save opens a small sheet: the playlist's name, one box **"Sync with my YouTube account"** ("Also appears in your YouTube library"), Save.

- Unticked: saved in Vela only.
- Ticked: also saved to your account, the same "save to library" YouTube's own button does (`like/like` with the playlist id; undone by `like/removelike`).
- Start state of the box: the existing "New playlists sync with YouTube" setting. Signed out: disabled, with the existing reason text.
- Later changes: the playlist's "Sync settings", as for your own playlists.
- Remove: "Remove from library"; when it is on the account, a ticked box "Also remove it from my YouTube account".

Writes follow the existing rules: applied here first; refused means rolled back with the reason; held by the hourly limit means kept in the outbox (`SAVE`, `UNSAVE`).

| event | result |
|---|---|
| Saved on your account, then removed on youtube.com | removed from Vela too, only once the playlist's own page says it is no longer saved; missing from a list or a failed read removes nothing |
| Saved on this device only | stays until you remove it in Vela, whatever the account says |
| Saved on youtube.com | appears at the next library sync |
| Owner deletes it or makes it private | cached list kept, marked unavailable |
| Your own playlist opened from search | shown as yours; no Save button |
| Mixes (endless, made per viewer) | no Save, no Download all |

## 4. The page

One header component for YouTube playlists and library playlists.

- Cover, title, owner with picture (opens the channel), "Playlist · 44 videos · 1,814,014 views", description that expands.
- Buttons: **Play all**, Save, Share, Shuffle, Download. Menu: Add all to a playlist, Open in browser, Refresh.
- Wide window: header card on the left, list on the right, as on youtube.com. Phone: header scrolls above the list.
- A soft wash from the cover behind the header, through the app's effect wrapper so performance mode can drop it.
- Own playlists show the same header; actions that do not apply are absent, Share only when synced.
- The header is a named component with a stable ID, styled only from design tokens (spec section 5).
- The list keeps going through the filter pipeline (hide rules, DeArrow titles) as it does today. Play all, Shuffle and Download all act on the videos shown;
  the saved copy keeps the whole list.

Whole-list actions:
- Play all starts with the first page at once and appends later pages to the queue, but only while the queue is still the one it made.
- Shuffle and Download all read every page first, with progress and Cancel. A list is never silently cut: if a page fails, it says how many were read.
- Share: `https://www.youtube.com/playlist?list=<id>`.

## 5. Data and code

- Migration `12.sqm`: `Playlist` gains `sourceId` (unique per account), `ownerName`, `ownerChannelId`, `coverUrl`, `onAccount`, `unavailableSince`.
  `remoteId` stays reserved for your own synced playlists, so no existing write path can reach someone else's list.
- `isReadOnly` becomes "Liked videos or saved". Every write path and "Save to..." sheet already honours it; delete and "Sync settings" get a saved branch.
- Backup and device sync carry saved playlists as references (id, title, on account), not as videos.
- Built: `Extractor.playlistPage` returns header, videos and token: the YouTube client first (full header, your region), NewPipe as fallback (no views, no save
  state). `AccountReads.playlistPage` for private lists, plus the save state.
- `AccountActions.savePlaylist` / `unsavePlaylist`.
- Library sync must classify each account playlist before importing: a page offering the save button is someone else's (saved), otherwise yours (synced).
  A saved list imported earlier as "synced" is converted and its pending writes dropped. Confirmed (report 165): the account's playlist list includes saved
  playlists, so today's sync imports them as synced, editable ones.

## 6. Stages (each tested, each leaving the app working)

1. Reader fix (section 1). Worth releasing on its own. **Done.**
2. Playlist page data and parser. **Done.**
3. Header and layout; Play all, Shuffle, Share, Download. No account writes. **Done** (the Save button comes with stage 4).
4. Saved playlists, device only: sheet, model, Library, backup and sync. **Done** (the sheet is a dialog, like New playlist).
5. Account save and unsave, and classification in library sync. **Done.**

Gate for each: `./gradlew allTests test :androidApp:compileDebugKotlin`; read-only live checks for stages 1, 2 and 5. The owner allowed a live test of the
save and remove writes; both worked (report 165).
