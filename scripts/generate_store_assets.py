#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
GRAPHICS_DIR = ROOT / "release" / "google-play" / "graphics"
FONT_PATH = ROOT / "app" / "src" / "main" / "assets" / "fonts" / "PressStart2P-Regular.ttf"


def infer_frame_count(path: Path) -> int:
    name = path.stem
    if "_48frame" in name:
        return 48
    if "_4frames" in name:
        return 4
    return 1


def load_frame(path: Path, frame_index: int = 0) -> Image.Image:
    sprite = Image.open(path).convert("RGBA")
    frames = infer_frame_count(path)
    if frames <= 1:
        return sprite
    frame_width = sprite.width // frames
    index = max(0, min(frame_index, frames - 1))
    return sprite.crop((frame_width * index, 0, frame_width * (index + 1), sprite.height))


def fit_sprite(sprite: Image.Image, max_w: int, max_h: int) -> Image.Image:
    ratio = min(max_w / sprite.width, max_h / sprite.height)
    new_size = (max(1, int(sprite.width * ratio)), max(1, int(sprite.height * ratio)))
    return sprite.resize(new_size, Image.Resampling.NEAREST)


def add_glow(base: Image.Image, sprite: Image.Image, xy: tuple[int, int], glow_color: tuple[int, int, int], blur: int = 18) -> None:
    glow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    mask = sprite.split()[-1]
    tinted = Image.new("RGBA", sprite.size, (*glow_color, 170))
    glow.paste(tinted, (0, 0), mask)
    overlay = Image.new("RGBA", base.size, (0, 0, 0, 0))
    overlay.paste(glow.crop((0, 0, sprite.width, sprite.height)), xy, glow.crop((0, 0, sprite.width, sprite.height)))
    overlay = overlay.filter(ImageFilter.GaussianBlur(blur))
    base.alpha_composite(overlay)


def add_shadow(base: Image.Image, sprite: Image.Image, xy: tuple[int, int], blur: int = 12, alpha: int = 110) -> None:
    shadow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    mask = sprite.split()[-1]
    shadow_sprite = Image.new("RGBA", sprite.size, (0, 0, 0, alpha))
    shadow.paste(shadow_sprite, (xy[0] + 10, xy[1] + 12), mask)
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    base.alpha_composite(shadow)


def draw_title(draw: ImageDraw.ImageDraw, width: int, height: int) -> None:
    title_font = ImageFont.truetype(str(FONT_PATH), 42)
    subtitle_font = ImageFont.truetype(str(FONT_PATH), 14)
    title = "FOREST RUN"
    subtitle = "Mercy changes what comes home."
    title_box = draw.textbbox((0, 0), title, font=title_font)
    title_w = title_box[2] - title_box[0]
    x = width * 0.08
    y = height * 0.14
    draw.text((x + 4, y + 4), title, font=title_font, fill=(22, 28, 34, 210))
    draw.text((x, y), title, font=title_font, fill=(250, 244, 188, 255))
    draw.text((x, y + 64), subtitle, font=subtitle_font, fill=(232, 242, 230, 255))
    draw.rounded_rectangle((x - 10, y + 92, x + title_w + 10, y + 122), radius=14, fill=(242, 233, 170, 210), outline=(255, 248, 220, 220), width=3)
    chip_font = ImageFont.truetype(str(FONT_PATH), 11)
    draw.text((x + 16, y + 101), "5 biomes  •  Bloom at 8 seeds", font=chip_font, fill=(46, 54, 28, 255))


