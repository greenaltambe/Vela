# 112 Filter pills without the check mark

## 1. Summary
The check mark in front of the selected filter pill is gone. Selection shows by colour and the toggle button's shape only, and the pill no longer changes width, so neighbours do not shift.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `allTests`, desktop + Android compile | pass | verified-by-running / compile-only |
| Look on a device | not run | not verified |

## 3. Files created/changed
- Changed: `ui/components/ExpressiveChip.kt` (the one chip used by Home, channel sort/filters, comments sort, Search types, Explore, Subscriptions groups, download rules, player menus, description sheet)
- Created: this report

## 4. Decisions the spec didn't dictate
- Left as they are: list rows that mark the chosen option with a check (settings choices, player quality/speed menus), the "Active" account tag, Like/Save buttons (their icon swaps to the filled form, same width). Tell me if any of those should lose the check too.

## 5. Known issues / unverified assumptions
- None known; no other chip type in the code adds a check.

## 6. Manual test checklist
1. Home chips: select each, no check, no width change.
2. Channel Videos sort chips (Latest / Popular / Oldest) and the comments sort chips: same.
