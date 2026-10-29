# 29 – Channel page "Unable to download webpage"

## 1. Summary
Phone log (`files/vela.log`) shows every failing channel open was `channel()` with an empty id: NewPipe fails `length=1; index=1`, yt-dlp requests `/channel//videos` and gets HTTP 404 ("Unable to download webpage"). Some listings produce a `VideoSummary` with a blank `channelId` (NewPipe mapping leaves it empty when the item has no uploader URL). Fix: never open a channel with a blank id; resolve it from the video first.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| Root cause (log lines, blank id, 404) | confirmed | phone log |
| `./gradlew allTests` | pass | running |
| Installed debug build | done | install only |
| Opening a channel from an affected card | not tested | n/a |

## 3. Files changed
- `ui/screens/Lists.kt` (channel tap: blank id -> `videoInfo(v.id)` then open)
- `ui/screens/VideoScreen.kt` (channel row prefers the loaded info's channel id)

## 4. Decisions
- Fix at the tap sites, not the extractors, so every source is covered. If `videoInfo` also fails, nothing opens (no error page).

## 5. Known issues
- Root cause of the blank ids in NewPipe/yt-dlp listings is not fixed; the subscribe/unsubscribe button in VideoScreen still uses `video.channelId` and may act on a blank id for such videos.
- Other errors (real network/429) are untested here.

## 6. Manual test checklist
1. Open a creator's channel from Home, Search, a Shorts-like card and the video screen's channel row; each opens.
2. If one still errors, send the last `channel(` lines of `files/vela.log`.
