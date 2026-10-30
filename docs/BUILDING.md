# Building Vela

## Requirements

- **JDK 21** (the Gradle daemon asks for Azul Zulu 21 through `gradle/gradle-daemon-jvm.properties`; Gradle downloads it if missing). Any JDK 21 works to *run* Gradle.
- **Android SDK** with platform 37 and build-tools (`ANDROID_HOME` set, licences accepted: `yes | sdkmanager --licenses`) for the Android app and for `:shared`'s Android target. No `local.properties` is needed when `ANDROID_HOME` is set.
- Linux desktop: **libmpv** (`mpv-libs` on Fedora, `libmpv2` on Debian/Ubuntu 24.04), recommended `ffmpeg`, `xvfb` for UI tests without a display.
- Packaging: `rpmbuild` (Fedora: `rpm-build`) for `.rpm`, `dpkg-deb` + `fakeroot` for `.deb`, WiX Toolset 3 on Windows for `.msi`.

## Commands

| Goal | Command |
|---|---|
| Debug APK | `./gradlew :androidApp:assembleDebug` |
| Release APK (unsigned unless signing is configured) | `./gradlew :androidApp:collectReleaseApk` -> `androidApp/build/release/Vela-<version>-android-arm64[-unsigned].apk` |
| Run the desktop app | `./gradlew :desktopApp:run` |
| Smoke test (no window, no network) | `./gradlew :desktopApp:smokeTest` |
| Offline tests | `./gradlew allTests test` (UI tests need a display: `xvfb-run -a ./gradlew allTests test`) |
| Version-code tests | `./gradlew -p buildSrc test` |
| Linux app image | `./gradlew :desktopApp:createDistributable` -> `desktopApp/build/compose/binaries/main/app/vela` |
| Linux .deb and .rpm (with runtime dependencies) | `./gradlew :desktopApp:packageLinux` -> `desktopApp/build/linux-packages/` |
| Portable archive | `:desktopApp:portableTarball` (Linux) / `:desktopApp:portableZip` (Windows) -> `desktopApp/build/portable/` |
| Windows .msi | `./gradlew :desktopApp:packageMsi` (on Windows, with WiX) |
| Licence notices | `./gradlew generateThirdPartyNotices` (rewrite) / `checkThirdPartyNotices` (verify) |
| Live tests (network, real tools, never in CI) | `./gradlew :extractor-newpipe:liveTest :extractor-ytdlp:liveTest` and other modules' `liveTest` |

Screenshot tests write to `docs/reports/...` locally; with the environment variable `CI` set they write to `desktopApp/build/screens` instead.

## Versions

`version.properties` holds `VERSION_NAME` (for example `0.1.0-beta.1`), the single source of truth (`buildSrc/src/main/kotlin/VelaVersion.kt`):

- Android `versionName` = that string; `versionCode` = `major*1,000,000 + minor*10,000 + patch*100 + stage` where stage is `alpha.n` = n, `beta.n` = 20+n, `rc.n` = 50+n, final = 99 (tested to be strictly increasing in release order; `0.1.0-beta.1` = 10021).
- Desktop packages carry the numeric part (`0.1.0`): jpackage accepts only numbers. The .msi, .deb and .rpm accept a leading `0` (verified for .rpm; .deb and .msi are produced by CI).
- Release builds take the version from the tag (`v0.1.0-beta.1`); the workflow fails if the tag and `version.properties` differ.

## Android signing

Never in the repository. The release build reads, in this order, the environment or an untracked `keystore.properties` in the repository root:

```
ANDROID_KEYSTORE_PATH or ANDROID_KEYSTORE_BASE64   ANDROID_KEYSTORE_PASSWORD   ANDROID_KEY_ALIAS   ANDROID_KEY_PASSWORD
keystore.properties:  storeFile=...  storePassword=...  keyAlias=...  keyPassword=...
```

