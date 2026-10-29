#!/usr/bin/env python3
"""Builds every Vela icon file from the final art in docs/icon (the SVGs are the source of truth).

  docs/icon/vela-adaptive-foreground.svg -> androidApp adaptive foreground + monochrome vectors (paths are converted, not redrawn)
  docs/icon/vela-icon-1024.png (rendered from vela-icon.svg) -> legacy mipmap PNGs (square and round), docs/vela-icon-512.png,
                                                              desktopApp/src/main/resources/icons/vela-{256,128,64,48}.png
The adaptive background (navy gradient + indigo glow) is written from the colours in vela-icon.svg.
ui/look/VelaMark.kt draws the in-app mark from the same sail/mast/star numbers.
Run: python3 tools/make-icon.py   (needs Pillow)
"""
import re
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "androidApp/src/main/res"
SVG = (ROOT / "docs/icon/vela-adaptive-foreground.svg").read_text()
K = 108 / 512                                         # SVG units -> adaptive-icon viewport

def parse_transform(text):
    """translate/scale chain -> (sx, sy, tx, ty) mapping a point as x*sx+tx."""
    m = (1.0, 1.0, 0.0, 0.0)
    for name, args in re.findall(r"(translate|scale)\(([^)]*)\)", text):
        v = [float(a) for a in re.split(r"[ ,]+", args.strip())]
        sx, sy, tx, ty = m
        if name == "translate": m = (sx, sy, tx + sx * v[0], ty + sy * (v[1] if len(v) > 1 else 0))
        else: m = (sx * v[0], sy * (v[1] if len(v) > 1 else v[0]), tx, ty)
    return m

GROUP = parse_transform(re.search(r'<g transform="([^"]+)"', SVG).group(1))

def fix(x, y, extra=None):
    sx, sy, tx, ty = GROUP
    x, y = x * sx + tx, y * sy + ty
    if extra: x, y = x + extra[0] * sx, y + extra[1] * sy
    return round(x * K, 2), round(y * K, 2)

def convert(d):
    """Absolute M/L/C/Q/Z path data in SVG units -> the same in viewport units (after the group transform)."""
    out, pts = [], []
    for cmd, args in re.findall(r"([MLCQZ])([^MLCQZ]*)", d):
        nums = [float(n) for n in re.findall(r"-?\d+\.?\d*", args)]
        xy = [fix(nums[i], nums[i + 1]) for i in range(0, len(nums), 2)]
        pts += xy
        out.append(cmd + " ".join(f"{x},{y}" for x, y in xy))
    return "".join(out), pts

def gradient(attr, colours, pts, vertical=False):
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    s, e = ((x0, y0), (x0, y1)) if vertical else ((x0, y0), (x1, y1))
    items = "".join(f'<item android:offset="{o}" android:color="{c}"/>' for o, c in colours)
    return (f'<aapt:attr name="android:{attr}"><gradient android:type="linear" android:startX="{s[0]}" android:startY="{s[1]}" '
            f'android:endX="{e[0]}" android:endY="{e[1]}">{items}</gradient></aapt:attr>')

WAKE = [(0, "#FF2EC4B6"), (0.5, "#FF5B5BD6"), (1, "#FFD946A8")]
SHEEN = [(0, "#59FFFFFF"), (0.55, "#00FFFFFF")]
HEAD = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"\n    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'

def elements():
    """The art's own paths, in order: sail, mast, wake, star, dot (the blurred glow copy is dropped)."""
    paths = re.findall(r'<path d="([^"]+)"([^/]*)/>', SVG)
    sail = paths[1][0]
    mast, wake_wave, star = (p[0] for p in paths[3:6])
    cx, cy, r = (float(re.search(f'{a}="([^"]+)"', SVG.split("<circle")[1]).group(1)) for a in ("cx", "cy", "r"))
    return sail, mast, wake_wave, star, (cx, cy, r)

def circle_path(cx, cy, r):
    (x, y), (x2, _) = fix(cx - r, cy), fix(cx + r, cy)
    rr = round((x2 - x) / 2, 2)
    return f"M{x},{y} a{rr},{rr} 0 1,0 {2 * rr},0 a{rr},{rr} 0 1,0 {-2 * rr},0 Z"

