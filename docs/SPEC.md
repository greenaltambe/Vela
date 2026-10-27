# YouTube Client App: Project Spec

Personal-use, highly customisable YouTube client for **Android 16** and **Linux (GNOME, Wayland)**. Goal: "Grayjay but better", with reliability as the top priority. Written 27 Oct 2026 from the planning chat. **No code has been written yet.**

## 1. Goals and constraints

- YouTube only, with full account login and sync (subscriptions, history, playlists, watch later).
- **Reliability is the main priority**: playback must keep working when YouTube changes things or blocks an IP.
- Personal use only. Third-party clients technically violate YouTube's ToS, so this is not for wide distribution.
- Highly customisable UI in the style of the Prism app for YouTube Music (see section 7). Customisation is a **later phase**, but the core must be built so it can be added without rewrites.
- Visual design is also later. Default font is a normal clean font (system or Inter-style); monospace is just one option the font picker offers later.
- Platforms: Android 16 is the primary device. Linux is GNOME on Wayland.

## 2. Feature list by priority

### P0: Core

- Extractor layer: search, video info, streams, channel pages
- Playback: quality/codec choice, speed, background audio, PiP, sleep timer
- Account login with sync: subscriptions, history, playlists, watch later
- Subscription feed
- Comments (read, replies, sorting)
- Chapters

### P1: Must-haves

- **SponsorBlock:** per-category actions (skip, mute, prompt, ignore), skip notifications, segment submission
- **Return YouTube Dislike:** on the player, optionally in feeds
- **DeArrow:** alternative titles and thumbnails, frame-pick mode, fallback to original
- **Downloads (very important):**
  - queue with pause, resume and retry (resume re-resolves a fresh stream URL, since URLs expire)
  - video at chosen quality, or audio-only
  - ffmpeg merge of separate video and audio streams
  - Android background service so downloads survive the app closing
  - custom download folder, including SD card
- **Offline library:** downloads keep SponsorBlock segments, DeArrow titles and thumbnails, comments and chapters so everything works with no connection
- **Shorts control:** hide completely, show in feed, or show in a separate tab
- **Account fallback playback:** see section 4

### P2: Important, after the core works

- Feed filters: hide watched, minimum length, keyword rules
- Search filters, search history with a clear option
- Auto-download rules (new videos from a channel, watch later) with Wi-Fi-only, storage cap and auto-delete after watching
- Local playlists
- Local-only history option (history not synced back to YouTube)
- Live chat (a separate polling stream, so added late)
- Backup and restore of settings
- Multi-account support (switch and add Google accounts)
- Settings search

### P3: Customisation layer

- Themes, AMOLED pure black, density, grid vs list layouts
- Gesture controls: brightness, volume, seek
- Per-channel rules: default speed, auto-add to playlist
- Reorderable tabs, hide any UI element
- Layered settings: global, then channel, then video
- **Looks:** save, switch and share the whole appearance as one preset
- Customize by tapping a UI element
- Performance mode (cheap effects for slow phones)
- Icon packs, app icon choice, font picker with custom font file support
- Reset look and layout to defaults
- Watch stats / yearly "Replay" card (watch time, top channels and videos)
- Quick-access tiles on Home (Liked, Downloads, Watch Later, pinned playlists)

### P4: Later

- Chromecast
- Linux MPRIS and media-key polish

## 3. Tech stack (decided)

**Kotlin Multiplatform + Compose Multiplatform**, chosen over Flutter because the extractor is the riskiest part of the project and NewPipeExtractor is community-maintained and updated quickly when YouTube breaks things.

| Area | Choice |
| --- | --- |
| Shared logic and UI | Kotlin Multiplatform, Compose Multiplatform |
| Android | Compose UI, Media3/ExoPlayer, foreground service or WorkManager for downloads, PiP |
| Linux | Compose Desktop on the JVM, libmpv (or VLC bindings) behind the same player interface |
| Extractor tier 1 | NewPipeExtractor (anonymous) |
| Extractor tier 2 | Our own thin authenticated Innertube client (logged-in requests) |
| Extractor tier 3 | Alternate client types (TV, mobile, web with PO token) |
| Extractor tier 4 | yt-dlp as last resort, mainly on Linux |
| Local DB | SQLDelight or Room (typed queries for history, downloads, per-account data, watch stats) |
| Downloads | Kotlin queue plus ffmpeg (bundled on Android, system or Flatpak ffmpeg on Linux) |
| Third-party APIs | SponsorBlock (hash-prefix lookup), Return YouTube Dislike, DeArrow |

**Login:** WebView sign-in keeping session cookies, which gives full sync. TV/device OAuth is more stable but exposes less, so cookie login is first.

**Known tradeoff:** Compose Desktop is less polished than Flutter on Linux, and effects like blur and glass may differ slightly between Android and desktop. Linux polish is treated as ongoing refinement, not a blocker.

## 4. Reliability design: playback fallback chain

When anonymous playback is blocked (IP block, "sign in to confirm you're not a bot", 403, unplayable status), the app retries with the logged-in account, like Grayjay does.

