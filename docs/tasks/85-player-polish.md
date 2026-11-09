# Task 85: player polish (seek bar touch rules, SponsorBlock markers on the wavy bar, channel name, landscape buttons, gesture lock)

Read CLAUDE.md, docs/SPEC.md, docs/reports/33-player-gestures-mini.md, 34-progress-gestures-pip-home-cache.md, 50-player-controls-description-sheet.md and 60-expressive-overhaul.md. Do not commit.
Report `docs/reports/85-player-polish.md` (fixed sections; Verification table separates VERIFIED-BY-RUNNING / compile-only / NOT VERIFIED). Terminal reply 5 lines max.
Every fix gets a test that FAILS on the old code (show it failing in the report). The Android runtime cannot be run by the agent: Compose tests on the desktop harness (`UiHarness`, touch input via `performTouchInput`) are the evidence, and device behaviour is listed for the owner in section 6.
All five items touch the same files; do them in this order: 5 (gesture lock), 1 (touch rules), 2 (markers), 4 (button sizes), 3 (channel name).

Files: `ui/components/Scrubber.kt` (Scrubber, drawProgress), `ui/player/PlayerView.kt` (controls), `ui/player/PlayerControls.kt` (PlayerIconButton, ControlButton), `ui/player/PlayerSheet.kt` (sheet drag, swipe up = fullscreen), `ui/player/PlayerGestures.kt` + `ui/screens/VideoScreen.kt` (~l.354, swipe down leaves fullscreen), `ui/screens/VideoScreen.kt` (~l.432, channel row).

## 1. Seek bar on touch: a tap must not seek (like YouTube)

Cause: `Scrubber`'s pointer handler treats `Press` as "dragging = fraction at the finger" and `Release` commits it, so any touch, even a brush while reaching for something else, seeks at once.
Rules (touch pointers only; mouse and stylus keep click-to-seek, desktop users expect it, keyboard/accessibility `setProgress` is unchanged):
- Press alone does nothing to the position and does not seek on release. A short "armed" feedback only (thumb/handle grows slightly), no preview card, no `onPreview`, no `onCommit`.
- Scrubbing starts when the finger moves horizontally past touch slop (use the platform slop, `awaitHorizontalTouchSlopOrCancellation`-style, so a vertical start does not scrub). Once started, the bar follows the finger (`onPreview` each move), release commits once (`onCommit`), cancel (second finger, system gesture, pointer cancel) commits nothing and clears the preview.
- Start position: if the press landed within a grab radius of the handle (`max(24.dp, thumb radius)`), the handle follows the finger by RELATIVE movement (no jump on grab); if it landed elsewhere on the bar, after slop the handle glides to the finger (short spring, not a jump) and then follows. Both are "drag", never "tap".
- Vertical drift while scrubbing is ignored for position (only x counts) and also blocks every other gesture (item 5). No fine-scrub-by-sliding-up in this task.
- `Scrubber` is also used for volume (desktop) and could be used elsewhere: add a parameter `tapToSeek: Boolean = pointerType != Touch` default decided per event by pointer type, so those call sites keep working; do not add a user setting.
Tests (fail first): touch down+up on the bar without movement => no `onCommit`, no `onPreview`, position unchanged; touch drag 200 px => one `onCommit` with the fraction under the final x, previews along the way; grab on the handle => first preview equals the current value; cancel mid-drag => no commit; mouse click => commit (desktop behaviour kept); drag with 80 dp vertical drift => fraction depends on x only.

## 2. SponsorBlock markers must follow the bar's form (wavy / ribbon / expressive)