def foreground(mono=False):
    sail, mast, wave, star, dot = elements()
    sd, spts = convert(sail); md, _ = convert(mast); wd, wpts = convert(wave); st, _ = convert(star)
    w = lambda units: round(units * GROUP[0] * K, 2)
    white = "#FFFFFFFF"
    out = [HEAD]
    if mono:
        out.append(f'  <path android:pathData="{sd}" android:fillColor="{white}"/>\n')
        out.append(f'  <path android:pathData="{md}" android:strokeColor="{white}" android:strokeWidth="{w(12)}" android:strokeLineCap="round"/>\n')
        out.append(f'  <path android:pathData="{wd}" android:strokeColor="{white}" android:strokeWidth="{w(14)}" android:strokeLineCap="round"/>\n')
        out.append(f'  <path android:pathData="{st}" android:fillColor="{white}"/>\n')
    else:
        out.append(f'  <path android:pathData="{sd}">{gradient("fillColor", WAKE, spts)}</path>\n')
        out.append(f'  <path android:pathData="{sd}">{gradient("fillColor", SHEEN, spts, vertical=True)}</path>\n')
        out.append(f'  <path android:pathData="{md}" android:strokeColor="#FFF4F6FF" android:strokeWidth="{w(12)}" android:strokeLineCap="round"/>\n')
        out.append(f'  <path android:pathData="{wd}" android:strokeWidth="{w(14)}" android:strokeLineCap="round">{gradient("strokeColor", WAKE, wpts)}</path>\n')
        out.append(f'  <path android:pathData="{st}" android:fillColor="#FFF4F6FF"/>\n')
    out.append(f'  <path android:pathData="{circle_path(*dot)}" android:fillColor="{white if mono else "#CCF4F6FF"}"/>\n')
    out.append("</vector>\n")
    return "".join(out)

def background():
    glow = (f'<path android:pathData="M0,0 H108 V108 H0 Z"><aapt:attr name="android:fillColor"><gradient android:type="radial" android:centerX="32.4" android:centerY="27" '
            'android:gradientRadius="75.6"><item android:offset="0" android:color="#595B5BD6"/><item android:offset="1" android:color="#005B5BD6"/></gradient></aapt:attr></path>')
    base = ('<path android:pathData="M0,0 H108 V108 H0 Z"><aapt:attr name="android:fillColor"><gradient android:type="linear" android:startX="0" android:startY="0" '
            'android:endX="108" android:endY="108"><item android:offset="0" android:color="#FF161E4A"/><item android:offset="1" android:color="#FF060914"/></gradient></aapt:attr></path>')
    return HEAD + f"  {base}\n  {glow}\n</vector>\n"

def round_icon(src, px):
    im = src.resize((px, px), Image.LANCZOS)
    m = Image.new("L", (px * 4, px * 4), 0); ImageDraw.Draw(m).ellipse([0, 0, px * 4 - 1, px * 4 - 1], fill=255)
    out = Image.new("RGBA", (px, px)); out.paste(im, (0, 0), m.resize((px, px), Image.LANCZOS))
    return out

if __name__ == "__main__":
    src = Image.open(ROOT / "docs/icon/vela-icon-1024.png").convert("RGBA")
    (RES / "drawable").mkdir(parents=True, exist_ok=True); (RES / "drawable-v24").mkdir(exist_ok=True)
    (RES / "drawable/ic_launcher_background.xml").write_text(background())
    (RES / "drawable-v24/ic_launcher_foreground.xml").write_text(foreground())
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(foreground(mono=True))
    for d, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        src.resize((px, px), Image.LANCZOS).save(RES / f"mipmap-{d}/ic_launcher.png")
        round_icon(src, px).save(RES / f"mipmap-{d}/ic_launcher_round.png")
    src.resize((512, 512), Image.LANCZOS).save(ROOT / "docs/vela-icon-512.png")
    desk = ROOT / "desktopApp/src/main/resources/icons"; desk.mkdir(parents=True, exist_ok=True)
    for px in (256, 128, 64, 48): src.resize((px, px), Image.LANCZOS).save(desk / f"vela-{px}.png")
    print("wrote launcher vectors, mipmaps, docs/vela-icon-512.png and desktop icons")