Without them the release APK is built **unsigned** and named `...-unsigned.apk` (forks and pull requests still build). Create a key with `scripts/generate-keystore.sh` (writes outside the repository, prints the CI secrets,
reminds you to back it up in two places: **losing the key means users can never update**). Check a signed APK with `apksigner verify --print-certs <apk>`.
R8/minification stays off: NewPipeExtractor (reflection-heavy parsing), the embedded Python/yt-dlp and the JNI libraries would need keep rules that are easy to get wrong and hard to test without a device.

## Desktop packaging notes

- The app image is built with `includeAllModules = true`: a trimmed jlink runtime lost `java.net.http` (found by the smoke test of the packaged app) and would also lose modules only reached by reflection.
- Compose's `packageDeb`/`packageRpm` cannot declare runtime packages or the window class, so `packageLinux` wraps the app image with `jpackage` directly: `Depends: libmpv2 | libmpv1` (.deb), `Requires: mpv-libs` (.rpm) and the menu entry from `packaging/linux/vela.desktop` (`StartupWMClass=Vela`; `main()` sets the X11 window class to match).
  Ubuntu 22.04 ships `libmpv1`: the alternative dependency covers it. ffmpeg is recommended, not required. yt-dlp is found or managed by the app.
- `./gradlew :desktopApp:smokeTest`, or `vela --smoke-test` on a packaged build, starts the real runtime without a window: opens a temporary database, loads settings, builds the extractor chain, looks for yt-dlp/ffmpeg/libmpv (reported, not failures) and exits 0 within 30 seconds, or non-zero with a message. `VELA_SMOKE_LOG=<file>` also writes the lines to a file (a packaged Windows exe has no console).

## Windows and libmpv

The Windows package must play, so the release workflow downloads a **pinned, checksum-verified** libmpv build into `desktopApp/appResources/windows-x64/` before packaging (the folder is empty in the repository):

- Source: shinchiro/mpv-winbuild-cmake, release `20261021`, file `mpv-dev-x86_64-20261021-git-e471496c56.7z` (contains `libmpv-2.dll`)
- URL: `https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/20261021/mpv-dev-x86_64-20261021-git-e471496c56.7z`
- SHA-256: `efc719eff3dc5954b4a5efc800632808bcec01cc9034940dd14e5eb7fe9ea633`

mpv is GPL-2.0-or-later (this build links FFmpeg and other libraries under GPL/LGPL terms); the corresponding source is available from that project's repository. The release includes `LIBMPV-SOURCE.txt` naming the exact build.
To build locally on Windows: download and verify the same file, extract `libmpv-2.dll` into that folder, then `./gradlew :desktopApp:packageMsi :desktopApp:portableZip`. The app also finds `libmpv-2.dll` next to `vela.exe` or on `PATH`, or via `-Dvela.libmpv=` / `VELA_LIBMPV`.
To change the pinned build, update `LIBMPV_URL`, `LIBMPV_SHA256` and `LIBMPV_VERSION` in `.github/workflows/release.yml` and the lines above together.

Windows specifics in the code: data in `%LOCALAPPDATA%\vela`; credentials in a DPAPI-protected file (never plaintext, and sign-in is disabled if DPAPI is unavailable); no MPRIS; no `notify-send`;
programs found as `<name>.exe` (also winget/scoop/chocolatey shims); the managed yt-dlp updater uses the `yt-dlp.exe` asset with SHA2-256SUMS verification.

## Repositories used for dependencies

`google()` (restricted to `androidx`, `com.android`, `com.google`), `mavenCentral()`, `gradlePluginPortal()` (plugins only) and JitPack (restricted to the group `com.github.TeamNewPipe`, so it cannot shadow anything else). The Gradle wrapper is pinned with `distributionSha256Sum`, and CI validates `gradle-wrapper.jar`.

## Common errors

- *"SDK location not found"*: set `ANDROID_HOME` (or create `local.properties` with `sdk.dir=...`).
- *"NoClassDefFoundError" in a packaged desktop app*: a module is missing from the runtime; the smoke test exists to catch this.
- *Desktop UI tests fail with a headless error*: run them under `xvfb-run -a`.
- *libmpv not found*: install `mpv-libs` / `libmpv2`, or set `VELA_LIBMPV` to the library path; the app runs without playback and says so.
