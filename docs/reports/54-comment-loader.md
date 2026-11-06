# 54 Comment and replies loader

## 1. Summary
The comments list and an opened replies thread showed the video-card placeholder (a wide picture with a title line). They now show a comment-shaped placeholder: round picture, a short name line, two text lines and a likes line, shimmering like the other placeholders. In a thread the placeholders are indented like replies and use the smaller reply picture.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `:shared:compileKotlinJvm`, `:androidApp:installDebug` | pass | compile / running |
| `ScreenStatesTest`, `LiveChat*`, `LayoutInvariantsTest`, `LookBuilderTest` | pass | running |
| Placeholder look on the phone | not seen: replies load too fast to catch in a screenshot | not verified |
| Loaded replies thread on the phone | seen, unchanged | running on device |

## 3. Files created/changed
- `ui/components/States.kt`: new `CommentSkeleton`.
- `ui/screens/CommentsSheet.kt`: comments list (5 placeholders) and replies thread (3, indented) use it; the video-card placeholder import is gone.

## 4. Decisions the spec didn't dictate
- Last text line alternates in width so the stack does not look like a copy of one block.
- "Loading…" text on the "Load more" buttons is unchanged.

## 5. Known issues / unverified assumptions
- Not seen on screen. A slow network (or airplane mode, then tapping "N replies") is needed to see it.

## 6. Manual test checklist
1. Open a video, open Comments with a slow connection: five comment-shaped placeholders, not video cards.
2. Tap "N replies" on a comment: the comment, then three indented reply placeholders with small round pictures.
3. Check the placeholders in dark mode and at a large system font size.
