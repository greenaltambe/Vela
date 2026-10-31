# 47 Beta fixes (desktop controls, share, region, icon, channel sort, mini-player grow, YouTube links)

## 1. Summary
Desktop player buttons now show in the windowed player (they were only built for fullscreen and the phone). Share copies the link on desktop, and every video "three dots" menu has Share (Copy link on desktop). New "Region for content" setting. The desktop window class now really becomes `Vela` (measured), which is what the dock uses to pick the icon. Channel sort Popular/Oldest is now done by YouTube over all videos (measured live). The video page grows out of the mini player on open. YouTube links open in Vela on Android, via `vela <link>` on Linux, and when pasted into Search.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| `:extractor-newpipe:liveTest channelVideosSortedAcrossAllVideos` | pass: Popular and Oldest first pages differ from Latest, page 2 works, no overlap | running (real YouTube) |
| Raw YouTube check before coding: Latest/Popular/Oldest chips each return a different first video | confirmed | running (curl) |
| Desktop window class and icon | `xprop`: WM_CLASS was `com-greenaltambe-vela-MainKt`, now `Vela`; `_NET_WM_ICON` now 256/128/64/48 | running |
| Desktop player buttons visible | fix by code reading (bottom row was `if (fullscreen)` only); not seen on screen | compile-only |
| Share / Copy link, region setting UI | not seen on screen | compile-only |
| Mini player grow animation | Android compile only; desktop wide windows skip it | compile-only |
| `YoutubeLinkTest` (parser) | pass | running |
| Android link intents, `vela <link>` + single instance | not run | compile-only |

