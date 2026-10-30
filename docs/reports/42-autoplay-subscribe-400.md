# 42 – Autoplay toggle, Subscribe HTTP 400

## 1. Summary
The Related header on the video page has an Autoplay switch (off by default; also Settings > Playback "Autoplay next video"): when a video ends, the queue's next video plays, else the first related one. Subscribe on the video page now uses the channel id from the video's own details (a real UC… id) instead of whatever the listing carried, which can be empty or a @handle; the account rejects those with HTTP 400.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| Install on the phone | NOT DONE: the phone's wireless debugging connection dropped during this step | n/a |
| Autoplay at the end of a video; Subscribe on a video from the account's Home feed | not exercised | compile-only |

## 3. Files changed
`Settings.kt` (`playback.autoplayNext`), `VideoScreen.kt` (switch, autoplay effect, channel id for subscribe/unsubscribe)

## 4. Decisions / known issues
- The cause of the 400 is inferred from the code path (blank or non-UC channel ids reach the subscribe call); it was not reproduced against YouTube. If it still happens, the error text under the subscribe row says which call failed.
- Subscribe on the channel page only changes the local subscription list (unchanged).

## 5. Manual test checklist
1. Reconnect the phone and install (`./gradlew :androidApp:installDebug`).
2. Open a video from Home (the account's feed), tap Subscribe: no HTTP 400.
3. Switch Autoplay on beside Related, let a short video end: the next one starts. Off: nothing happens.
