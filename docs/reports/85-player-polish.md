# Report 85: player polish

## 1. Summary

All five items are implemented in the order given (gesture lock, touch rules, markers, button sizes, channel name). A finger on the seek bar now only arms it; scrubbing starts after the touch slop, commits once on release and locks every other player gesture (sheet drag, fullscreen swipes, double-tap, hold) until 150 ms after the lift. SponsorBlock markers are drawn as the wave's own stroke in the marker colour, in the EXPRESSIVE, WAVE and RIBBON styles. All round player buttons share one implementation and one size table. The channel name gets up to two lines and the row re-flows to a stacked layout from a real measure. Evidence is desktop Compose tests; nothing was run on a phone.

## 2. Verification

| Check | Result | Verified by |
| --- | --- | --- |
| Touch: tap = no commit and no preview; drag from elsewhere glides and commits once under the final x; grabbing the handle = first preview is the current value; vertical drift ignored; mouse click still seeks; gesture cut mid-drag commits nothing and clears the preview | passed (`PlayerPolishTest`) | VERIFIED-BY-RUNNING (desktop harness) |
| Same touch rules against the old press-to-seek handler (`LegacyScrubber` in the test) | tap and grab-the-handle tests FAIL on it, as required | VERIFIED-BY-RUNNING |
| Item 5: scrub with 300 px of downward drift on the bar seeks and does not minimise | passed. **Failed on the old `PlayerSheet.kt`** (old sheet restored from git, bar showed gone = page minimised) | VERIFIED-BY-RUNNING, fail-first shown |
| Item 5: swipe down on the picture still minimises; double-tap seek still works | passed | VERIFIED-BY-RUNNING |
| Item 5: 150 ms tail, fullscreen swipe-down with drift | NOT VERIFIED by a test (code only, see section 5) | NOT VERIFIED |
| `markerSpans`: fully played, partly played (split at the handle gap), unplayed, tiny, across a chapter gap, start/end | passed (`MarkerSpansTest`) | VERIFIED-BY-RUNNING |
| Markers drawn into an `ImageBitmap`: EXPRESSIVE marker pixels move vertically between two wave phases; WAVE draws played and unplayed markers; BOLD marker unchanged on the centre line | passed | VERIFIED-BY-RUNNING (pixels). Old code would fail the first two by construction (flat rect, WAVE returned before drawing markers); not run against the old code |
| RIBBON strand tint | compiled, no pixel test | compile-only |
| Buttons: plain / long-press / disabled `ControlButton` identical bounds (48 dp) and identical drawn disc diameter; fullscreen: back, captions (disabled), mute, quality, speed, sleep, fullscreen all 48 x 48, previous = next = 56, play 64 | passed | VERIFIED-BY-RUNNING. The bounds check passes on old code too (the old long-press disc was drawn at 88 % *inside* the same bounds); the drawn-disc test is the one that catches it |
| Enabled captions with a real long-press in fullscreen | not tested (the fake video has no caption tracks) | NOT VERIFIED |
| Channel row: short name stays inline and the button is visible; 60-char name at font x1.3 stacks, button visible, full name in the content description; a name that fits in two lines is not ellipsized | passed | VERIFIED-BY-RUNNING |
| Channel row: no recomposition loop | not measured (the layout is a pure function of constraints) | NOT VERIFIED |
| Whole offline suite (`allTests`, `:innertube-auth:test`, `:desktopApp:test` incl. `LayoutInvariantsTest`), `:androidApp:compileDebugKotlin` | everything passed except `ThemeSourceScanTest` (a raw `Color.White` in my marker code); fixed (markers are 15 % fainter via alpha) and that test plus `PlayerPolishTest` re-run green. The full suite was not re-run after that one-line fix. | VERIFIED-BY-RUNNING (Android part compile-only) |
| Everything on a phone (touch slop, real velocity, haptics, TalkBack) | NOT VERIFIED | owner, section 6 |

## 3. Files created/changed

Created
- `ui/components/GestureLock.kt` (`PlayerGestureLock`, `LocalPlayerGestureLock`)
- `ui/screens/ChannelRow.kt`
- Tests: `desktopApp/.../ui/PlayerPolishTest.kt`, `shared/src/commonTest/.../ui/MarkerSpansTest.kt`

