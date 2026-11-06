# 65 Streams that did not play, details that did not load

## 1. Summary
On this phone every anonymous tier is told "Sign in to confirm you're not a bot", so only the signed-in yt-dlp tier can resolve streams. It did resolve them (12-24 s), but yt-dlp picked the `WEB_EMBEDDED_PLAYER` client, whose googlevideo URLs answer 403 to any byte range past about 1 MiB, so playback never started (0:00 / 0:00) and the session re-resolved again and again. The account tier now asks yt-dlp for the `web_safari` client; playback starts and keeps going. Details (likes, comments, description) now also use the account when the anonymous tiers have just been told to sign in, and the video page no longer prints the raw tier message.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| Cause: 403 pattern per range (same URL, same headers) | 0-1023 .. 0-786431 and offsets 262144, 524288: 206. 0-1048575, 0-2 MB.., offset 1048576 and 2000000, open-ended, 10 MB: 403. URL client `WEB_EMBEDDED_PLAYER`, no `pot` | measured on the phone (temporary probe, removed) |
| Client choice, same video, resolve then play ~40 s | `tv`: "The page needs to be reloaded", `tv_simply`, `android_vr`: "Requested format is not available", `web_safari`: PLAYING | running on device |
| `web_safari` over time | PLAYING at 22.9 s, 37.5 s, 53.2 s, no 403 | running on device |
| Final build: a Home video plays (position 101 s after 40 s), likes / comments load | seen | running on device |
| `allTests`, `:desktopApp:test`, `:androidApp:installDebug` | pass, 0 failures (new `detailsGoToTheAccountWhenTheAnonymousTiersAreToldToSignIn`) | running |
| Desktop yt-dlp reproduction | not possible (no JS runtime here) | not done |
| Other videos / age-restricted / long videos | one video played end to end for 100 s; others not tried | not verified |

## 3. Files created/changed
- `androidApp/.../playback/PlaybackRuntime.kt`: the account tier passes `--extractor-args youtube:player_client=web_safari`.
- `shared/.../extractor/chain/FallbackChain.kt`: `DETAILS` may reach the account when the details setting is off but an anonymous tier answered SIGN_IN_REQUIRED.
- `shared/.../ui/screens/VideoScreen.kt`: plain one-line "Details could not be loaded" text (tag `video.infoError`).
- `shared/src/commonTest/.../chaos/ChaosScenariosTest.kt`: new test.

## 4. Decisions the spec didn't dictate
- One client (`web_safari`), not a list: with a list yt-dlp merges formats and may still pick the embedded client's. `tv` needs a fresh page and failed here.
- Applied to the Android account tier only; the desktop account tier and the anonymous yt-dlp tier are unchanged (desktop could not be measured).
- Details use the account only after a sign-in answer, so the "Use the account for details" setting still means "always".

## 5. Known issues / unverified assumptions
- Resolve still takes 12-25 s per video on this phone (QuickJS challenge), so the first frame is slow; nothing caches it across opens.
- The re-resolve loop is bounded by `maxReResolves`; it was not changed. A client that 403s on a different video would still re-resolve a few times.
- With `web_safari` a video's quality list may differ from before (formats yt-dlp offers for that client); not compared.
- Channel name and avatar under the title were blank for the video I tested (account details do not return them); not investigated.
- PO tokens are still not implemented; if YouTube starts requiring one for `web_safari`, this breaks again.

## 6. Manual test checklist
1. Open 5 different videos from Home; each should start playing within about 30 s. Note any that do not.
2. Play one for several minutes; seek forward past the first minute.
3. Open an age-restricted video (with the setting on) and a live stream.
4. Video page: likes, description ("...more") and the Related list load; if details fail, one grey line appears.
5. Settings > Accounts: the account tier is not paused; Health shows the tiers.
