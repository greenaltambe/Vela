# 122 Version 0.6.1-beta.2

## 1. Summary
The release workflow failed at "version check": the pushed tag `v0.6.1-beta.2` did not match `version.properties` (`0.6.0-beta.2`). Version set to `0.6.1-beta.2` and the changelog gained a section for the Windows ffmpeg and Continue watching changes.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `version.properties` now `VERSION_NAME=0.6.1-beta.2` | yes | read back with `git diff` |
| `./gradlew -p buildSrc test` | could not run: local toolchain error (`java-25-openjdk` has no compiler), unrelated to the change | not run |
| Release workflow with the new version | untested | not run |

## 3. Files created/changed
- `version.properties`, `CHANGELOG.md` (new `[0.6.1-beta.2]` section and compare links)

## 4. Decisions the spec didn't dictate
- Followed the tag you pushed instead of renaming the tag: the code changes since 0.6.0-beta.2 are a feature plus a fix, and no 0.6.1-beta.1 exists, which is unusual but harmless.

## 5. Known issues / unverified assumptions
- `v0.6.1-beta.2` points at the older commit (it was tagged before this fix), so it must be moved or recreated.
- Android `versionCode` derivation for this version string was not run (buildSrc tests blocked locally; CI runs them).

## 6. Manual test checklist
1. Commit, then move the tag to the new commit: `git tag -f v0.6.1-beta.2 && git push -f origin v0.6.1-beta.2` (or delete the failed tag/release first).
2. Check the "version check" job passes and the rest of the release runs.
