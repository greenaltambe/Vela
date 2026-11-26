# 169 Version 0.6.1-beta.7

## 1. Summary
`VERSION_NAME` is 0.6.1-beta.7. The "Unreleased" changelog entries (playlist page, saved playlists, playlist sync fixes, navigation icons) moved under that heading, with the compare links updated.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:compileDebugKotlin generateThirdPartyNotices` | pass; 882 tests re-ran, the rest were unchanged since the 2,149-test run of report 168 | run (the Android app compile is compile-only) |
| Third-party notices | unchanged | run |
| Release notes for 0.6.1-beta.7, extracted with the workflow's own command | found | run |
| `./gradlew -p buildSrc test` | NOT RUN (does not run on this machine's JDK) | CI |
| Release workflow, APK, packages | NOT RUN | needs the owner |

## 3. Files created/changed
`version.properties`, `CHANGELOG.md`, this report.

## 4. Decisions the spec didn't dictate
None.

## 5. Known issues / unverified assumptions
- Reports 167 and 168 list what was not tried on a phone or against your account.
- The database moves from version 12 to 13; Android does not allow installing the older build over this one.

## 6. Manual test checklist
1. Commit and push; wait for CI to pass.
2. Tag and push the tag.
3. Install the released APK over beta.6 and check that your playlists are all there.
4. Run the checklist of report 168.