def draw_feature_graphic() -> None:
    width, height = 1024, 500
    image = Image.new("RGBA", (width, height), (0, 0, 0, 255))
    draw = ImageDraw.Draw(image)

    for y in range(height):
        t = y / height
        if t < 0.65:
            top = (26, 58, 92)
            bottom = (110, 164, 126)
            local = t / 0.65
            color = tuple(int(top[i] + (bottom[i] - top[i]) * local) for i in range(3))
        else:
            top = (84, 116, 62)
            bottom = (38, 62, 34)
            local = (t - 0.65) / 0.35
            color = tuple(int(top[i] + (bottom[i] - top[i]) * local) for i in range(3))
        draw.line((0, y, width, y), fill=(*color, 255))

    draw.ellipse((710, 58, 864, 212), fill=(250, 214, 132, 210))
    draw.ellipse((676, 34, 900, 246), fill=(246, 220, 156, 66))
    draw.rectangle((0, 348, width, height), fill=(34, 56, 30, 255))
    draw.rounded_rectangle((54, 70, 970, 430), radius=34, outline=(246, 240, 214, 64), width=4)

    heroine = fit_sprite(load_frame(ROOT / "app/src/main/assets/sprites/char/runner_girl_technical_48frame.png", 10), 300, 300)
    fox = fit_sprite(load_frame(ROOT / "app/src/main/assets/sprites/animals/fox_4frames.png", 1), 170, 170)
    owl = fit_sprite(load_frame(ROOT / "app/src/main/assets/sprites/birds/owl_4frames.png", 2), 156, 156)
    lily = fit_sprite(load_frame(ROOT / "app/src/main/assets/sprites/plants/lily_of_valley_4frames.png", 1), 140, 170)

    heroine_xy = (500, 150)
    fox_xy = (792, 246)
    owl_xy = (770, 92)
    lily_xy = (644, 258)

    for sprite, xy, glow in (
        (heroine, heroine_xy, (255, 226, 158)),
        (fox, fox_xy, (255, 204, 176)),
        (owl, owl_xy, (194, 214, 255)),
        (lily, lily_xy, (208, 255, 214)),
    ):
        add_shadow(image, sprite, xy)
        add_glow(image, sprite, xy, glow)
        image.alpha_composite(sprite, xy)

    draw_title(draw, width, height)

    footer_font = ImageFont.truetype(str(FONT_PATH), 12)
    footer = "Run. Spare. Bloom. Return."
    draw.text((68, 430), footer, font=footer_font, fill=(226, 236, 224, 255))
    image.save(GRAPHICS_DIR / "feature-graphic.png")


def draw_promo_square() -> None:
    width, height = 512, 512
    image = Image.new("RGBA", (width, height), (18, 34, 48, 255))
    draw = ImageDraw.Draw(image)

    for y in range(height):
        t = y / height
        top = (28, 54, 92)
        bottom = (68, 112, 70)
        color = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        draw.line((0, y, width, y), fill=(*color, 255))

    draw.ellipse((300, 54, 430, 184), fill=(248, 214, 138, 220))
    draw.ellipse((282, 38, 448, 202), fill=(248, 222, 154, 60))
    draw.rectangle((0, 344, width, height), fill=(34, 60, 30, 255))

    heroine = fit_sprite(load_frame(ROOT / "app/src/main/assets/sprites/char/runner_girl_technical_48frame.png", 10), 212, 212)
    owl = fit_sprite(load_frame(ROOT / "app/src/main/assets/sprites/birds/owl_4frames.png", 2), 102, 102)
    lily = fit_sprite(load_frame(ROOT / "app/src/main/assets/sprites/plants/lily_of_valley_4frames.png", 1), 92, 116)

    hero_xy = (140, 160)
    owl_xy = (336, 188)
    lily_xy = (82, 266)

    for sprite, xy, glow in (
        (heroine, hero_xy, (255, 224, 152)),
        (owl, owl_xy, (196, 214, 255)),
        (lily, lily_xy, (208, 255, 214)),
    ):
        add_shadow(image, sprite, xy, blur=9, alpha=96)
        add_glow(image, sprite, xy, glow, blur=12)
        image.alpha_composite(sprite, xy)

    title_font = ImageFont.truetype(str(FONT_PATH), 24)
    tag_font = ImageFont.truetype(str(FONT_PATH), 10)
    draw.text((36, 44), "FOREST", font=title_font, fill=(248, 242, 186, 255))
    draw.text((36, 82), "RUN", font=title_font, fill=(248, 242, 186, 255))
    draw.rounded_rectangle((34, 120, 254, 150), radius=12, fill=(242, 233, 170, 210), outline=(255, 248, 220, 220), width=3)
    draw.text((48, 130), "Mercy. Bloom. Return.", font=tag_font, fill=(44, 54, 30, 255))
    image.save(GRAPHICS_DIR / "promo-square.png")


def main() -> None:
    GRAPHICS_DIR.mkdir(parents=True, exist_ok=True)
    draw_feature_graphic()
    draw_promo_square()
    print(f"Wrote store graphics to {GRAPHICS_DIR}")


if __name__ == "__main__":
    main()
