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
