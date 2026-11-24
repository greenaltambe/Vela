# 144 Release workflow: apt downloads that stall

## 1. Summary
The release run for `v0.6.1-beta.5` published nothing: the job "Flatpak libraries (libmpv, ffmpeg)" was cancelled after 20 min 16 s during `apt-get install` (101 of 107 packages downloaded, then the 20-minute limit), the Flatpak job needs it, the publish job needs the Flatpak job, so "GitHub release" was skipped and only the source archive exists. `release.yml` now makes apt time out and retry stalled downloads and gives that job 40 minutes.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| Cause: the job's log ends in "The operation was canceled" at 20 m 16 s, and the job's `timeout-minutes` was 20 | consistent | read from the pasted log and the workflow; not reproducible here |
| `release.yml` parses as YAML; the job list is unchanged | pass | verified by running (a YAML parse only) |
| The new apt options, and that the workflow now completes | NOT RUN | needs a real run on GitHub |

## 3. Files created/changed
`.github/workflows/release.yml` (the `-o Acquire::Retries=5 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30` options on the apt steps of the `linux` and `flatpak-libs` jobs; `flatpak-libs` timeout 20 to 40 minutes), this report.

## 4. Decisions the spec didn't dictate
The publish job still needs the Flatpak bundle (a release without it is not published silently). `ci.yml` has a similar apt line and was not changed.

## 5. Known issues / unverified assumptions
The cause is read from the log (a slow mirror, 265 kB/s on the update step); the exact reason one package stalled is not known. The Android, Linux and Windows jobs' results were not in the pasted log: they should show green for the re-run to publish everything.

## 6. Manual test checklist
1. Open the failed run (release #21) on GitHub and choose "Re-run failed jobs". It re-runs the Flatpak libraries job and the jobs after it, reusing the files the other jobs already built; no new tag is needed.
2. When "GitHub release" is green, check the release page: APK, .deb, .rpm, tarball, .flatpak, .msi, .zip, SHA256SUMS.
3. Commit and push the workflow change separately (it is not part of the tag).
