#!/usr/bin/env python3
"""Builds every Vela icon file from one mark: sails on a mast over a wave (white), on the brand red.

  docs/icon/vela-icon.svg, vela-icon-round.svg, vela-adaptive-foreground.svg   the sources, written here
  docs/icon/vela-icon-1024.png, vela-icon.ico, docs/vela-icon-512.png         rendered from them
  androidApp/src/main/res: adaptive background / foreground / monochrome vectors, the notification mark, the legacy mipmap PNGs
  desktopApp/src/main/resources/icons/vela-{256,128,64,48}.png
The red is the Material primary of the app's light scheme (ui/theme/RedColorSchemes.kt). ui/look/VelaMark.kt draws the same sail.
Run: python3 tools/make-icon.py   (needs ImageMagick's `magick` with SVG support)
"""
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "androidApp/src/main/res"
RED = "#BB1614"
# The mark in a 512-unit square: a sailing boat's sails (a curved main sail that also reads as a play button, and a smaller jib) on a mast, over one wave.
# Elements: ("fill", path), ("stroke", path, width). The box is x 130..396, y 84..392.
SAIL = "M236,98C322,150 388,238 396,334C396,340 392,342 386,342L246,342C240,342 236,338 236,332Z"
JIB = "M208,176C182,226 168,282 164,334C164,340 168,342 174,342L208,342Z"
MAST = "M222,86L222,352"
WAVE = "M134,378C170,398 206,360 258,378C310,396 346,360 392,382"
ELEMENTS = [("fill", SAIL), ("fill", JIB), ("stroke", MAST, 10), ("stroke", WAVE, 16)]
CX, CY = 263.0, 238.0    # the middle of the box

def svg_mark(scale, color="#FFFFFF"):
    inner = ""
    for e in ELEMENTS:
        if e[0] == "fill": inner += f'<path d="{e[1]}" fill="{color}"/>'
        else: inner += f'<path d="{e[1]}" fill="none" stroke="{color}" stroke-width="{e[2]}" stroke-linecap="round"/>'
    return f'<g transform="translate(256 256) scale({scale}) translate({-CX} {-CY})">{inner}</g>'

def write_svgs():
    d = ROOT / "docs/icon"; d.mkdir(exist_ok=True)
    (d / "vela-icon.svg").write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><rect width="512" height="512" rx="128" fill="{RED}"/>{svg_mark(0.8)}</svg>\n')
    (d / "vela-icon-round.svg").write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><circle cx="256" cy="256" r="256" fill="{RED}"/>{svg_mark(0.72)}</svg>\n')
    (d / "vela-adaptive-foreground.svg").write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">{svg_mark(0.72)}</svg>\n')

def render(svg, px, out):
    out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["magick", "-background", "none", "-density", "600", str(svg), "-resize", f"{px}x{px}", str(out)], check=True)

# ---- Android vectors (108 x 108 viewport; the adaptive safe zone is the middle 66)
import re

def convert(d, k, size):
    c = size / 2
    out = []
    for cmd, args in re.findall(r"([MLCZ])([^MLCZ]*)", d):
        nums = [float(n) for n in re.findall(r"-?\d+\.?\d*", args)]
        pts = [(round(c + (nums[i] - CX) * k, 2), round(c + (nums[i + 1] - CY) * k, 2)) for i in range(0, len(nums), 2)]
        out.append(cmd + " ".join(f"{x},{y}" for x, y in pts))
    return "".join(out)

def vector(size_dp, viewport, k, color, strokes=True):
    head = ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size_dp}dp" android:height="{size_dp}dp" android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n')
    body = ""
    for e in ELEMENTS:
        path = convert(e[1], k, viewport)
        if e[0] == "fill": body += f'  <path android:pathData="{path}" android:fillColor="{color}"/>\n'
        elif strokes: body += f'  <path android:pathData="{path}" android:strokeColor="{color}" android:strokeWidth="{round(e[2] * k, 2)}" android:strokeLineCap="round"/>\n'
    return head + body + "</vector>\n"

def background():
    return ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            f'  <path android:pathData="M0,0 H108 V108 H0 Z" android:fillColor="#FF{RED[1:]}"/>\n</vector>\n')

if __name__ == "__main__":
    write_svgs()
    (RES / "drawable").mkdir(parents=True, exist_ok=True); (RES / "drawable-v24").mkdir(exist_ok=True)
    (RES / "drawable/ic_launcher_background.xml").write_text(background())
    (RES / "drawable-v24/ic_launcher_foreground.xml").write_text(vector(108, 108, 0.17, "#FFFFFFFF"))
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(vector(108, 108, 0.17, "#FFFFFFFF"))
    (RES / "drawable/ic_stat_vela.xml").write_text(vector(24, 24, 0.07, "#FFFFFFFF"))
    d = ROOT / "docs/icon"
    render(d / "vela-icon.svg", 1024, d / "vela-icon-1024.png")
    render(d / "vela-icon.svg", 512, ROOT / "docs/vela-icon-512.png")
    for dens, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        render(d / "vela-icon.svg", px, RES / f"mipmap-{dens}/ic_launcher.png")
        render(d / "vela-icon-round.svg", px, RES / f"mipmap-{dens}/ic_launcher_round.png")
    desk = ROOT / "desktopApp/src/main/resources/icons"
    for px in (256, 128, 64, 48): render(d / "vela-icon.svg", px, desk / f"vela-{px}.png")
    subprocess.run(["magick", str(d / "vela-icon-1024.png"), "-define", "icon:auto-resize=256,128,64,48,32,16", str(d / "vela-icon.ico")], check=True)
    print("wrote the icon sources, launcher vectors, notification mark, mipmaps, desktop icons and the ico")
