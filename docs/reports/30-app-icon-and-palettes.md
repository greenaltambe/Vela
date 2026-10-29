# 30 – App icon, top bar, colour palettes

## 1. Summary
New launcher icon: the Vela constellation (stars joined by thin lines) with three stars forming a play triangle in the palette gradient, on deep navy. The same mark sits at the top-left of the top bar; the profile button moved to the top-right with a gradient ring. The five colour settings (dark/light accent, accent source, soften toggle, tab marker) and the profile-side option are replaced by one setting, "Colour palette": 8 gradients, one tap, applied app-wide.

## 2. Verification
| check | result | verified-by |
|---|---|---|
| `./gradlew allTests` | pass | running |
| Contrast: all text >= 4.5:1 for every palette x theme x pure black; button label >= 3:1 over every gradient stop | pass | running |
| Debug build installed; Home screenshot shows mark left, ringed profile right, glow | seen | phone |
| Launcher icon on the home screen, themed (monochrome) icon, Palette page on device | not checked | n/a |

## 3. Files created/changed
- Icon: `tools/make-icon.py` (generator), `androidApp/src/main/res/{drawable,drawable-v24,mipmap-*}` (background, foreground, monochrome vectors, legacy PNGs), `docs/vela-icon-512.png`
- Palettes: `core/settings/LookSettings.kt` (`look.palette`; removed accent, accentLight, accentSource, accentSoftened, tabMarker, avatarSide), `ui/look/Palettes.kt`, `LookBuilder.kt`, `LookTokens.kt`, `LocalLook.kt` (`Vela.wake`), `Contrast.kt` (`onAll`), `nav/NavConfig.kt`
- UI: `ui/look/VelaMark.kt`, `components/Components.kt` (top bar, gradient PrimaryButton, tab marker), `screens/ProfileMenu.kt` (ring), `AppShell.kt` (corner glow), `screens/CustomizeScreens.kt` (palette grid; hub reduced to Colour palette, Text, Icons & shapes, Glass, Tabs)
- Tests updated: LookBuilder, Contrast, NavConfig, NavigationUi, SettingsUi

## 4. Decisions the spec didn't dictate
- The gradient appears on: primary buttons, switches, selected-tab marker, selected-card borders, the profile ring, the mark, and a faint (10-16 %) glow in the top corners. The accent for links and selected text is the palette's middle stop, nudged for contrast.
- Palettes: Vela (default), Aurora, Ocean, Sunset, Ember, Orchid, Meadow, Slate.
- Removed the "Looks/Home/Feeds/Video" placeholder rows from the Customize hub (their routes remain).
- The launcher icon is static (default palette); it does not follow the setting.

## 5. Known issues / unverified assumptions
- Old saved accent/tab-marker/profile-side values are ignored (the palette starts at Vela).
- Sliders, progress bars and the player scrubber use the accent colour, not the full gradient.
- Desktop window icon not set.

## 6. Manual test checklist
1. Check the launcher icon on your home screen (and themed icons if enabled).
2. Open the app: mark top-left, ringed profile top-right; tap the profile, the menu opens.
3. Profile > Customize Vela > Colour palette: tap each palette; buttons, switches, tab marker and ring change; Undo works.
4. Try light and dark themes with a bright palette (Meadow, Ember) and check button labels read well.
