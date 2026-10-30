# 46 Publish prep (first beta): cleanup, licence, docs, signing, CI and release workflows

## 1. Summary
**Decision needed before anything is made public: the git HISTORY is not safe to publish as it is.** It contains device screenshots with the owner's real name, a video-call face and chat lists, and a throwaway-account label. The working tree is scrubbed; history was NOT rewritten. Recommendation: (c), publish a fresh squashed history (details in the findings below).
Done: scan and scrub, `.gitignore`/`.gitattributes`, GPL-3.0 `LICENSE`, generated `THIRD_PARTY_NOTICES.md` with a CI check, README, SECURITY, CONTRIBUTING, CHANGELOG, BUILDING, RELEASING, issue/PR templates, one version source with tested version codes, environment-driven release signing (unsigned otherwise), Linux deb/rpm with dependencies and a correct menu entry, portable archives, `--smoke-test`, Windows support code, `ci.yml`, `release.yml`, `dependabot.yml`.
Windows packaging, the workflows and the .deb could not be run here (NOT VERIFIED; first-run checklist in section 6). The desktop UI tests (`:desktopApp:test`, which `allTests` does not include) had 14 failures that already existed at the last commit; they are fixed.

### Safety scan findings (tree and history)
Scanner: gitleaks/trufflehog not installed and not runnable here, so a script scanned the working tree and `git log -p --all` (19 commits, 5 MB of patches) for cookie names, `Authorization`/`Bearer`, API-key and private-key shapes, `google:` account labels, home paths, IP:port pairs, e-mail addresses, owner name; then every tracked image class was opened and looked at.

| file / commit | what | severity | fix |
|---|---|---|---|
| `docs/reports/icon-swap-screens/phone-home.png`, `phone-recents.png`, `phone-launcher.png`, `phone-topbar-crop.png` (commit 203c568) | Phone screens showing the owner's real name, the real account avatar, a video call (a person's face), WhatsApp and chat lists | HIGH | moved out of the repo to `~/vela-private-screens/`, replaced by neutral placeholders |
| `docs/reports/23-screens/phone-*.png` (22 files, 203c568) | Device captures: the owner's real name + `google:xxxxxx` (profile menu, settings home), the real account avatar in every top bar | HIGH | same |
| `docs/reports/21-screens/*.png` (10 files, 43268f8) | Device captures with the real watch history, real live-chat messages | MEDIUM | same |
| `docs/tasks/assets/12-phone-{1,2,3}-*.jpg` (f539376) | Owner's phone screenshots: a real search query, the real subscription feed, status bar | MEDIUM | same |
| `docs/reports/22-android-age-restricted.md`, `23-design-foundation.md` (dc9126d) | The throwaway account label `google:xxxxxx` | LOW | replaced by `google:xxxxxx` |
| tests (`AuthCoreTest`, `RedactionTest`, `SecretStore` tests, account tests) | Fake cookie and token strings, and `@example.com` / gmail-style test addresses, used to prove that redaction works | none (fake) | left; `.gitleaks.toml` allow-lists test source sets |
| `innertube-auth/src/test/resources/fixtures/*.json` | Captured account responses | none: scrubbed by `capture_fixtures.py` (checked: only `Test Person`, `test.person@example.com`, `UCscrubbed*`) | left |
| git author lines | `greenaltambe <...@users.noreply.github.com>` on all 19 commits | none (GitHub noreply address) | left |
| package id `com.greenaltambe.vela` | the owner's handle in the application id | note | the owner decides whether to keep it (changing it later breaks updates) |
| `/home/...`, IP addresses, device serials, keystores, `.env`, API keys, private keys | none found in tree or history | | |

