# Later: in-app updates from GitHub Releases

Status: deferred by the owner. Not started.

## Idea
The app checks the latest GitHub release (`/repos/<owner>/<repo>/releases/latest`, tag `v<VERSION_NAME>`), compares it with its own
version (handle `-beta.N` prereleases, with a setting to include or skip them) and offers the update.

## Per platform
- **Android**: download the signed APK, hand it to the system installer via `FileProvider`. Needs `REQUEST_INSTALL_PACKAGES` and the
  user allowing "Install unknown apps" once. The user always confirms the install. Same signing key required. Not for any
  F-Droid/Play build.
- **Windows** (experimental): download the installer/zip and open it. In-place replacement (Conveyor/Velopack) is possible but adds a
  build dependency. SmartScreen warns without code signing.
- **Linux**: deb/rpm cannot replace themselves without root, so download the package and open it with the system installer (`xdg-open`)
  or link to the release. In-place update only for the portable tarball in a writable directory. Flatpak/repo would be the better
  long-term route.

## Recommended first version
1. Update check in `:shared` (at most once a day on startup, plus a manual button in Settings > About).
2. Android: in-app download then system installer. Desktop: download the matching asset to Downloads and open it. No in-place replacement.

## Open decisions
- Check on by default or opt-in? It is a startup network call to GitHub, so disclose it and make it switchable.
- Verify the download against a `SHA256SUMS` file published with each release (release workflow must produce it).
