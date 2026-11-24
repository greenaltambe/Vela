# 137 Feed overhaul, task 5: Home settings

## 1. Summary
The "Home shows (Recommended / Trending)" setting is gone: signed in, Home is always your account's feed; signed out, it is Trending. A new
switch, "YouTube's chips on Home" (on by default), turns YouTube's chips off, leaving Home as just the feed with the Filter button. Old
backups and databases that carry `home.source` restore quietly.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `HomeSourceUiTest` (12): signed out never asks the account; chips off shows the feed and Filter button but no chips | pass | running (desktop Compose UI tests) |
| `LookSettingsTest`: `home.source` is a known legacy key (old backups restore without a warning) | pass | running (offline) |
| `./gradlew allTests` (settings schema, summaries, copy, backup tests included) | pass | running (offline) |
| `:androidApp:compileDebugKotlin` | pass | compile-only |
| Settings page on a phone | not checked | needs the owner |

## 3. Files created/changed
- `shared/.../core/settings/Settings.kt`: `homeSource` and `HomeSource` removed; `homeYoutubeChips` (`home.youtubeChips`, boolean, default true) added.
- `SettingCopy.kt`, `SettingsPages.kt` (Home page owns the new key; its summary reads "YouTube chips on/off · region"), `LookSettings.kt` (`home.source` listed as legacy).
- `shared/.../ui/screens/HomeScreen.kt`: no source check; chips drawn only when the setting is on; turning it off while a chip is open returns to All.
- `shared/src/jvmTest/.../LookSettingsTest.kt`, `desktopApp/src/test/.../HomeSourceUiTest.kt`.

## 4. Decisions the spec didn't dictate
- The switch is global (not per account). The "hide a chip" setting was not added (owner decision).
- The "Trending now (your Home source setting)" note no longer exists; the signed-out note stays.
- The existing "Chips on Home" switch (hides the whole row) is separate from the new one (only YouTube's chips).

## 5. Known issues / unverified assumptions
- A stored `home.source` value stays in the database unused (like other legacy keys).
- Anyone who had Home set to Trending while signed in now sees their account feed; Trending is in Explore.

## 6. Manual test checklist
1. Settings > Home: "YouTube's chips on Home" is on; the page's summary line says "YouTube chips on".
2. Switch it off, go to Home: only the Filter button, no chips, your feed as before. Switch on: chips return.
3. With a chip open (for example a topic), switch the setting off and return to Home: the plain feed shows.
4. There is no "Home shows" option any more.
