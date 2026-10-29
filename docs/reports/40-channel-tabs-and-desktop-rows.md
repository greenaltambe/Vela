# 40 – Channel tabs, sorting pills, sideways rows on Linux

## 1. Summary
The channel page has tabs: Home (description and the latest uploads), Videos, Shorts (only while Shorts are on), Live, Playlists and About. Videos, Shorts and Live have sorting pills (Latest, Popular, Oldest); Playlists has Recently added and A to Z. A playlist opens its videos on a new page. Sideways rows (Subscriptions channels and Shorts, the search start page) now scroll on Linux: mouse drag, sideways or Shift + wheel, and round arrows at the row's ends. The Shorts row on Subscriptions is hidden when Shorts are globally off, read live from the setting.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` (including the wrapper test that every wrapper forwards the new extractor methods) | pass | running |
| Phone: tabs row on a channel, Videos tab with Latest / Popular / Oldest pills, Live tab with its empty message | seen | phone screenshots |
| Shorts, Playlists and About tabs, opening a playlist, sorting results, the sideways rows with mouse/arrows on Linux, Shorts row hidden with Shorts off | not exercised | compile-only |

## 3. Files changed
`ChannelScreen.kt` (rewritten), `ChannelPlaylistScreen.kt` (new), `Navigator.kt` (`Route.ChannelPlaylist`), `AppShell.kt`, `HScrollRow.kt` (new), `SubscriptionsScreen.kt`, `SearchScreen.kt`; extractor: `Extractor.kt` (`channelLive`, `channelPlaylists`, `playlistVideos`), `Models.kt` (`PlaylistRef`), `NewPipeExtractor.kt`, `ItemMapping.kt` and the wrappers (`FallbackChain`, `TimeoutExtractor`, `PacedExtractor`, `CaptchaAwareExtractor`, `StubExtractor`, `AuthenticatedYtDlpExtractor`), `WrappersForwardEverythingTest`

## 4. Decisions
- Sorting works on what has loaded so far (the source's own order is newest first); a note says so. YouTube's own server-side sort is not available from NewPipe.
- "Home" is not YouTube's composed channel home (not available): it is the description and the latest uploads, with "See all videos".
- yt-dlp has no Live, Playlists or playlist support here (they answer "not supported" and the tab says so).

## 5. Known issues
- A tab without data from the source shows an error or an empty message rather than hiding.
- On Linux the sideways rows do not follow a plain vertical wheel (that keeps scrolling the page).

## 6. Manual test checklist
1. Open a channel: tabs; Videos > Popular / Oldest; Shorts; Live; Playlists (open one, scroll it); About.
2. Subscriptions on Linux: drag the channel and Shorts rows, use Shift + wheel and the arrows.
3. Settings > Shorts > Hide: the Shorts row on Subscriptions and the Shorts tab on a channel disappear.
