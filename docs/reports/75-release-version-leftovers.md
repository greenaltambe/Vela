# 75 Release workflow, version 0.2.0-beta.1, left-over screens, performance (no phone)

## 1. Summary
Version is now `0.2.0-beta.1` (version.properties, README, a new CHANGELOG section that the release job reads). The release workflow now always signs the Android APK (your four secrets if present, otherwise a key it creates for that run) and publishes the release instead of leaving a draft (pre-release when the tag has `-beta`/`-rc`/`-alpha`). Left-over parts: remaining filter chips (Explore topics, channel sort, download rule quality) use the shared expressive chip; the channel page header is more expressive (bigger avatar, subscribe pair, tonal auto-download and About cards). Performance: shimmer placeholders no longer recompose every frame, and a hand-written baseline profile ships in release builds. Desktop screens were rendered at 1280x800 and looked at. Nothing was committed or pushed.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests`, `:desktopApp:test`, `:desktopApp:smokeTest`, `:androidApp:assembleDebug`, `:androidApp:assembleMeasure` (with the baseline profile), `generateThirdPartyNotices`, buildSrc version tests | pass | running |
| Desktop renders (1280x800) of Home, Subscriptions, video, Library, Search, Channel, Explore, History | looked at; fine | running (desktop test renders) |
| Release workflow (signing step, publish step) | edited, not run (needs a tag on GitHub) | not verified |
| Anything on the phone, frame times after these changes | phone was asleep | not verified |

## 3. Files created/changed
- Version and release: `version.properties`, `README.md`, `CHANGELOG.md`, `.github/workflows/release.yml`, `docs/RELEASING.md`, `CLAUDE.md`.
- Screens: `ExploreScreen.kt`, `ChannelScreen.kt`, `DownloadRulesScreen.kt`.
- Performance: `ui/look/Motion.kt` (shimmer drawn, not recomposed), `androidApp/src/main/baseline-prof.txt`.

## 4. Decisions the spec didn't dictate
- Version `0.2.0-beta.1` (a minor bump for the redesign); change `version.properties` if you want another number, the tag must be `v` plus it.
- Without signing secrets each release is signed with a different key, so installs of a newer release over an older one need an uninstall first; adding the four secrets once fixes that (docs/RELEASING.md).
- Icons: the icon set already uses outlined icons everywhere and the filled form only for selected / active states (nav tabs, like, save, bell with unread, Shorts rail toggles), so no change was needed.

## 5. Known issues / unverified assumptions
- Not done (needs a device to judge): the player drag still re-measures the video page at a changing size, the biggest remaining morph cost; the shared shimmer transition; per-card press effect cost.
- The baseline profile is hand-written (broad), not measured.
- Remaining plain-Material parts (dialogs, text buttons, the Diagnostics screen, Accounts safety dialogs) inherit the expressive theme but were not redesigned.
- The release job cannot be tested without pushing a tag; the first tagged run should be a manual workflow dry run (Actions > release > Run workflow).

## 6. Manual test checklist
1. Run the release workflow once by hand (Actions > release > Run workflow) and install the APK from the artifact on a phone: it should install.
2. Tag `v0.2.0-beta.1` when ready: a published pre-release with the APK, Linux and Windows packages, SHA256SUMS.
3. Install `assembleMeasure` on the phone and re-run the scroll / tab / minimise checks.
4. Desktop: open Home, Subscriptions, a video, Library, a channel; check the layout at a wide window and a narrow one.
