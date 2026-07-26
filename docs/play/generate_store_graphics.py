#!/usr/bin/env python3
"""Generates the two graphics Google Play requires for a store listing.

    python3 docs/play/generate_store_graphics.py

Writes into the directory this script lives in:

  icon-512.png                  512x512 app icon, opaque (Play masks it itself)
  feature-graphic-1024x500.png  1024x500 banner

The mark is drawn from the same coordinates as the launcher icon's vector
(`app/src/main/res/drawable/ic_launcher_foreground.xml`), which uses a 108-unit
viewport, so the store icon and the on-device icon are the same artwork. Keep
the two in step if either changes.

Requires Pillow and the DejaVu fonts.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

NAVY = (0x14, 0x21, 0x3D)
WHITE = (0xFF, 0xFF, 0xFF)
FOLD = (0x93, 0xA6, 0xC0)
BLUE = (0x2F, 0x81, 0xF7)
MUTED = (0xC7, 0xD4, 0xE8)

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

# Everything is drawn at this multiple and downscaled, which is cheaper than
# fighting Pillow's lack of anti-aliasing on shape fills.
SS = 4

OUT = Path(__file__).parent


def mark_layer(page_width_px: float) -> Image.Image:
    """The page-with-keyhole mark on transparency.

    Drawn on its own RGBA layer so the cut-away corner is genuinely transparent
    instead of being painted in the background colour, which only looks right
    when the background is flat.
    """
    unit = page_width_px / 40.0  # the page spans x 36..76 in vector units
    width = int(round(40 * unit))
    height = int(round(60 * unit))  # and y 24..84
    layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)

    def at(x: float, y: float) -> tuple[float, float]:
        return ((x - 36) * unit, (y - 24) * unit)

    # Page body, then knock the corner out and lay the fold over the gap.
    draw.rounded_rectangle([at(36, 24), at(76, 84)], radius=4 * unit, fill=WHITE + (255,))
    draw.polygon([at(60.6, 22), at(78, 22), at(78, 39.4)], fill=(0, 0, 0, 0))
    draw.polygon([at(61, 24), at(76, 39), at(61, 39)], fill=FOLD + (255,))

    # Keyhole: a circle over a single tapered stem.
    draw.ellipse([at(46, 46), at(62, 62)], fill=BLUE + (255,))
    draw.polygon(
        [at(51.2, 56), at(56.8, 56), at(59, 71.5), at(49, 71.5)],
        fill=BLUE + (255,),
    )
    return layer


def build_icon() -> None:
    size = 512
    canvas = Image.new("RGB", (size * SS, size * SS), NAVY)
    # A launcher only guarantees the centre 72 of an adaptive icon's 108 units
    # is visible, so map that window onto the whole square.
    unit = size * SS / 72.0
    offset = -(108 * unit - size * SS) / 2
    mark = mark_layer(40 * unit)
    canvas.paste(
        mark,
        (int(round(offset + 36 * unit)), int(round(offset + 24 * unit))),
        mark,
    )
    canvas.resize((size, size), Image.LANCZOS).save(OUT / "icon-512.png")
    print("wrote icon-512.png")


def build_feature_graphic() -> None:
    width, height = 1024, 500
    canvas = Image.new("RGB", (width * SS, height * SS), NAVY)
    draw = ImageDraw.Draw(canvas)

    for y in range(height * SS):
        lift = 20 * (1 - y / (height * SS))
        draw.line([(0, y), (width * SS, y)], fill=tuple(int(c + lift) for c in NAVY))

    margin = 72 * SS
    mark = mark_layer(300 * SS * 40 / 60)
    canvas.paste(mark, (margin, (height * SS - mark.height) // 2), mark)

    text_x = margin + mark.width + 64 * SS
    available = width * SS - text_x - margin

    def fitted(text: str, path: str, start_pt: int) -> ImageFont.FreeTypeFont:
        """Largest size at which the text still fits the column."""
        for points in range(start_pt, 8, -1):
            font = ImageFont.truetype(path, points)
            if draw.textlength(text, font=font) <= available:
                return font
        return ImageFont.truetype(path, 9)

    title = "AnonPDF"
    subtitle = "Read and edit PDFs, entirely on your phone"
    chips = ["NO ADS", "NO ACCOUNTS", "NO PERMISSIONS"]

    title_font = fitted(title, FONT_BOLD, 96 * SS)
    subtitle_font = fitted(subtitle, FONT_REGULAR, 34 * SS)
    chip_font = ImageFont.truetype(FONT_BOLD, 24 * SS)

    pad_x, pad_y, gap = 16 * SS, 10 * SS, 12 * SS
    while chip_font.size > 10:
        row = sum(draw.textlength(c, font=chip_font) + pad_x * 2 for c in chips)
        if row + gap * (len(chips) - 1) <= available:
            break
        chip_font = ImageFont.truetype(FONT_BOLD, chip_font.size - 1)

    title_h = draw.textbbox((0, 0), title, font=title_font)[3]
    subtitle_h = draw.textbbox((0, 0), subtitle, font=subtitle_font)[3]
    chip_h = draw.textbbox((0, 0), "X", font=chip_font)[3] + pad_y * 2

    top = (height * SS - (title_h + 22 * SS + subtitle_h + 34 * SS + chip_h)) // 2

    draw.text((text_x, top), title, font=title_font, fill=WHITE)
    y = top + title_h + 22 * SS
    draw.text((text_x, y), subtitle, font=subtitle_font, fill=MUTED)
    y += subtitle_h + 34 * SS

    x = text_x
    for chip in chips:
        chip_w = draw.textlength(chip, font=chip_font)
        draw.rounded_rectangle(
            [x, y, x + chip_w + pad_x * 2, y + chip_h], radius=9 * SS, fill=BLUE,
        )
        draw.text((x + pad_x, y + pad_y - 3 * SS), chip, font=chip_font, fill=WHITE)
        x += chip_w + pad_x * 2 + gap

    canvas.resize((width, height), Image.LANCZOS).save(
        OUT / "feature-graphic-1024x500.png",
    )
    print("wrote feature-graphic-1024x500.png")


if __name__ == "__main__":
    build_icon()
    build_feature_graphic()
