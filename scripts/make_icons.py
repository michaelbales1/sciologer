"""Turn the 2008 Sciologer icons (legacy/drawing/*.png) into web-ready masks.

Each source icon is black line art on an opaque white shape. For theming, every icon
becomes two square alpha masks in web/src/assets/icons/:

  <type>-line.png  the black lines (alpha = darkness)   -> drawn in the type's color
  <type>-fill.png  the whole silhouette (source alpha)  -> drawn in a light tint

The 2008 set has no keyword icon, so a tag is drawn here in the same line style.

Run from the project root:  uv run --with pillow python scripts/make_icons.py
"""

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "legacy" / "drawing"
OUT = ROOT / "web" / "src" / "assets" / "icons"
SIZE = 128  # output side in px; drawn at up to ~48 css px on 2x screens
PAD = 8
# The 2008 strokes are ~2px at this size, which vanishes at map and list sizes
# (16-24px). Thicken them; the fill is grown by the same amount so lines stay inside.
STROKE_GROW = 5

SOURCES = {
    "author": "person.png",
    "mesh": "mainheading.png",
    "journal": "journal.png",
    "substance": "substance.png",
    "institution": "institution.png",
    "grant": "grant.png",
    "paper": "paper.png",
}


def square(img: Image.Image) -> Image.Image:
    """Crop to content and center on a transparent square canvas."""
    img = img.crop(img.getbbox())
    side = max(img.size)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
    inner = SIZE - 2 * PAD
    canvas = canvas.resize((inner, inner), Image.LANCZOS)
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    out.paste(canvas, (PAD, PAD))
    return out


def masks(img: Image.Image) -> tuple[Image.Image, Image.Image]:
    img = img.convert("RGBA")
    alpha = img.getchannel("A")
    darkness = ImageChops.invert(img.convert("L"))
    line_alpha = ImageChops.multiply(darkness, alpha)
    black = Image.new("L", img.size, 0)
    line_alpha = line_alpha.filter(ImageFilter.MaxFilter(STROKE_GROW))
    alpha = alpha.filter(ImageFilter.MaxFilter(STROKE_GROW))
    line = Image.merge("RGBA", (black, black, black, line_alpha))
    fill = Image.merge("RGBA", (black, black, black, alpha))
    return line, fill


def tag_icon() -> Image.Image:
    """A luggage-tag outline matching the 2008 icons' heavy line weight."""
    s = 420
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    body = [(60, 120), (270, 120), (370, 210), (270, 300), (60, 300)]
    d.polygon(body, fill=(255, 255, 255, 255))
    d.line(body + [body[0]], fill=(0, 0, 0, 255), width=14, joint="curve")
    d.ellipse((240, 186, 288, 234), outline=(0, 0, 0, 255), width=12)
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    icons = {name: Image.open(SRC / file).convert("RGBA") for name, file in SOURCES.items()}
    icons["keyword"] = tag_icon()
    for name, img in icons.items():
        line, fill = masks(square(img))
        line.save(OUT / f"{name}-line.png", optimize=True)
        fill.save(OUT / f"{name}-fill.png", optimize=True)
        print(f"wrote {name}")


if __name__ == "__main__":
    main()