**History recommendation (the owner decides): (c)** publish a fresh squashed history: `git checkout --orphan public && git add -A && git commit -m "Vela 0.1.0-beta.1"` (after the final tree is as wanted), push THAT, and keep the current repository private as the archive.
Why not (b): the images and the account label sit in several commits (203c568, 43268f8, f539376, dc9126d); `git filter-repo` would work (paths: `docs/reports/icon-swap-screens/`, `docs/reports/23-screens/phone-*`, `docs/reports/21-screens/`, `docs/tasks/assets/12-phone-*`, plus a text replacement for `google:xxxxxx`) but gives the same result as (c) with more risk and keeps a history that was never reviewed for publication. Until decided, do not make the repository public and do not push this history anywhere public.

## 2. Verification
RUN = executed here. NOT RUN = could not be executed here.

| check | result | how |
|---|---|---|
| Fresh copy of the tracked + untracked-not-ignored files into an empty directory, no `local.properties`: `allTests test :androidApp:assembleDebug` | RUN, 2 attempts. #1 found a compile error in a test caused by my own `YtDlpUpdater` signature change (fixed). #2 passed everything (`allTests`, `assembleDebug`, `smokeTest` = SMOKE OK) except `IconPacksTest.contactSheets` in CI mode (screenshot folder not created; fixed). The fix was verified by `CI=true ./gradlew allTests test --rerun-tasks` in the working tree (all green, and the git status was identical before and after: CI runs do not dirty the tree), not by a third fresh copy | `git ls-files -co --exclude-standard` copy, `CI=true` |
| Offline suite of the working tree after the last change (`./gradlew allTests test`; also with `CI=true --rerun-tasks`) | RUN: pass (6 m 43 s; includes the 101 desktop UI tests, 0 failures) | Gradle |
| `:desktopApp:test` at the last commit, before my changes (baseline) | 14 failures of 101 (already red; `allTests` never ran them) | Gradle in a `git archive HEAD` copy |
| Version codes monotonic, tested (`-p buildSrc test`, 4 tests) | pass | JUnit; `0.1.0-beta.1` = 10021 |
| `checkThirdPartyNotices` | pass | Gradle |
| Unsigned release APK (`collectReleaseApk`) | RUN: `Vela-0.1.0-beta.1-android-arm64-unsigned.apk`, 41 MB | Gradle |
| Signed release APK with a THROWAWAY keystore from environment variables | RUN: `Vela-0.1.0-beta.1-android-arm64.apk` (no `-unsigned`); `apksigner verify --print-certs` shows the signer; the keystore was deleted afterwards | Gradle + apksigner 36.0.0 |
| Packaged desktop app smoke test (`bin/vela --smoke-test`) | pass; it found a real bug: the trimmed runtime lacked `java.net.http` (fixed: `includeAllModules`) | RUN on `createDistributable` output |
| Unpacked portable tarball smoke test | pass; found and fixed lost executable bits | RUN |
| `.rpm` | built; `rpm -qpR` lists `mpv-libs`; menu entry has `StartupWMClass=Vela`, `Name=Vela` | RUN |
| `.deb` | NOT RUN (no `dpkg-deb` on this Fedora machine); CI builds it and prints `dpkg-deb -I` | |
| Windows: compile of the Windows code paths, DPAPI store, `.msi`, zip, libmpv bundling, smoke test of `vela.exe` | NOT RUN (no Windows machine; the shared code compiles on Linux) | |
| `ci.yml` / `release.yml` / `dependabot.yml` | YAML parses (PyYAML); NOT RUN on GitHub; `actionlint` and `gitleaks` could not be executed here (downloading and running binaries was blocked), their pinned versions and SHA-256 are taken from the projects' published checksum files | |
| Action pins | RUN: each full SHA resolved from the real tag with `git ls-remote` | |
| Windows libmpv pin | RUN: file downloaded and hashed here, contains `libmpv-2.dll` | |
| `gradle-wrapper.jar` | RUN: SHA-256 `497c8c2a...` equals the checksum Gradle publishes for 9.5.1 | |
| Secret scan of the tree after the scrub | RUN, clean | grep over tracked + untracked-not-ignored files |

