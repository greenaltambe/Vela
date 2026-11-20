# 111 Round avatars

## 1. Summary
Channel and profile pictures are circles again everywhere they used expressive scalloped shapes, so nothing of the picture is cut away.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `allTests`, desktop + Android compile | pass | verified-by-running / compile-only |
| Look on a device | not run | not verified |

## 3. Files created/changed
- Changed: `ui/components/ChannelStrip.kt` (Following row: all pictures round, the "All" tile round too, the "has new videos" ring follows), `ui/screens/ChannelScreen.kt` (channel header picture), `ui/screens/ProfileMenu.kt` (top-bar profile button)
- Created: this report

## 4. Decisions the spec didn't dictate
- Only pictures of channels/accounts changed. Decorative expressive shapes stay: the Library playlist badge, the settings page tiles, the Shorts play badge.

## 5. Known issues / unverified assumptions
- Other places already used `Avatar` with its default circle, so they are unchanged.

## 6. Manual test checklist
1. Subscriptions > Following row: all circles; a channel with new videos has a round ring.
2. Open a channel: the header picture is a circle.
3. The top-bar profile button is a circle.
