# Vela

A YouTube client for Android and Linux that keeps working. Your subscriptions, history, playlists and downloads live on your device, and if one way of getting a
video fails, Vela quietly tries the next one. It looks like Material 3 Expressive, in light, dark or whatever your system uses.

> **Beta (0.2.0-beta.1).** Android is the main platform, Linux works well, Windows is experimental.

## What you get

- Home, search, channels, playlists, Shorts, live streams and chat, chapters, captions, speed, sleep timer, mini player and picture-in-picture
- SponsorBlock, Return YouTube Dislike and DeArrow, each one a switch you can turn off
- Downloads with subtitles, automatic download rules, backup and restore
- Local history, subscriptions with new-upload notifications, and playlists, all without signing in
- Optional Google accounts, used only when everything else fails
- Keyboard shortcuts and media keys on desktop

## Install

Grab a file from the [Releases](../../releases) page.

- **Android 7.0+**: open the `.apk` (you may need to allow installs from your browser or files app).
- **Linux**: `.deb` (`sudo apt install ./Vela-<version>-linux-amd64.deb`), `.rpm` (`sudo dnf install ./Vela-<version>-linux-x86_64.rpm`) or the portable `.tar.gz`.
  Vela needs `libmpv` for playback; `ffmpeg` is nice to have for merging downloads.
- **Windows (experimental)**: the `.msi` or `.zip`. Expect rough edges, and SmartScreen will warn because the installer isn't code-signed.

Each release has a `SHA256SUMS` file if you want to check your download.

## Build it yourself

```
git clone <this repository> && cd Vela
./gradlew :androidApp:assembleDebug      # Android (needs the Android SDK)
./gradlew :desktopApp:run                # desktop (needs libmpv)
```

More in [docs/BUILDING.md](docs/BUILDING.md). Tests: `./gradlew allTests`. Releasing: [docs/RELEASING.md](docs/RELEASING.md).

## A word of caution

Vela is an independent project and is **not affiliated with YouTube or Google**. Third-party clients may go against YouTube's terms, so use it at your own risk,
and if you sign in, use a throwaway account. More detail in [docs/SPEC.md](docs/SPEC.md).

## More

[Docs](docs/README.md) · [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [Third-party notices](THIRD_PARTY_NOTICES.md)

Vela is free software under the **GPL v3 or later** ([LICENSE](LICENSE)). It builds on NewPipeExtractor and youtubedl-android.
