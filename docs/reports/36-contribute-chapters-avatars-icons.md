# 36 – Contribute, chapters sheet, channel avatars, channel loader, menu icons

## 1. Summary
Contribute is a consistent card (header row that opens it; same primary/secondary button family) and is hidden unless "Show Contribute under videos" is on (Settings, modules). The current chapter's name sits above the seek bar; tapping it opens a chapters bottom sheet in the app's look (the inline chapter list and the "Chapter:" line on the page are gone). Channel pictures now show on video cards and the video page (NewPipe items and the account's Home feed carry them), and are saved when you subscribe. Opening a channel is immediate; if a listing has no channel id the channel page finds it itself behind its loader. The press highlight of "more", channel rows, avatars and chapter rows is clipped to the corner radius and padded. The three-dot menu is horizontal in every icon pack and the card menu shows a clock for Watch later and a list icon for other playlists (new icon `watchLater` in all packs).

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| Live probe: NewPipe trending 15/15, search 20/20, related 20/20 items carry an uploader avatar | measured | network (temporary probe, removed) |
| Phone: horizontal dots, rounded card menu with the Watch later clock, channel pictures on Home cards | seen | phone screenshots |
| Contribute hidden by default / card design, chapters title + sheet, channel loader for id-less listings, press highlights | not exercised | compile-only |

## 3. Files changed
- Settings/icons: `Settings.kt` (`modules.showContribute`), `VelaIcons.kt`, `Material*.kt`, `LucideIcons.kt` (`more` horizontal, `watchLater`), `screens/Common.kt` (menu icons)
- Video page: `ModuleWidgets.kt` (Contribute card), `ChaptersSheet.kt` (new), `PlayerView.kt` (chapter title, sheet), `VideoScreen.kt` (avatars, no inline chapters), `Components.kt` (SecondaryButton, press highlights, avatar highlight)
- Avatars/channel: `Models.kt` (`channelAvatarUrl`), `ItemMapping.kt`, `InnertubeParsers.kt`, `Navigator.kt` (`Route.Channel.viaVideoId`), `ChannelScreen.kt`, `Lists.kt`

## 4. Decisions
- Avatars come with the items; history, downloads and other lists built locally still show the first letter. A collaboration video from the account's feed may also show a letter (YouTube gives it no single channel picture).
- The chapters sheet replaces the inline list rather than sitting beside it.
- Only Contribute and the chapters sheet use the new `SecondaryButton`; other outlined buttons (player error actions, etc.) still use Material's pill shape.

## 5. Known issues
- Home loaded from the account's recommended feed depends on guessed InnerTube shapes for avatars (confirmed for ordinary videos on the phone, not for lockup items).

## 6. Manual test checklist
1. Settings > Extras/modules: turn on "Show Contribute under videos": the card appears under a video; off: it is gone. Check its buttons look like the rest.
2. Play a video with chapters: the chapter name shows above the seek bar; tap it: a rounded bottom sheet lists chapters; tap one to seek.
3. Open channels from Home, Search, History: the page opens at once with skeletons.
4. Open a card's menu: Watch later has a clock; the dots are horizontal everywhere (try another icon pack).
