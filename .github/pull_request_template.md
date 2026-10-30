## What and why

## Checks
- [ ] `./gradlew allTests` passes (offline tests only; live tests are not part of CI).
- [ ] No cookies, tokens, personal data or screenshots of a real account in the change.
- [ ] Architecture rules of `docs/SPEC.md` section 5 respected.
- [ ] If dependencies changed: `./gradlew generateThirdPartyNotices` run and the result included.
- [ ] If behaviour changed: `CHANGELOG.md` updated.
