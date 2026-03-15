#!/usr/bin/env python3
from __future__ import annotations

from collections import Counter, deque
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "Final_Assets (2)"
DST = ROOT / "app" / "src" / "main" / "assets" / "sprites"


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
    pixels = list(frame.getdata())
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
    pixels = list(image.getdata())
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
    original = list(image.getdata())
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
    pixels = list(image.getdata())
    for y in range(cutoff, image.height):
        row_start = y * image.width
        for x in range(image.width):
            idx = row_start + x
            pixels[idx] = (0, 0, 0, 0)
    out = Image.new("RGBA", image.size)
    out.putdata(pixels)
    return out


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


def process_frame(frame: Image.Image, *, strategy: str, remove_support_ratio: float, component_floor_ratio: float) -> Image.Image:
    if strategy == "char":
        return strip_light_neutral_background(frame)
    cleaned = flood_fill_background(frame)
    cleaned = clear_bottom_band(cleaned, remove_support_ratio)
    cleaned = keep_subject_components(cleaned, component_floor_ratio=component_floor_ratio)
    return crop_alpha(cleaned)


def slice_grid(mapping: Mapping) -> list[Image.Image]:
    src = Image.open(SRC / mapping.src_rel).convert("RGBA")
    frame_w = src.width // mapping.cols
    frame_h = src.height // mapping.rows
    frame_indices = mapping.explicit_indices or tuple(range(mapping.count))

    frames: list[Image.Image] = []
    for index in frame_indices:
        x = (index % mapping.cols) * frame_w
        y = (index // mapping.cols) * frame_h
        frame = src.crop((x, y, x + frame_w, y + frame_h))
        frames.append(
            process_frame(
                frame,
                strategy=mapping.strategy,
                remove_support_ratio=mapping.remove_support_ratio,
                component_floor_ratio=mapping.component_floor_ratio,
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
    out_path.parent.mkdir(parents=True, exist_ok=True)
    strip.save(out_path)


def import_one(mapping: Mapping) -> None:
    frames = slice_grid(mapping)
    pack_strip(frames, DST / mapping.dst_rel)


def main() -> None:
    mappings = [
        Mapping("char/runner_girl_technical_48frame.png", "char/runner_girl_technical_48frame.png", 8, 6, 48, strategy="char", component_floor_ratio=0.02),
        Mapping("char/runner_girl_jump_48frame.png", "char/runner_girl_jump_48frame.png", 8, 6, 48, strategy="char", component_floor_ratio=0.02),
        Mapping("char/runner_girl_duck_48frame.png", "char/runner_girl_duck_48frame.png", 8, 6, 48, strategy="char", component_floor_ratio=0.02),
        Mapping("char/runner_girl_hit_sequence.png", "char/runner_girl_hit_sequence.png", 4, 3, 12, strategy="char", component_floor_ratio=0.02),
        Mapping("char/runner_girl_death_sequence.png", "char/runner_girl_death_sequence.png", 4, 3, 12, strategy="char", component_floor_ratio=0.02),
        Mapping("plants/cactus/cactus_4frames.png", "plants/cactus_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("plants/eucalyptus/eucalyptus_4frames.png", "plants/eucalyptus_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("plants/hyacinth/hyacinth_4frames.png", "plants/hyacinth_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("plants/lily_of_the_valley/lily_of_the_valley_4frames.png", "plants/lily_of_valley_4frames.png", 2, 2, 4, component_floor_ratio=0.04),
        Mapping("plants/vanilla/vanilla_4frames.png", "plants/vanilla_orchid_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("trees/bamboos/bamboos_4frames.png", "trees/bamboo_4frames.png", 4, 1, 4, component_floor_ratio=0.03),
        Mapping("trees/cherry_blossoms/cherry_blossoms_4frames.png", "trees/cherry_blossom_4frames.png", 2, 2, 4, component_floor_ratio=0.03),
        Mapping("trees/jacaranda/jacaranda_4frames.png", "trees/jacaranda_4frames.png", 2, 2, 4, component_floor_ratio=0.03),
        Mapping("trees/weeping_willow/weeping_willow_4frames.png", "trees/weeping_willow_4frames.png", 2, 2, 4, component_floor_ratio=0.03),
        Mapping("animals/cats/cats_4frames.png", "animals/cat_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("animals/dogs/dogs_4frames.png", "animals/dog_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("animals/fox/fox_4frames.png", "animals/fox_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("animals/hedgehogs/hedgehogs_4frames.png", "animals/hedgehog_4frames.png", 4, 1, 4, component_floor_ratio=0.05),
        Mapping("animals/wolves/wolves_4frames.png", "animals/wolf_4frames.png", 4, 2, 8, component_floor_ratio=0.05),
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
    for mapping in mappings:
        import_one(mapping)


if __name__ == "__main__":
    main()
