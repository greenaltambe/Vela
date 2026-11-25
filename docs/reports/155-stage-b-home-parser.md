# 155 Stage B: Home parser that keeps groups apart

## 1. Summary
New `HomeFeedParser` reads a Home response into feed videos, YouTube's groups (Shorts rows and other rows, with title and position), the next-page token and an accounting of every item. It is **not wired into the app yet**, so nothing looks different; stage C does that.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:compileDebugKotlin` | pass | run (the Android app compile is compile-only) |
| `HomeFeedParserTest`, 19 tests on hand-built responses | pass | run |
| Live, signed in, `gl=US`: 4 pages of All and every chip | every top-level item counted; 0 videos lost against the old flat read; no fallback | run (live, read-only) |
| Live: next-page token | same as the old reading on every page compared; two chips have no continuation command anywhere | run (live) |
| Live: groups found | All page 1: two Shorts rows of 9. One topic chip (Gaming): four rows of 12, and one games row left out. | run (live) |
| Live: unknown items | one on the first run (`feedCustomizationFormViewModel`, the custom-feed edit form); added to the known non-videos; none after | run (live) |

## 3. Files created/changed
- New: `innertube-auth/.../HomeFeedParser.kt`, `HomeFeedParserTest.kt`, `liveTest/.../LiveHomeFeedParseTest.kt`.
- `InnertubeParsers.kt`: the single-item readers are now functions (`videoRenderer`, `videoLockup`, `shortItem`); `videos()` uses them and behaves as before.
- `ChannelBrowse.kt`: its Shorts reader and count parser moved to `InnertubeParsers.kt`.
- `shared`: `FeedGroup`, `FeedGroupKind` (`Accounts.kt`); `VideoSummary.shortsSequence`.
- Plan status, this report.

## 4. Decisions the spec didn't dictate
- A card counts as a Short only if its own tap opens the Shorts player; a Shorts link elsewhere in the card (a menu) does not.
- An unknown top-level item that holds videos becomes a group, so its videos never enter the feed.
- The flat fallback also applies when nothing can be read from the list although the response holds videos.
- Advertisements are never read as videos, wherever they sit.
- Playlists, mixes, podcasts and courses in the feed stay left out, as today, and are now counted by name.
- A group with no title is called "More from YouTube".

## 5. Known issues / unverified assumptions
- "Most hyped" is still unseen; covered only by a made-up unknown section in the tests.
- The live run used `gl=US` (the bundled client); the region has no bearing on the parser's shapes as far as measured.
- A video that appears both in the feed and in a group, or in two groups, is not yet de-duplicated across them (stage C).
- The classic `videoRenderer` shapes are tested only on hand-built data; the live Home uses lockups.

## 6. Manual test checklist
1. Nothing user-visible changed in this stage. Open Home, a chip, History and a channel's Shorts tab: they should look and load as before.
