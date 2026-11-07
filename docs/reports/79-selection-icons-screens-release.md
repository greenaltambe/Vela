# 79 Selection bar, bookmark icon, screenshots, release set-up

## 1. Summary
Choosing a row in History / Downloads / a playlist no longer moves the list: the same app bar stays (title, back button and actions cross-fade to "N selected", with a hint on the subtitle line), so its height does not change. The Save icon is an outlined bookmark and fills when the video is saved. All old screenshots (about 150 in `docs/reports/*-screens`, `docs/screenshots`, `docs/tasks/assets`, phone captures of a real account) are deleted; seven new ones from the test build with sample data are in `docs/screenshots` and the README. The release workflow signs the APK (your secrets, or a key made for the run) and publishes the release; the CI workflow is unchanged.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests`, `:desktopApp:test`, `:desktopApp:smokeTest`, `:androidApp:assembleDebug`, `:androidApp:assembleRelease` | pass | running |
| Phone: History selection (same bar height, "1 selected", outlined bookmark in the bar) | seen | running on device |
| Phone: Downloads / playlist selection | same component, not opened | not verified |
| Workflow files parse (YAML) and keep their pinned actions | yes; `actionlint` / `shellcheck` are not installed here, CI's `actionlint` job checks them | partly verified |
| The release workflow itself | needs a pushed tag | not verified |

## 3. Files created/changed
`ui/components/LibraryScaffold.kt`, `ui/theme/MaterialOutlined.kt` (save = `BookmarkBorder`), README (screenshots, look and features text), `docs/screenshots/*` (new), deletions listed above, `docs/RELEASING.md`, `.github/workflows/release.yml` (earlier), `version.properties` and `CHANGELOG.md` (0.2.0-beta.1).

## 4. Known issues
- Older reports under `docs/reports` still mention the deleted screenshot folders; the links are dead.
- The README screenshots show grey placeholders for thumbnails (sample data, no network).

## 5. Manual test checklist
1. History / Downloads / a playlist: long press a row; nothing shifts; the bookmark in the bar is outlined.
2. A video page: Save is an outlined bookmark, filled once the video is in a playlist.
