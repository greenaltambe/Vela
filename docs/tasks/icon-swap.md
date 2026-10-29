# Small task: swap in the new Vela icon (sail = play button)

Source art (final, designed in chat): `docs/icon/vela-icon.svg` (full rounded icon), `docs/icon/vela-adaptive-foreground.svg`
(transparent foreground, already inside the adaptive safe zone), `docs/icon/vela-icon-1024.png`. Background colours: gradient #161E4A -> #060914
with a soft indigo glow top-left (see the SVG). Do not commit. Report `docs/reports/icon-swap.md` (5 lines is enough).

1. Replace the Android launcher: adaptive background (navy gradient vector), foreground vector converted from the SVG (keep the gradient
   #2EC4B6 -> #5B5BD6 -> #D946A8; the blurred glow under the sail may be dropped or approximated with a low-alpha copy),
   a monochrome vector (sail triangle + mast + wake + star, single colour) for themed icons, legacy mipmap PNGs generated from the SVG
   at all densities (round and square), and the Play-style 512 PNG at `docs/vela-icon-512.png`.
2. Linux: window/taskbar icon from the same art at 256/128/64/48 PNG, set on the Compose window (reports 30 said the desktop window icon was
   not set), plus the `.desktop` entry/icon file if one exists in the repo.
3. In-app mark (`VelaMark`) redrawn from the same art so the top bar mark matches: sail triangle in the palette gradient + mast + star; it keeps following
   the chosen colour palette (the launcher icon stays static). Keep it legible at 24 dp.
4. Remove `tools/make-icon.py` usage or update it so one generator produces everything (or document the SVG as the source of truth).
5. Verify on the phone: launcher icon, themed icon, recents; on Linux: window icon in the GNOME dock/alt-tab. Screenshots in `docs/reports/icon-swap-screens/`.
