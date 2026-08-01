#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFilter, ImageFont, UnidentifiedImageError
except ImportError as exc:
    raise SystemExit(
        "Pillow is required. Install scripts/requirements.txt before generating assets."
    ) from exc

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_GRAPHICS_DIR = ROOT / "release" / "google-play" / "graphics"
FONT_PATH = ROOT / "app" / "src" / "main" / "assets" / "fonts" / "PressStart2P-Regular.ttf"
SCHEMA_VERSION = 1
HEX_40 = re.compile(r"[0-9a-f]{40}")
EXPECTED_OUTPUTS = {
    "feature-graphic.png": (1024, 500),
    "promo-square.png": (512, 512),
}


@dataclass(frozen=True)
class SpriteSpec:
    path: Path
    frame_count: int
    frame_index: int


SPRITES = {
    "heroine": SpriteSpec(
        ROOT / "app/src/main/assets/sprites/char/runner_girl_technical_48frame.png",
        frame_count=48,
        frame_index=10,
    ),
    "fox": SpriteSpec(
        ROOT / "app/src/main/assets/sprites/animals/fox_4frames.png",
        frame_count=4,
        frame_index=1,
    ),
    "owl": SpriteSpec(
        ROOT / "app/src/main/assets/sprites/birds/owl_4frames.png",
        frame_count=4,
        frame_index=2,
    ),
    "lily": SpriteSpec(
        ROOT / "app/src/main/assets/sprites/plants/lily_of_valley_4frames.png",
        frame_count=4,
        frame_index=1,
    ),
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def current_candidate_sha() -> str:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise SystemExit(
            "Could not resolve store-graphics candidate SHA: "
            + (result.stderr or result.stdout).strip()
        )
    candidate = result.stdout.strip().lower()
    if HEX_40.fullmatch(candidate) is None:
        raise SystemExit(f"git returned an invalid candidate SHA: {candidate!r}")
    return candidate


def validate_inputs() -> None:
    required = [Path(__file__).resolve(), FONT_PATH]
    required.extend(spec.path for spec in SPRITES.values())
    missing = [str(path) for path in required if not path.is_file()]
    empty = [str(path) for path in required if path.is_file() and path.stat().st_size <= 0]
    if missing:
        raise SystemExit("Missing required store-art source files:\n- " + "\n- ".join(missing))
    if empty:
        raise SystemExit("Empty required store-art source files:\n- " + "\n- ".join(empty))


def load_frame(spec: SpriteSpec) -> Image.Image:
    if spec.frame_count < 1:
        raise ValueError(f"Invalid frame count for {spec.path}: {spec.frame_count}")
    if spec.frame_index not in range(spec.frame_count):
        raise ValueError(
            f"Frame index {spec.frame_index} outside 0..{spec.frame_count - 1} for {spec.path}"
        )

    try:
        with Image.open(spec.path) as source:
            source.verify()
        with Image.open(spec.path) as source:
            sprite = source.convert("RGBA")
    except (UnidentifiedImageError, OSError) as exc:
        raise ValueError(f"Unreadable store-art sprite {spec.path}: {exc}") from exc
    if sprite.width <= 0 or sprite.height <= 0 or sprite.width % spec.frame_count != 0:
        raise ValueError(
            f"Sprite width {sprite.width} is not divisible by {spec.frame_count}: {spec.path}"
        )

    frame_width = sprite.width // spec.frame_count
    left = frame_width * spec.frame_index
    frame = sprite.crop((left, 0, left + frame_width, sprite.height))
    if frame.getbbox() is None:
        raise ValueError(f"Selected frame is completely transparent: {spec.path}")
    return frame


def fit_sprite(sprite: Image.Image, max_w: int, max_h: int) -> Image.Image:
    if sprite.width <= 0 or sprite.height <= 0 or max_w <= 0 or max_h <= 0:
        raise ValueError("Cannot fit a sprite into non-positive geometry")
    ratio = min(max_w / sprite.width, max_h / sprite.height)
    new_size = (
        max(1, int(sprite.width * ratio)),
        max(1, int(sprite.height * ratio)),
    )
    return sprite.resize(new_size, Image.Resampling.NEAREST)


def add_glow(
    base: Image.Image,
    sprite: Image.Image,
    xy: tuple[int, int],
    glow_color: tuple[int, int, int],
    blur: int = 18,
) -> None:
    mask = sprite.getchannel("A")
    tinted = Image.new("RGBA", sprite.size, (*glow_color, 170))
    local = Image.new("RGBA", sprite.size, (0, 0, 0, 0))
    local.paste(tinted, (0, 0), mask)
    overlay = Image.new("RGBA", base.size, (0, 0, 0, 0))
    overlay.paste(local, xy, local)
    base.alpha_composite(overlay.filter(ImageFilter.GaussianBlur(blur)))


def add_shadow(
    base: Image.Image,
    sprite: Image.Image,
    xy: tuple[int, int],
    blur: int = 12,
    alpha: int = 110,
) -> None:
    mask = sprite.getchannel("A")
    shadow_sprite = Image.new("RGBA", sprite.size, (0, 0, 0, alpha))
    shadow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    shadow.paste(shadow_sprite, (xy[0] + 10, xy[1] + 12), mask)
    base.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(blur)))


