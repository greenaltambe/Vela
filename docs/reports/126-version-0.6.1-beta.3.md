# 126 Version 0.6.1-beta.3

## 1. Summary
Version set to 0.6.1-beta.3 (`version.properties`) with a changelog section for the floating mini player, Ctrl+X / close cross, playlist size and sort pills. The CI change for the Robolectric download flake was NOT applied (the edit was blocked by the permission classifier; see section 5).

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `./gradlew allTests` on the code of this release | pass | running |
| Version string accepted by `VelaVersion` (beta.3 is within 1..29) | by reading the code | not run (local toolchain cannot run buildSrc tests; CI does) |
| actionlint on the workflows | not run (the download+run was blocked) | not run |
| CI Android job flake fix | not applied | n/a |

## 3. Files created/changed
- `version.properties`, `CHANGELOG.md`

## 4. Decisions the spec didn't dictate
- Next beta number after the already-tagged `v0.6.1-beta.2`, so the tag is new.

## 5. Known issues / unverified assumptions
- The failed CI run ("Android debug build and lint") was Robolectric failing to download its Android SDK jar from Maven Central (`MavenArtifactFetcher`, IOException): a network flake, not a code problem. It can happen again until CI caches that jar and/or retries the test step. Proposed change: an `actions/cache` step (pinned by commit SHA `55cc8345...`, v6.1.0) for `~/.m2/repository/org/robolectric`, and a 3-attempt loop around the Gradle step in the `test-linux` and `android` jobs of `.github/workflows/ci.yml`.
- The bundled-ffmpeg lookup in the Windows release job was not run on Windows.

## 6. Manual test checklist
1. Commit, push, tag `v0.6.1-beta.3` (commands given in chat).
2. Watch the release run; confirm `version check`, the Windows ffmpeg step and the smoke test pass.
