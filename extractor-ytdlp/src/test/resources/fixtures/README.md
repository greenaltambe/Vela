# yt-dlp fixtures

**These are real captures** from `yt-dlp 2026.10.26` (each JSON carries a `_fixture` key with the version),
produced by `scripts/capture_fixtures.py`, trimmed to a few formats and scrubbed (client IP and URL signature
material replaced by `x`; `expire` is kept because the mapper reads it). Nothing in them is hand-written.

| File | What it is |
|---|---|
| `video.json` | `dQw4w9WgXcQ`: storyboard `sb0`, HLS audio-only `233` (`acodec: null`), HLS video `270`, https audio `139/140/249/251`, https video-only `137/248/401` |
| `short.json` | a Short fetched through `/watch?v=`; the only Short signal is `media_type: "short"` |
| `audio_drc.json` | `jNQXAC9IVRw`: `140-drc`/`251-drc` next to `140`/`251` |
| `audio_multilang.json` | `0e3GPea1Tyg`: dubbed tracks `251-0`..`251-23`; the default has `language_preference: 10` |
| `flat_search.json` | `--flat-playlist ytsearch5:` results (includes a channel entry between videos) |
| `flat_channel.json` | `--flat-playlist` of a channel's `/videos` (entries have no channel fields) |
| `stderr_*.txt` | stderr of failing runs: unavailable, terminated account, truncated id, not a URL, missing channel |

Not captured, because it cannot be reproduced without a specific account or video: a **private** video, an
**age-restricted** video, a **members-only** video, a **bot/sign-in** wall and **HTTP 429/403**. The patterns for
those in `YtDlpErrors.kt` are written from memory of yt-dlp's wording and are marked as such in the tests.

Real shapes the first, hand-written version got wrong (all asserted in `YtDlpMappingTest`): `--dump-single-json` has
no top-level `http_headers`; HLS audio-only formats have `acodec: null` (not `"none"`); there is no muxed format
(`18`) in the list; a Short is only marked by `media_type`; channel-listing entries have no channel fields;
`timestamp` is present, so the upload time is exact; the default dub is the one with `language_preference: 10`.

To refresh: `python3 scripts/capture_fixtures.py`.
