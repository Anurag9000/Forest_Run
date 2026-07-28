#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

try:
    from PIL import Image, UnidentifiedImageError
except ImportError as exc:
    raise SystemExit(
        "Pillow is required. Install scripts/requirements.txt before preparing a release."
    ) from exc

ROOT = Path(__file__).resolve().parent.parent
RELEASE_ROOT = ROOT / "release" / "google-play"
GRAPHICS_DIR = RELEASE_ROOT / "graphics"
METADATA_DIR = RELEASE_ROOT / "metadata" / "en-US"
FINAL_SCREENSHOTS_DIR = RELEASE_ROOT / "screenshots" / "final"
SUMMARY_PATH = RELEASE_ROOT / "BUILD_SUMMARY.md"
MACHINE_SUMMARY_PATH = RELEASE_ROOT / "build_summary.json"
BUILD_FILE = ROOT / "app" / "build.gradle.kts"
MANIFEST_FILE = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"

PLACEHOLDER_APPLICATION_IDS = {
    "com.yourname.forest_run",
    "com.example.forest_run",
    "com.example.forestrun",
}

REQUIRED_AUDIO = (
    "sfx_jump",
    "sfx_land",
    "sfx_seed_ping",
    "sfx_bark",
    "sfx_screech",
    "sfx_howl",
    "sfx_bloom",
    "sfx_mercy_miss",
    "sfx_hit",
    "music_garden",
    "music_run_1",
    "music_run_2",
    "music_run_3",
    "music_bloom",
    "music_rest",
)


def fail(message: str) -> "NoReturn":
    raise SystemExit(message)


def require_file(path: Path, label: str = "required file") -> Path:
    if not path.is_file():
        fail(f"Missing {label}: {path.relative_to(ROOT)}")
    if path.stat().st_size == 0:
        fail(f"Empty {label}: {path.relative_to(ROOT)}")
    return path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_assignment(build_text: str, key: str) -> str:
    patterns = (
        rf'\b{re.escape(key)}\s*=\s*"([^"]+)"',
        rf"\b{re.escape(key)}\s*=\s*([0-9]+)",
    )
    for pattern in patterns:
        match = re.search(pattern, build_text)
        if match:
            return match.group(1)
    fail(f"Unable to parse {key} from app/build.gradle.kts")


def inspect_png(path: Path, expected_size: tuple[int, int] | None = None) -> dict:
    require_file(path, "PNG asset")
    try:
        with Image.open(path) as source:
            source.verify()
        with Image.open(path) as source:
            width, height = source.size
            mode = source.mode
    except (UnidentifiedImageError, OSError) as exc:
        fail(f"Invalid PNG {path.relative_to(ROOT)}: {exc}")

    if expected_size is not None and (width, height) != expected_size:
        fail(
            f"{path.relative_to(ROOT)} has size {width}x{height}; "
            f"expected {expected_size[0]}x{expected_size[1]}"
        )
    return {
        "path": str(path.relative_to(ROOT)),
        "width": width,
        "height": height,
        "mode": mode,
        "bytes": path.stat().st_size,
        "sha256": sha256(path),
    }


def verify_project_identity(build_text: str, allow_placeholder_id: bool) -> dict:
    application_id = parse_assignment(build_text, "applicationId")
    version_name = parse_assignment(build_text, "versionName")
    version_code = int(parse_assignment(build_text, "versionCode"))
    compile_sdk = int(parse_assignment(build_text, "compileSdk"))
    target_sdk = int(parse_assignment(build_text, "targetSdk"))
    min_sdk = int(parse_assignment(build_text, "minSdk"))

    if application_id in PLACEHOLDER_APPLICATION_IDS and not allow_placeholder_id:
        fail(
            f"Refusing release with placeholder applicationId={application_id}. "
            "Choose the final package identity or pass --allow-placeholder-id for a non-upload dry run."
        )
    if version_code < 1:
        fail("versionCode must be positive")
    if not re.fullmatch(r"[0-9]+(?:\.[0-9A-Za-z-]+)+", version_name):
        fail(f"versionName does not look release-ready: {version_name!r}")
    if min_sdk > target_sdk or target_sdk > compile_sdk:
        fail(
            f"Invalid SDK ordering: minSdk={min_sdk}, targetSdk={target_sdk}, compileSdk={compile_sdk}"
        )

    require_file(MANIFEST_FILE, "Android manifest")
    return {
        "application_id": application_id,
        "version_name": version_name,
        "version_code": version_code,
        "min_sdk": min_sdk,
        "target_sdk": target_sdk,
        "compile_sdk": compile_sdk,
    }


