#!/usr/bin/env python3
from __future__ import annotations

from collections import Counter, deque
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from typing import Iterable

from PIL import Image

from stable_file_reader import StableFileError, atomic_write_bytes, ensure_directory_exists


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "Final_Assets (2)"
DST = ROOT / "app" / "src" / "main" / "assets" / "sprites"


def _rgba_pixels(image: Image.Image) -> list[tuple[int, int, int, int]]:
    raw = image.tobytes()
    return [tuple(raw[offset:offset + 4]) for offset in range(0, len(raw), 4)]


@dataclass(frozen=True)
class Mapping:
    src_rel: str
    dst_rel: str
    cols: int
    rows: int
    count: int
    strategy: str = "default"
    remove_support_ratio: float = 0.0
    component_floor_ratio: float = 0.08
    explicit_indices: tuple[int, ...] | None = None
    annotation_policy: str = "none"
    row_columns: tuple[int, ...] | None = None


def quantize(color: tuple[int, int, int, int]) -> tuple[int, int, int]:
    return (color[0] // 16, color[1] // 16, color[2] // 16)


def background_palette(frame: Image.Image) -> set[tuple[int, int, int]]:
    px = frame.load()
    w, h = frame.size
    samples: list[tuple[int, int, int]] = []
    for x in range(w):
        samples.append(quantize(px[x, 0]))
        samples.append(quantize(px[x, h - 1]))
    for y in range(h):
        samples.append(quantize(px[0, y]))
        samples.append(quantize(px[w - 1, y]))
    return {color for color, _count in Counter(samples).most_common(32)}


def near_any(color: tuple[int, int, int, int], palette: set[tuple[int, int, int]], tolerance: int = 1) -> bool:
    if color[3] < 250:
        return False
    qr, qg, qb = quantize(color)
    for pr, pg, pb in palette:
        if abs(qr - pr) <= tolerance and abs(qg - pg) <= tolerance and abs(qb - pb) <= tolerance:
            return True
    return False


def crop_alpha(image: Image.Image, pad: int = 4) -> Image.Image:
    image = image.convert("RGBA")
    bbox = image.getbbox()
    if bbox is None:
        return image
    left, top, right, bottom = bbox
    return image.crop((
        max(0, left - pad),
        max(0, top - pad),
        min(image.width, right + pad),
        min(image.height, bottom + pad),
    ))


def flood_fill_background(frame: Image.Image, tolerance: int = 1) -> Image.Image:
    frame = frame.convert("RGBA")
    palette = background_palette(frame)
    w, h = frame.size
    pixels = _rgba_pixels(frame)
    visited = [False] * (w * h)
    q: deque[int] = deque()

    def enqueue(x: int, y: int) -> None:
        if x < 0 or y < 0 or x >= w or y >= h:
            return
        idx = y * w + x
        if visited[idx]:
            return
        visited[idx] = True
        if near_any(pixels[idx], palette, tolerance=tolerance):
            pixels[idx] = (0, 0, 0, 0)
            q.append(idx)

    for x in range(w):
        enqueue(x, 0)
        enqueue(x, h - 1)
    for y in range(h):
        enqueue(0, y)
        enqueue(w - 1, y)

    while q:
        idx = q.popleft()
        x = idx % w
        y = idx // w
        enqueue(x - 1, y)
        enqueue(x + 1, y)
        enqueue(x, y - 1)
        enqueue(x, y + 1)

    out = Image.new("RGBA", frame.size)
    out.putdata(pixels)
    return out


def connected_components(image: Image.Image) -> list[tuple[list[int], tuple[int, int, int, int]]]:
    image = image.convert("RGBA")
    w, h = image.size
    pixels = _rgba_pixels(image)
    visited = [False] * (w * h)
    components: list[tuple[list[int], tuple[int, int, int, int]]] = []

    for start in range(w * h):
        if visited[start] or pixels[start][3] == 0:
            continue
        stack = [start]
        visited[start] = True
        component: list[int] = []
        min_x = w
        min_y = h
        max_x = 0
        max_y = 0

        while stack:
            idx = stack.pop()
            component.append(idx)
            x = idx % w
            y = idx // w
            min_x = min(min_x, x)
            min_y = min(min_y, y)
            max_x = max(max_x, x)
            max_y = max(max_y, y)
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if nx < 0 or ny < 0 or nx >= w or ny >= h:
                    continue
                nidx = ny * w + nx
                if visited[nidx] or pixels[nidx][3] == 0:
                    continue
                visited[nidx] = True
                stack.append(nidx)

        components.append((component, (min_x, min_y, max_x + 1, max_y + 1)))
    return components


def keep_subject_components(image: Image.Image, component_floor_ratio: float = 0.08) -> Image.Image:
    image = image.convert("RGBA")
    components = connected_components(image)
    if not components:
        return image

    largest_area = max(len(component) for component, _bbox in components)
    main_component, main_bbox = max(components, key=lambda entry: len(entry[0]))
    keep: list[tuple[list[int], tuple[int, int, int, int]]] = [(main_component, main_bbox)]
    expanded_bbox = list(main_bbox)
    margin = max(10, int(max(image.size) * 0.06))

    changed = True
    while changed:
        changed = False
        for component, bbox in components:
            if any(component is existing[0] for existing in keep):
                continue
            area = len(component)
            close_enough = not (
                bbox[2] < expanded_bbox[0] - margin or
                bbox[0] > expanded_bbox[2] + margin or
                bbox[3] < expanded_bbox[1] - margin or
                bbox[1] > expanded_bbox[3] + margin
            )
            substantial = area >= largest_area * component_floor_ratio
            if close_enough or substantial:
                keep.append((component, bbox))
                expanded_bbox[0] = min(expanded_bbox[0], bbox[0])
                expanded_bbox[1] = min(expanded_bbox[1], bbox[1])
                expanded_bbox[2] = max(expanded_bbox[2], bbox[2])
                expanded_bbox[3] = max(expanded_bbox[3], bbox[3])
                changed = True

    pixels = [(0, 0, 0, 0)] * (image.width * image.height)
    original = _rgba_pixels(image)
    for component, _bbox in keep:
        for idx in component:
            pixels[idx] = original[idx]

    out = Image.new("RGBA", image.size)
    out.putdata(pixels)
    return crop_alpha(out)


def clear_bottom_band(image: Image.Image, ratio: float) -> Image.Image:
    if ratio <= 0.0:
        return image
    image = image.convert("RGBA")
    cutoff = int(image.height * (1.0 - ratio))
    pixels = _rgba_pixels(image)
    for y in range(cutoff, image.height):
        row_start = y * image.width
        for x in range(image.width):
            idx = row_start + x
            pixels[idx] = (0, 0, 0, 0)
    out = Image.new("RGBA", image.size)
    out.putdata(pixels)
    return out


def _is_top_index_annotation(
    image: Image.Image,
    component: list[int],
    bbox: tuple[int, int, int, int],
) -> bool:
    width, height = image.size
    x1, y1, x2, y2 = bbox
    area = len(component)
    component_width = x2 - x1
    component_height = y2 - y1
    touches_edge_guide = (
        (y1 <= 1 or y2 >= height - 1)
        and 1 <= area <= 200
        and 3 <= component_width <= min(50, int(width * 0.65))
        and 1 <= component_height <= 3
    )
    detached_grid_stroke = (
        1 <= area <= 250
        and component_height <= 12
        and component_width >= max(6, component_height * 2)
    )
    clipped_edge_fragment = (
        (x1 <= 1 or x2 >= width - 1)
        and 1 <= area <= 300
        and component_width <= 24
        and component_height <= 30
    )
    return (
        touches_edge_guide
        or detached_grid_stroke
        or clipped_edge_fragment
        or (
            y1 <= int(height * 0.28)
            # Glyphs can be colored and fragment into tiny disconnected
            # strokes after background removal. Keep this limited to small,
            # detached components in the upper annotation gutter; pose pixels
            # connected to the character are preserved.
            and 1 <= area <= 250
            and 1 <= component_width <= min(30, int(width * 0.35))
            and 1 <= component_height <= 30
            and y2 <= int(height * 0.26)
        )
    )


def _is_bottom_index_annotation(
    image: Image.Image,
    component: list[int],
    bbox: tuple[int, int, int, int],
) -> bool:
    _width, height = image.size
    _x1, y1, x2, y2 = bbox
    area = len(component)
    component_width = x2 - _x1
    component_height = y2 - y1
    return (
        y1 >= int(height * 0.62)
        and 70 <= area <= 700
        and 6 <= component_width <= 32
        and 10 <= component_height <= 40
    )


def remove_frame_index_annotations(image: Image.Image, policy: str) -> Image.Image:
    """Remove detached atlas index glyphs while preserving authored sprite pixels.

    The jump atlas contains small detached labels and grid remnants around its
    poses. The Hyacinth and Vanilla Orchid atlases contain one number below
    each plant. Policies are attached only to those mappings, and remove whole
    disconnected components matching the corresponding annotation region.
    """
    if policy == "none":
        return image
    if policy not in {"top_index", "bottom_index"}:
        raise ValueError(f"Unsupported frame-index annotation policy: {policy}")

    source = image.convert("RGBA")
    components = connected_components(source)
    remove_indices: set[int] = set()
    pixels = _rgba_pixels(source)
    matcher = _is_top_index_annotation if policy == "top_index" else _is_bottom_index_annotation
    for component, bbox in components:
        if matcher(source, component, bbox):
            remove_indices.update(component)
    if not remove_indices:
        return source

    for index in remove_indices:
        pixels[index] = (0, 0, 0, 0)
    result = Image.new("RGBA", source.size)
    result.putdata(pixels)
    return result


def strip_light_neutral_background(frame: Image.Image) -> Image.Image:
    frame = frame.convert("RGBA")
    out = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    src = frame.load()
    dst = out.load()
    for y in range(frame.height):
        for x in range(frame.width):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            mx = max(r, g, b)
            mn = min(r, g, b)
            avg = (r + g + b) / 3
            if avg > 180 and mx - mn < 40:
                continue
            dst[x, y] = (r, g, b, a)
    return crop_alpha(out, pad=2)


def process_frame(
    frame: Image.Image,
    *,
    strategy: str,
    remove_support_ratio: float,
    component_floor_ratio: float,
    annotation_policy: str = "none",
) -> Image.Image:
    if strategy == "char":
        cleaned = strip_light_neutral_background(frame)
        return remove_frame_index_annotations(cleaned, annotation_policy)
    cleaned = flood_fill_background(frame)
    cleaned = clear_bottom_band(cleaned, remove_support_ratio)
    cleaned = keep_subject_components(cleaned, component_floor_ratio=component_floor_ratio)
    return remove_frame_index_annotations(cleaned, annotation_policy)


def slice_grid(mapping: Mapping) -> list[Image.Image]:
    if mapping.cols <= 0 or mapping.rows <= 0 or mapping.count <= 0:
        raise ValueError("Sprite grid rows, columns, and frame count must be positive")
    if mapping.strategy not in {"default", "char"}:
        raise ValueError(f"Unsupported sprite cleanup strategy: {mapping.strategy}")
    if mapping.annotation_policy not in {"none", "top_index", "bottom_index"}:
        raise ValueError(
            f"Unsupported frame-index annotation policy: {mapping.annotation_policy}"
        )
    if mapping.row_columns is not None:
        if len(mapping.row_columns) != mapping.rows:
            raise ValueError("row_columns must provide one column count per row")
        if any(columns <= 0 for columns in mapping.row_columns):
            raise ValueError("row_columns entries must be positive")
        if max(mapping.row_columns) > mapping.cols:
            raise ValueError("row_columns entries cannot exceed the declared maximum columns")
        if sum(mapping.row_columns) != mapping.count:
            raise ValueError("row_columns must sum to the declared frame count")
    if not 0.0 <= mapping.remove_support_ratio < 1.0:
        raise ValueError("remove_support_ratio must be in [0, 1)")
    if not 0.0 <= mapping.component_floor_ratio <= 1.0:
        raise ValueError("component_floor_ratio must be in [0, 1]")

    with Image.open(SRC / mapping.src_rel) as source:
        src = source.convert("RGBA")
    if src.width <= 0 or src.height <= 0:
        raise ValueError(f"Sprite sheet must be non-empty: {mapping.src_rel}")
    if mapping.cols > src.width or mapping.rows > src.height:
        raise ValueError(
            f"Sprite grid {mapping.cols}x{mapping.rows} exceeds sheet dimensions "
            f"{src.width}x{src.height}: {mapping.src_rel}"
        )
    if mapping.row_columns is not None and mapping.explicit_indices is not None:
        raise ValueError("explicit_indices cannot be combined with row_columns")
    if mapping.row_columns is not None:
        frames: list[Image.Image] = []
        for row, columns in enumerate(mapping.row_columns):
            top = row * src.height // mapping.rows
            bottom = (row + 1) * src.height // mapping.rows
            for column in range(columns):
                left = column * src.width // columns
                right = (column + 1) * src.width // columns
                frame = src.crop((left, top, right, bottom))
                frames.append(
                    process_frame(
                        frame,
                        strategy=mapping.strategy,
                        remove_support_ratio=mapping.remove_support_ratio,
                        component_floor_ratio=mapping.component_floor_ratio,
                        annotation_policy=mapping.annotation_policy,
                    )
                )
        return frames

    frame_indices = (
        tuple(range(mapping.count))
        if mapping.explicit_indices is None
        else mapping.explicit_indices
    )
    if len(frame_indices) != mapping.count:
        raise ValueError(
            f"Expected {mapping.count} frame indices, found {len(frame_indices)}"
        )
    if len(set(frame_indices)) != len(frame_indices):
        raise ValueError("Sprite frame indices must be unique")
    if any(index < 0 or index >= mapping.cols * mapping.rows for index in frame_indices):
        raise ValueError("Sprite frame index is outside the source grid")

    frames: list[Image.Image] = []
    for index in frame_indices:
        column = index % mapping.cols
        row = index // mapping.cols
        left = column * src.width // mapping.cols
        right = (column + 1) * src.width // mapping.cols
        top = row * src.height // mapping.rows
        bottom = (row + 1) * src.height // mapping.rows
        frame = src.crop((left, top, right, bottom))
        frames.append(
            process_frame(
                frame,
                strategy=mapping.strategy,
                remove_support_ratio=mapping.remove_support_ratio,
                component_floor_ratio=mapping.component_floor_ratio,
                annotation_policy=mapping.annotation_policy,
            )
        )
    return frames


def pack_strip(frames: Iterable[Image.Image], out_path: Path) -> None:
    frames = list(frames)
    max_w = max(frame.width for frame in frames)
    max_h = max(frame.height for frame in frames)
    strip = Image.new("RGBA", (max_w * len(frames), max_h), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        x = index * max_w + (max_w - frame.width) // 2
        y = max_h - frame.height
        strip.alpha_composite(frame, (x, y))
    buffer = BytesIO()
    strip.save(buffer, format="PNG")
    try:
        ensure_directory_exists(out_path.parent, "sprite asset parent directory")
        atomic_write_bytes(out_path, buffer.getvalue(), reject_symlink_leaf=True)
    except StableFileError as exc:
        raise SystemExit(f"could not publish sprite asset {out_path}: {exc}") from exc


def import_one(mapping: Mapping) -> None:
    frames = slice_grid(mapping)
    pack_strip(frames, DST / mapping.dst_rel)


def asset_mappings() -> list[Mapping]:
    return [
        Mapping("char/runner_girl_technical_48frame.png", "char/runner_girl_technical_48frame.png", 8, 6, 48, strategy="char", component_floor_ratio=0.02),
        Mapping("char/runner_girl_jump_48frame.png", "char/runner_girl_jump_42frame.png", 8, 6, 42, strategy="char", component_floor_ratio=0.02, annotation_policy="top_index", row_columns=(6, 6, 6, 8, 8, 8)),
        # The supplied duck atlas is a regular 6x6 grid despite its legacy
        # source filename. Preserve its 36 authored poses and publish an
        # accurately named runtime strip.
        Mapping("char/runner_girl_duck_48frame.png", "char/runner_girl_duck_36frame.png", 6, 6, 36, strategy="char", component_floor_ratio=0.02, annotation_policy="top_index"),
        Mapping("char/runner_girl_hit_sequence.png", "char/runner_girl_hit_sequence.png", 4, 3, 12, strategy="char", component_floor_ratio=0.02),
        Mapping("char/runner_girl_death_sequence.png", "char/runner_girl_death_sequence.png", 4, 3, 12, strategy="char", component_floor_ratio=0.02),
        Mapping("plants/cactus/cactus_4frames.png", "plants/cactus_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("plants/eucalyptus/eucalyptus_4frames.png", "plants/eucalyptus_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("plants/hyacinth/hyacinth_4frames.png", "plants/hyacinth_4frames.png", 4, 1, 4, component_floor_ratio=0.05, annotation_policy="bottom_index"),
        Mapping("plants/lily_of_the_valley/lily_of_the_valley_4frames.png", "plants/lily_of_valley_4frames.png", 2, 2, 4, component_floor_ratio=0.04),
        Mapping("plants/vanilla/vanilla_4frames.png", "plants/vanilla_orchid_4frames.png", 4, 1, 4, component_floor_ratio=0.05, annotation_policy="bottom_index"),
        Mapping("trees/bamboos/bamboos_4frames.png", "trees/bamboo_4frames.png", 4, 1, 4, component_floor_ratio=0.03),
        Mapping("trees/cherry_blossoms/cherry_blossoms_4frames.png", "trees/cherry_blossom_4frames.png", 2, 2, 4, component_floor_ratio=0.03),
        Mapping("trees/jacaranda/jacaranda_4frames.png", "trees/jacaranda_4frames.png", 2, 2, 4, component_floor_ratio=0.03),
        Mapping("trees/weeping_willow/weeping_willow_4frames.png", "trees/weeping_willow_4frames.png", 2, 2, 4, component_floor_ratio=0.03),
        Mapping("animals/cats/cats_4frames.png", "animals/cat_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("animals/dogs/dogs_4frames.png", "animals/dog_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("animals/fox/fox_4frames.png", "animals/fox_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("animals/hedgehogs/hedgehogs_4frames.png", "animals/hedgehog_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("animals/wolves/wolves_4frames.png", "animals/wolf_8frames.png", 4, 2, 8, component_floor_ratio=0.05),
        Mapping("birds/ducks/ducks_4frames.png", "birds/duck_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("birds/ducks/ducks_4frames.png", "birds/duck_flying.png", 4, 1, 4, remove_support_ratio=0.24, component_floor_ratio=0.05),
        Mapping("birds/tits/tits_4frames.png", "birds/tit_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("birds/tits/tits_4frames.png", "birds/tit_flying.png", 4, 1, 4, remove_support_ratio=0.20, component_floor_ratio=0.05),
        Mapping("birds/chickadees/chickadees_4frames.png", "birds/chickadee_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("birds/chickadees/chickadees_4frames.png", "birds/chickadee_flying.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("birds/owls/owls_4frames.png", "birds/owl_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("birds/owls/owls_4frames.png", "birds/owl_flying.png", 4, 1, 4, remove_support_ratio=0.18, component_floor_ratio=0.05),
        Mapping("birds/eagles/eagles_4frames.png", "birds/eagle_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("birds/eagles/eagles_4frames.png", "birds/eagle_flying.png", 4, 1, 4, remove_support_ratio=0.22, component_floor_ratio=0.05),
        Mapping("vfx/vfx_jump_dust.png", "vfx/vfx_jump_dust.png", 4, 1, 4, component_floor_ratio=0.03),
        Mapping("vfx/vfx_slide_grass.png", "vfx/vfx_slide_grass.png", 4, 4, 16, component_floor_ratio=0.02),
    ]


def main() -> None:
    for mapping in asset_mappings():
        import_one(mapping)


if __name__ == "__main__":
    main()