## 3. Files created/changed
- **Safety**: 41 device screenshots replaced by placeholders (originals in `~/vela-private-screens/`, outside the repo); `google:xxxxxx` removed from two reports.
- **Repo hygiene**: `.gitignore` (rewritten, grouped), `.gitattributes`, `.gitleaks.toml`, `README.md` (the KMP template text is gone), `LICENSE`, `THIRD_PARTY_NOTICES.md`, `SECURITY.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `docs/README.md`, `docs/BUILDING.md`, `docs/RELEASING.md`, `docs/screenshots/` (6 neutral screens), `CLAUDE.md` (release pointers).
- **Build**: `version.properties`, `buildSrc/` (`VelaVersion` + tests), `androidApp/build.gradle.kts` (version, signing from env/`keystore.properties`, `collectReleaseApk`, R8 stays off with the reason), `desktopApp/build.gradle.kts` (formats Deb/Rpm/Msi, no Dmg; metadata; `includeAllModules`; `smokeTest`, `packageLinux`, `portableTarball`, `portableZip`; CI screenshot dir), `gradle/notices.gradle.kts`, `gradle/libs.versions.toml` + `shared/build.gradle.kts` (jna-platform), `scripts/generate-keystore.sh`, `packaging/linux/vela.desktop`, `docs/icon/vela-icon.ico`, `desktopApp/appResources/windows-x64/`.
- **App code**: `desktopApp` (`SmokeTest.kt`, `main.kt`, `DesktopWiring.kt`), `shared/jvmMain` (`LinuxSecretStores.kt`: DPAPI store and "unavailable" store; `XdgPaths.kt`: Windows paths), `extractor-ytdlp` (`BinaryLocator`, `YtDlpUpdater`: `.exe`, Windows asset), `player-mpv` (`MpvLibrary`: Windows DLL lookup), About text in `ProfileMenu.kt`, `AccountsScreen.kt` message.
- **CI**: `.github/workflows/ci.yml`, `release.yml`, `.github/dependabot.yml`, `ISSUE_TEMPLATE/{bug_report,feature_request,config}`, `pull_request_template.md`.
- **Tests fixed** (they were already failing at the last commit): `UiHarness` (fixture uses the three-tab bar the tests assume; `screensDir` for CI), `NavigationUiTest` via that, `SettingsUiTest` (wait for the page to settle; a placeholder tag was added in `SettingsHost`), `LibrarySyncUiTest` (the playlist card again says "YouTube account · N videos"), `HomeSourceUiTest` (no "Recommended"/"Trending" heading any more), `DownloadRulesUiTest` (the auto-download switch is offered on subscribed channels), `AccountActionsUiTest` (channel id from the video's details), `LayoutInvariantsTest` (text on a Shorts card sits on its own scrim), `ScreenStatesTest` (the video page is a layer over its base screen, so the comments error is reached through the comments panel over the Library).

## 4. Decisions the spec didn't dictate
- Licence GPL-3.0-or-later (task); the package id `com.greenaltambe.vela` is left as is.
- Windows secrets: DPAPI (`Crypt32Util`, per user) or, if unavailable, no store at all and sign-in disabled; never the plaintext/beside-the-key file. The Windows build is "experimental".
- Linux packages are made by calling `jpackage` on the Compose app image (`packageLinux`), because Compose's `packageDeb`/`packageRpm` cannot declare dependencies or a window class. `.deb` depends on `libmpv2 | libmpv1`, `.rpm` on `mpv-libs`; ffmpeg only recommended.
- `includeAllModules = true` (about 40 MB larger) instead of guessing a module list.
- The X11 window class is set to `Vela` in `main()` (needs `--add-opens java.desktop/sun.awt.X11`, set in the build) to match `StartupWMClass`.
- Windows libmpv: shinchiro/mpv-winbuild-cmake 20261021 (SHA-256 `efc719ef...`), fetched by the release workflow into `desktopApp/appResources/windows-x64/`; recorded in `docs/BUILDING.md`.
- Version code scheme in `docs/BUILDING.md`; desktop packages carry the numeric `0.1.0` (`.rpm` verified to accept a leading 0).
- Actions pinned by SHA: checkout 7.0.1, setup-java 6.1.0 (Zulu 21, matching the Gradle daemon criteria), gradle/actions 6.4.1, upload-artifact 7.0.2, download-artifact 8.0.2, attest-build-provenance 4.2.2. actionlint 1.7.12 and gitleaks 8.30.1 are downloaded with a checksum check inside the jobs.
- No `canary.yml`: live YouTube tests from shared CI addresses are unreliable and against the project's pacing rules.
- `gh release create` (the CLI on the runner) instead of a third-party release action.
- Repositories used: google (restricted to androidx/com.android/com.google), mavenCentral, gradlePluginPortal (plugins), JitPack (restricted to `com.github.TeamNewPipe`).

## 5. Known issues / unverified assumptions
- **History** (section 1) is the blocker for going public.
- `gitleaks` and `actionlint` never ran here; a first CI run may report false positives in `secret-scan` (extend `.gitleaks.toml`) or style issues in the workflows.
- `ci.yml` runs `lintDebug` for the Android module; I did not run it here. If it fails on existing warnings, relax it (a baseline) in a follow-up.
- The `.deb` (including the `libmpv2 | libmpv1` alternative syntax), the `.msi` (needs WiX 3 on the runner; the step looks it up under `C:\Program Files (x86)\WiX Toolset*`), the Windows smoke test, DPAPI and the libmpv lookup on Windows are untested.
- The smoke test touches the real keyring on a machine with libsecret (a probe item is written and deleted); on CI it falls back to a file in a temp folder.
- Windows `.msi` is not code-signed (SmartScreen warns); the portable zip likewise.
- The pinned Windows libmpv is a third-party community build; its licence terms (GPL/LGPL, source available from the project) are recorded in `docs/BUILDING.md`; `THIRD_PARTY_NOTICES.md` licence for `nanojson` is "to be confirmed".
- Local (non-CI) test runs regenerate the tracked PNGs in `docs/reports/12-screens` and `23-screens` (sample data only), so `git status` shows them modified; commit or discard as you like.
- The README screenshots come from the offline UI test fixtures and predate some design changes.
- Dependabot ignores NewPipeExtractor and youtubedl-android (bumped by `scripts/bump-newpipe.sh`); Gradle dependency updates are still weekly.
- If the Windows runner image drops WiX 3, the WiX step fails loudly (it does not fall back).

## 6. Manual test checklist (owner)
1. Read section 1 and decide on the history (recommendation: fresh squashed history in a new private repo; keep this one as the archive). Check `~/vela-private-screens/` and delete or keep it.
2. Decide whether to keep the application id `com.greenaltambe.vela`.
3. Create the private GitHub repository and follow `docs/RELEASING.md` "One-time GitHub setup" (Actions read-only, secrets, Dependabot, secret scanning + push protection, private vulnerability reporting, branch protection, replace `OWNER/REPO`).
4. `scripts/generate-keystore.sh`, back the keystore up in two places, add the four `ANDROID_*` secrets.
5. Push, let `ci` run. If something fails, `gh run view --log-failed` and paste the output into the next task (most likely candidates: `secret-scan`, `actionlint`, `lintDebug`, the Windows jobs).
6. Run `release` via workflow_dispatch (dry run). Download the artifacts: install the APK (is it signed? `apksigner verify --print-certs`), `sudo apt install ./Vela-*.deb` on Ubuntu (does it pull libmpv2? does the menu entry show "Vela"?), `sudo dnf install ./Vela-*.rpm` on Fedora.
7. On a real Windows machine: unzip the portable zip, run `vela.exe` (SmartScreen will warn): does it start, play a video (libmpv bundled), store an account sign-in (DPAPI)? Run `vela.exe --smoke-test` with `VELA_SMOKE_LOG` set and look at the log.
8. Tag `v0.1.0-beta.1` only when the dry run is good; check the draft release and publish it by hand.
9. Make the repository public last.
