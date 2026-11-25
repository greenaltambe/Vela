# 158 Stage E: reading YouTube's Shorts feed

## 1. Summary
Vela can now read YouTube's recommended Shorts, from the account (`AccountReads.shorts`) and anonymously (`Extractor.shortsFeed`), with trimmed requests in your region. **The Shorts tab does not use it yet** (stage F). The one visible change is a new "shortsFeed" check on the health screen.

## 2. Verification
| check | result | verified-by-running vs compile-only |
|---|---|---|
| `./gradlew allTests test :androidApp:compileDebugKotlin` | pass | run (the Android app compile is compile-only) |
| First full run | failed: `CaptchaAwareExtractor` did not forward the new call (caught by the forwarding test); fixed | run |
| `ShortsFeedTest`, 13: start, pages, ads and repeats dropped, seeds, full answers, mask retry, expired and foreign tokens, error mapping, region, no credentials when anonymous, the account path | pass | run |
| New chain and health tests (one each) | pass | run |
| Live, anonymous, `gl=IN`: start and 3 pages | 8 to 9 Shorts a page, all with pictures, 22 distinct and 4 repeats | run (live) |
| Live, signed in: start and 3 pages | 14 to 17 a page, 49 distinct, no repeats | run (live, read-only) |
| Live: answer sizes with the field mask, never needing the full retry | start 396 characters (about 294,000 unmasked, task 152); pages 2,900 to 6,200 | run (live) |
| Live: from a Home Short's seed | 15 Shorts, not including itself, with a next token | run (live) |
| On a phone | NOT RUN | needs the owner |

## 3. Files created/changed
- `innertube-auth`: new `ShortsFeed.kt`, `ShortsFeedTest.kt`, `liveTest/.../LiveShortsFeedTest.kt`; `ChannelBrowse.kt` (anonymous requests), `AltClient.kt`, `InnertubeClient.kt` (field mask), `InnertubeAccountReads.kt`.
- `shared`: `Extractor.kt`, `Models.kt` (`shortsFeed` capability), `Accounts.kt`, the timeout, pacing, chain, fault and captcha wrappers, `ExtractorHealth.kt`; tests for the wrappers, chain and health.
- Plan status, this report.

## 4. Decisions the spec didn't dictate
- Tokens are stamped by source; the account never sends an anonymous one and the reverse.
- A masked request that is refused, or answers with nothing, is repeated once without the mask.
- A new sequence that names no Short counts as a failure; an empty later page without a token counts as the end.
- The anonymous feed paces itself (one request at a time, 500 ms apart), since its source is not behind the anonymous pacer.
- Two small changes to the fallback chain for all requests: a "not supported" answer no longer resets what it remembers about that source, and no longer replaces the real error of an earlier source.

## 5. Known issues / unverified assumptions
- Only the alt client serves the anonymous feed. If that source is switched off, stage F falls back to the channel mix.
- The anonymous feed repeats some Shorts across pages (4 of 26 here); stage F de-duplicates.
- Token lifetime is still unmeasured.
- The endpoints and field masks are undocumented.

## 6. Manual test checklist
1. Settings > health screen: run the checks. The alt client shows "shortsFeed" as passing.
2. Open the Shorts tab, a video and a channel: unchanged.
