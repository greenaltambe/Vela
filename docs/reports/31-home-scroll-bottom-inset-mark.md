# 31 – Home paging + pull to refresh, bottom inset, icon curves/crop

## 1. Summary
Home loads the next page by itself near the end and supports pull to refresh. Scrolling pages now clear the floating bottom bar (the reserved height used to be measured inside the bar's margin and gesture inset). The in-app mark is no longer clipped; the sail and star in the icon art are rounder.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| Mark whole at top-left; last card clears the bar after scrolling Home | seen | phone screenshots |
| Auto-load at the end; pull to refresh | not exercised | compile-only |
| Regenerated launcher icons (rounder art) | not viewed on device | n/a |

## 3. Files changed
- `ui/screens/HomeScreen.kt` (PullToRefreshBox, onEndReached), `ui/nav/BottomChrome.kt` (height measured outermost), `BackupScreen`, `HealthScreen`, `AccountsScreen`, `AccountSafetyUi` (bottom inset)
- `ui/look/VelaMark.kt` (bigger crop, small-radius tile, rounder sail/star)
- `docs/icon/*.svg`, `docs/icon/vela-icon-1024.png`, launcher resources via `tools/make-icon.py` (sail corners r~40, plumper star)

## 4. Decisions
- The "Load more" button stays as a fallback; paging only triggers when the list is Ready and nothing is loading.
- I changed the final art in `docs/icon` (rounder sail/star) on request.

## 5. Known issues
- Other screens that lay out their own scrolling column were not all audited beyond those listed.

## 6. Manual test checklist
1. Home: scroll to the end; more videos appear without tapping. Pull down from the top: spinner, list refreshes.
2. Scroll to the bottom of Home, Library, Settings, Accounts, Health: last item fully above the bar, with the mini player open too.
3. Check the launcher icon's rounder sail/star.
