#!/usr/bin/env python3
from __future__ import annotations

import os
import re
import subprocess
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
GRAPHICS_DIR = ROOT / "release" / "google-play" / "graphics"
METADATA_DIR = ROOT / "release" / "google-play" / "metadata" / "en-US"
SUMMARY_PATH = ROOT / "release" / "google-play" / "BUILD_SUMMARY.md"
BUILD_FILE = ROOT / "app" / "build.gradle.kts"


def require_file(path: Path) -> None:
    if not path.exists():
        raise SystemExit(f"Missing required release asset: {path.relative_to(ROOT)}")


def parse_version(build_text: str, key: str) -> str:
    match = re.search(rf'{key}\s*=\s*"([^"]+)"', build_text)
    if match:
        return match.group(1)
    match = re.search(rf"{key}\s*=\s*([0-9]+)", build_text)
    if match:
        return match.group(1)
    raise SystemExit(f"Unable to parse {key} from app/build.gradle.kts")


def verify_graphics() -> list[str]:
    results: list[str] = []
    feature = GRAPHICS_DIR / "feature-graphic.png"
    promo = GRAPHICS_DIR / "promo-square.png"
    require_file(feature)
    require_file(promo)

    feature_size = Image.open(feature).size
    promo_size = Image.open(promo).size
    if feature_size != (1024, 500):
        raise SystemExit(f"feature-graphic.png has wrong size: {feature_size}")
    if promo_size != (512, 512):
        raise SystemExit(f"promo-square.png has wrong size: {promo_size}")

    results.append(f"- Feature graphic: `{feature.relative_to(ROOT)}` `{feature_size[0]}x{feature_size[1]}`")
    results.append(f"- Promo square: `{promo.relative_to(ROOT)}` `{promo_size[0]}x{promo_size[1]}`")
    return results


def verify_metadata() -> list[str]:
    results: list[str] = []
    for rel in ("title.txt", "short-description.txt", "full-description.txt"):
        path = METADATA_DIR / rel
        require_file(path)
        text = path.read_text().strip()
        if not text:
            raise SystemExit(f"Metadata file is empty: {path.relative_to(ROOT)}")
        results.append(f"- Metadata: `{path.relative_to(ROOT)}`")
    return results


def build_release_bundle() -> Path:
    env = os.environ.copy()
    env.setdefault("JAVA_HOME", "/opt/android-studio/jbr")
    env.setdefault("GRADLE_USER_HOME", "/tmp/forest_run_gradle")
    subprocess.run(
        ["./gradlew", "bundleRelease", "--no-daemon"],
        cwd=ROOT,
        env=env,
        check=True,
    )
    bundle = ROOT / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab"
    require_file(bundle)
    return bundle


def write_summary(version_name: str, version_code: str, bundle: Path, lines: list[str]) -> None:
    screenshots_dir = ROOT / "release" / "google-play" / "screenshots" / "raw"
    screenshot_count = len(list(screenshots_dir.glob("*.png"))) if screenshots_dir.exists() else 0
    summary = [
        "# Play Release Build Summary",
        "",
        f"- Version name: `{version_name}`",
        f"- Version code: `{version_code}`",
        f"- Release bundle: `{bundle.relative_to(ROOT)}`",
        f"- Bundle size: `{bundle.stat().st_size}` bytes",
        f"- Raw screenshots present: `{screenshot_count}`",
        "",
        "## Verified Assets",
        "",
        *lines,
        "",
        "## Remaining Before Final Store Upload",
        "",
        "- Capture and curate the final screenshot set on a connected device.",
        "- Complete the final Play Console upload/release pass.",
    ]
    SUMMARY_PATH.write_text("\n".join(summary) + "\n")


def main() -> None:
    build_text = BUILD_FILE.read_text()
    version_name = parse_version(build_text, "versionName")
    version_code = parse_version(build_text, "versionCode")

    lines: list[str] = []
    lines.extend(verify_graphics())
    lines.extend(verify_metadata())
    bundle = build_release_bundle()
    write_summary(version_name, version_code, bundle, lines)
    print(f"Wrote release summary to {SUMMARY_PATH}")


if __name__ == "__main__":
    main()
