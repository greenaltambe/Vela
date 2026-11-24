# Later: clickable social handles in video descriptions

Status: deferred by the owner. Not started; the source format is not yet confirmed.

## Symptom
A description like this (sample from a Defunctland video):

```
Support us on Patreon and Unlock Bonus Content:
Patreon: defunctland

Facebook Group:
Facebook: Defunctland-1142494142561537

Defunctland Twitter:
Twitter: defunctland

Defunctland Subreddit:
Reddit: defunctland
```

On YouTube each `Name: handle` is a clickable link to the real profile (patreon.com/defunctland, reddit.com/r/defunctland, ...). In
Vela it shows as plain text, so the handles are not tappable.

## Open question (find out first)
What the extractor actually returns for such a description. YouTube builds these from structured link runs (visible text plus a hidden
URL, often a `youtube.com/redirect?...&q=<real url>` redirect), so the plain-text description may have lost the URLs. Check the raw
`attributedDescription` / link runs in the NewPipe and yt-dlp results and in `plainDescription` (shared `extractor`).

## Ideas
1. If the extractor can keep the link runs: render them as clickable spans in the description (unwrap `redirect?q=` to the real URL).
2. If it only gives text: recognise `Patreon|Facebook|Twitter|X|Instagram|Reddit|TikTok|Discord|...: handle` lines and map them to the
   site's profile URL. Fragile (Facebook's value above is a page id, Reddit needs `/r/` vs `/u/`), so prefer option 1.
3. Open the link through the app's existing external-link handling, with the same confirmation as other links.

Desktop and Android both render descriptions, so check both. Rendering is a manual test for the report.
