# 129: version 0.6.1-beta.4

## 1. Summary
`version.properties` bumped to `0.6.1-beta.4`; `CHANGELOG.md` has a `[0.6.1-beta.4]` section (playlist sync model, Watch later, overflow, toasts, Liked videos, desktop search pill) and its compare link.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew :desktopApp:test allTests` (before the bump) | pass | verified by running |
| `./gradlew -p buildSrc test` | NOT RUN: this machine's JDK 25 has no compiler for the toolchain ("does not provide the required capabilities: [JAVA_COMPILER]") | not run |
| Release workflow dry run | not run | not run |

## 3. Files created/changed
`version.properties`, `CHANGELOG.md`, this report.

## 4. Decisions the spec didn't dictate
Stayed on the 0.6.1 line (beta.4) although the database moves to schema 12; the update keeps a `.bak-v11` copy.

## 5. Known issues / unverified assumptions
The Android version code is derived from the version; its monotonic check (`buildSrc` test) was not run here, CI covers it. The playlist sync is unverified against a real account (see report 84).

## 6. Manual test checklist
1. Run the commands in the terminal reply of the previous message: push `main`, wait for CI, dry-run the `release` workflow, then tag and push the tag.
2. Install the built APK over beta.3 and confirm the app opens with your playlists intact (a `.bak-v11` copy is made on desktop).
