# Later: search channels in the New group / Edit group dialog

Status: deferred by the owner. Not started.

## Problem
Subs > "New group" (and editing a group) opens a dialog with a Name field and "Channels in this group": a plain, alphabetical list of
every subscribed channel with a checkbox, plus "Select all". With hundreds of subscriptions there is no way to find the channel to add
except scrolling the whole list.

## Idea
Add a search field above the list in that dialog that filters the channels by name as you type.

## Details to design
- Local filter on the user's own subscriptions, case-insensitive on the channel name; no network.
- Keep ticked channels ticked when the filter changes (a hidden channel stays selected). Show "N selected" so this is visible.
- "Select all" must act on the visible (filtered) channels only, or be relabelled "Select shown", so it does not silently tick hundreds.
- Optional "show selected only" toggle, to review what is in the group.
- Desktop: focus the search field when the dialog opens? (The Name field has focus first; decide the order, and keep Tab / Enter sensible.)
  Android: keyboard should not cover the list; the dialog is a bottom sheet there.
- Edit mode: ticked channels (current members) could be listed first.
- Group logic: `shared/.../feed/SubscriptionGroups.kt`; the dialog is in the Subs screen UI. The dialog list should stay smooth with
  several hundred rows (see `ListQueryBudgetTest`).
- Tests: the filter and the selection-kept-while-filtering logic are plain functions and can be unit-tested offline; the layout is a
  manual test on both platforms.
