"""Draws the Shulker Stickers icon as 32x32 pixel art, scaled to 128 and 512.

Usage: python docs/images/make_icon.py <output folder>. Copy icon.png to
fabric/src/main/resources/assets/shulker_stickers/ and icon-512.png to docs/images/.
"""
import sys
from PIL import Image, ImageDraw

S = 32
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

OUT = (40, 22, 48, 255)
TOP = (186, 140, 196, 255)
TOP_HI = (206, 168, 214, 255)
FRONT = (150, 102, 162, 255)
RIGHT = (112, 72, 126, 255)
SEAM = (72, 42, 84, 255)
SEAM_R = (58, 32, 68, 255)

# faces (front 4..21 x 12..29, depth 6)
top = [(4, 12), (10, 6), (27, 6), (21, 12)]
right = [(21, 12), (27, 6), (27, 23), (21, 29)]
front = [(4, 12), (21, 12), (21, 29), (4, 29)]
d.polygon(top, fill=TOP)
d.polygon(right, fill=RIGHT)
d.polygon(front, fill=FRONT)
d.line([(10, 7), (26, 7)], fill=TOP_HI)
# lid seam
d.line([(4, 17), (21, 17)], fill=SEAM)
d.line([(21, 17), (27, 11)], fill=SEAM_R)
d.line([(4, 18), (21, 18)], fill=SEAM)
d.line([(22, 17), (27, 12)], fill=SEAM_R)
# outline
d.line(top + [top[0]], fill=OUT)
d.line([(21, 12), (21, 29)], fill=OUT)
d.line([(27, 6), (27, 23), (21, 29), (4, 29), (4, 12)], fill=OUT)

# sticker on the body with a peeled corner
PAPER = (246, 238, 214, 255)
PAPER_SH = (214, 200, 168, 255)
d.rectangle([7, 20, 18, 27], fill=PAPER)
d.line([(7, 27), (18, 27)], fill=PAPER_SH)
d.line([(18, 20), (18, 27)], fill=PAPER_SH)
img.putpixel((18, 20), FRONT)
img.putpixel((17, 20), PAPER_SH)
img.putpixel((18, 21), PAPER_SH)
# gem glyph
GEM = (52, 190, 160, 255)
GEM_HI = (150, 236, 214, 255)
GEM_SH = (28, 120, 104, 255)
gem = {
    21: [(11, GEM_HI), (12, GEM_HI), (13, GEM), (14, GEM)],
    22: [(10, GEM_HI), (11, GEM), (12, GEM), (13, GEM), (14, GEM), (15, GEM_SH)],
    23: [(11, GEM), (12, GEM), (13, GEM_SH), (14, GEM_SH)],
    24: [(12, GEM), (13, GEM_SH)],
}
for y, row in gem.items():
    for x, c in row:
        img.putpixel((x, y), c)
# name line under the gem
d.line([(9, 26), (16, 26)], fill=(120, 100, 80, 255))

# small sticker on the lid top
d.polygon([(12, 9), (14, 7), (20, 7), (18, 9)], fill=PAPER)
d.point([(15, 8), (16, 8)], fill=(214, 72, 72, 255))

centered = Image.new("RGBA", (S, S), (0, 0, 0, 0))
centered.paste(img, (0, -2))
img = centered
out = sys.argv[1]
img.resize((128, 128), Image.NEAREST).save(out + "/icon.png")
img.resize((512, 512), Image.NEAREST).save(out + "/icon-512.png")
