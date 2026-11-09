# 103: Shorts options menu, Shorts order from a channel, blank notification row

## 1. Summary
- **Notification row:** "Download finished" had an empty bold line above it. The row's headline is the notification's title, and that one was saved empty (a download of a video that had no title, see report 100). Rows with no title now lead with their message ("Download finished"), and the message is not repeated underneath. The cause (titles missing on link-opened videos) was fixed in report 100.
- **Shorts from a channel play in that channel's order.** Opening a Short from a channel's Shorts tab (or its Home shelf) plays the Shorts after it in that list, in order; after the loaded ones it carries on with the rest of that channel's Shorts, and only then mixes in other channels. Before, the first batch was three Shorts per channel from a mix of channels, shuffled, fetched from the top of the channel, so the next Short was unrelated and not the next in the list. The Subscriptions Shorts row also plays the Shorts after the one tapped.
- **Shorts options menu:** a three-dot button at the top right, and hold-the-picture, open the same menu: Description, Captions (with caption style), Playback speed, Save to…, Copy link, Go to the channel, Open as a normal video.

## 2. Verification
| Check | Result | Verified-by |
|---|---|---|
| Notification row on the phone: bold "Download finished" with its time, no empty line | seen | running (phone) |
| Order: Kurzgesagt's Shorts tab reads Ketamine, Sun, Magic Mushrooms, Regrow an Organ; opened "Sun" and swiped up three times: Magic Mushrooms, Humans Can Regrow an Organ, Your Skeleton Is Electric | seen, matches the tab | running (phone, titles read from the screen) |
| Three-dot menu with all seven rows; Description sheet (title, channel, 545.9K views, 25.9K likes, 1 month ago, full text with hashtags and links) | seen | running (phone) |
| Captions: the track list, choosing English (auto-generated) drew captions over the Short; swiping to the next Short kept them on | seen | running (phone) |
| Hold the picture opens the menu | seen | running (phone) |
| `allTests`, build, no crash in the log | pass | running |
| Playback speed, Save to…, Caption style from this menu, Go to channel, Copy link | built, not tapped | compile-only |
| Order when the opened Short was in a channel Home shelf; the Subscriptions row; a channel with few Shorts (continues into others) | not tried | not verified |
| Unit test for the ordering | none: the source needs the app services; checked on the phone instead | gap |

## 3. Files created/changed
New: `ui/screens/ShortsMenus.kt`. Changed: `ui/nav/Navigator.kt` (`ShortsPlayer` carries `following` and `stayOnChannel`), `ui/AppShell.kt`, `ui/screens/ShortsScreen.kt` (menu, long press, captions that persist, ordered source), `ui/screens/ChannelScreen.kt` (`openShort`), `ui/screens/SubscriptionsScreen.kt`, `ui/screens/NotificationsScreen.kt`, `ui/player/PlayerView.kt` (caption style sheet is shared).

## 4. Decisions
- Both entry points, as you suggested: the button is discoverable, holding is quick.
- Captions are a switch for the session: once on, the next Short's default track turns on by itself; choosing Off turns them off. Caption size and background follow the same settings as the video player.
- The menu acts on the Short in view, and sheets step back to the list rather than closing.
- "Stay on the channel" only applies when opened from a channel; the Shorts tab and Subscriptions feed keep their mix.
- Not added: quality (Shorts play at the player's automatic choice), "Not interested", comments in the menu (they have their own button).

## 5. Known issues
- A channel's Shorts tab sorted by Popular or Oldest is followed in the sort you had open (the list is what you saw).
- If the extractor cannot continue a channel's Shorts, the feed moves on to other channels instead.
- Captions are not drawn in the Shorts mini preview or on desktop (not run).

## 6. Manual test checklist
1. Open a channel, Shorts tab, tap the third Short, swipe up: the fourth plays.
2. Three-dot menu: Description, Captions (pick one, swipe on), Speed, Save to…
3. Hold the picture: the menu opens.
4. Notifications: no empty line on "Download finished".

## 7. Follow-up: same components as the long videos (after your question)
You asked whether the options could reuse the long-video components. Most already did: the menu sheet and rows (`PlayerMenuSheet`, `PlayerMenuRow`), the speed picker, the Save-to sheet and the caption style sheet are the very ones the video player uses. **The Description was the exception:** I had written a simpler sheet of my own, so it looked different. I could have reused the real thing and should have; now I did.
- Description now opens the video page's own `DescriptionPanel` ("About this video": likes / views / date chips, transcript, tags, working links), in the same place Shorts already show comments: under the picture, which shrinks to make room. Comments and Description share that slot (opening one closes the other); Back or the X closes it. My own description sheet is deleted.
- The three-dot button hides while a panel is open (with the picture shrunk it crowded the like button).
- Seen on the phone: the panel with two different Shorts, the button hidden while it is open, Back closing the panel and the button returning. `allTests` pass.
- What still differs from the long-form player, on purpose: the entry point (a three-dot button and hold, the long video has its gear and CC buttons), and the Shorts-only rows ("Go to channel", "Open as a normal video"). The panel's chapters row is empty for Shorts (they have none).
