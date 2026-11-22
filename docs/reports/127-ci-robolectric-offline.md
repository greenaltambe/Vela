# 127 CI: Robolectric runs offline

## 1. Summary
Both CI runs of the push failed ("offline tests (Linux)" and "Android debug build and lint") with `AndroidDownloadsTest` / `ChunkedDataSourceTest` > classMethod FAILED at `MavenArtifactFetcher`: Robolectric's own downloader could not fetch its Android SDK jar (`android-all-instrumented:14-robolectric-10818077-i7`) from Maven Central on the runner. The tests passed locally only because that jar was already in `~/.m2`. Gradle now fetches the jar as an ordinary dependency (its cache and retries) and Robolectric runs offline from it; nothing is downloaded at test time.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `:androidApp:testDebugUnitTest` with Robolectric's `~/.m2` copy moved away (the CI condition): passes, nothing re-created in `~/.m2`, jar present in `androidApp/build/robolectric-jars` | pass | running |
| `checkThirdPartyNotices`, `:androidApp:assembleDebug`, `:androidApp:lintDebug` | pass | running |
| `./gradlew allTests test` | pass | running |
| On a GitHub runner | untested | not run |

## 3. Files created/changed
- `androidApp/build.gradle.kts` (configuration `robolectricAndroidAll`, `robolectricJars` Sync task, `robolectric.offline` and `robolectric.dependency.dir` for every Test task), `gradle/libs.versions.toml` (`robolectric-android-all`)

## 4. Decisions the spec didn't dictate
- Fixed in the build, not by adding retries or caches to `.github/workflows/ci.yml`: the cause was deterministic on the runner, so retrying would only hide it.
- The jar version is pinned in the version catalog next to Robolectric's; it must match the jar Robolectric 4.17 names for `@Config(sdk = [34])`. If Robolectric is upgraded the error message names the new jar to put there.

## 5. Known issues / unverified assumptions
- Assumes the runner can fetch the same jar through Gradle (Maven Central via Gradle's resolver); if Maven Central itself is down, any Gradle build would fail the same way.
- The v0.6.1-beta.3 tag points at the commit before this fix. The release jobs do not run these tests, so the release is unaffected.

## 6. Manual test checklist
1. Commit and push to main (no new tag needed).
2. Check `ci` on that commit is green, in particular "offline tests (Linux)" and "Android debug build and lint".
