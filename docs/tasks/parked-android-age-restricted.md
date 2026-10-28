# CLOSED by task 22 (2026-10-28): age-restricted videos now play on Android through yt-dlp embedded in the APK

Evidence: `docs/reports/22-android-age-restricted.md`. Spike B (embedded yt-dlp: Python 3.12 + QuickJS, youtubedl-android library) worked on the OPPO CPH2707 / Android 16: HtVdAasjOgU plays anonymously (no account) through the normal chain, and with the throwaway account as the last tier. Spike A (WebView PO token) was NOT needed and was not built. The text below is the history of what was established before.

# (was) PARKED: age-restricted videos on Android (decision 2026-10-28: close Stage C of task 17, keep the friendly failure)

Status: Android shows an explanation + "Open in browser" for age-restricted videos. Linux plays them through yt-dlp (needs a JS runtime, newest yt-dlp). Nothing for this is half-built or registered.

## What was established
- Anonymous clients (IOS, ANDROID, ANDROID_VR, VISIONOS, TVHTML5, MWEB, others) all return LOGIN_REQUIRED/UNPLAYABLE for the age-restricted test video HtVdAasjOgU. Matrix in docs/reports/15-reliability-2.md.
- yt-dlp's own route that works (with or without the account) is the embedded web client with a JS runtime. This project does NOT replicate embed-site spoofing; running unmodified yt-dlp on Linux is the accepted exception.
- Own /player on WEB/MWEB/TVHTML5 with cookies returned UNPLAYABLE even for a normal video in task 13 (no token, incomplete web context).
- NewPipeExtractor's PoTokenProvider hook is only used for its Android/iOS clients; those tokens come from DroidGuard/iosGuard and cannot be minted outside the real apps. A WebView BotGuard token only helps web-family clients.
- Linux attempt to mint a BotGuard token with bgutils-js 4.0.3 + jsdom 30.1.3: challenge fetch and integrity-token request worked (HTTP 200, ttl 43 200 s) but the minter failed: APF:Failed (interpreter in the jsdom window) and PMD:Undefined (Node globals with jsdom patched in). BotGuard rejects the jsdom environment. Says nothing about a real Chrome WebView.
- Grayjay's approach (source read 2026-10-28): own Innertube JS client, BotGuard tokens generated on the device with a browser-based and an engine-based generator, authenticated client for age/bot checks, captcha WebView storing GOOGLE_ABUSE_EXEMPTION.

## If revived (estimates, not measured)
- Spike A: bundle bgutils-js (MIT) as an app asset, run it in a hidden WebView on a youtube.com origin, signed WEB/MWEB /player call through the governor with cookies + visitorData + token, decipher stream URLs (n/signature) with NewPipe's player-JS code if its API allows. Success = real 206 GET on the device for HtVdAasjOgU and for 5 normal videos. May still end UNPLAYABLE.
- Spike B: embedded yt-dlp via an embedded Python runtime plus a JS engine executable in the native lib dir; same updater/rollback design as Linux; would also give Android an anonymous yt-dlp tier (parity). Check licence/AGP compatibility first.
- Measure per spike: success, latency cold/warm, APK size, memory, update path, maintenance, governor fit. Stop rule: if nothing plays on the device, write what was tried and leave the friendly failure.
- Use the on-device self-test + adb loop from task 17 for all evidence.

## Addendum: how Grayjay's YouTube plugin (config v367) handles age-restricted videos (source read 2026-10-28)
- Detection: playabilityStatus LOGIN_REQUIRED or CONTENT_CHECK_REQUIRED, and the reason text containing "your age".
- Opt-in: setting "Allow Age Restricted" (default OFF). When off it stops with a clear message ("Age restricted videos can be allowed using the plugin settings"). Same pattern for "controversial" content.
- Signed-in route ("Allow Loginfallback", default on, used only when login is required): (1) POST youtubei/v1/verify_age with a nextEndpoint.watchEndpoint {videoId, playerParams, racyCheckOk:true, contentCheckOk:true} and setControvercy:true, authenticated; (2) then an authenticated web /player request with signatureTimestamp (sts from the player JS), contentCheckOk, and a BotGuard PO token. If it still says LOGIN_REQUIRED it gives up with the reason.
- Anonymous route (only when the setting is on and streamingData is missing): a TVHTML5_SIMPLY_EMBEDDED_PLAYER /player request (clientScreen EMBED, thirdParty.embedUrl = the video's own youtube.com watch URL, referer = that URL, signatureTimestamp, its own user agent). If the reply says "sign in" it gives up. NOTE: this project's rule is no embed-site spoofing; Grayjay's variant uses the video's own watch URL, not a third-party site, but it is still a way to get age-gated streams without a signed-in session, so it needs an explicit owner decision. Task 15's embedded probe used embedUrl google.com and got "no longer supported", so it says nothing about the Grayjay-shaped request.
- PO tokens: BotGuard generated on the device, first with a browser-based generator (host Browser package / WebView), falling back to an engine generator using JSDOM wired to a custom environment. Our jsdom attempt (APF:Failed / PMD:Undefined) was probably an incomplete environment, not proof that jsdom cannot work (inference).
- Implication for Spike A: the missing pieces in our task-13 attempt likely include verify_age, sts, contentCheckOk/racyCheckOk and a token. Still a sizeable build.