def read_external_gradle_properties() -> dict[str, str]:
    values: dict[str, str] = {}
    for path in (ROOT / "gradle.properties", Path.home() / ".gradle" / "gradle.properties"):
        if not path.is_file():
            continue
        for raw_line in path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def verify_release_signing(build_text: str, allow_unsigned: bool) -> None:
    release_block = re.search(r"release\s*\{(?P<body>.*?)\n\s*\}", build_text, re.DOTALL)
    has_signing_reference = bool(
        release_block
        and re.search(r"\bsigningConfig\b", release_block.group("body"))
    )
    has_signing_declaration = "signingConfigs" in build_text
    if not (has_signing_reference and has_signing_declaration):
        if allow_unsigned:
            return
        fail(
            "No explicit release signing configuration was found. Configure an upload/release key "
            "outside source control, or pass --allow-unsigned for a non-upload dry run."
        )

    properties = read_external_gradle_properties()
    required = (
        "FOREST_RUN_KEYSTORE",
        "FOREST_RUN_STORE_PASSWORD",
        "FOREST_RUN_KEY_ALIAS",
        "FOREST_RUN_KEY_PASSWORD",
    )
    missing = [name for name in required if not (os.environ.get(name) or properties.get(name))]
    if missing and not allow_unsigned:
        fail(
            "Release signing is configured, but credentials are missing:\n- "
            + "\n- ".join(missing)
            + "\nProvide them as environment variables or external Gradle properties, "
            "or pass --allow-unsigned for a non-upload dry run."
        )


def verify_graphics() -> list[dict]:
    feature = inspect_png(GRAPHICS_DIR / "feature-graphic.png", (1024, 500))
    promo = inspect_png(GRAPHICS_DIR / "promo-square.png", (512, 512))

    manifest_path = require_file(
        GRAPHICS_DIR / "graphics_manifest.json",
        "generated graphics manifest",
    )
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"Invalid graphics manifest: {exc}")

    expected_hashes = {
        item.get("file"): item.get("sha256")
        for item in manifest.get("outputs", [])
        if isinstance(item, dict)
    }
    for facts in (feature, promo):
        filename = Path(facts["path"]).name
        expected = expected_hashes.get(filename)
        if expected != facts["sha256"]:
            fail(
                f"Graphics manifest hash mismatch for {filename}; regenerate store assets before release"
            )
    return [feature, promo]


def verify_metadata() -> list[dict]:
    limits = {
        "title.txt": 80,
        "short-description.txt": 160,
        "full-description.txt": 10_000,
    }
    results: list[dict] = []
    for filename, safety_limit in limits.items():
        path = require_file(METADATA_DIR / filename, "store metadata")
        text = path.read_text(encoding="utf-8").strip()
        if not text:
            fail(f"Metadata file is empty: {path.relative_to(ROOT)}")
        if len(text) > safety_limit:
            fail(
                f"Metadata is unexpectedly long ({len(text)} chars): {path.relative_to(ROOT)}. "
                "Revalidate current store limits before upload."
            )
        if "TODO" in text.upper() or "YOURNAME" in text.upper():
            fail(f"Placeholder/TODO text remains in {path.relative_to(ROOT)}")
        results.append(
            {
                "path": str(path.relative_to(ROOT)),
                "characters": len(text),
                "sha256": sha256(path),
            }
        )
    return results


def verify_screenshots() -> list[dict]:
    if not FINAL_SCREENSHOTS_DIR.is_dir():
        fail(
            "Curated screenshot directory is missing. Run capture_store_screenshots.sh and "
            "curate_store_screenshots.py first."
        )

    paths = sorted(FINAL_SCREENSHOTS_DIR.glob("*.png"))
    if len(paths) < 4:
        fail(f"At least 4 curated screenshots are required; found {len(paths)}")

    results = [inspect_png(path) for path in paths]
    dimensions = {(item["width"], item["height"]) for item in results}
    if len(dimensions) != 1:
        fail(f"Curated screenshots use mixed dimensions: {sorted(dimensions)}")
    for item in results:
        if item["width"] <= item["height"]:
            fail(f"Curated screenshot is not landscape: {item['path']}")

    hashes = [item["sha256"] for item in results]
    if len(hashes) != len(set(hashes)):
        fail("Curated screenshot set contains exact duplicates")

    require_file(
        RELEASE_ROOT / "screenshots" / "CURATED_SET.md",
        "curated screenshot report",
    )
    return results


def verify_audio_assets() -> list[str]:
    raw_dir = ROOT / "app" / "src" / "main" / "res" / "raw"
    if not raw_dir.is_dir():
        fail(f"Missing Android raw-resource directory: {raw_dir.relative_to(ROOT)}")

    available = {path.stem for path in raw_dir.iterdir() if path.is_file()}
    missing = [name for name in REQUIRED_AUDIO if name not in available]
    if missing:
        fail("Missing required audio resources:\n- " + "\n- ".join(missing))
    return sorted(REQUIRED_AUDIO)


