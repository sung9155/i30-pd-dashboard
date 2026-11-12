# Usage: python scripts/make_icons.py <out dir>  -> dash.png (apps/dashboard ic_launcher), hub.png (apps/hub ic_launcher)
"""Icon mockups in the OEM all-menu style (glossy blue, soft glow), 4x supersampled."""
import math, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont
OUT = sys.argv[1]
K = 4; N = 128 * K

def lin(size, top, bot):
    g = Image.new('RGBA', size)
    d = ImageDraw.Draw(g)
    for y in range(size[1]):
        t = y / max(1, size[1] - 1)
        d.line((0, y, size[0], y), fill=tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(4)))
    return g

def masked(fill, mask):
    out = Image.new('RGBA', mask.size, (0, 0, 0, 0)); out.paste(fill, (0, 0), mask); return out

def gloss(base, mask):
    # top highlight band, like the OEM icons
    hl = Image.new('L', mask.size, 0); d = ImageDraw.Draw(hl)
    w, h = mask.size; d.ellipse((-w * 0.2, -h * 0.75, w * 1.2, h * 0.48), fill=90)
    hl = Image.composite(hl, Image.new('L', mask.size, 0), mask)
    white = Image.new('RGBA', mask.size, (255, 255, 255, 255))
    base.paste(white, (0, 0), hl.filter(ImageFilter.GaussianBlur(K * 2)))
    return base

def glow(img):
    a = img.split()[3].filter(ImageFilter.GaussianBlur(K * 6))
    g = Image.new('RGBA', img.size, (40, 140, 255, 0)); g.putalpha(a.point(lambda v: v * 0.30))
    out = Image.new('RGBA', img.size, (0, 0, 0, 0)); out.alpha_composite(g); out.alpha_composite(img); return out

BLUE_T, BLUE_B = (110, 190, 255, 255), (20, 70, 170, 255)

def gauge(variant):
    m = Image.new('L', (N, N), 0); d = ImageDraw.Draw(m)
    c, r = N / 2, N * 0.42
    d.ellipse((c - r, c - r, c + r, c + r), fill=255)
    body = gloss(masked(lin((N, N), BLUE_T, BLUE_B), m), m)
    d2 = ImageDraw.Draw(body)
    ri = r * 0.80
    d2.ellipse((c - ri, c - ri, c + ri, c + ri), fill=(10, 30, 70, 255))
    # tick arc 225 deg sweep, red zone at the end
    for i in range(0, 11):
        a = math.radians(135 + i * 27)
        r0, r1 = ri * (0.78 if i % 5 else 0.70), ri * 0.92
        col = (255, 80, 60, 255) if i >= 9 else (200, 230, 255, 255)
        d2.line((c + r0 * math.cos(a), c + r0 * math.sin(a), c + r1 * math.cos(a), c + r1 * math.sin(a)), fill=col, width=K * 3)
    a = math.radians(135 + 7.2 * 27)
    d2.line((c, c, c + ri * 0.82 * math.cos(a), c + ri * 0.82 * math.sin(a)), fill=(255, 120, 40, 255), width=K * 4)
    d2.ellipse((c - K * 7, c - K * 7, c + K * 7, c + K * 7), fill=(230, 240, 255, 255))
    if variant == 'pd':
        f = ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf', int(ri * 0.38))
        w = d2.textbbox((0, 0), 'PD', font=f)[2]
        d2.text((c - w / 2, c + ri * 0.28), 'PD', font=f, fill=(200, 230, 255, 255))
    return glow(body)

def grid():
    m = Image.new('L', (N, N), 0); d = ImageDraw.Draw(m)
    s, g0 = N * 0.34, N * 0.07
    x0 = (N - 2 * s - g0) / 2
    for i in range(2):
        for j in range(2):
            x, y = x0 + i * (s + g0), x0 + j * (s + g0)
            d.rounded_rectangle((x, y, x + s, y + s), radius=s * 0.24, fill=255)
    body = gloss(masked(lin((N, N), BLUE_T, BLUE_B), m), m)
    # one tile in accent (the dashboard), like a highlighted app
    acc = Image.new('L', (N, N), 0); ImageDraw.Draw(acc).rounded_rectangle((x0, x0, x0 + s, x0 + s), radius=s * 0.24, fill=255)
    body.paste(gloss(masked(lin((N, N), (120, 230, 255, 255), (0, 130, 170, 255)), acc), acc), (0, 0), acc)
    return glow(body)

icons = {'dash': gauge('plain'), 'dash_pd': gauge('pd'), 'hub': grid()}
for k, v in icons.items():
    v.resize((128, 128), Image.LANCZOS).save(f'{OUT}/{k}.png')
