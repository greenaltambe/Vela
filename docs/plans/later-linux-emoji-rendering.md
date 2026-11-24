# Later: emoji drawn as outlines on desktop (Linux)

Status: deferred by the owner. Not started, cause not confirmed.

## Symptom
In comments and community posts on Linux desktop, some emoji render in color and some as monochrome outlines. Android is expected to be fine
(system color emoji font). Windows is unchecked.

## Likely cause
Compose Desktop (Skia) picks the fallback font per character. The dev machine has both `Noto Color Emoji` (COLRv1, `Noto-COLRv1.ttf`)
and the monochrome `Noto Emoji`. Skia's COLRv1 support is patchy, so some emoji land on the outline font. Newer emoji, ZWJ sequences
(skin tones, flags) and `U+FE0F` variation selectors are the usual suspects. Vela has no emoji-specific code in comment rendering.

## Steps to confirm
1. Note which emoji come out as outlines (a few examples) and check for a shared pattern (`U+FE0F`, ZWJ, newer Unicode).
2. Check whether titles and the search box show the same thing.
3. Temporarily remove `google-noto-emoji-fonts` (the monochrome package) and see if comments turn color.
4. Check on Windows.

## Fix options
1. **Small**: a font family list for comment text with `Noto Color Emoji` ahead of the monochrome font. Try first.
2. **Larger**: bundle a bitmap (CBDT/PNG) color emoji font, about 10 MB, as the fallback. Same result on every distro. Needs an entry in
   `THIRD_PARTY_NOTICES.md` (run `./gradlew generateThirdPartyNotices`) and adds to package size.
3. Map emoji to images (as some apps do): most work, only if the font routes fail.

Rendering can only be confirmed by running the app on Linux, so the report must list it as a manual test.