def run_gradle(tasks: list[str]) -> None:
    wrapper = require_file(ROOT / "gradlew", "Gradle wrapper")
    java = shutil.which("java")
    if java is None:
        fail("Java 21 is unavailable: set JAVA_HOME or add a Java 21 runtime to PATH")

    version_result = subprocess.run(
        [java, "-version"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    version_text = version_result.stderr or version_result.stdout
    version_match = re.search(r'version "(?:1\.)?(\d+)', version_text)
    if not version_match or int(version_match.group(1)) < 21:
        fail(f"Java 21 or newer is required for the API 36 test gate; found: {version_text.splitlines()[0] if version_text else 'unknown'}")

    command = ["bash", str(wrapper), *tasks, "--no-daemon", "--stacktrace"]
    print("Running:", " ".join(command))
    subprocess.run(command, cwd=ROOT, env=os.environ.copy(), check=True)


def verify_bundle() -> dict:
    bundle = require_file(
        ROOT / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab",
        "release bundle",
    )
    return {
        "path": str(bundle.relative_to(ROOT)),
        "bytes": bundle.stat().st_size,
        "sha256": sha256(bundle),
    }


def write_summaries(payload: dict) -> None:
    MACHINE_SUMMARY_PATH.parent.mkdir(parents=True, exist_ok=True)
    MACHINE_SUMMARY_PATH.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    identity = payload["identity"]
    bundle = payload.get("bundle")
    lines = [
        "# Play Release Build Summary",
        "",
        f"- Application ID: `{identity['application_id']}`",
        f"- Version: `{identity['version_name']}` (`{identity['version_code']}`)",
        f"- SDKs: min `{identity['min_sdk']}`, target `{identity['target_sdk']}`, compile `{identity['compile_sdk']}`",
        f"- Graphics verified: `{len(payload['graphics'])}`",
        f"- Curated screenshots verified: `{len(payload['screenshots'])}`",
        f"- Required audio assets verified: `{len(payload['audio'])}`",
    ]
    if bundle:
        lines.extend(
            [
                f"- Release bundle: `{bundle['path']}`",
                f"- Bundle size: `{bundle['bytes']}` bytes",
                f"- Bundle SHA-256: `{bundle['sha256']}`",
            ]
        )
    else:
        lines.append("- Release bundle: build skipped (asset-validation dry run)")

    lines.extend(
        [
            "",
            "## Automated Checks Passed",
            "",
            "- project identity and version structure",
            "- explicit release-signing configuration (unless dry-run override used)",
            "- generated graphic dimensions and manifest hashes",
            "- non-empty metadata without obvious placeholders",
            "- non-empty, uniform, unique curated screenshot set",
            "- required runtime audio resources",
            "- unit tests, release lint, and release bundle build (unless --skip-build)",
            "",
            "## Manual Checks Still Required",
            "",
            "- gameplay correctness and long-run performance on representative devices",
            "- visual review that each screenshot shows the intended scenario",
            "- accessibility, audio, haptic, privacy, policy, data-safety, and store-listing review",
            "- verification of current Android and Play requirements immediately before upload",
        ]
    )
    SUMMARY_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate and build a Forest Run Play release")
    parser.add_argument(
        "--skip-build",
        action="store_true",
        help="Validate release inputs without running Gradle or producing an AAB",
    )
    parser.add_argument(
        "--allow-placeholder-id",
        action="store_true",
        help="Allow the placeholder application ID for a non-upload dry run",
    )
    parser.add_argument(
        "--allow-unsigned",
        action="store_true",
        help="Allow missing release signing for a non-upload dry run",
    )
    args = parser.parse_args()

    build_text = require_file(BUILD_FILE, "Android build file").read_text(encoding="utf-8")
    identity = verify_project_identity(build_text, args.allow_placeholder_id)
    verify_release_signing(build_text, args.allow_unsigned)

    payload = {
        "identity": identity,
        "graphics": verify_graphics(),
        "metadata": verify_metadata(),
        "screenshots": verify_screenshots(),
        "audio": verify_audio_assets(),
        "bundle": None,
        "dry_run_overrides": {
            "allow_placeholder_id": args.allow_placeholder_id,
            "allow_unsigned": args.allow_unsigned,
            "skip_build": args.skip_build,
        },
    }

    if not args.skip_build:
        run_gradle(["testDebugUnitTest", "lintRelease", "bundleRelease"])
        payload["bundle"] = verify_bundle()

    write_summaries(payload)
    print(f"Release validation passed. Summary: {SUMMARY_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
