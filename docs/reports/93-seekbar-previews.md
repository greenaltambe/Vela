# 93: Seek-bar preview thumbnails (storyboards)

## 1. Summary
While you drag the seek bar, the preview card above the finger now shows the real picture for that position (YouTube's storyboard), not the video's static poster. The card works for videos opened from a link too (they have no poster). Videos without a storyboard keep the old card (poster or time only). Source: NewPipe's `previewFrames`; other tiers have none, so no preview there (nothing breaks).

## 2. Verification
| Check | Result | Verified-by |
|---|---|---|
| Real storyboard data (measured on 3 videos) | 160x90 frames in 5x5 sheets, one frame every 2 s (3:36 video, 5 pages) and every 5 s (10:34 video, 6 pages); the 2005 "Me at the zoo" has none | running (live probe against YouTube, removed afterwards) |
| Page/cell lookup incl. clamping, wrong numbers, too few pages | `StoryboardTest` (6) | running |
| Level choice (smallest level at least 160 px wide, else the largest) | by code reading and the live numbers | partly verified |
| Preview card on the phone while scrubbing Big Buck Bunny | frame from the meadow scene at 2:04 shown in the card | running (phone) |
| `allTests`, `:extractor-newpipe:test`, `:innertube-auth:test`, build | pass | running |
| A video with no storyboard on the phone, desktop, light theme | not checked | not verified |
| Time to load the first sheet on a slow connection | not measured | not verified |

## 3. Files changed
`extractor/Models.kt` (`Storyboard`, `StoryboardFrame`, `VideoInfo.storyboard`), `extractor-newpipe/.../StreamMapping.kt` (`bestStoryboard`), `ui/components/Scrubber.kt` (card + `StoryboardPicture`), `ui/player/PlayerView.kt` and `ui/screens/VideoScreen.kt` (pass it in), test `StoryboardTest`.

## 4. Decisions
- A sheet is fetched when the finger first reaches a frame in it (one 160x90x25 JPEG, tens of KB); the poster (when there is one) shows until it arrives. No prefetch of all pages (a long video has dozens).
- One quality level (about 160 px wide), chosen for download size.
- The picture area appears whenever there is a frame or a poster; before, a missing poster dropped the picture entirely (this was found on the phone).

## 5. Known issues
- Only the NewPipe tier supplies storyboards; if the video info came from another tier there is no preview.
- Live streams have no storyboard.
- Desktop uses the same code but was not run.

## 6. Manual test checklist
1. Open a normal video, drag along the seek bar: the card follows the finger with a changing picture and the time.
2. Scrub far (past a sheet boundary): a short blank/poster moment, then the picture.
3. Open a very old video or a live stream: the card shows time (and poster) only.

## 7. Crash found by you, and its fix
Scrubbing forward on a long video crashed the app. The crash log on the phone showed `IllegalArgumentException: Width and height must be greater than or equal to zero` inside `StoryboardPicture`: when the finger crossed into a new sheet, the image painter briefly reported no size (NaN), my check only rejected values <= 0 (NaN passes), and Compose threw while drawing. Fix: the placement maths is now a function that returns nothing for any NaN, infinite, zero or negative input (`storyboardPlacement`), tested by `StoryboardPlacementTest` (4 tests). Afterwards I scrubbed the whole 10-minute (6-sheet) video six times on the phone: no crash entries in the log.
