# 81 Community posts and post comments (phase 2 of 2)

## 1. Summary
Comments on community posts were empty because YouTube now sends each comment as a `commentViewModel` (a key) with the text, author and counts in `commentEntityPayload` entities, while the parser only knew the old `commentRenderer`; a failed load was also shown as "No comments on this post." Both are fixed: the parser reads the new and the old shape, picks the continuation of the comment section, and a failure shows an error row with Retry and an "Open in browser" button. Post cards are redesigned (tonal card, folded long text, bounded images, 2-up image grid, poll with real share bars, video as a row, tonal Comments button).

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| Live: a channel's Community tab, a post's `FEpost_detail` and its comments continuation (a public channel, anonymous) | the detail has one continuation (the comments); the comments response has 0 `commentRenderer` and 20 `commentViewModel` + 20 `commentEntityPayload`: the old parser could only return an empty list | running (real network) |
| New parser on that real response | 20 comments, author / text / likes / time read; token found | running (throw-away test, removed; the captured response is not kept in the repo) |
| `ChannelBrowseTest`: view-model shape, old shape, comment-section continuation (hand-written minimal JSON) | pass | running |
| `ChannelTabsNativeUiTest`: comments listed, failure shows error + Retry (not an empty list), post without detail link explains and offers the browser | pass | running |
| Desktop renders of posts (poll, multi-image, video, long text) at 360 x 800 and 1280 x 800 | looked at (poll share bars fixed after the first look) | running |
| Comments sheet visual, real post on the phone | not looked at | not verified |

## 3. Files created/changed
- `innertube-auth/.../ChannelBrowse.kt`: `parsePostComments` (new + old shape), `parsePostDetail` (comment-section continuation), `postDetail` returns a failure when the comments call fails.
- `ui/screens/ChannelScreen.kt`: `PostCard` and `PostDetailContent` redone.
- Tests: `ChannelBrowseTest.kt`, `ChannelTabsNativeUiTest.kt`.

## 4. Decisions the spec didn't dictate
- First page of comments only (about 20); no paging and no replies yet (the reply count is shown). Paging needs a new method through every extractor wrapper.
- No fallback to NewPipe for post comments (it does not support them); the failure is shown instead.
- Post links were already clickable (they are URL annotations); only timestamps in post text do nothing.
- The Comments button is always shown; a post without a detail link opens the sheet with a message and the browser button.

## 5. Known issues / unverified assumptions
- Needs a check on the phone with a real post; YouTube may change the response again (the parser reads by key, not by position).
- Comment sort, replies, likes by the creator are not shown.

## 6. Manual test checklist
1. Open a channel with a Community tab, tap Comments on a post: the comments list (author, time, likes, replies count).
2. Turn off the network, open Comments: error with Retry; turn it on, Retry loads them.
3. Check a poll (bars match the shares), a multi-image post (grid), a long post (Read more), a video post (tap opens it).
4. "Open in browser" in the sheet opens the post on youtube.com.

## Addendum: replies, images, polls, filters, playlist pictures, channel search

**Found live first (anonymous, real channels):**
- Post comment replies: a thread carries `replies.commentRepliesRenderer` with its own continuation; fetching it returns the same view-model shape (checked: 1 reply parsed).
- Polls: the anonymous response has **no results at all**, only the choices and `totalVotes` ("1.8M votes"); YouTube sends shares only to a viewer who voted. So the app cannot show results without a signed-in vote; the card now shows the total and says "Vote on YouTube to see the results" (it reads the share fields too if a response ever carries them).
- Channel playlists: the playlist lockup keeps its picture under `collectionThumbnailViewModel.primaryThumbnail...`, not `thumbnailViewModel`; the parser only looked at the latter, so every playlist had no thumbnail. Fixed.

**Changes:**
- Replies: "N replies" under a comment loads them on demand (indented with a thin line, like video comments), "Show more replies" pages, errors show Retry. They go through `Extractor.replies` with a native token (`n1:`); `AltClient.replies` serves those, NewPipe returns TokenNotMine so the chain moves on.
- Images: tapping an image opens a full-screen viewer (swipe between images, pinch / double-tap zoom, close button): new `ui/components/ImageViewer.kt`. A multi-image post shows "+N more, tap to see all".
- Filter chips and shelf rows now start at the screen's side gutter (they ran to the edge).
- "Search this channel": a tonal pill with a search icon, clear button and search key.
- Tests: parser (replies token kept, playlist thumbnail), UI (replies expand, image viewer opens and closes, poll total and hint).

**Unverified:** the full-screen viewer and the replies on the phone with a real post; pinch zoom was not exercised (no touch test).
