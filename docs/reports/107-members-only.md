# 107 Hide members-only videos / badge

## 1. Summary
New setting "Hide members-only videos" (Settings > Subscriptions & feed filters, global with per-channel override, default off). When off, members-only videos show a "MEMBERS ONLY" badge on the thumbnail. Detection was added for all three sources (NewPipe, InnerTube feeds/channel pages, yt-dlp).

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `allTests`, `:innertube-auth:test`, `:extractor-newpipe:test`, `:extractor-ytdlp:test` | pass | verified-by-running |
| New tests: HideMembersOnlyTest (off/on/per-channel), MembersOnlyParseTest (classic badge style, lockup badge text) | pass | verified-by-running (hand-built JSON) |
| Android + desktop compile | pass | compile-only |
| Real members-only video shows badge / is hidden | not run | not verified |

## 3. Files created/changed
- Model/setting: `extractor/Models.kt` (`VideoSummary.isMembersOnly`), `core/settings/Settings.kt`, `SettingCopy.kt`, `SettingsPages.kt`
- Filter: `feed/FeedSteps.kt` (`HideMembersOnly`, added to the standard pipeline; not the Shorts pipeline)
- Detection: `extractor-newpipe/.../ItemMapping.kt` (ContentAvailability.MEMBERSHIP), `extractor-ytdlp/.../YtDlpMapping.kt` (availability == subscriber_only), `innertube-auth/.../InnertubeParsers.kt`, `ChannelBrowse.kt`
- UI: `ui/components/Components.kt` (badge, test tag `card.membersOnly`)
- Tests: `HideMembersOnlyTest.kt`, `MembersOnlyParseTest` in `LockupChannelIdTest.kt`; this report

## 4. Decisions the spec didn't dictate
- Filter applies wherever the standard feed pipeline runs (Home, Subscriptions, search lists, related), not on a channel's own page: there the videos stay visible with the badge.
- Badge uses the tertiary colour; it shares the top-left slot with LIVE/SHORT (LIVE and SHORT win).
- InnerTube detection is a text match on "MEMBERS_ONLY" / "Members only" / "Members first" in the renderer.

## 5. Known issues / unverified assumptions
- InnerTube badge shapes are from documented/known formats, not recorded; NewPipe's MEMBERSHIP flag is only set when YouTube marks it in the list item. Some sources may miss it (then no badge, not hidden).
- The RSS subscription feed carries no members flag, so those entries can't be hidden or badged.
- Not covered by the pipeline: downloads, history, playlists.

## 6. Manual test checklist
1. Settings > Subscriptions & feed filters: find "Hide members-only videos" (also via search "members").
2. Off: open a channel with members-only videos (e.g. its Videos tab, search for it): badge "MEMBERS ONLY" on those.
3. On: those videos vanish from search/Home/Subscriptions lists.
4. Per channel override: turn on globally, off for one channel: its members-only videos show again.
5. Note any members-only video shown without a badge and which screen it was on.
