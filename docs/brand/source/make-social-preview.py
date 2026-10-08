"""Membuat docs/brand/social-preview.png (1280x640) dari screenshot Attract dan wordmark.

Pakai: python docs/brand/source/make-social-preview.py   (butuh Pillow; fonts dari src/main/resources/fonts)
"""
from PIL import Image, ImageDraw, ImageFilter, ImageFont

CREAM, INK, INK2, VERM, BUTTER = '#F7EFE2', '#241B16', '#6B5D52', '#D9411E', '#F4C95D'
FONTS = 'src/main/resources/fonts/'
W, H = 1280, 640

img = Image.new('RGB', (W, H), CREAM)
d = ImageDraw.Draw(img)

# lingkaran butter di belakang screenshot (seperti layar Attract)
d.ellipse((760, 90, 1260, 590), fill=BUTTER)

# screenshot Attract, sudut membulat + bayangan
shot = Image.open('docs/screenshots/1-attract.png').convert('RGB').resize((560, 315), Image.LANCZOS)
mask = Image.new('L', shot.size, 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot.width, shot.height), radius=28, fill=255)
shadow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
ImageDraw.Draw(shadow).rounded_rectangle((690, 250, 1250, 565), radius=28, fill=(36, 27, 22, 70))
shadow = shadow.filter(ImageFilter.GaussianBlur(24))
img.paste(shadow, (0, 0), shadow)
img.paste(shot, (680, 230), mask)

serif = ImageFont.truetype(FONTS + 'Fraunces-SemiBold.ttf', 104)
italic = ImageFont.truetype(FONTS + 'Fraunces-Italic-Regular.ttf', 104)
sans = ImageFont.truetype(FONTS + 'PlusJakartaSans-SemiBold.ttf', 30)
small = ImageFont.truetype(FONTS + 'PlusJakartaSans-SemiBold.ttf', 24)

# wordmark: "Van " "de" " B" "oo" "th" (de italic, oo vermilion), dibagi dua baris
def draw_runs(x, y, runs):
    for text, font, color in runs:
        d.text((x, y), text, font=font, fill=color)
        x += d.textlength(text, font=font)

draw_runs(72, 92, [('Van ', serif, INK), ('de', italic, INK)])
draw_runs(72, 200, [('B', serif, INK), ('oo', serif, VERM), ('th', serif, INK)])
d.text((72, 360), 'An offline photobooth for events.', font=sans, fill=INK2)
d.text((72, 410), 'Six layouts. Print or QR download.', font=small, fill=INK2)
d.rounded_rectangle((72, 500, 72 + 360, 500 + 66), radius=33, fill=VERM)
d.text((72 + 180, 500 + 33), 'Java + React + Electron', font=small, fill='#FFFFFF', anchor='mm')

img.save('docs/brand/social-preview.png', optimize=True)
print(img.size)
