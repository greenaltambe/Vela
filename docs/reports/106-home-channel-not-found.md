# 106 Channel "could not be found" from Home

## 1. Summary
Home-feed videos parsed from the newer `lockupViewModel` shape had `channelId` hardcoded to `""`. Opening the channel then went through the `viaVideoId` fallback in `ChannelScreen`, which needs a successful `videoInfo` call and shows "This channel could not be found." when that fails. Search results carry the id, so they worked. The lockup parser now reads the channel id (`UC...`) from the `browseEndpoint` in the lockup metadata.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `:innertube-auth:test` incl. new `LockupChannelIdTest` (id read; blank stays blank) | pass | verified-by-running (hand-built JSON) |
| Real Home feed in the app | not run | not verified |

## 3. Files created/changed
- Changed: `innertube-auth/.../InnertubeParsers.kt`
- Created: `innertube-auth/src/test/.../LockupChannelIdTest.kt`, this report

## 4. Decisions the spec didn't dictate
- Only `UC` browse ids count, so a video's own endpoint is never mistaken for a channel.
- Left the `viaVideoId` fallback as is for lockups that carry no channel link.

## 5. Known issues / unverified assumptions
- The test JSON is built from the documented shape, not a recording; the real Home lockup may place the endpoint differently (any `browseEndpoint` under the metadata is searched, which should cover it).
- If the error still appears for some cards, the fallback's `videoInfo` call is failing for those videos: send which video.
- Shorts parsed in `ChannelBrowse.parseShort` also use a blank channelId; not touched (Shorts UI passes the channel separately).

## 6. Manual test checklist
1. Open Home (signed in, recommended feed) and tap the channel avatar/name on several cards: the channel page opens.
2. Repeat from a feed card in the "latest" / subscriptions shelves.
3. If any card still says "could not be found", note its title.
