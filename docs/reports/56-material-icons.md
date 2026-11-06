# 56 Material icons (phase 2 of 5)

## 1. Summary
Every icon is now a default Material icon behind one seam, `AppIcons` (`ui/theme/AppIcons.kt`). Outlined is the normal form; Filled is used for the selected / on state of tabs and of like, dislike, save, download, notifications, watch later, comment, explore and the Shorts bolt. Thin icons (search, close, back, share, settings, more, ...) have a single form. The seam: `AppIconSet` + `LocalIconSet` (provided by `AppTheme` from `Appearance.icons`), so a later look can bring its own set with no call-site change.

## 2. Verification
| Check | Result | Verified by |
|---|---|---|
| `allTests` and `:desktopApp:test` (1,087 test cases, 0 failures) | pass | running |
| New `AppIconsTest` (every slot covered, selectable ones have a different filled form, thin ones have none and fall back) | pass | running |
| `ThemeSourceScanTest` | pass | running |
| `:androidApp:assembleDebug`, `:desktopApp:smokeTest` | pass | compile / running |
| Icons on the phone | not looked at (phone away this round) | compile-only |
| Desktop rendering of the bar, rail and mini player icons | seen in test screenshots | running |

## 3. Files created/changed
- New `ui/theme/AppIcons.kt` (`AppIconSet`, `MaterialIconSet`, `LocalIconSet`, `IconNames`, `AppIcons`); `MaterialOutlined.kt` and `MaterialFilled.kt` moved from `ui/look/` into `ui/theme/` (new slot `menu`).
- Deleted `ui/look/VelaIcons.kt`, `ui/look/LocalLook.kt` (the last of the old `Vela` object).
- About 38 files: `Vela.icons.x` became `AppIcons.x` (codemod).
- `Appearance.kt`, `AppTheme.kt`: `icons` field and `LocalIconSet`.

## 4. Decisions the spec didn't dictate
- `material-icons-extended:1.7.3` stays the source (already a dependency). It is frozen upstream; it is only referenced in the two Material map files, so swapping it for generated vectors later touches nothing else.
- The filled set is restricted to the slots that can be selected (`IconNames.withFilledForm`); Search is single-form, as asked.
- `AppIcons` accessors are composable getters reading `LocalIconSet`, like the old `Vela.icons`, so a theme can swap the set.

## 5. Known issues / unverified assumptions
- Not seen on the phone. `desktopApp:liveTestClasses` fails to compile in `AgeRestrictedWiringLiveTest` (a yt-dlp constructor mismatch, in code this task did not touch); the live tests are not part of `check`.
- Some slots map to the closest Material icon rather than an exact match (for example save is Bookmark, quality is HighQuality).

## 6. Manual test checklist
1. Bottom bar: tabs outlined, the selected tab's icon filled.
2. Video page: like / dislike / save fill in when on; share, close, back stay single-weight.
3. Search icon in the top bar is the same whether or not you are on Search.
