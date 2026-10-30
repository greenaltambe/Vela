# Task 46: prepare the repository for GitHub (first beta): cleanup, licence, docs, signing, CI and release workflows

Read CLAUDE.md first. Do NOT commit, push, create a remote, or touch GitHub settings (the owner does that). Report
`docs/reports/46-publish-prep.md` (usual VERIFIED / NOT VERIFIED table). Terminal reply 5 lines max, only owner instructions.
Platforms for the beta: Android (primary), Linux (supported), Windows (EXPERIMENTAL: packaged and smoke-tested, not feature-complete).
The repo will first be pushed PRIVATE, CI run there, then made public: everything here must be safe for a public repo including HISTORY.

## A. Safety scan FIRST (tree and git history), before any other change
1. Run a secret scanner over the working tree and the whole history (gitleaks or trufflehog via a pinned container/binary; if neither
   can be installed offline, write a careful script: grep -r over `git log -p --all` for cookie names (SAPISID, __Secure-, SID=, HSID, LOGIN_INFO),
   `Authorization`, `Bearer`, `api_key`, `AIza`, private keys, keystores, `.jks`, `.p12`, long base64/hex blobs).
2. Personal data: the throwaway account id (`google:xxxxxx` and similar), real emails, channel/handle names of the owner's real account,
   adb wireless IPs/ports, absolute home paths (`/home/greenaltambe`), device serials. Check test fixtures, docs/reports, task files and
   SCREENSHOTS (open a sample of docs/reports/**/*.png and docs/reports/*-screens/*: any visible real account name, email, avatar, subscriptions list
   of the real account must be flagged; the reference-app screenshots from the owner, if any ended up in the repo, must be flagged too).
3. Output a findings table in the report (file/commit, what, severity, suggested fix). Fix what is in the working tree (scrub or replace with
   neutral placeholders). For anything in HISTORY: do NOT rewrite history. Recommend one of: (a) nothing found; (b) rewrite with
   git-filter-repo (list exact paths/commits); (c) publish a fresh squashed history (`git checkout --orphan`) and keep the old repo private.
   The owner decides. Until decided, say so at the top of the terminal reply.

## B. Cleanup
1. `git ls-files` review: untrack (git rm --cached, leave files on disk) anything generated or local: build outputs, `.idea/` (keep only shared
   run configs if any are useful), `.gradle`, `.kotlin`, `local.properties`, IDE files, stray root `build/`, logs, hprof, test output, any binaries
   (yt-dlp, mpv, ffmpeg, python), large unused assets. List every removal in the report.
2. Rewrite `.gitignore` properly (grouped, commented): Gradle/Kotlin/Android Studio, IDE (JetBrains, VS Code), OS junk, native/crash files
   (`hs_err_pid*`, `*.hprof`, `*.log`), packaging outputs (`*.apk`, `*.aab`, `*.deb`, `*.rpm`, `*.msi`, `*.exe`, `*.AppImage`, `dist/`), secrets
   (`*.jks`, `*.keystore`, `*.p12`, `keystore.properties`, `.env*`, `*.cookies.txt`, `.vela-test/`, `google-services.json` if ever), Python caches,
   Claude Code local state (`.claude/`, but keep `CLAUDE.md`), and screenshots/tmp produced by tests outside docs. Add `.gitattributes`
   (LF line endings for source/scripts, `*.bat` CRLF, binary markers for images/fonts, `gradlew` executable).
3. Make test screenshot output configurable: `vela.screens.dir` defaults to `build/` outside the repo docs when the env var `CI` is set, so CI never dirties the tree
   and never depends on docs paths; local runs keep writing to `docs/reports/...` as today.
4. Keep `docs/tasks`, `docs/reports`, `docs/design`, `docs/plans`, `docs/SPEC.md` where they are (they are the build log and other tasks reference them), add
   `docs/README.md` as an index. Remove nothing from docs; if the repo has oversized PNGs (>1 MB each), list them in the report (do not delete).
5. Consistent module READMEs are NOT needed. Remove the stale template README text (the KMP wizard text) completely (see E).
6. Dependency hygiene: no dependency pulled from a non-pinned source; JitPack restricted as already configured; list every repository used. Confirm the Gradle
   wrapper version is pinned with a checksum (`distributionSha256Sum` in gradle-wrapper.properties) and `gradle/wrapper/gradle-wrapper.jar` is the official one.

## C. Licence and legal files
1. `LICENSE` = GPL-3.0-or-later full text (NewPipeExtractor and youtubedl-android are GPL-3.0; the repo has no licence file yet). Add SPDX headers? Not required;
   add a short licence note in README and the About screen already listing third-party notices (verify it exists and is accurate).
2. `THIRD_PARTY_NOTICES.md`: generated from the actual dependency graph (Gradle licence report, e.g. a small task or script that lists group:artifact:version +
   licence, committed output + a check in CI that it is up to date), plus bundled fonts (OFL texts), icon sets (licences of Lucide and any other icon pack used),
   libmpv/ffmpeg/yt-dlp (not bundled in the repo; how they are obtained), and the Vela icon (own work, same licence as the project).
3. `README.md` disclaimer: Vela is an independent project, not affiliated with or endorsed by YouTube/Google; use is at your own risk and subject to YouTube's terms;
   it never uses third-party solver services; accounts are optional and used last (link to docs/SPEC.md account-safety section); use a throwaway account for testing.
4. `SECURITY.md` (how to report privately, what counts: credential leakage, SecretStore, updater signature/checksum bypass, remote-config signing), `CONTRIBUTING.md`
   (build, offline tests, never commit cookies, how the task/report workflow works for contributors, code style), issue templates (bug report asking for the Health/Diagnostics
   report and explicitly saying NEVER paste cookies/tokens; feature request) and a PR template.

## D. Versioning, signing, packaging
1. Single source of truth for the version: a `version.properties` (or Gradle property) `VERSION_NAME=0.1.0-beta.1`; Android `versionName` from it,
   `versionCode` derived (e.g. major*1_000_000 + minor*10_000 + patch*100 + beta/rc ordinal) and unit-tested to be monotonic; desktop `packageVersion` = numeric
   `major.minor.patch` (jpackage restrictions: MSI/DEB/RPM accept 3 numeric parts; verify 0.x works, otherwise document the mapping, e.g. 1.0.0 stays until 1.0).
   CI release builds take the version from the tag (`v0.1.0-beta.1`) and fail if it differs from version.properties.
2. Android release signing via env/`keystore.properties` (never in the repo): `ANDROID_KEYSTORE_PATH|BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
   `ANDROID_KEY_PASSWORD`. No secrets present => release build is produced UNSIGNED (still builds, clearly named `-unsigned`), so forks and PRs work.
   `scripts/generate-keystore.sh` (documented, interactive, writes outside the repo, prints the base64 command and the reminder to back the keystore up in two places:
   losing it means users can never update). Release build: arm64-v8a only as today; decide and document R8/minify (currently off: leave off, note why: NewPipe + embedded Python).
3. Desktop packaging (`desktopApp/build.gradle.kts`): formats Deb, Rpm (the owner is on Fedora), Msi (Windows); REMOVE Dmg (no macOS target). Package metadata (vendor,
   description, license file, copyright, icon files for each OS from docs/icon, menu group/shortcut, `.desktop` entry with correct StartupWMClass, MIME-less). Linux packages
   declare runtime dependencies (Debian/Ubuntu `libmpv2` (or `libmpv1` on 22.04: document which), Fedora `mpv-libs`); recommend `ffmpeg`; yt-dlp is managed by the app.
   Also produce a portable tarball/zip via `createDistributable` for Linux and Windows. Verify the jlink/modules list still includes everything used at runtime
   (jdk.httpserver already; test a packaged build, not only `run`).
4. Smoke test mode: `--smoke-test` CLI flag in the desktop app (and a Gradle task `:desktopApp:smokeTest`) that starts the real runtime without showing a window needing
   interaction: opens the database, loads settings, builds the extractor chain, locates yt-dlp/mpv (reports found/not found, not failure), loads libmpv if present, then exits 0
   within 30 s; non-zero with a clear message on any crash. No network access required. Used by CI on every OS against the PACKAGED app where possible.
5. Windows (experimental): make `:desktopApp` compile and package on Windows. Audit Linux-only code paths (MPRIS D-Bus, libsecret/AES file store, `.so` loading, binary
   locator, shutdown hooks, file permissions) and make them degrade gracefully: no MPRIS on Windows (no crash), SecretStore on Windows MUST be a real protected store
   (DPAPI via JNA `CryptProtectData`, per-user) or, if not implemented in this task, accounts/cookies login is DISABLED on Windows with a clear message (never plaintext cookies).
   libmpv on Windows: the app looks for `libmpv-2.dll`/`mpv-2.dll` next to the executable and on PATH; CI may download a pinned, checksum-verified libmpv build into the
   distributable (record URL, version and SHA-256 in `docs/BUILDING.md` and the workflow, comply with its GPL terms; if this is not done, the Windows package says in the About screen and
   README that playback needs libmpv installed). yt-dlp on Windows: the managed updater must pick the `.exe` asset and verify SHA2-256SUMS as on Linux. List every known gap in README under "Windows (experimental)".

## E. Documentation
1. `README.md` rewritten: one-paragraph pitch, screenshots (pick 4-6 existing, clean, non-personal screenshots from reports; copy to `docs/screenshots/` with neutral names; check them for personal data),
   feature list grouped (watching, reliability chain, accounts and safety, offline, customisation, keyboard), platform status table (Android / Linux / Windows), install instructions per
   platform (APK sideload + checksum, deb/rpm, Windows zip/msi, libmpv requirement), first-run notes (yt-dlp auto-update, age-restricted setting off by default), build-from-source,
   running tests (offline vs live; live tests need network + env var, never run in CI), disclaimer, licence.
2. `docs/BUILDING.md` (JDK, Android SDK, commands per target, signing, Windows notes, common errors), `docs/RELEASING.md` (checklist: bump version.properties, update CHANGELOG,
   tag, workflow_dispatch dry run, secrets list, verify checksums, publish the draft release, rollback), and `CHANGELOG.md` (Keep a Changelog; 0.1.0-beta.1 entry summarising the app).
3. Include in RELEASING.md the owner-only GitHub setup checklist: create PRIVATE repo, push, set Actions permissions to read-only by default, add the 4 Android secrets, enable
   Dependabot alerts + secret scanning + push protection + private vulnerability reporting, branch protection on main (require CI), run the workflows, then make it public.

## F. GitHub Actions (all in `.github/`)
General rules: pin every third-party action by full commit SHA with a version comment; `permissions: contents: read` at workflow level, elevated only per job; `concurrency` to cancel
superseded runs; no secrets in PR workflows from forks; set `timeout-minutes`; cache Gradle via `gradle/actions/setup-gradle`; Temurin JDK matching the project's needs (check AGP/Kotlin
requirements; likely 17+); Android SDK from the runner image (`ANDROID_HOME`), accept licences, no `local.properties` dependence; no live (network) tests ever; `-Pci=true` style flag if needed;
upload test reports/logs as artifacts on failure.
1. `ci.yml` (push to main, PRs): job `test-linux` (ubuntu-24.04): install `xvfb` + `libmpv2` (and what Compose desktop UI tests need), run `./gradlew allTests test` under `xvfb-run`; job
   `android` : `assembleDebug` + `lint`/`lintDebug` if configured + unit tests for the Android module; job `windows-compile` (windows-latest): `:shared:jvmTest` and `:desktopApp:compileKotlin`
   (+ `:desktopApp:test` if it can run headless there; otherwise document); job `licences` verifying THIRD_PARTY_NOTICES.md is current; job `actionlint` (workflow lint) and a scan job (gitleaks)
   on every push. A path filter is NOT used (keep it simple).
2. `release.yml` (tags `v*`, plus `workflow_dispatch` = dry run that builds everything and uploads artifacts but creates no release): version check vs tag; jobs `android` (signed APK if secrets exist,
   else unsigned; run `apksigner verify` on signed output; `v0.1.0-beta.1` => file `Vela-0.1.0-beta.1-android-arm64.apk`), `linux` (ubuntu: `.deb`, `.rpm` (install `rpm`), portable tarball; run smoke test
   on the unpacked distributable under xvfb with libmpv installed), `windows` (windows-latest: `.msi`, portable zip, optional libmpv fetch as in D5; run `--smoke-test` on the packaged exe),
   then `publish`: needs all, downloads artifacts, writes `SHA256SUMS`, creates a DRAFT GitHub Release (prerelease flag when the tag has `-beta`/`-rc`), notes from CHANGELOG, attaches everything, and
   creates build provenance attestations (`actions/attest-build-provenance`; needs `id-token: write` and `attestations: write` only on that job).
3. `.github/dependabot.yml`: gradle (weekly, grouped by minor/patch; ignore/pin NewPipeExtractor and youtubedl-android: they are bumped deliberately via `scripts/bump-newpipe.sh`) and github-actions (weekly).
4. Optional and OFF by default: a scheduled `canary.yml` is NOT added (live YouTube tests from shared CI IPs are unreliable and against the project's pacing rules); say so in the report.

## G. Verification (be honest about what cannot be run here)
1. Fresh-clone test: `git clone` the working tree (including uncommitted-but-tracked changes is impossible, so first `git stash`-free copy via `git worktree`/`git archive` of the current index plus working files NOT ignored) into a temp dir, with no local.properties,
   and run `./gradlew allTests test :androidApp:assembleDebug` there. This catches files that exist only locally or are ignored but required. Fix every failure.
2. Run locally on Linux: `packageDeb packageRpm createDistributable`, install-inspect the deb/rpm metadata (`dpkg-deb -I`, `rpm -qpR`), run the smoke test on the distributable, `assembleRelease` unsigned, and
   signed with a THROWAWAY keystore created in a temp dir (verify with `apksigner verify --print-certs`; delete the keystore afterwards).
3. Validate YAML with `actionlint` (install it if possible) and fix everything; dry-run what can be run with `act` only if available (do not spend time installing it).
4. Windows and the workflows themselves cannot be executed here: mark them NOT VERIFIED and give the owner the exact first-run checklist (push private, run `ci` and `release` via workflow_dispatch,
   download the artifacts, run the Windows smoke test on a real Windows machine if available). Describe what logs to paste back (`gh run view --log-failed`) so a follow-up task can fix CI.
5. Re-run the secret scan at the end; the tree must be clean. Full suite green after the last change. Update `CLAUDE.md` (new files, the no-push rule stays, release process pointers).
