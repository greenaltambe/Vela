# 35 – Video page polish

## 1. Summary
Menus (quality, speed, sleep timer, and every other dropdown, dialog and chip) now take the corner-radius setting and the Look's surfaces. Filled buttons (Subscribe and the rest) use the palette gradient darkened just enough for white text, so the label is readable. The five actions under the player no longer clip: they keep their natural width and scroll sideways if the window is narrow. Tapping the picture hides the controls while paused too (pausing shows them). Share opens the Android share sheet (the link is copied where there is none, e.g. Linux). The description sits on a card. Comments are one wide button that swaps the related videos for the comments (Back or "Related videos" returns). Details, related videos and the first comments are remembered for the last 8 videos (30 min), so reopening a video no longer shows loading skeletons.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| Phone: five actions all visible, description card, Comments button, Subscribe label white on the darkened gradient | seen | phone screenshots |
| Contrast: white over every stop of the button fill in every palette >= 4.5:1 | pass | running |
| Quality/speed/sleep menu shape, share sheet, controls hiding while paused, comments opening, no skeletons on reopening | not exercised | compile-only |

## 3. Files changed
`ui/VelaTheme.kt` (Material shapes and surface containers), `ui/look/Contrast.kt`, `ui/look/LocalLook.kt` (`buttonFill`, `onButton`), `ui/components/Components.kt` (PrimaryButton), `ui/screens/VideoScreen.kt`, `ui/screens/VideoPageCache.kt` (new), `ui/player/PlayerView.kt`, `ui/platform/PlatformUi.kt` (`LocalShare`), `androidApp/.../MainActivity.kt` (share chooser), `ui/AppShell.kt`, `ContrastTest`

## 4. Decisions
- All Material menus/dialogs follow the radius by theming Material's shape slots rather than restyling each menu.
- The comments button shows how many comments are loaded; there is no comment icon (every icon pack would need one).
- The cache is in memory only (gone when the app is closed).

## 5. Known issues
- The five-action row scrolls on narrow windows instead of shrinking labels.
- Related videos still load fresh the first time a video is opened or after 30 minutes.

## 6. Manual test checklist
1. Open a video; tap the quality / speed / sleep buttons: menus have rounded corners matching the setting and the app's surface colour.
2. Tap Share: the Android share sheet opens with the title and link.
3. Pause, then tap an empty part of the picture: the controls hide; tap again: they show.
4. Tap Comments: related videos are replaced by comments; Back (or "Related videos") brings related back.
5. Open a video, minimise it, open it again: details and related videos are there at once.
