# Security policy

## Reporting a vulnerability

Please report security problems **privately**, not in a public issue: use GitHub's "Report a vulnerability" (Security tab > Advisories) on this repository.
Include what you found, how to reproduce it, and which version. You will get an answer as soon as possible; this is a volunteer project, so there is no guaranteed time.

## What counts

Reports about any of these are welcome and treated as security issues:

- **Credential leakage**: cookies, tokens, `Authorization` values, e-mail addresses or account names reaching logs, the Diagnostics/Health report, crash output, backups, the clipboard or the network where they should not.
- **The secret store** (`SecretStore` implementations: libsecret, the encrypted file, Windows DPAPI): anything that lets another user or app read stored credentials, or that stores them unprotected.
- **The updater**: bypassing the signature or checksum checks of the managed yt-dlp update (the SHA2-256SUMS verification and the rollback), or making the app run a binary it did not verify.
- **Signed remote configuration**: accepting an unsigned or wrongly signed file, or a file that changes anything beyond what `docs/remote-config.md` allows.
- **The release pipeline**: a way to get a modified build published under the project's name.

## What does not count

- YouTube or Google changing or blocking their services (open a normal issue).
- Problems that need an already-compromised device or a rooted phone.
- Use that violates YouTube's terms of service: Vela is used at your own risk (see the README).

## For testers

Use a **throwaway Google account**. Never paste cookies or tokens anywhere; if a log contains any, delete them before sharing it.
