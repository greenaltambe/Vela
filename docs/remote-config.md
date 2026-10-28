# Signed remote configuration

Lets you change client profiles, switch a broken profile off, and change the health-canary videos **without shipping a new build**.
It can change nothing else.

## One-time setup
1. `scripts/sign-remote-config --gen-key ~/vela-config-key.pem` (needs openssl 3). Keep the key private and out of the repo.
2. Put the printed public key line into `innertube-auth/src/main/resources/remote-config-pubkey.txt` and rebuild. While that file has no key line, the URL setting has no effect.
3. Host the signed file over **https** (GitHub Pages, a gist raw URL, any static host) and put its URL in Settings > Advanced > "Remote configuration URL". Empty = bundled data only.

## Publishing a change
1. Write `payload.json`:
   ```json
   {"version": 2, "issuedAt": "2026-10-28T00:00:00Z", "expiresAt": "2026-12-28T00:00:00Z",
    "disabledProfiles": ["IOS"], "canaryVideoIds": ["dQw4w9WgXcQ"],
    "profiles": [ { "name": "ANDROID", "clientNameId": 3, "enabled": true, "userAgent": "...", "client": { "clientName": "ANDROID", "clientVersion": "20.10.38" } } ]}
   ```
   `profiles` is optional (same shape as `innertube-profiles.json`; present = replaces the bundled list). `disabledProfiles` applies to bundled and remote profiles.
2. `scripts/sign-remote-config ~/vela-config-key.pem payload.json signed.json`, then upload `signed.json`.

## What the app enforces
HTTPS only; at most 64 KiB; Ed25519 signature over the payload under the bundled key; `version` strictly greater than the last accepted one (no rollback or replay, so raise it every time); not expired; not issued more than a day in the future. Anything else is refused and the last good configuration (or the bundled data) stays in force. An accepted config that expires is ignored. The app asks once per start. Diagnostics shows the outcome ("remote config" line).

Android 13+ verifies Ed25519 natively; on older Android the feature is inert (bundled data).