def font(size: int) -> ImageFont.FreeTypeFont:
    if size <= 0:
        raise ValueError("Font size must be positive")
    return ImageFont.truetype(str(FONT_PATH), size)


def draw_title(draw: ImageDraw.ImageDraw, width: int, height: int) -> None:
    title_font = font(42)
    subtitle_font = font(14)
    title = "FOREST RUN"
    subtitle = "Mercy changes what comes home."
    title_box = draw.textbbox((0, 0), title, font=title_font)
    title_width = title_box[2] - title_box[0]
    x = width * 0.08
    y = height * 0.14
    draw.text((x + 4, y + 4), title, font=title_font, fill=(22, 28, 34, 210))
    draw.text((x, y), title, font=title_font, fill=(250, 244, 188, 255))
    draw.text((x, y + 64), subtitle, font=subtitle_font, fill=(232, 242, 230, 255))
    draw.rounded_rectangle(
        (x - 10, y + 92, x + title_width + 10, y + 122),
        radius=14,
        fill=(242, 233, 170, 210),
        outline=(255, 248, 220, 220),
        width=3,
    )
    draw.text(
        (x + 16, y + 101),
        "5 biomes  •  Bloom at 8 seeds",
        font=font(11),
        fill=(46, 54, 28, 255),
    )


def composite_character(
    image: Image.Image,
    sprite: Image.Image,
    xy: tuple[int, int],
    glow: tuple[int, int, int],
    *,
    shadow_blur: int = 12,
    shadow_alpha: int = 110,
    glow_blur: int = 18,
) -> None:
    add_shadow(image, sprite, xy, blur=shadow_blur, alpha=shadow_alpha)
    add_glow(image, sprite, xy, glow, blur=glow_blur)
    image.alpha_composite(sprite, xy)


def draw_feature_graphic(output_dir: Path) -> Path:
    width, height = EXPECTED_OUTPUTS["feature-graphic.png"]
    image = Image.new("RGBA", (width, height), (0, 0, 0, 255))
    draw = ImageDraw.Draw(image)

    for y in range(height):
        t = y / height
        if t < 0.65:
            top, bottom, local = (26, 58, 92), (110, 164, 126), t / 0.65
        else:
            top, bottom, local = (84, 116, 62), (38, 62, 34), (t - 0.65) / 0.35
        colour = tuple(int(top[i] + (bottom[i] - top[i]) * local) for i in range(3))
        draw.line((0, y, width, y), fill=(*colour, 255))

    draw.ellipse((710, 58, 864, 212), fill=(250, 214, 132, 210))
    draw.ellipse((676, 34, 900, 246), fill=(246, 220, 156, 66))
    draw.rectangle((0, 348, width, height), fill=(34, 56, 30, 255))
    draw.rounded_rectangle((54, 70, 970, 430), radius=34, outline=(246, 240, 214, 64), width=4)

    heroine = fit_sprite(load_frame(SPRITES["heroine"]), 300, 300)
    fox = fit_sprite(load_frame(SPRITES["fox"]), 170, 170)
    owl = fit_sprite(load_frame(SPRITES["owl"]), 156, 156)
    lily = fit_sprite(load_frame(SPRITES["lily"]), 140, 170)

    composite_character(image, heroine, (500, 150), (255, 226, 158))
    composite_character(image, fox, (792, 246), (255, 204, 176))
    composite_character(image, owl, (770, 92), (194, 214, 255))
    composite_character(image, lily, (644, 258), (208, 255, 214))

    draw_title(draw, width, height)
    draw.text((68, 430), "Run. Spare. Bloom. Return.", font=font(12), fill=(226, 236, 224, 255))

    destination = output_dir / "feature-graphic.png"
    image.convert("RGB").save(destination, format="PNG", optimize=True)
    return destination


