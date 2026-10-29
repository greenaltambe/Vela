# Icon swap

1. Android: adaptive background, foreground and monochrome vectors are generated from `docs/icon/vela-adaptive-foreground.svg` (paths converted, gradient kept, blurred glow dropped, sheen kept); legacy mipmaps (square + round, 5 densities) and `docs/vela-icon-512.png` come from `docs/icon/vela-icon-1024.png`.
2. Linux: `desktopApp/src/main/resources/icons/vela-{256,128,64,48}.png`, set on the Compose window (`main.kt`). No `.desktop` file exists in the repo, so none was changed.
3. `VelaMark` redrawn as sail + mast + star in the palette gradient (no wake or dot, for legibility); `tools/make-icon.py` now builds everything from the SVGs/1024 PNG (they are the source of truth).
4. Verified: `allTests` pass; debug build installed; phone screenshots in `docs/reports/icon-swap-screens/` show the new icon in recents and the new mark in the top bar. The desktop app starts with the icon without errors.
5. Not verified: themed (monochrome) icon on the phone, the icon on the phone's home screen grid, and the GNOME dock / alt-tab icon (Wayland, no screenshot possible here; a dock entry may also need a `.desktop` file with `StartupWMClass`).