## 3. Files created/changed
- Player: `ui/player/PlayerView.kt` (menu buttons and fullscreen button also in the desktop window).
- Share: `ui/screens/Common.kt` (`ShareMenuItem`, used by `VideoMenu`), `LibraryScreens.kt` (playlist item menu), `VideoScreen.kt` (note "Link copied").
- Region: `core/settings/Settings.kt` (`content.region`), `SettingsPages.kt` (Home page), `NewPipeExtractor`/`NewPipeBootstrap` (`region` read on every call), `DesktopWiring.kt`, `PlaybackRuntime.kt`.
- Channel sort: `Extractor.kt` (`ChannelVideoSort`, `channelVideosSorted`), wrappers (`Paced`, `Timeout`, `Captcha`, `FallbackChain`), `NewPipeExtractor.kt`, `ChannelScreen.kt`, `ChannelStore.kt`, `libs.versions.toml` + `extractor-newpipe/build.gradle.kts` (NewPipe's own nanojson, same commit it already depends on), tests (`WrappersForwardEverythingTest`, `ForeignContinuationTest`, live test).
- Icon: `desktopApp/.../main.kt`.
- Mini player: `Navigator.kt` (`growFromMini`), `VideoScreen.kt`, `AppShell.kt`.
- Links: new `library/YoutubeLink.kt` + test, `Navigator.openYoutubeLink`, `SearchScreen.kt`, `AndroidManifest.xml`, `MainActivity.kt`, new `desktopApp/.../SingleInstance.kt`, `main.kt`, `packaging/linux/vela.desktop` (`Exec=... %U`).

## 4. Decisions the spec didn't dictate
- Region: a pick-one list of 52 countries, default US (the old fixed value), language stays English. It changes NewPipe's content country (trending, anonymous recommendations). Not applied to yt-dlp or the alternative-client tiers.
- Channel sort: Videos tab only. Shorts and Live pills still sort what is loaded (the note stays there). If the source cannot sort (other tiers), the page falls back to sorting loaded videos with the old note.
- The grow animation reuses the existing pull-down state in reverse (page starts "pulled down", animates to 0), so start and end positions match the minimise animation. Skipped for windows 1000 dp or wider and with reduced motion.
- Linux: browsers do not hand `https` links to apps, so true "default handler" is not possible there. What works: `vela <link>` (also `xdg-open`-style launchers, `Open with`), a running Vela receives it (single instance over a loopback socket, token in a user-only file in the data dir), and pasting a link in Search.
- Android: VIEW for youtube.com, m., music., youtu.be and SEND text/plain. Channel `@handle` links are not recognised (only `/channel/UC…`), playlists open as a YouTube playlist.

## 5. Known issues / unverified assumptions
- Desktop controls: I could not see the window (Wayland); the cause is from code. If buttons are still missing, tell me which.
- Grow animation: Android's `PlayerView` is a SurfaceView; whether it follows the page's scale/translation without a glitch is unmeasured.
- Opening a link shows the video page with an empty title until its details load.
- Starting a second `vela` while the first is stuck at start answers nothing; the stale `instance` file in the data dir is then taken over.
- Region only affects what YouTube returns anonymously; cached Home/trending lists refresh on the next load.
- Sorted channel lists use YouTube's chip order Latest, Popular, Oldest (English labels checked; a layout change would fail the sort and fall back).

## 6. Manual test checklist
1. Desktop, open a video in the window, hover: mute, volume, captions, quality, speed, sleep and fullscreen buttons appear.
2. Desktop, Share on the video page: "Link copied" appears; paste elsewhere. Open a video's three dots in Home, Search, a channel, a playlist: "Copy link" there; on the phone it says "Share" and opens the sheet.
3. Settings, Home: "Region for content"; pick Japan, reload Home (trending) and check it changes.
4. Rebuild the desktop package, start it: dock/alt-tab shows the Vela icon (also check `./gradlew :desktopApp:run`). The GNOME dock still needs the installed `.desktop` entry to match.
5. A channel with many videos, Videos tab, Popular: the most-viewed video of the channel is first (not just of the loaded ones); scroll for more; Oldest likewise.
6. Phone: play a video, minimise, swipe up (and tap) on the mini player: the picture grows into the player. Also with the PiP-style mini player.
7. Phone: tap a youtu.be link in another app / Messages; on Android 12+ first enable Settings, Apps, Vela, Open by default, Add links. Share a YouTube link to Vela from the YouTube/Chrome share sheet.
8. Linux: `vela https://youtu.be/dQw4w9WgXcQ` with Vela closed (opens), and again with it running (same window opens it, no second window). Paste a YouTube link into Search on any platform.

## Follow-up (rpm icon, shortcuts, mini-player drag)
- rpm icon: jpackage replaced `lib/vela.png` with its default 32x32 Duke when wrapping the app image; `packaging/linux/vela.png` (resource dir) now overrides it. Verified by unpacking the rebuilt rpm: 1024x1024 Vela icon (RUN). The dock icon after reinstall is not seen (may need a re-login for the icon cache).
- Shortcuts: j/l = 10 s back/forward, k = play/pause, c = captions on/off (first track); m, f, Escape, space, arrows already existed. Focus is taken back by the page when nothing holds it (compile + unit test `ShortcutsTest`, not tried on the desktop).
- Mini-player drag (Android, compile-only): `Navigator.driveExpand/finishExpand`; the page opens at the first 24 dp of drag in its pulled-down state and follows the finger; release past 1/3 (or a flick) completes, else it goes back. Mini card fades as it goes. Not tested on a device; the PiP-style mini is still tap only.
- Fullscreen cursor (desktop): transparent pointer while fullscreen and the controls are hidden. Seamless mini-player picture: page picture starts/ends on the mini's real picture bounds, card stays put and hides its own picture while dragging (Android, compile-only).
- Refresh (desktop): `RefreshBox` replaces pull-to-refresh on Home, Explore, Subscriptions with a corner button and Ctrl+R (the only pages that had pull to refresh).
- Space hold: on the video page keys are now taken in the preview pass (a clicked button used to eat Space); a release counts only if no press follows within 90 ms (X11 repeat sends release+press pairs, which ended the hold and toggled pause); `PlaybackSession.heldSpeed` drives the "2x speed" badge for keyboard and touch. Not tried on the desktop (compile + `allTests` only).
