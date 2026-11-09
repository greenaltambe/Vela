# 97: Subscription groups

## 1. Summary
On the Subscriptions page, a chip row (All, your groups, Edit, New group) filters the channel strip, the Shorts row and the feed to one group. "New group" opens an editor: a name and a checklist of your subscribed channels. "Edit" (shown when a group is selected) renames, changes members or deletes (with Undo). A subscribed channel's page menu has "Add to group". A channel can be in several groups. Groups are per account and stored as one plain settings row (carried by Backup and restore).

## 2. Verification
| Check | Result | Verified-by |
|---|---|---|
| Create/rename/delete, unique names ignoring case, ids unique, toggle, several groups per channel, per account, undo of delete, revision, odd characters | `SubscriptionGroupsTest` (6) | running |
| `allTests`, build | pass | running |
| On the phone with three real subscriptions: New group editor, name typed, two channels ticked, Save: chip "Science" selected, channel strip showed only those two, Shorts row showed only their Shorts | seen | running (phone) |
| Edit chip moved next to the chosen group and shortened to "Edit" | built and installed, not re-seen | compile-only |
| Edit/rename/delete, "Add to group" from a channel page, an empty group's message | not tapped | compile-only |
| Feed list (not Shorts) under a group | the feed had just been refreshed for new subscriptions; only the Shorts row was seen filtered | partly verified |

## 3. Files changed
New: `feed/SubscriptionGroups.kt`, `ui/screens/SubscriptionGroupsUi.kt`, test `SubscriptionGroupsTest.kt`. Changed: `SubscriptionsScreen.kt`, `ChannelScreen.kt` (menu + picker), `AppServices.kt`.

## 4. Decisions
- Groups filter the page, not Home; Home is unchanged.
- The chip row only shows once you follow at least one channel.
- A deleted group comes back with Undo (members included).
- Group order is creation order; no reordering yet.

## 5. Known issues
- To test I subscribed to three channels (Kurzgesagt, NASA, Palash Muchhal - Topic) and unsubscribed afterwards. A group named "Science" is still stored; it will show up if you subscribe to channels again (Edit > Delete removes it). Cached videos from those channels stay in the feed until the next refresh.
- No notification-per-group or "play group" shortcut yet.

## 6. Manual test checklist
1. Subscriptions > New group: name it, tick channels, Save: the page filters to them.
2. Edit: rename, add or remove channels, then Delete and Undo.
3. A channel page ⋮ > Add to group: tick groups, Done.
4. Select All again: everything returns.

## 7. Design and consistency pass (after your request)
- **Group editor:** headline title, large-shape name field, a live "N channels in this group" count with Select all / Select none, channel rows with the channel's real avatar, tinted (secondary container) when ticked, in a separated list; Delete is in the error colour with a trash icon; Save/Cancel use the app buttons. Seen on the phone with your Politics group (opened and cancelled/restored, see below).
- **Add to group sheet:** rows show the group name and its channel count with a checkbox and the same tint; a new "New group" button starts a group that already contains this channel (before, groups could only be made from the Subscriptions page). Seen on the phone.
- **Chips:** the Edit chip sits directly after the selected group, so it is visible without scrolling. Seen on the phone.
- A mistake of mine: while testing the picker one of my taps ticked NASA in your **Politics** group; I put it back (Politics again has Sarthak Goswami and The Science Asylum) and checked on screen. I also subscribed to Kurzgesagt for the test and unsubscribed again; NASA was already yours. Nothing else was changed in your groups.
- Not verified: creating a group from the picker, Delete and its Undo (not tapped, to avoid touching your groups).
