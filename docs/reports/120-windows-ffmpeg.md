# 120 Windows ffmpeg bundled

## 1. Summary
Windows downloads failed with "ffmpeg was not found ... Fedora: sudo dnf install" because the package shipped no ffmpeg and the lookup only checked PATH and package-manager shims. The release workflow now bundles a pinned, checksum-verified gyan.dev ffmpeg 9.1 essentials build, the locator finds it in the app's resources folder, and the not-found message is OS-specific.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `:extractor-ytdlp:test` (incl. new bundled-ffmpeg lookup test) | pass | running |
| `:net-okhttp:test`, `:desktopApp:compileKotlin` | pass | running |
| Pinned zip downloaded; SHA-256 computed `b9bbbb3a...58fd`; contains `bin/ffmpeg.exe` (110 MB) and `LICENSE` | ok | running (download) |
| Workflow steps (download, verify, copy, smoke assertion) | untested | not run (needs Windows runner) |
| ffmpeg.exe found and merging on real Windows | untested | not run |

## 3. Files created/changed
- Changed: `extractor-ytdlp/.../BinaryLocator.kt` (new `bundledDirs`: resources dir and exe dir, searched after PATH), `net-okhttp/.../FfmpegMediaMerger.kt` (OS-aware hint), `.github/workflows/release.yml` (FFMPEG_* env, download step, attribution line, smoke assertion `SMOKE ffmpeg: found`), `docs/BUILDING.md`, `desktopApp/appResources/windows-x64/README.md`
- Test: `BinaryLocatorTest.kt`

## 4. Decisions the spec didn't dictate
- PATH ffmpeg wins over the bundled one (user's choice respected); bundled is the fallback.
- Pinned release 9.1 (stable tag), not a rolling git build. Essentials build is GPLv3, compatible with Vela's GPL-3.0; licence shipped as `FFMPEG-LICENSE.txt`, source pointer added to `LIBMPV-SOURCE.txt`.
- Only `ffmpeg.exe` is bundled (not ffprobe/ffplay).

## 5. Known issues / unverified assumptions
- Windows MSI/zip grows by roughly 40-50 MB compressed (110 MB exe); not measured.
- Assumes `compose.application.resources.dir` points at the resources folder in the packaged app (same assumption libmpv already relies on).
- The new CI smoke assertion will fail the release job if the bundled file is not found; that is intended.
- Antivirus scanning of an unsigned ffmpeg.exe is untested.

## 6. Manual test checklist
1. Run the release workflow (or a tag build); confirm the "ffmpeg (pinned, checksum verified)" step and the smoke test pass.
2. Install the MSI on a Windows machine without ffmpeg; Diagnostics should list `ffmpeg: ...\resources\ffmpeg.exe (common location)`.
3. Download a video at a quality that needs merging (e.g. 1080p); it should complete and play.
4. Retry the previously failed "Abba - Gimme Gimme Gimme" download; it should merge without re-downloading.
5. Check the portable zip also contains `ffmpeg.exe` and works the same.
