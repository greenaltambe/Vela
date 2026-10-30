# 45 Video page as a layer above the screen (stage 1 of report 44)

## 1. Summary
The video page no longer replaces the screen under it. `AppShell` now keeps the screen under the video (`Navigator.baseRoute`) composed, with its top bar and bottom bar, and draws the video page as a separate layer above it (`VideoLayer`). Minimising or pulling the page down shows the real, unchanged screen behind it, so the screenshot workaround is deleted. Navigation (the stack, `Route.Video`, Back, queue, mini player) is unchanged.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `:shared`, `:androidApp`, `:desktopApp` compile | pass | compile |
| `./gradlew allTests` | pass | running (offline) |
| Smooth minimise, nothing reloads or shifts, scroll kept | not checked | needs a device |

## 3. Files created/changed
- `ui/nav/Navigator.kt`: `baseRoute`, `baseCanGoBack`.
- `ui/AppShell.kt`: removed `prevScreen`/`prevShot`/`shotFade` and the video branch of `AnimatedDestination`; added `VideoLayer`; the chrome (top bar, bottom bar, rail selection) follows `baseRoute`.

## 4. Decisions the spec didn't dictate
- The top and bottom bars stay composed under the video page (covered by it) so the screen behind it looks exactly as before it opened.
- Exit animation: slides down only for a plain Back that was not pulled down; pulled down or leaving to another page (a channel) it fades. Opening: rises with a fade.
- The page background is opaque and melts away as the page is pulled (same 1.8x curve the page content used); an empty pointer handler stops touches reaching the screen below.
- The layer gets the same CompositionLocals as the screen, with no chrome inset.
- Not done: skipping drawing of the covered screen (overdraw while a video plays) and hiding it from screen readers.

## 5. Known issues / unverified assumptions
- The covered screen keeps composing and drawing while a video plays; cost on a low-end phone unmeasured.
- TalkBack can still reach the screen under the video page.
- Back from a channel opened out of the video page shows the video page again over the screen under it (the stack is Home, Video, Channel); animation untested.
- Switching accounts while a video page is open no longer rebuilds the video page (the screen under it still rebuilds).

## 6. Manual test checklist
1. Minimise a video from Home, Channel, Search, Library, Explore: the real screen is visible behind while dragging; nothing reloads or moves afterwards; scroll position kept.
2. Open a video: the page rises over the screen; Back sinks it.
3. Open a channel from the video page, press Back: you return to the video page.
4. Fullscreen, rotate, PiP, and both mini player styles (card, picture) still work; the mini player appears when the page closes.
5. Wide window / desktop: the side rail stays visible beside the video page.
6. Tap in empty parts of the video page: nothing happens on the screen below.
