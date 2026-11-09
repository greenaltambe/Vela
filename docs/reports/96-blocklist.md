# 96: One blocklist (channels and words) everywhere

## 1. Summary
Settings > Your feed > "Blocked channels & words" holds one list. Blocked channels and title words are hidden in Home (all shelves), Search (videos, and channel results), Explore, Subscriptions (feed and Shorts row) and Related. Block a channel from any video's ⋮ menu ("Block <name>", with Undo) or a channel page's menu ("Block channel" / "Unblock channel"). The list can be copied as text to share and pasted back (also accepts plain words, one per line).

## 2. Verification
| Check | Result | Verified-by |
|---|---|---|
| Rules (channel id, word ignoring case, blank entries never match all), storage without duplicates, tabs/newlines in names, export then import, revision per edit, feed step order and empty list | `BlocklistTest` (9) | running |
| `allTests` (incl. the Settings pages summary test, updated), build | pass | running |
| Home: "Block StevenCrowder" from the video menu: the video disappeared at once, toast with Undo | seen | running (phone) |
| Settings screen: list shows the channel and a word ("prank") added by typing; single header after a fix (the shell bar was doubling it) | seen | running (phone) |
| Unblock, Copy list, Paste list, Undo on the manage screen, Search results, channel-page menu | built, not tapped | compile-only |
| Words on a real list, light theme, desktop | not checked | not verified |

## 3. Files changed
New: `feed/Blocklist.kt` (`Blocklist`, `BlockRules`, `BlocklistStep`), `ui/screens/BlocklistScreen.kt`, test `feed/BlocklistTest.kt`. Changed: `feed/FeedSteps.kt` (both pipelines take the rules), `AppServices.kt` (`blocklist`), `ui/screens/Common.kt` (pipeline, `rememberFiltered` refilters on edits, video menu item), `SubscriptionsScreen.kt`, `SearchScreen.kt` (channel results), `ChannelScreen.kt` (menu), `nav/Navigator.kt`, `AppShell.kt`, `SettingsPages.kt`, `SettingsHost.kt`.

## 4. Decisions
- One list shared by all accounts; stored as two plain settings rows, so Backup and restore carries it.
- Words are plain substrings of the title, ignoring case (no regex; the existing keyword filter in Settings > Feed keeps regex and per-channel rules).
- Not applied to: a channel's own page, History, playlists, downloads, notifications, and the Shorts player's own feed (these are things you opened or saved on purpose). Blocking a channel you are subscribed to hides it in Subscriptions too; unsubscribing is separate.
- Home's pre-existing hint "N hidden by your feed filters (Settings > Feed)" now also counts blocked videos but still points to Settings > Feed.

## 5. Known issues
- The Shorts player and Notifications do not honour the blocklist yet.
- Sharing is by copying text; there is no file export.

## 6. Manual test checklist
1. Block a channel from a video's ⋮ menu on Home; Undo restores; then block it again.
2. Settings > Blocked channels & words: add a word, see matching titles vanish from Home and Search; tap the cross to unblock.
3. Copy list, then on another install paste it.

## 7. Design and consistency pass (after your request)
Audited the new screens against the rest of the app and Material 3 Expressive; fixed:
- **Blocked screen:** now opens with the tonal info card used by other settings pages; the word field is the app's pill field (same as Search) with the add button inside it, as an expressive `IconButtonDefaults.shapes()` button (was an outlined field and a separate button); blocked words are **input chips** (the Material component for removable typed entries, wrapping in a flow row, tap to remove); blocked channels are segmented list rows with the channel's initial as an avatar and "Tap to open its page"; Copy list is disabled when the list is empty; section titles line up.
- **Blocklist now also applies to** the Shorts player (blocked channels are skipped and their Shorts dropped), in-app notifications, and new-upload notifications (no row and no system alert for a blocked channel, including from the background refresh).
- **Hints** on Home and Search now read "hidden by your filters or blocklist" (they pointed to Settings > Feed only).
- **Playlist Shuffle** uses the app's `SecondaryButton` (was a raw outlined button).
- Verified on the phone: new Blocked screen, Copy then Paste ("Nothing new to add" the second time).
- Not verified: Shorts player and notification filtering on the phone (code and build only).

## 8. Also in this pass (queue and player)
- Queue drag: a haptic when a row is picked up and a confirm when it lands in a new place.
- The volume indicator shows a speaker (or muted) icon.