Cause (read from `drawProgress`): markers are drawn as flat rectangles on the straight centre line (step 2), and the played part is drawn afterwards as an animated wave (EXPRESSIVE), ribbon strands (RIBBON) over them, so inside the watched part a segment looks like a flat, still block under or beside a moving wave. In the WAVE style the function returns early and draws NO markers at all (and no buffered span, no chapter gaps). HAIRLINE/BOLD(_DOT) are fine.
Fix: the colour belongs to the stroke, not to a separate shape.
- Played part, EXPRESSIVE and WAVE: build the wave path once (as now) and draw it in the progress brush; then for each marker overlapping the played span draw the SAME path again in the marker colour inside `clipRect(left = max(markerX0, playedX0), right = min(markerX1, playedEndX))`, same stroke width and caps. The coloured piece therefore rises and falls with the wave, moves with the phase, thickens with the drag bold scale and follows the handle gap. Use the wave's own cut points; no extra padding, no 3 dp minimum inside the wave (keep the 3 dp minimum for the flat part only).
- Played part, RIBBON: tint the strands the same way (clip to the marker span, draw the strands with the marker colour at their own alpha). Keep the base band flat rect as now underneath.
- Unplayed part (flat, right of the handle gap): keep the flat marker rects, but clip them to start after the handle gap in EXPRESSIVE so a marker never pokes into the gap, and round the ends to the track thickness so the unplayed segment matches the track's rounded ends.
- WAVE style: draw markers in the unplayed flat part and use the clipped-wave rule in the played part (the early `return` must stop skipping markers, buffered span and chapter gaps; at minimum markers).
- Played and unplayed markers keep the same colour per category; the unplayed one may be 15 % lighter so "already passed" reads without hiding the category. Do not change colours or categories.
- Hover/drag bold (`boldScale`) applies to marker strokes too.
- Extract the geometry into a pure function (`markerSpans(markers, geometry, xNow, handleGap) -> played spans + unplayed spans`) with unit tests: marker fully played, partly played (split at `xNow - handleGap`), unplayed, tiny marker, marker straddling a chapter gap, marker at the very start/end.
Tests (fail first): draw the bar into an `ImageBitmap` for EXPRESSIVE with a marker inside the played part, at two wave phases: the marker-coloured pixels' vertical position changes between phases (the old flat rect does not); WAVE style now has marker-coloured pixels; flat styles unchanged (golden compare of the existing screenshots).
Alternative considered and rejected: a separate straight "marker rail" under the bar (honest, always flat) because the owner wants the segments to read as part of the bar; mention it in the report only if the clipped-wave look fails on a real device.

## 3. Channel name always readable on the video page (Material 3 Expressive)

