# 160 Version 0.6.1-beta.6

## 1. Summary
`version.properties` is `0.6.1-beta.6`; `CHANGELOG.md` has the matching section (Home's YouTube groups and switch, the Shorts feed, region, Lightning seek bar, Gold scheme, fixes) and compare links. Nothing was committed, tagged or pushed.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:assembleDebug :desktopApp:smokeTest` after the bump | pass | run |
| Built debug APK | versionName `0.6.1-beta.6`, versionCode 60126 (one above beta.5's scheme) | read from the build output |
| Smoke test on this machine | OK; it reports libmpv and yt-dlp as not found here | run |
| `checkThirdPartyNotices` | pass (no dependency changes) | run |
| The release workflow's notes step on the new changelog section | the same `awk` finds the section | run |
| `./gradlew -p buildSrc test` | NOT RUN: this machine's JDK 25 has no compiler for the toolchain, as in report 143; CI runs it | not run |
| Search of untracked and changed files for cookies, keys, screenshots of a real account | none found (only made-up values in tests) | run |
| CI, release workflow dry run | not run | needs the owner |

## 3. Files created/changed
`version.properties`, `CHANGELOG.md`, this report.

## 4. Decisions the spec didn't dictate
Stayed on the 0.6.1 line (beta.6). The changelog also covers the already committed "Lightning" work, which no release contains yet.

## 5. Known issues / unverified assumptions
The new Home groups and Shorts feed have been tested offline and with read-only live reads; device testing is the owner's (reports 156 to 159).

## 6. Manual test checklist
1. Run the commit and push commands, wait for CI to be green.
2. Optionally dry-run the release workflow and install its APK over beta.5.
3. Push the tag: this publishes a release.
