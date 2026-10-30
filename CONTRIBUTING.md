# Contributing

Thanks for helping. Vela is a Kotlin Multiplatform app (Android and desktop). The ground rules:

## Build and test

```
./gradlew allTests test                    # all OFFLINE tests (what CI runs)
./gradlew :androidApp:assembleDebug        # debug APK
./gradlew :desktopApp:run                  # desktop app (needs libmpv installed on Linux)
./gradlew :desktopApp:smokeTest            # starts the real runtime without a window and exits
./gradlew -p buildSrc test                 # version-code tests
```

Plain `test` skips `:shared`; `allTests` covers it. **Live tests** (real network, real yt-dlp / mpv / a device) live in each module's `liveTest` source set,
are never part of `check` and never run in CI: `./gradlew :extractor-newpipe:liveTest :extractor-ytdlp:liveTest`. See `docs/BUILDING.md` for the details.

## Rules

- Read `docs/SPEC.md` (section 5, architecture rules) before architectural work. Every YouTube access goes through the `Extractor` interface; everything visual comes from the design tokens; settings are declared once in the typed schema.
- **Never commit cookies, tokens, keystores, `local.properties` or screenshots of a real account.** The `.gitignore` covers the usual files; CI runs a secret scan. Test with a throwaway account, and put fake values in test fixtures.
- Offline tests must pass; add tests for new behaviour. Prefer measuring to assuming: when a decision depends on real network or tool behaviour, measure it and say so in the PR.
- Code style: Kotlin official style (`kotlin.code.style=official`), small focused functions, comments that say *why*. Match the surrounding code.
- Dependencies: after changing any, run `./gradlew generateThirdPartyNotices` and include the result (CI checks it). NewPipeExtractor and youtubedl-android are bumped deliberately (`scripts/bump-newpipe.sh`), not by bots.
- Pacing rules for the account tier are part of the design (`docs/SPEC.md` section 4): do not add features that poll or write to an account without going through the governor.

## How work is organised here

Each piece of work is a numbered task (`docs/tasks/NN-name.md`) with a report (`docs/reports/NN-name.md`) containing a verification table (verified by running vs compile-only),
the files changed, decisions the spec did not dictate, known issues and a manual test checklist. Contributors do not have to write task files, but a PR that changes behaviour
should say how it was verified and what could not be. Maintainers commit and tag by hand.
