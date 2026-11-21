# 117: Flatpak packaging in the release workflow, version 0.5.0-beta.1

## 1. Summary
The release workflow now also builds a Flatpak bundle (`Vela-<version>-linux-x86_64.flatpak`) and attaches it to the release. The manifest packs the portable app image the `linux` job already builds (it carries its own Java runtime), plus libmpv, ffmpeg and notify-send with their libraries taken from Ubuntu 24.04. Nothing is compiled by flatpak-builder. Version bumped to 0.5.0-beta.1 with a CHANGELOG section. The workflow and manifest have NOT been run: the first CI run is the real test.

## 2. Verification
| Check | Result | Verified by |
| --- | --- | --- |
| Workflow and manifest YAML parse | ok | running (python yaml) |
| `stage.sh` with a fake app tarball: stage folder complete, metainfo versioned | ok | running |
| Metainfo `appstreamcli validate`, desktop file `desktop-file-validate` | pass (one pedantic note: no screenshots) | running |
| `bundle-libs.sh`, `stage.sh` shell syntax | ok | running (`sh -n`) |
| `bundle-libs.sh` on Ubuntu with libmpv2 | NOT RUN (this machine is Fedora without libmpv) | not verified |
| flatpak-builder build, install and playback inside the sandbox | NOT RUN (no flatpak-builder here) | not verified |
| `buildSrc` version tests with the new version | pass | running |

## 3. Files created/changed
Created: `packaging/flatpak/` (`io.github.greenaltambe.Vela.yml`, `.desktop`, `.metainfo.xml.in`, `vela-flatpak`, `bundle-libs.sh`, `stage.sh`), this report.
Changed: `.github/workflows/release.yml` (jobs `flatpak-libs` and `flatpak`; `publish` waits for it and collects `*.flatpak`), `version.properties` (0.5.0-beta.1), `CHANGELOG.md`, `docs/BUILDING.md`, `docs/RELEASING.md`, `.gitignore`.

## 4. Decisions the spec didn't dictate
- App id `io.github.greenaltambe.Vela` (from the GitHub account); licence in the metainfo `GPL-3.0-only` (what the LICENSE file says; change it if you meant "or later").
- Runtime Freedesktop 24.08; `--socket=x11` only (the window toolkit is X11, Wayland sessions use XWayland). Permissions: network, ipc, pulseaudio, dri, `xdg-videos` (default downloads folder), Notifications, ScreenSaver (keep awake), own name `org.mpris.MediaPlayer2.vela` (media keys).
- libmpv, ffmpeg and notify-send come from Ubuntu's packages with their dependencies copied (all but libraries the runtime provides: C library, GPU, X11/Wayland, audio, fonts). Alternative would be building mpv and its dependencies in the manifest, which is much longer and needs checksums for each source; this way is untested too but short.
- yt-dlp is not bundled: the app downloads and manages its own copy into its data folder, as it does everywhere (needs the runtime's Python 3).
- The builder container is `ghcr.io/flathub-infra/flatpak-github-actions:freedesktop-24.08` by tag (a container image cannot be pinned like an action; the action itself is pinned to v6.8's commit).
- Not for Flathub as is: Flathub builds from source offline; this is a self-hosted bundle.

## 5. Known issues / unverified assumptions
- Everything about the build itself: expect a few failed CI runs. Likely trouble spots: the container image tag, the list of excluded libraries in `bundle-libs.sh` (a missing library shows as an error when libmpv loads, an extra one as a clash with the runtime), hardware decoding (needs the runtime's VAAPI extension on the user's side) and the sandbox's file access for a custom downloads folder.
- No auto-update (a bundle file, not a repository); the download is large (ffmpeg's libraries).
- Notifications use `notify-send` (bundled) through the Notifications permission; not tried.
- libmpv from Ubuntu 24.04 is mpv 0.37; fine for the app's use, not compared with Fedora's.

## 6. Manual test checklist for the user
1. Commit, push, then run Actions > `release` > Run workflow (dry run). Open the `flatpak-libs` and `flatpak` jobs; send me the log of any failure.
2. Download the `flatpak` artifact, then `flatpak install --user ./Vela-0.5.0-beta.1-linux-x86_64.flatpak` (the runtime is fetched from Flathub), run `flatpak run io.github.greenaltambe.Vela`.
3. In the app: play a video (libmpv found?), press the media keys, finish a download (Videos/Vela), check a notification and that the screen does not blank while playing.
4. `flatpak run --command=sh io.github.greenaltambe.Vela -c 'vela-flatpak --smoke-test'` should print SMOKE OK.

## 7. Follow-up: first CI run failed (tag v0.5.0-beta.1), fixed, version 0.5.0-beta.2
- Failure: the `flatpak` job in the container `freedesktop-24.08` died while updating the runtime: "Invalid cross-device link", then a segfault of flatpak-builder. That runtime is end-of-life ("no longer receiving fixes"), so the container tried to update the preinstalled 24.08 and the update failed on the container's file system. The build never reached my manifest. The other jobs (Android, Linux, Windows, flatpak-libs) had passed.
- Change: runtime and container moved to Freedesktop 25.08 (current; the image ships it ready, so there is nothing to update). `runtime-version: '25.08'`, image tag `freedesktop-25.08`.
- Version: 0.5.0-beta.2 (the tag v0.5.0-beta.1 already exists on the remote and published no release, since `publish` waits for every job). CHANGELOG section renamed to match.
- Unverified: whether 25.08 builds cleanly with the bundled libraries from Ubuntu 24.04 (glibc 2.39 vs the runtime's newer one is fine in that direction). Rerun and send the log if it fails.

## 8. Follow-up: second CI run (tag v0.5.0-beta.2) failed the same way
- The 25.08 container failed identically ("Invalid cross-device link" updating the runtime, then a flatpak-builder segfault), so it is the container, not the runtime's age: the action runs `flatpak-builder --install-deps-from=flathub`, which tries to update the runtime that sits in the image's layers, and that fails on the container's file system.
- Change: the `flatpak` job no longer uses the container or the action. It runs on the Ubuntu runner: apt installs `flatpak`, `flatpak-builder` and `elfutils`, the user installation gets Platform and Sdk 25.08 from Flathub, then `flatpak-builder --user --disable-rofiles-fuse` and `flatpak build-bundle`. Also relaxed `kernel.apparmor_restrict_unprivileged_userns` (Ubuntu 24.04 blocks the user namespaces bubblewrap needs).
- Unverified: still not run. Possible next problems: bubblewrap permissions on the runner, or a manifest error that only shows once the build really starts.
- Lesson recorded for the release steps: run the workflow by hand (Run workflow) and look at the Flatpak job before pushing a tag; the tag only needs to exist for `publish`.