Cause: the channel row is `Avatar | Column(weight 1: name maxLines = 1 + Ellipsis, subscribers) | Subscribe / Subscribed + bell` and the buttons take their width first, so longer names end in an ellipsis. `ui/screens/VideoScreen.kt` ~l.432-440.
Behaviour (the name has priority, the buttons adapt):
1. The name may use 2 lines (`titleMedium`, bold). Avatar aligns to the first line, subscribers count under the name.
2. If, with the buttons present, the name still does not fit in 2 lines, the row re-flows to a stacked layout with an expressive spring (`animateContentSize` / `AnimatedContent` with the app's `VelaMotion.snappy`): avatar + name + subscribers use the full width on top, the subscribe button group moves under them, left-aligned (connected button group, as now, `ButtonGroupDefaults.ConnectedSpaceBetween`), full width on COMPACT screens.
3. Last resort (very long names at huge font scale): 2 lines with ellipsis on the stacked layout, and the full name in the node's content description so TalkBack reads it; tapping the row still opens the channel page, which shows the whole name.
Decide stacked vs inline from a real measure (a `Layout`/`SubcomposeLayout` or `TextMeasurer` with the available width after measuring the buttons), not from `onTextLayout` feedback that could flicker or loop. Must respond to font scale and to the window width (COMPACT, MEDIUM, and the "beside" tablet layout).
Also check `DescriptionSheet` and the mini player header for the same single-line ellipsis on the channel name and apply the same 2-line rule where the name is the only text on its line.
Tests (fail first), tags `video.channel`, `video.channelRow`, `video.subscribe`: a 60-character channel name at 360 dp width and at font scale 1.3: no ellipsis in the inline or stacked layout (assert the text node is not visually overflowing or the stacked tag exists and the name shows fully); a short name keeps the inline layout (no extra row); the subscribe button is always fully visible and clickable in both layouts; no layout loop (recompose count bounded).

## 4. Landscape (fullscreen) buttons: one size

Cause (read from `PlayerIconButton`): two code paths draw the round button differently. Without `onLongClick` it is Material's `FilledIconButton` (`size` wide scrim disc, 24 dp icon). With `onLongClick` (only the CAPTIONS button has one) it is a hand-drawn disc at `0.88 * size` with the glyph forced to `0.42 * size` (about 20 dp in a 48 dp button). So captions looks smaller than mute, audio, quality, speed, sleep, settings, back, fullscreen. The transport buttons (previous/next 56, play/pause 64 in fullscreen) are intentionally bigger but must look like one family.
Fix:
- Make both paths render identically: disc = full `size`, glyph = the same as the other path (24 dp icon; the play/pause glyph keeps its explicit 40 dp), same scrim alpha, same press morph. Prefer a single implementation: wrap the Material `FilledIconButton` and add long-press with a gesture on top that does not swallow the tap, or keep the custom path but make its numbers equal; whichever, there must be no second look.
- Define the sizes in one place (`PlayerButtonSizes`: utility 48, transport 56 / play 64 in fullscreen, 48 / 56 in the compact player, compact fullscreen toggle 40 stays only in the compact top-right) and use it in `PlayerView` instead of literals. Disabled captions ("no captions for this video") must be the same size as the enabled one.
- In fullscreen the bottom row and the top row must use equal sizes and equal spacing; check `menuButtons(true)` spacing with 7 buttons on a 360 dp tall phone (landscape) so nothing clips or shrinks.
Tests (fail first): in fullscreen compose test, collect the bounds of `player.back`, `player.settings`, `player.captions` (enabled, disabled and with long-press), `player.mute`, `player.audio-track`, `player.quality`, `player.speed`, `player.sleep-timer`, `player.fullscreen` and assert equal width and height (48 x 48 dp); previous / next equal to each other, play/pause bigger; add this to `LayoutInvariantsTest`. Add a screenshot of the landscape controls to the report.

## 5. No minimise / fullscreen gestures while the seek bar is being used

Cause (read from `PlayerSheet` `gestureModifier`): the sheet's drag handler uses `awaitFirstDown(requireUnconsumed = false)` and then reads every move of the same pointer without checking whether a child already consumed it, so a slow horizontal scrub with 8 dp of vertical wobble starts dragging the sheet (minimise on release) or, with a firm upward drift (over 72 dp, or fast), toggles fullscreen. The fullscreen swipe-down (`playerVerticalSwipe`) already respects consumption, but is not covered by a test.
Fix:
- A shared state `PlayerGestureLock` (CompositionLocal or state hoisted in the sheet): `Scrubber` sets it from the first touch on the bar until release/cancel plus a short tail (150 ms) so the lift of the finger cannot become a fling. While locked: the sheet drag never starts or continues, the swipe-up-to-fullscreen never fires, the fullscreen swipe-down never fires, double-tap/hold-speed/brightness/volume gestures on the picture are ignored.
- The sheet handler also respects consumption: a gesture whose down or first moves are consumed by a child is dropped (`if (down.isConsumed) return`, and stop when `change.isConsumed` before dragging started).
- The sheet drag may not START on the control strip at the bottom of the picture: the scrubber's bounds plus 12 dp around it (report them with `onGloballyPositioned`); starting a swipe anywhere else on the picture works as before. Same band for the fullscreen swipe-down.
- The lock also covers the volume and speed sliders inside menus only through consumption (they are in sheets); no extra work there.
Tests (fail first): `PlayerSheet` with a touch scrub that moves 300 px horizontally and 120 dp vertically: sheet progress unchanged, fullscreen not toggled, position committed once; the same vertical movement started on the picture (not the bar) still minimises / goes fullscreen; fullscreen: scrub with downward drift does not leave fullscreen, swipe down on the picture does; the 150 ms tail (virtual clock). Include a regression test for the double-tap seek and hold-for-speed gestures still working outside the bar.

## Out of scope

Fine-scrubbing by sliding up, new progress styles, changing SponsorBlock categories or colours, the miniplayer's own progress line, desktop (mouse) seek behaviour, a user setting for tap-to-seek.

## Hand back

In section 5 of the report: which of items 1-5 you could only prove by desktop Compose tests and not on a phone; whether the stacked channel layout ever triggered at default font scale on 360 dp for the owner's real subscribed channels' longest names (use the DB's subscription names to find the longest and test with them); any style where the clipped-wave markers looked wrong. Section 6 (owner, on the phone): tap the bar at random places (nothing happens), drag it (seeks, no minimise even if the finger drifts up/down), swipe the picture down (minimises) and up (fullscreen); watch a video with sponsor segments in the played part with the Expressive and Wave styles and see the coloured parts ripple with the bar; open a video with a long channel name; rotate to landscape and check all round buttons are the same size.