Changed
- `ui/components/Scrubber.kt`: new pointer handler (`awaitEachGesture`, `awaitHorizontalTouchSlopOrCancellation`, `horizontalDrag`), `tapToSeek: Boolean?` (null = per pointer type), `lockGestures`, armed state, relative grab / glide; `markerSpans`, `MarkerSpan(s)`; `drawProgress` tints the wave / strands and draws flat markers
- `ui/player/PlayerSheet.kt` (lock, band around the bar, drop when a child consumed the moves), `PlayerGestures.kt` (`lock` for `playerVerticalSwipe`), `screens/VideoScreen.kt` (fullscreen swipe uses the lock and the band; `ChannelRow`)
- `ui/player/PlayerControls.kt` (single `PlayerIconButton` implementation on Material's expressive icon button with long-press on top; `PlayerButtonSizes`), `PlayerView.kt` (sizes from the table, double-tap / hold respect the lock, volume bar outside the lock)
- `ui/screens/DescriptionSheet.kt` (channel card title 2 lines)

## 4. Decisions the spec didn't dictate

- **A consumed `down` is not a reason for the sheet to drop a gesture** (deviation from "`if (down.isConsumed) return`"): the picture's own tap detector (`detectTapGestures`) consumes every down, so that rule killed minimise / swipe-up on the picture (my own regression test caught it). The lock (set by the bar in the Main pass, before the sheet sees the touch), the 12 dp band and "moves consumed by a child before the drag started" cover the real cases.
- Long press on a round button: Material's icon button has no long-click, so the press is timed from its interaction source (system long-press timeout); the release that ends a long press is swallowed and does not also click. This also changed the pressed look of the buttons from the old custom squeeze/halo to Material's expressive circle-to-square morph for all of them (one look, as asked).
- **Final channel row (second revision, chosen by rendering the four candidates at 360 dp, long and short names, subscribed and not):** one row, name up to two lines, and ONE control: the word button `Subscribe`, or once subscribed a small bell + chevron pill (`SubscribedPill`, tag `video.subscribed`) whose menu holds All / Personalized / None and Unsubscribe. Why: one-line name with the full buttons cut the name to "The Extre…"; the icon-only pair (check + bell) kept two cryptic buttons and still cut long names; the two-tier layout shows everything but is the "name on another line" pattern the owner rejected and makes short names tall for nothing; the single pill gave the name about 70 dp more than the full buttons, kept the word "Subscribe" where the action is new, and matches the familiar pattern. The screenshots were scratch and are not kept. The measure-and-compact machinery below was removed; `ChannelRow` is a plain inline layout. Tests: the pill is under 80 dp and on the name's row, a long name keeps its row and its content description, the menu unsubscribes and the word button returns.
- (superseded by the final channel row above) **Revised after owner feedback (stacked layout rejected):** the channel row never re-flows to another line now. The name keeps up to two lines beside the buttons; when it would not fit with the full buttons ("Subscribe" / "Subscribed" + bell), the buttons are composed as icon-only (plus / check toggle, bell) instead. The decision uses the label measured with the real font plus fixed paddings (an estimate made before composing, so only one set of buttons exists; two sets were tried and the unplaced one still showed up in the semantics tree). `ChannelRow` takes `fullActionsLabel` / `fullActionsExtra` for that. The test now asserts the buttons stay on the name's row and the subscribe button gets narrower. The older bullet below describes the rejected stacking and is superseded.
- **Live chat redesign (owner request):** `ChatPanelUi.kt` replaces the outlined button + plain lines: a tonal card whose header (icon tile, "Live chat" / "Chat replay", message count, pulsing LIVE pill, chevron) opens and closes it with a spring; messages are bubbles (coloured initial, author, text), paid ones tertiary cards with an amount pill, memberships primary cards; the list follows the newest message at the bottom and a "Latest" pill appears when scrolled up. Polling logic is unchanged. `LiveChatUiTest` was updated to look for author, text and amount as separate parts. Desktop-tested only; the look is not seen on a device.
- (superseded) Channel row: the "does it fit" rule is purely "the name fits in two lines at the width left beside the buttons", with no minimum width. With the **Subscribed + bell** pair on a 360 dp phone most names therefore stack; with "Subscribe" alone they stay inline longer. A 60-character name cannot show in full at font x1.3 even stacked (it needs 3 lines); the spec's own last-resort rule (two lines + ellipsis + full text in the content description) applies, so the test asserts stacking, visibility of the button and the content description rather than "no ellipsis".
- The mini player has no channel name, nothing changed there.
- Unplayed marker colour is 15 % lighter only in EXPRESSIVE and WAVE (the flat styles keep their exact old drawing).
- Button-size assertions are in `PlayerPolishTest`, not `LayoutInvariantsTest` (that class is a route sweep; fullscreen needs its own setup).
- No screenshot of the landscape controls was added (the harness only writes screenshots when the build sets `vela.screens.dir`).

## 5. Known issues / unverified assumptions

**Findings (hand-back):**
- Proven only by desktop Compose tests, not on a phone: all five items. Touch slop, fling velocity and the real feel of the glide spring are device behaviour. The system pointer *cancel* cannot be injected on the desktop harness, so cancellation is tested by cutting the gesture's coroutine, which runs the same cleanup (no commit, preview cleared).
- The stacked channel layout at default font scale on 360 dp for the owner's real channels: NOT checked (the DB of the owner's subscriptions is not available to the agent). With the fixture's "Subscribed" state a name of about 20+ characters already stacks at 360 dp.
- Clipped-wave markers: pixel-checked in EXPRESSIVE and WAVE (marker pixels follow the wave), not looked at on a device; RIBBON is compile-only. The "marker rail" alternative was not needed so far.

Other:
- Item 5 parts without a test: the 150 ms tail (virtual clock) and the fullscreen swipe-down with drift; both are the same `PlayerGestureLock` check (`isLocked(change.uptimeMillis)`) as the tested sheet path.
- WAVE style still does not draw the buffered span or chapter gaps (only markers were added).
- The desktop volume bar opts out of the lock (`lockGestures = false`).
- Android is compile-only.

## 6. Manual test checklist (phone)

1. Open a video, tap the seek bar at random places: nothing moves, the handle only swells slightly.
2. Put a finger on the bar and drag sideways: it follows (it glides to the finger if you started away from the handle), the time card shows, lifting seeks once. Drift the finger up and down while dragging: no minimise, no fullscreen.
3. Swipe the picture down: minimises. Swipe it up: fullscreen. In fullscreen, swipe the picture down: leaves fullscreen; scrub with downward drift: stays.
4. Double-tap the left/right half of the picture and hold it: seek and fast playback still work.
5. Open a video with SponsorBlock segments, play past one, and look at the Expressive and Wave progress styles: the coloured part ripples with the bar and moves with the handle; unplayed segments are flat and slightly lighter.
6. Open a video from a channel with a long name (also with the system font at 130 %): the name shows up to two lines; with Subscribed + bell the buttons drop under the name.
7. Rotate to landscape: back, captions, mute, quality, speed, sleep timer and fullscreen are the same size; long-press captions still opens the caption list and a short tap toggles them.
