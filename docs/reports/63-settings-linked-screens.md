# 63 Settings redesign, phase 3: the screens behind the links

## 1. Summary
Accounts, Backup, Health, Automatic downloads, Account activity and the Profile menu are on the same frame and rows as the rest of Settings (collapsing large title, one centred column, segmented rows, tonal info cards, expressive buttons). Accounts: each account is a row with an "Active" chip and an overflow menu, and Add, Privacy and Safety are rows with icons. Backup: switch rows for what to include, one large Create button, a Restore row with a preview card and Restore / Cancel buttons. Health: one row per source with an OK / Failing / Unknown chip that expands to its checks and history, a large "Run checks now" button, and a Developer section. Automatic downloads: rules as rows with a switch and a delete icon; the editor and the "add a rule" list are bottom sheets. Account activity: a row per request. Profile menu: Material list items with a divider. Accounts, Backup and Health also appear in the right pane on wide windows.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests` + `:desktopApp:test` (1,093 test cases, 0 failures) | pass | running |
| `AccountsUiTest`, `BackupUiTest`, `DownloadRulesUiTest`, `AccountSwitcherUiTest`, `NavigationUiTest`, `SettingsUiTest`, `LayoutInvariantsTest` | pass | running |
| `:androidApp:installDebug`, `:desktopApp:smokeTest` | pass | install / running |
| Desktop renders: Accounts (light), Backup (dark, "no file picker" state), Automatic downloads (dark) | seen, look right (an empty "Rules" header was found and fixed) | running (test render) |
| Health with real data, the rule editor sheet, the account menu, Profile menu, Account activity rows | not rendered or seen (the test fixture has no health service and no rules) | compile / tests only |
| Phone | not looked at | installed, not viewed |

## 3. Files created/changed
- `ui/screens/AccountsScreen.kt` (list on `SettingsScaffold`; `InfoRow`, `InfoActionRow` helpers; "Active" chip; the signed-out banner sits under the accounts so it shows when it appears; the account-activity entry is a row now).
- `ui/screens/BackupScreen.kt`, `ui/screens/HealthScreen.kt`, `ui/screens/DownloadRulesScreen.kt`, `ui/screens/AccountSafetyUi.kt` (Account activity), `ui/screens/ProfileMenu.kt`.
- `ui/settings/SettingItem.kt`: `SwitchRow` for yes/no choices that are not stored settings.
- `ui/AppShell.kt`: the shell's top bar is hidden for Automatic downloads too (it has its own).

## 4. Decisions the spec didn't dictate
- The debug "simulate a rate limit" button moved from Accounts to Health > Developer (`health.simulateRateLimit`), next to Diagnostics.
- Account rows keep tags `accounts.row.*`, `accounts.menu.*`; Backup keeps `backup.*` (the history switch is `backup.history`, the search switch is new: `backup.searchHistory`); rules keep `rules.*`; Health keeps `health.*`; the unused `accounts.s...` simulate tag is gone.
- Deleting a rule stays one tap (an icon), as before; editing opens a bottom sheet with chips that wrap and switches instead of checkboxes.
- Account activity has an `accounts.activity` entry row in Accounts > Safety (before, the text pointed to a screen nothing linked to).

## 5. Known issues / unverified assumptions
- Health's tier rows, chips and history, the rule editor sheet, the account overflow menu and Account activity were not rendered; they use the same components as the pages that were.
- Account activity rows show every request with the same plain style (no result icon).
- Diagnostics, the Downloads screen's storage card and Account activity are still full screens outside the two-pane.
- `ResetPageButton` in `SettingsHost.kt` is now unused.

## 6. Manual test checklist
1. Settings > Accounts: rows, the "Active" chip, the three-dot menu (Sign out / Remove), "Sign out everywhere" in red with its confirm dialog, "Account activity" row.
2. Backup: switch rows, "Create backup", "Restore from a file…" then the preview card and Restore (merge) / Cancel.
3. Health: Run checks now; a source row expands to its checks; "History (n earlier runs)"; Developer > Diagnostics and Simulate a rate limit.
4. Settings > Automatic downloads: Add rule opens a sheet of channels; tap a rule to edit (chips, Audio only, Wi-Fi only, Save); the switch and the delete icon.
5. Profile avatar: the menu items, Switch account (avatars), Sign out confirm.
6. A desktop window >= 840 dp: Accounts, Backup and Health open in the right pane with no Back arrow.