1. Anonymous client first (fast, nothing tied to the account)
2. Retry logged in on failure
3. Alternate client types (TV, mobile, web with PO token)
4. yt-dlp as last resort

Details:

- **Remember what worked:** if anonymous keeps failing on this IP, skip to the working tier for a while, then retry anonymous later.
- **Account fallback setting:** "only on failure" (default), "always use account", or "never".
- **Diagnostics screen:** shows which tier served each video and why the others failed.
- **Account risk:** logged-in playback ties activity to the account and accounts can occasionally get flagged, hence "only on failure" as the default.
- **Swappable extractor:** all YouTube access sits behind one interface so fixes touch only that layer.
- **PO tokens:** on Android a hidden WebView can generate them. On Linux it is harder, so the yt-dlp fallback matters most there.

## 5. Architecture rules (apply from day one)

- **One typed settings schema:** every option declared once (key, type, default, scope, label, keywords). UI, backup/restore, validation and settings search all read from it.
- **Layered overrides:** global, then per-channel, then per-video.
- **Feed as a pipeline** of filter and transform steps (hide Shorts, hide watched, DeArrow titles). New rule means a new step.
- **Modular features:** SponsorBlock, RYD and DeArrow are separate modules behind a common interface, each toggleable.
- **Everything visual is data:** colours, fonts, corner radius, blur, spacing, icons and layout come from design tokens in the active Look (a single JSON object), never hardcoded.
- **Named components:** each screen is built from components with stable IDs that read config from the active Look. This enables customize-by-tapping and reorderable Home sections later.
- **Effects behind one wrapper** (blur, gradients) that can degrade gracefully for performance mode.
- **Per-account storage** for subscriptions, history and downloads from the start (cheap now, painful to retrofit).
- **Local watch history stored from day one** to power future watch stats and the local-only history option.
- **Downloads data model designed in phase 1**, even though downloads are built later.
- **Player behind an interface** (Media3 on Android, libmpv on Linux) so Chromecast and other backends can be added later.

## 6. Build phases

1. **Extractor layer:** search, video info, streams, channel, comments, fallback chain, diagnostics
2. **Playback:** player, quality choice, background audio, PiP
3. **Login and sync:** account, subscriptions, history, playlists
4. **Modules:** SponsorBlock, Return YouTube Dislike, DeArrow
5. **Downloads and offline library**
6. **Shorts options, live chat, and the customisation layer**
7. **Later:** Chromecast

Downloads sit in phase 5 because they depend on a stable extractor and playback, but the data model is designed in phase 1.

## 7. UI reference (Prism for YouTube Music)

The customisation target is the Prism app. Visual work comes after the core.

**Home screen:** gradient background that follows the current artwork or accent colour, glassy translucent cards, greeting header with quick-action buttons and a "Continue" chip, quick-access tiles (Liked, Downloads, Replay, recent playlists), yearly stats card, floating pill-shaped bottom nav with the mini player docked above it.

**Settings structure to copy the idea of:**

- Settings search bar at the top, account card with sign out, switch account and add Google account
- Performance mode toggle
- "Customize" entry with: Theme (System, Light, Dark, plus Pure black), "The whole look" (Looks, Color, Text, Icons and shapes, Glass and backgrounds), "Each part" (Tabs and mini player, Home and Library, Player, Lyrics which maps to our comments/chapters/description page), "Customize by tapping" toggle, and Reset to defaults
- Sections for Listening (playback, controls and gestures), Your content (recommendations and stats, downloads/storage/Smart Downloads with a size cap), Devices (car, speakers, TVs), Backup and about

**Takeaways:** a Look is one saved JSON object that can be switched and shared as a file. Every setting carries a label and keywords for search.

## 8. Environment

- **Android:** version 16 (rooted status not confirmed, probably irrelevant).
- **Linux:** GNOME on Wayland. Plan: GNOME-style header bar or client-side decorations, follow the system light/dark preference, MPRIS over D-Bus (dbus-java) for GNOME media controls, libnotify for download notifications, Flatpak packaging that bundles the JVM, mpv and ffmpeg.
- **Wayland caveat:** JVM desktop apps can show blurry scaling or window-drag quirks. Workable but the main rough edge of Compose Desktop.

## 9. Open questions for the first coding chat

- Confirm the Linux project layout: single KMP repo with `shared`, `androidApp` and `desktopApp` modules, versus starting with the Linux desktop target first.
- Which database library (SQLDelight vs Room KMP).
- Exact libmpv binding approach on the JVM.
- How the authenticated Innertube client handles cookie refresh and rate limits.
- Whether to bundle yt-dlp or use the system one.

## 10. How to use this doc in a new chat

The next chat is the **Linux coding project**. Suggested first task: set up the Kotlin Multiplatform project skeleton and build **phase 1 (extractor layer)** on the desktop target first, with the settings schema and extractor interface in place so Android can be added on top. Keep the architecture rules in section 5 in mind from the first file.
