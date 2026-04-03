#!/usr/bin/env python3
import json
import shutil
import struct
import sys
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parent.parent
SCREENSHOT_DIR = ROOT_DIR / "release" / "google-play" / "screenshots"
RAW_DIR = SCREENSHOT_DIR / "raw"
FINAL_DIR = SCREENSHOT_DIR / "final"
MANIFEST_PATH = SCREENSHOT_DIR / "curation_manifest.json"
SUMMARY_PATH = SCREENSHOT_DIR / "CURATED_SET.md"


def read_png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as fh:
        signature = fh.read(8)
        if signature != b"\x89PNG\r\n\x1a\n":
            raise ValueError(f"{path} is not a PNG file")
        chunk_len = struct.unpack(">I", fh.read(4))[0]
        chunk_type = fh.read(4)
        if chunk_type != b"IHDR":
            raise ValueError(f"{path} is missing IHDR chunk")
        if chunk_len != 13:
            raise ValueError(f"{path} has invalid IHDR length")
        width, height = struct.unpack(">II", fh.read(8))
        return width, height


def load_manifest() -> dict:
    try:
        return json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise SystemExit(f"Missing manifest: {MANIFEST_PATH}") from exc


def validate_manifest(manifest: dict) -> list[dict]:
    items = manifest.get("screenshots")
    if not isinstance(items, list) or not items:
        raise SystemExit("Manifest must define a non-empty screenshots list")

    seen_targets = set()
    for item in items:
        required = [
            "order",
            "raw_file",
            "final_file",
            "scenario",
            "title",
            "purpose",
        ]
        missing = [key for key in required if key not in item]
        if missing:
            raise SystemExit(f"Manifest entry missing keys {missing}: {item}")

        target = item["final_file"]
        if target in seen_targets:
            raise SystemExit(f"Duplicate final filename in manifest: {target}")
        seen_targets.add(target)

    return sorted(items, key=lambda entry: entry["order"])


def curate(items: list[dict]) -> tuple[list[tuple[dict, int, int]], int]:
    FINAL_DIR.mkdir(parents=True, exist_ok=True)
    copied = []
    expected_dimensions = None

    for item in items:
        raw_path = RAW_DIR / item["raw_file"]
        if not raw_path.exists():
            raise SystemExit(f"Missing raw screenshot: {raw_path}")

        width, height = read_png_size(raw_path)
        if expected_dimensions is None:
            expected_dimensions = (width, height)
        elif (width, height) != expected_dimensions:
            raise SystemExit(
                "Screenshot dimensions do not match: "
                f"{raw_path} is {width}x{height}, expected "
                f"{expected_dimensions[0]}x{expected_dimensions[1]}"
            )

        target_path = FINAL_DIR / item["final_file"]
        shutil.copy2(raw_path, target_path)
        copied.append((item, width, height))

    return copied, len(copied)


def write_summary(copied: list[tuple[dict, int, int]], count: int) -> None:
    lines = [
        "# Curated Screenshot Set",
        "",
        f"- Source manifest: `{MANIFEST_PATH.relative_to(ROOT_DIR)}`",
        f"- Raw source directory: `{RAW_DIR.relative_to(ROOT_DIR)}`",
        f"- Final output directory: `{FINAL_DIR.relative_to(ROOT_DIR)}`",
        f"- Curated screenshots: `{count}`",
        "",
        "## Final Set",
        "",
    ]

    for item, width, height in copied:
        lines.extend(
            [
                f"### {item['order']}. {item['title']}",
                "",
                f"- Scenario: `{item['scenario']}`",
                f"- Raw file: `{item['raw_file']}`",
                f"- Final file: `{item['final_file']}`",
                f"- Size: `{width}x{height}`",
                f"- Purpose: {item['purpose']}",
                "",
            ]
        )

    SUMMARY_PATH.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    manifest = load_manifest()
    items = validate_manifest(manifest)
    copied, count = curate(items)
    write_summary(copied, count)
    print(f"Curated {count} screenshots into {FINAL_DIR}")
    print(f"Wrote summary to {SUMMARY_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
