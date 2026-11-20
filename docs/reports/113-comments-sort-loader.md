# 113 Comments: loader when changing the sort

## 1. Summary
Choosing another comment order set the list to Loading, but the old comments stayed in the list under the placeholders, so the placeholders sat above the scrolled-out old rows and nothing seemed to happen. While a new order loads, the list now shows only the placeholders (and no "Load more" button) and jumps to the top; the new comments replace them when they arrive.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `allTests`, desktop + Android compile | pass | verified-by-running / compile-only |
| Behaviour in the comments sheet | not run | not verified |

## 3. Files created/changed
- Changed: `ui/screens/CommentsSheet.kt`
- Created: this report

## 4. Decisions the spec didn't dictate
- If the new order fails, the old comments come back with the existing note (unchanged logic: the controller keeps them).
- The list also scrolls to the top when switching Top / Newest / Timed.

## 5. Known issues / unverified assumptions
- The placeholders are the existing comment skeletons, not a spinner.

## 6. Manual test checklist
1. Open comments, scroll down a little, tap Newest: the list jumps to the top and shows placeholders until the new comments arrive.
2. Tap Top again: same.

## Follow-up: Notifications showed old videos as "19 minutes ago"
Cause: `FeedService.refresh` treated every video missing from the cache as a new upload and stamped the notification with the time of detection. A channel just subscribed (import, account sync) brings its whole recent list, and a video that only now reached the feed can be weeks old.
Fix (`feed/FeedService.kt`): an upload is announced only if it was published within the last 3 days AND its channel already had videos in the cache (so a newly added channel announces nothing), and its date is known. New test `NewUploadNotificationTest` (old, fresh and newly-subscribed cases) and `allTests` pass; not run in the app.
Not changed: notifications already stored stay until you clear them (Notifications page); the "ago" shown is still the time the app noticed the upload.
Test: subscribe to or sync a channel, refresh Subscriptions: no notifications appear for its existing videos; a genuinely new upload still notifies.
