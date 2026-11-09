# 98: Caption style and size, and creator-styled captions (Tsoding's chat)

## 1. Summary
- **Your controls:** a "Caption style" sheet (long-press the CC button, or the player's Settings sheet) with a live preview, **Size** (Small, Normal, Large, Huge), **Background** (Outline, Shade, Solid) and a switch **Use the creator's style**. The same three are saved as settings (Settings > Playback & quality) and apply to every video, in the page player and in fullscreen.
- **Creator styles:** captions used to be flattened to white text at the bottom. Vela now reads the file's colours, bold/italic/underline and cue positions. Tsoding's captions turned out to be the Twitch chat: each cue holds several messages, usernames carry Twitch colours (a `STYLE` block of `::cue(c.color8A2BE2)` rules) and the cue says `line:0% position:25% size:75% align:start`, a box at the top right. These now draw as authored.
- Authored colours that would be unreadable on a dark box (a dark brown or pure blue username) are lightened to at least 4.5:1 contrast.

## 2. Verification
| Check | Result | Verified-by |
|---|---|---|
| Measured a real Tsoding video (`0toGj487cpA`, English track): WebVTT with a STYLE block of ~20 colour classes, cue settings, several messages per cue; the `srv3` format has `<pen>` styles too | as described above | running (live fetch, probe removed) |
| Parser: placement, colours from the STYLE block and from `colorRRGGBB` / named classes, bold/italic/underline nesting, messages split over lines, word timestamps dropped, rolling auto-captions still dedupe, active cues (one plain, one per placed box), CSS colours, contrast fix | `VttTest` (10), fixture shaped like the measured file | running |
| `allTests` and build | pass | running |
| **A crash I caused and fixed:** the parser's regex had an unescaped `}`; the JVM accepts it, **Android's regex engine does not** (`PatternSyntaxException`), so the first phone build crashed the moment captions loaded | seen in the crash log, fixed, re-tested on the phone | running (phone) |
| Phone, Tsoding video, English track: chat drawn in a box top right with each username in its Twitch colour, readable on the dark picture | seen | running (phone) |
| Phone, style sheet: preview, Size/Background chips, creator switch; Outline + Large changed the preview | seen | running (phone) |
| "Use the creator's style" off (plain bottom captions for a chat-style file) | switch toggled, result on the picture not looked at | not verified |
| Fullscreen captions, light theme, desktop, `srv3` rich formats | not checked | not verified |

## 3. Files changed
New: `library/CaptionColors.kt`, `core/settings/CaptionStyle.kt`, `ui/player/CaptionOverlay.kt`, tests `library/VttTest.kt`. Changed: `library/Vtt.kt` (rewritten parser; `Cue` gained `lines` and `placement`, `Vtt.at` kept), `core/settings/Settings.kt` + `SettingCopy.kt` (3 settings), `ui/player/PlayerView.kt` (overlay, style sheet, menu rows), `ui/screens/VideoScreen.kt` (reads and saves the style, both player calls).

## 4. Decisions
- Defaults keep today's look: Normal, Shade, creator's style on.
- Placed (chat-like) overlays are drawn at 85% of the chosen size; with several lines they would otherwise cover the picture. Fullscreen text is 15% larger.
- Only the latest cue in each placed box is shown (chat cues repeat earlier messages), plus one ordinary bottom cue.
- Outline is a dark glow behind the letters (one shadow), not a stroked edge.
- Colour is the only creator look limited by the viewer: colours are lightened for contrast, never changed to another hue.
- Not done: font family, text colour override, edge/opacity sliders, `srv3`/`json3` formats, per-channel caption style.

## 5. Known issues
- Chat captions from older streams may not exist (Tsoding's own words in the chat say YouTube stopped showing chat in captions at some point).
- A very long chat box can cover much of a small player; choose Small, Outline, or turn the creator's style off.
- I changed your caption settings during testing and restored them to the defaults.

## 6. Manual test checklist
1. Open `https://www.youtube.com/watch?v=0toGj487cpA`, long-press CC, pick "English" (not auto-generated): chat box top right with coloured names.
2. Caption style: try Small and Huge, Outline and Solid; the preview and the live captions follow.
3. Turn "Use the creator's style" off: the chat becomes plain white text at the bottom.
4. Check fullscreen and a normal video (white captions at the bottom, as before).

## 7. Follow-up: does the creator control caption position?
Yes, partly, and the creator's control is limited to what the WebVTT file says. I measured 43 caption files from a spread of videos (talks, news, lyrics, anime, vlogs, other languages, a chat stream) on 2026-11:
- Settings that appear: `line:0%`, `position:`, `size:`, `align:start`. Never seen: `vertical:`, `Region:` blocks, a numeric `line:` (a line number rather than a percentage).
- 28 of 43 files had settings, but almost all were YouTube's automatic captions, which carry `align:start position:0%` on every cue. That is not a creator placement, and my first version treated it as one, so automatic captions would have been pushed to the left edge. **Found by this check and fixed:** a cue only counts as placed when it has a `line` or a box narrower than the picture (`size` under 99%). Tested (`VttTest`, 12) and confirmed on the phone: an automatic caption track is centred at the bottom again.
- What Vela honours now: `line` (top edge), `position`, `size`, `align`, colours, bold/italic/underline.
- Not honoured: `vertical:` (vertical writing, not seen), `Region:` blocks (not seen), numeric `line:` (not seen), `srv3` pen styles (font size, background, edge, which YouTube's own caption editor can set; the file we read is the WebVTT conversion, which keeps colours and position but not those).
- The viewer's "Use the creator's style" switch turns all creator placement and colour off.
