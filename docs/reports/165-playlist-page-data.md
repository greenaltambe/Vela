# 165 Playlist page data; save and remove tried

## 1. Summary
Stage 2 of `docs/plans/youtube-playlists.md`: the app can now read a playlist page (title, owner, cover, counts, whether it can be saved and is saved) with its videos, signed in or out, with NewPipe as fallback. No screen uses it yet. Saving and removing a playlist on the account both work. Saved playlists do appear in the account's playlist list, so today's sync treats them as your own.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:compileDebugKotlin` | pass | run (the Android app compile is compile-only) |
| `PlaylistPageTest`, 3 new (both headers, saved/not saved/signed out/own, untrusted pages) | pass | run |
| New `PlaylistPageChainTest`, 3 tests; with the source order rule removed 2 fail | pass | run |
| Remove from library, then save again (`like/removelike`, `like/like` with the playlist id), one public playlist you had saved | both accepted; ends saved and listed, as before | run (live, **account writes**, one of each) |
| While removed | page says not saved; the library list drops it (2 lists instead of 3) | run (live) |
| Library list entry of a saved playlist | listed, with the owner's name and a channel link; Watch later and Liked say "Private" | run (live) |
| Account page: the saved public playlist | 44 videos; can be saved: yes; saved: yes | run (live, read-only) |
| Account page: Watch later, Liked videos | 28; 100 then more; can be saved: no | run (live, read-only) |
| No account: the public playlist and an 870-video list, two pages | 44; 100 + 100; can be saved: yes; saved: unknown | run (live, read-only) |
| No account: a playlist id that does not exist | failure, not an empty playlist | run (live, read-only) |
| NewPipe fallback, same two lists | 44; 100 + 100; title, owner, cover, count | run (live, read-only) |
| Signed-in playlist pages carry no sign-in prompt (used to detect a signed-out answer) | none on three pages | run (live, read-only) |
| Anything on screen | NOT APPLICABLE | nothing uses it yet |

## 3. Files created/changed
- Shared: `Extractor.kt`, `Models.kt` (`PlaylistDetails`, `PlaylistContents`), `ContinuationToken.kt`, `FallbackChain.kt`, `PacedExtractor.kt`, `TimeoutExtractor.kt`, `Captcha.kt`, `auth/Accounts.kt`.
- Sources: `PlaylistPageParser.kt`, `ChannelBrowse.kt`, `AltClient.kt`, `InnertubeAccountReads.kt`, `InnertubeClient.kt`, `NewPipeExtractor.kt`, `AuthenticatedYtDlpExtractor.kt`.
- Tests: new `PlaylistPageChainTest.kt`, new `LivePlaylistPageTest.kt`, `PlaylistPageTest.kt`, `LivePlaylistReadTest.kt`, `WrappersForwardEverythingTest.kt`.
- The plan, this report. Temporary probes and raw responses deleted.

## 4. Decisions the spec didn't dictate
- The full YouTube client is asked first for this page whatever the source order; a later page goes back to the source that issued its token.
- A failure to read a playlist page is not held against that source for video playback.
- The fallback leaves views and save state unset rather than guessing.
- The account's page read follows "Region for content".
- Without an account, pages are requested at least half a second apart.

## 5. Known issues / unverified assumptions
- **Now:** because saved playlists are in the account's list, "Sync with YouTube" imports them as synced playlists you appear able to edit (since report 164 with their videos). Stage 5 fixes it. What YouTube answers to an edit or delete of someone else's playlist was not tried.
- Removing and saving again may have changed the playlist's place in your YouTube library.
- A playlist you own other than Watch later and Liked videos has never been seen: "cannot be saved" for it is an expectation.
- No measured playlist had a description; reading it is unverified.
- The new header's save button is recognised by its icon name.
- Mixes were not checked.

## 6. Manual test checklist
1. On youtube.com: "The Rust Lang Book" is still saved in your library.
2. In Vela's Library, see whether it shows as "Synced with YouTube". If so, do not delete it or edit it until stage 5.
3. Before stage 5: make a playlist of your own with two videos on youtube.com and say so.
