"""Deterministic Android resource conversion of the user's selected icon; no generated artwork.

Usage: python tools/fashionfit_icons.py --preview-dir <ignored local directory>
Pillow is a development-only dependency. Never reads user photos or credentials.
"""
import argparse
from collections import deque
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'tools/assets/fashionfit/selected-icon-source.png'
RES = ROOT / 'fashion_app/app/src/main/res'
CREAM = (249, 243, 237, 255)


def build(preview_dir):
    original = Image.open(SOURCE).convert('RGBA')
    assert original.size == (1254, 1254)
    clean = original.copy()
    px = clean.load()
    w, h = clean.size
    # Only the neutral dark component connected to the outer edge is removed.
    # This cannot reach the warm ribbon or its enclosed shadow.
    queue = deque([(x, y) for x in range(w) for y in (0, h-1)] +
                  [(x, y) for y in range(h) for x in (0, w-1)])
    visited = set()
    removed = 0
    while queue:
        x, y = queue.popleft()
        if (x, y) in visited or not (0 <= x < w and 0 <= y < h):
            continue
        visited.add((x, y))
        r, g, b, a = px[x, y]
        if max(r, g, b) - min(r, g, b) >= 25 or max(r, g, b) >= 240:
            continue
        px[x, y] = CREAM
        removed += 1
        queue.extend(((x-1, y), (x+1, y), (x, y-1), (x, y+1)))
    # The themed icon uses the existing warm ribbon's silhouette, not the cream tile.
    silhouette = Image.new('L', original.size)
    mask = silhouette.load()
    max_radius = 0.0
    ribbon_pixels = 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = original.getpixel((x, y))
            alpha = max(0, min(255, round((r-g-18) * 255 / 8))) if r > b else 0
            mask[x, y] = alpha
            if alpha:
                assert clean.getpixel((x, y)) == (r, g, b, a), 'Ribbon pixel changed'
                max_radius = max(max_radius, ((x-w/2)**2 + (y-h/2)**2)**0.5)
                ribbon_pixels += 1
    assert removed > 0 and ribbon_pixels > 100000
    # 108dp layers @4x; every ribbon pixel fits the 64dp safe circle.
    side = 432
    tile_side = int(w * (32*4-2) / max_radius)
    offset = (side-tile_side)//2
    foreground = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    foreground.alpha_composite(clean.resize((tile_side, tile_side), Image.Resampling.LANCZOS), (offset, offset))
    mono = Image.new('RGBA', (side, side), (255, 255, 255, 0))
    alpha = Image.new('L', (side, side))
    alpha.paste(silhouette.resize((tile_side, tile_side), Image.Resampling.LANCZOS), (offset, offset))
    mono.putalpha(alpha)
    out = RES / 'drawable-nodpi'
    out.mkdir(exist_ok=True)
    foreground.save(out/'fashionfit_launcher_foreground.png')
    mono.save(out/'fashionfit_launcher_monochrome.png')
    composed = Image.new('RGBA', (side, side), CREAM)
    composed.alpha_composite(foreground)
    viewport = composed.crop((72, 72, 360, 360))
    mono_view = mono.crop((72, 72, 360, 360))
    # Outside the safe circle the themed silhouette must remain empty.
    for y in range(side):
        for x in range(side):
            if alpha.getpixel((x, y)) > 8:
                assert (x-side/2)**2+(y-side/2)**2 <= (33*4)**2
    samples = []
    for round_icon in (False, True):
        shape = Image.new('L', viewport.size)
        draw = ImageDraw.Draw(shape)
        if round_icon:
            draw.ellipse((0, 0, 287, 287), fill=255)
        else:
            draw.rounded_rectangle((0, 0, 287, 287), radius=64, fill=255)
        legacy = viewport.copy()
        legacy.putalpha(shape)
        samples.append(legacy)
        for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
            legacy.resize((size,size),Image.Resampling.LANCZOS).save(
                RES/f'mipmap-{density}'/('ic_launcher_round.webp' if round_icon else 'ic_launcher.webp'),
                'WEBP', lossless=True)
    themed = Image.new('RGBA', (288,288), (229,220,210,255))
    tint = Image.new('RGBA', (288,288), (83,57,42,255)); tint.putalpha(mono_view.getchannel('A'))
    themed.alpha_composite(tint)
    preview_dir.mkdir(parents=True,exist_ok=True)
    viewport.save(preview_dir/'FashionFit-icon.png')
    board = Image.new('RGBA',(3*320,350),'#e4e4e4')
    for i, sample in enumerate([*samples,themed]):
        board.alpha_composite(sample,(i*320+16,42))
    d=ImageDraw.Draw(board)
    for i, label in enumerate(['Legacy / squircle','Round / adaptive mask','Android 13 themed']):
        d.text((i*320+16,14),label,fill='black')
    board.convert('RGB').save(preview_dir/'FashionFit-icon-review.png')
    report={'sourceSha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
            'sourceSize':original.size,'edgePixelsReplaced':removed,
            'ribbonPixelsUnchangedBeforeResampling':ribbon_pixels,
            'adaptiveLayerPixels':side,'tilePixels':tile_side,
            'ribbonInside66DpSafeCircle':True,'legacySizes':[48,72,96,144,192],
            'newArtworkGenerated':False,'deviceLauncherVerified':False}
    (preview_dir/'ICON_VERIFICATION.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview-dir',type=Path,required=True)
    build(parser.parse_args().preview_dir)
