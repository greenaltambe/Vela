# Releasing

Maintainers only. The workflows never push, tag or publish by themselves: a tag starts a build, and the result is a **draft** release that you publish by hand.

## Release checklist

1. `main` is green on CI (`ci` workflow: Linux tests, Android build and lint, Windows compile and tests, licence notices, workflow lint, secret scan).
2. Bump `version.properties` (`VERSION_NAME=0.1.0-beta.2`). Check `./gradlew -p buildSrc test` still passes (it proves the Android version codes increase).
3. Update `CHANGELOG.md`: move the `[Unreleased]` entries under a new `## [0.1.0-beta.2]` heading (the workflow takes the release notes from that section and fails without it); update the compare links at the bottom.
4. If dependencies changed: `./gradlew generateThirdPartyNotices` and commit the result.
5. Commit, then **dry run**: Actions > `release` > Run workflow (workflow_dispatch) on `main`. It builds the APK, the Linux packages and the Windows packages, runs the smoke tests and uploads the artifacts; it creates no release.
   Download the artifacts and try them (install the APK, run `bin/vela --smoke-test`, start the Windows build on a real machine).
6. Tag and push: `git tag v0.1.0-beta.2 && git push origin v0.1.0-beta.2`. The tag must equal `v` + `VERSION_NAME`, or the workflow stops at its first job.
7. The `publish` job creates a **draft** release with all files, `SHA256SUMS`, and build provenance attestations (marked pre-release when the tag contains `-alpha`, `-beta` or `-rc`).
8. Check the draft: file names (`Vela-<version>-android-arm64.apk`, `...-linux-amd64.deb`, `...-linux-x86_64.rpm`, `...-linux-x64.tar.gz`, `...-windows-x64.msi`, `...-windows-x64.zip`), that the APK is **signed** (not `-unsigned`), and the checksums
   (`sha256sum -c SHA256SUMS`). `gh attestation verify <file> --repo <owner>/<repo>` verifies provenance. Then publish the release.
9. **Rollback**: do not delete users' ability to update. If a release is bad, mark it pre-release or delete the *release* (not the tag history of signed builds you want to keep), publish a fixed version with a higher `VERSION_NAME`
   (Android refuses downgrades, so a fix is always a new, higher version), and note it in the changelog.

### Secrets used by the release workflow

| Secret | Content |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | the release keystore, `base64 -w0 <file>` |
| `ANDROID_KEYSTORE_PASSWORD` | store password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password |

Without them the APK is built **unsigned** (named `-unsigned`). Create the key with `scripts/generate-keystore.sh` and **back it up in two places**: losing it means nobody can update the app.

## One-time GitHub setup (owner only; nothing in this repository does it for you)

1. Create a **private** repository, add the remote and push `main` (and the tags you want). Do not make it public yet.
2. Settings > Actions > General > Workflow permissions: **Read repository contents** (read-only by default). Under "Fork pull request workflows", keep secrets unavailable to fork PRs (the default).
3. Settings > Secrets and variables > Actions: add the four Android secrets above (optional for the first dry runs).
4. Settings > Code security: enable **Dependabot alerts**, Dependabot security updates, **Secret scanning** with **Push protection**, and **Private vulnerability reporting** (needed for `SECURITY.md`).
5. Settings > Branches: add a protection rule for `main`: require the `ci` checks (test-linux, android, windows-compile, licences, actionlint, secret-scan) to pass before merging.
6. Replace `OWNER/REPO` in `.github/ISSUE_TEMPLATE/config.yml` and in the links at the bottom of `CHANGELOG.md`.
7. Run the workflows in the private repository: `ci` (push), then `release` via workflow_dispatch. Fix what fails (paste `gh run view --log-failed` into the next task).
8. Read `docs/reports/46-publish-prep.md` section 1: the git **history** contains personal screenshots and an account label that the working tree no longer has. Publish a fresh squashed history (or rewrite it) before making the repository public.
9. Only then: Settings > General > Danger zone > Change visibility > Public.
