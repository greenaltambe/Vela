# Vela: standing rules

- Read `docs/SPEC.md` before architectural work. The section 5 architecture rules apply to every file.
- **Never commit.** The owner commits manually.
- **Every task gets a report** at `docs/reports/NN-short-name.md` (NN = task number). Fixed sections, in order:
  1. Summary (3-5 lines)
  2. Verification (table: check | result | verified-by-running vs compile-only)
  3. Files created/changed (grouped)
  4. Decisions the spec didn't dictate
  5. Known issues / unverified assumptions
  6. Manual test checklist for the user (numbered, concrete)

  Keep it factual, no filler.
- **The terminal reply at the end is 5 lines at most**: where the report is, and only what the owner personally must do
  (manual tests, installs, approvals). No test output or summaries in the terminal.
- **Prefer measuring over assuming.** When a decision depends on real network or tool behaviour, test it and put the
  numbers in the report.

## Build notes

- Modules: `:shared` (KMP), `:androidApp`, `:desktopApp`, `:extractor-newpipe`, `:extractor-ytdlp`.
- Offline tests: `./gradlew allTests` (plain `test` skips :shared). Live tests (real network / real binaries) live in each module's `liveTest` source
  set and are never part of `check`: `./gradlew :extractor-newpipe:liveTest :extractor-ytdlp:liveTest`.
- The agent cannot run the Android app; anything Android-runtime is "compile-only" in reports.

## Publishing and releases (task 46)

- The owner creates the remote, pushes, tags and publishes; the agent never does. See `docs/RELEASING.md` (checklist, GitHub setup) and `docs/BUILDING.md` (commands, signing, Windows).
- One version: `version.properties` (`VERSION_NAME`). Android `versionCode` and the desktop package version derive from it (`buildSrc/VelaVersion`, tested with `./gradlew -p buildSrc test`).
- CI is `.github/workflows/ci.yml` (offline tests only, never live tests); releases are `.github/workflows/release.yml` (tag `v<VERSION_NAME>`, draft release). Actions are pinned by commit SHA.
- After changing dependencies run `./gradlew generateThirdPartyNotices` (CI checks `THIRD_PARTY_NOTICES.md`).
- Never commit device screenshots of a real account, cookies, keystores or personal data; the `.gitignore` and the CI secret scan back this up.
- Desktop packaging: `:desktopApp:packageLinux` (deb/rpm with dependencies), `:desktopApp:portableTarball` / `portableZip`, `:desktopApp:smokeTest` (also `vela --smoke-test`). Windows is experimental.