def draw_promo_square(output_dir: Path) -> Path:
    width, height = EXPECTED_OUTPUTS["promo-square.png"]
    image = Image.new("RGBA", (width, height), (18, 34, 48, 255))
    draw = ImageDraw.Draw(image)

    for y in range(height):
        t = y / height
        top, bottom = (28, 54, 92), (68, 112, 70)
        colour = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        draw.line((0, y, width, y), fill=(*colour, 255))

    draw.ellipse((300, 54, 430, 184), fill=(248, 214, 138, 220))
    draw.ellipse((282, 38, 448, 202), fill=(248, 222, 154, 60))
    draw.rectangle((0, 344, width, height), fill=(34, 60, 30, 255))

    heroine = fit_sprite(load_frame(SPRITES["heroine"]), 212, 212)
    owl = fit_sprite(load_frame(SPRITES["owl"]), 102, 102)
    lily = fit_sprite(load_frame(SPRITES["lily"]), 92, 116)

    composite_character(image, heroine, (140, 160), (255, 224, 152), shadow_blur=9, shadow_alpha=96, glow_blur=12)
    composite_character(image, owl, (336, 188), (196, 214, 255), shadow_blur=9, shadow_alpha=96, glow_blur=12)
    composite_character(image, lily, (82, 266), (208, 255, 214), shadow_blur=9, shadow_alpha=96, glow_blur=12)

    title_font = font(24)
    draw.text((36, 44), "FOREST", font=title_font, fill=(248, 242, 186, 255))
    draw.text((36, 82), "RUN", font=title_font, fill=(248, 242, 186, 255))
    draw.rounded_rectangle(
        (34, 120, 254, 150),
        radius=12,
        fill=(242, 233, 170, 210),
        outline=(255, 248, 220, 220),
        width=3,
    )
    draw.text((48, 130), "Mercy. Bloom. Return.", font=font(10), fill=(44, 54, 30, 255))

    destination = output_dir / "promo-square.png"
    image.convert("RGB").save(destination, format="PNG", optimize=True)
    return destination


def inspect_output(path: Path, expected_size: tuple[int, int]) -> dict[str, object]:
    if not path.is_file() or path.stat().st_size <= 0:
        raise ValueError(f"Generated graphic is missing or empty: {path}")
    try:
        with Image.open(path) as source:
            source.verify()
        with Image.open(path) as source:
            size = source.size
            mode = source.mode
    except (UnidentifiedImageError, OSError) as exc:
        raise ValueError(f"Generated graphic is unreadable: {path}: {exc}") from exc
    if size != expected_size:
        raise ValueError(
            f"Generated graphic has size {size[0]}x{size[1]}, expected "
            f"{expected_size[0]}x{expected_size[1]}: {path}"
        )
    if mode not in {"RGB", "RGBA"}:
        raise ValueError(f"Generated graphic has unsupported mode {mode}: {path}")
    return {
        "file": path.name,
        "width": size[0],
        "height": size[1],
        "mode": mode,
        "bytes": path.stat().st_size,
        "sha256": sha256(path),
    }


def source_evidence() -> list[dict[str, object]]:
    paths = [Path(__file__).resolve(), FONT_PATH]
    paths.extend(spec.path for spec in SPRITES.values())
    unique_paths = sorted(set(paths), key=lambda path: str(path.relative_to(ROOT)))
    return [
        {
            "path": str(path.relative_to(ROOT)),
            "bytes": path.stat().st_size,
            "sha256": sha256(path),
        }
        for path in unique_paths
    ]


def atomic_write_json(path: Path, payload: dict[str, object]) -> None:
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as stream:
            temporary = Path(stream.name)
            json.dump(payload, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        temporary = None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def remove_path(path: Path) -> None:
    if path.is_dir():
        shutil.rmtree(path)
    elif path.exists():
        path.unlink()


def publish_directory(staging: Path, destination: Path, backup: Path) -> None:
    remove_path(backup)
    if destination.exists():
        destination.replace(backup)
    try:
        staging.replace(destination)
    except BaseException:
        if not destination.exists() and backup.exists():
            backup.replace(destination)
        raise
    remove_path(backup)


def generate_store_assets(output_dir: Path, candidate_sha: str) -> dict[str, object]:
    validate_inputs()
    if HEX_40.fullmatch(candidate_sha.lower()) is None:
        raise ValueError("candidate_sha must be a 40-character Git SHA")
    output_dir = output_dir.expanduser().resolve()
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    staging = output_dir.parent / f".{output_dir.name}.staging"
    backup = output_dir.parent / f".{output_dir.name}.backup"
    remove_path(staging)
    staging.mkdir(parents=False, exist_ok=False)
    try:
        generated = [draw_feature_graphic(staging), draw_promo_square(staging)]
        facts = [
            inspect_output(path, EXPECTED_OUTPUTS[path.name])
            for path in generated
        ]
        if {item["file"] for item in facts} != set(EXPECTED_OUTPUTS):
            raise ValueError("Generated graphic set does not match the required output set")
        manifest = {
            "schemaVersion": SCHEMA_VERSION,
            "generatedBy": str(Path(__file__).resolve().relative_to(ROOT)),
            "candidateSha": candidate_sha.lower(),
            "sourceAssets": source_evidence(),
            "outputs": facts,
        }
        atomic_write_json(staging / "graphics_manifest.json", manifest)
        publish_directory(staging, output_dir, backup)
        return manifest
    except BaseException:
        remove_path(staging)
        raise


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate validated Forest Run store graphics")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_GRAPHICS_DIR)
    parser.add_argument("--candidate-sha")
    args = parser.parse_args()

    candidate_sha = (args.candidate_sha or current_candidate_sha()).lower()
    manifest = generate_store_assets(args.output_dir, candidate_sha)
    print(
        f"Wrote {len(manifest['outputs'])} candidate-bound store graphics "
        f"for {candidate_sha} to {args.output_dir.resolve()}"
    )


if __name__ == "__main__":
    main()
