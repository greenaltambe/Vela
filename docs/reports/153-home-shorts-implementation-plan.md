# 153 Implementation plan: Home groups, Shorts feed, region

## 1. Summary
The plan is in `docs/plans/home-groups-shorts.md`: six stages (region, Home parser, groups and switch, non-blocking Shorts playback, Shorts data, Shorts screen), each leaving the app working. It sets ten testable invariants, a failure table and a request budget. No app code changed.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| Seams named in the plan (`Extractor`, wrappers and forwarding test, `FallbackChain`, `AccountReads` fakes, `PlaybackSession.start`, health canaries) | exist as described | code read |
| The plan itself | not built, nothing run | n/a |
| Measurements it relies on | reports 150 to 152 | run there |

## 3. Files created/changed
`docs/plans/home-groups-shorts.md`, this report.

## 4. Decisions the spec didn't dictate
- New since report 152: look one Short ahead (so per-channel settings and the blocklist apply before it is shown); a blocked Short is skipped, not removed from the pager; the Shorts source is pinned once chosen; the hide switch is applied when drawing, not when parsing; region is built first.
- The health screen gains an anonymous Shorts check.

## 5. Known issues / unverified assumptions
- No plan can make undocumented YouTube responses work every time; the plan bounds what happens when they do not.
- The look-ahead is expected to remove the "no channel yet" limitation of report 152; unproven until built.
- Whether a field mask works on the seedless Shorts answer is to be measured in stage E.

## 6. Manual test checklist
1. Read sections 1 to 4 of the plan and object to any behaviour in the failure table.
2. Set Settings > Home > Region for content to your country.
3. Reply "build" to start stage A, and say whether to stop for your commit after every stage or to continue through all six.
