#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import sys
from pathlib import Path, PurePosixPath
from typing import Any, Sequence

ROOT = Path(__file__).resolve().parent.parent
HEX_40 = re.compile(r"[0-9a-f]{40}")
HEX_64 = re.compile(r"[0-9a-f]{64}")
MAX_SUMMARY_BYTES = 2 * 1024 * 1024
EXPECTED_APPLICATION_ID = "com.anurag9000.forestrun"
EXPECTED_GRAPHICS_PATHS = {
    "release/google-play/graphics/feature-graphic.png",
    "release/google-play/graphics/promo-square.png",
}
EXPECTED_METADATA_PATHS = {
    "release/google-play/metadata/en-US/title.txt",
    "release/google-play/metadata/en-US/short-description.txt",
    "release/google-play/metadata/en-US/full-description.txt",
}
SCREENSHOT_PREFIX = PurePosixPath("release/google-play/screenshots/final")


class ReleaseSummaryError(ValueError):
    pass


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def required_dict(source: dict[str, Any], key: str) -> dict[str, Any]:
    value = source.get(key)
    if not isinstance(value, dict):
        raise ReleaseSummaryError(f"build summary {key} must be an object")
    return value


def required_text(source: dict[str, Any], key: str, label: str) -> str:
    value = source.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ReleaseSummaryError(f"{label}.{key} must be a non-blank string")
    return value.strip()


def _safe_fact_path(root: Path, facts: dict[str, Any], label: str) -> tuple[str, Path]:
    relative = required_text(facts, "path", label)
    if any(ord(character) < 32 or ord(character) == 127 for character in relative):
        raise ReleaseSummaryError(f"{label}.path is unsafe")
    normalized = relative.replace("\\", "/")
    if normalized.startswith("/") or re.match(r"^[A-Za-z]:", normalized):
        raise ReleaseSummaryError(f"{label}.path is unsafe")
    pure = PurePosixPath(normalized)
    if not pure.parts or any(part in {"", ".", ".."} for part in pure.parts):
        raise ReleaseSummaryError(f"{label}.path is unsafe")

    root = root.expanduser().resolve()
    lexical = root.joinpath(*pure.parts)
    current = root
    for part in pure.parts:
        current = current / part
        try:
            metadata = current.lstat()
        except FileNotFoundError as exc:
            raise ReleaseSummaryError(f"{label} file is missing: {relative}") from exc
        except OSError as exc:
            raise ReleaseSummaryError(f"could not inspect {label}: {current}: {exc}") from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise ReleaseSummaryError(f"{label} must not traverse a symbolic link: {relative}")

    resolved = lexical.resolve()
    try:
        resolved.relative_to(root)
    except ValueError as exc:
        raise ReleaseSummaryError(f"{label}.path escapes the release root") from exc
    try:
        metadata = resolved.stat()
    except OSError as exc:
        raise ReleaseSummaryError(f"could not stat {label}: {relative}: {exc}") from exc
    if not stat.S_ISREG(metadata.st_mode):
        raise ReleaseSummaryError(f"{label} must be a regular file: {relative}")
    return pure.as_posix(), resolved


def _recorded_sha256(facts: dict[str, Any], label: str) -> str:
    recorded_hash = facts.get("sha256")
    if not isinstance(recorded_hash, str) or HEX_64.fullmatch(recorded_hash) is None:
        raise ReleaseSummaryError(f"{label} SHA-256 is malformed")
    return recorded_hash


def verify_file_fact(root: Path, facts: dict[str, Any], label: str) -> Path:
    _, absolute = _safe_fact_path(root, facts, label)
    size = absolute.stat().st_size
    if facts.get("bytes") != size:
        raise ReleaseSummaryError(f"{label} byte count is stale")
    if digest(absolute) != _recorded_sha256(facts, label):
        raise ReleaseSummaryError(f"{label} SHA-256 is stale")
    return absolute


def verify_metadata_fact(root: Path, facts: dict[str, Any], label: str) -> Path:
    _, absolute = _safe_fact_path(root, facts, label)
    if digest(absolute) != _recorded_sha256(facts, label):
        raise ReleaseSummaryError(f"{label} SHA-256 is stale")
    try:
        text = absolute.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        raise ReleaseSummaryError(f"{label} is not readable UTF-8: {exc}") from exc
    if facts.get("characters") != len(text):
        raise ReleaseSummaryError(f"{label} character count is stale")
    return absolute


def _fact_dicts(value: object, *, label: str, exact_count: int | None = None) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        raise ReleaseSummaryError(f"Release summary {label} must be a list")
    if exact_count is not None and len(value) != exact_count:
        raise ReleaseSummaryError(
            f"Release summary must contain exactly {exact_count} {label}"
        )
    if any(not isinstance(item, dict) for item in value):
        raise ReleaseSummaryError(f"Release summary {label} entries must be objects")
    return value


def _require_exact_paths(
    root: Path,
    facts_list: list[dict[str, Any]],
    expected: set[str],
    *,
    label: str,
    metadata: bool = False,
) -> None:
    observed: set[str] = set()
    for index, facts in enumerate(facts_list):
        path, _ = _safe_fact_path(root, facts, f"{label}[{index}]")
        if path in observed:
            raise ReleaseSummaryError(f"Release summary duplicates {label} path: {path}")
        observed.add(path)
        if metadata:
            verify_metadata_fact(root, facts, f"{label}[{index}]")
        else:
            verify_file_fact(root, facts, f"{label}[{index}]")
    if observed != expected:
        raise ReleaseSummaryError(
            f"Release summary {label} paths differ from the canonical set"
        )


def _verify_screenshot_facts(root: Path, facts_list: list[dict[str, Any]]) -> None:
    observed_paths: set[str] = set()
    observed_hashes: set[str] = set()
    for index, facts in enumerate(facts_list):
        label = f"screenshots.images[{index}]"
        path, _ = _safe_fact_path(root, facts, label)
        pure = PurePosixPath(path)
        if pure.parent != SCREENSHOT_PREFIX or pure.suffix.lower() != ".png":
            raise ReleaseSummaryError(
                f"{label}.path must be a curated final screenshot PNG"
            )
        if path in observed_paths:
            raise ReleaseSummaryError(f"Release summary duplicates screenshot path: {path}")
        observed_paths.add(path)
        verify_file_fact(root, facts, label)
        recorded_hash = _recorded_sha256(facts, label)
        if recorded_hash in observed_hashes:
            raise ReleaseSummaryError("Release summary contains duplicate screenshot image hashes")
        observed_hashes.add(recorded_hash)


def verify_release_summary(
    root: Path,
    release_root: Path,
    expected_candidate_sha: str,
) -> dict[str, Any]:
    root = root.resolve()
    release_root = release_root.resolve()
    expected_candidate_sha = expected_candidate_sha.lower()
    if HEX_40.fullmatch(expected_candidate_sha) is None:
        raise ReleaseSummaryError("expected candidate SHA must be 40 hexadecimal characters")
    machine_path = release_root / "build_summary.json"
    human_path = release_root / "BUILD_SUMMARY.md"
    for path in (machine_path, human_path):
        try:
            size = path.stat().st_size
        except FileNotFoundError as exc:
            raise ReleaseSummaryError(f"Missing release summary: {path}") from exc
        if size <= 0 or size > MAX_SUMMARY_BYTES:
            raise ReleaseSummaryError(f"Release summary has invalid size: {path}")
    try:
        payload = json.loads(machine_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ReleaseSummaryError(f"Invalid machine release summary: {exc}") from exc
    if not isinstance(payload, dict):
        raise ReleaseSummaryError("Machine release summary must be a JSON object")

    candidate = required_dict(payload, "candidate")
    candidate_sha = required_text(candidate, "sha", "candidate").lower()
    if candidate_sha != expected_candidate_sha or candidate.get("branch") != "main":
        raise ReleaseSummaryError("Release summary candidate is not the expected main commit")
    identity = required_dict(payload, "identity")
    if identity.get("application_id") != EXPECTED_APPLICATION_ID:
        raise ReleaseSummaryError("Release summary application ID is not final")
    if not isinstance(identity.get("version_code"), int) or identity["version_code"] <= 0:
        raise ReleaseSummaryError("Release summary version code is invalid")

    overrides = required_dict(payload, "dry_run_overrides")
    expected_override_keys = {"allow_placeholder_id", "allow_unsigned", "skip_build"}
    if set(overrides) != expected_override_keys or any(
        not isinstance(overrides[key], bool) for key in expected_override_keys
    ):
        raise ReleaseSummaryError("Release summary dry-run overrides are malformed")

    graphics = _fact_dicts(payload.get("graphics"), label="graphics", exact_count=2)
    metadata = _fact_dicts(payload.get("metadata"), label="metadata files", exact_count=3)
    audio = payload.get("audio")
    if not isinstance(audio, list) or len(audio) != 15 or len(audio) != len(set(audio)):
        raise ReleaseSummaryError("Release summary required-audio evidence is incomplete")
    _require_exact_paths(
        root,
        graphics,
        EXPECTED_GRAPHICS_PATHS,
        label="graphics",
    )
    _require_exact_paths(
        root,
        metadata,
        EXPECTED_METADATA_PATHS,
        label="metadata",
        metadata=True,
    )

    screenshots = required_dict(payload, "screenshots")
    if screenshots.get("candidate_sha") != expected_candidate_sha:
        raise ReleaseSummaryError("Screenshot evidence candidate differs from release candidate")
    if screenshots.get("package_name") != "com.anurag9000.forestrun.debug":
        raise ReleaseSummaryError("Screenshot evidence package is invalid")
    images = _fact_dicts(screenshots.get("images"), label="screenshot images")
    if len(images) < 4:
        raise ReleaseSummaryError("Release summary contains too few screenshots")
    _verify_screenshot_facts(root, images)

    bundle = payload.get("bundle")
    mapping = payload.get("r8_mapping")
    if overrides["skip_build"]:
        if bundle is not None or mapping is not None:
            raise ReleaseSummaryError("Skip-build summary must not claim bundle or R8 evidence")
    else:
        if not isinstance(bundle, dict) or not isinstance(mapping, dict):
            raise ReleaseSummaryError("Built release summary is missing bundle or R8 evidence")
        verify_file_fact(root, bundle, "bundle")
        verify_file_fact(root, mapping, "r8_mapping")
        if bundle.get("application_id") != EXPECTED_APPLICATION_ID:
            raise ReleaseSummaryError("Bundle application ID differs from release identity")
        if bundle.get("version_code") != identity.get("version_code") or bundle.get("version_name") != identity.get("version_name"):
            raise ReleaseSummaryError("Bundle version differs from release identity")
        signature_verified = bundle.get("signature_verified")
        if not isinstance(signature_verified, bool):
            raise ReleaseSummaryError("Bundle signature status is malformed")
        signer = bundle.get("signer_sha256")
        if signature_verified:
            if not isinstance(signer, str) or HEX_64.fullmatch(signer) is None:
                raise ReleaseSummaryError("Verified bundle signer fingerprint is malformed")
        elif not overrides["allow_unsigned"] or signer is not None:
            raise ReleaseSummaryError("Unsigned bundle is not explicitly identified as a dry run")
        if not isinstance(mapping.get("application_classes"), int) or not isinstance(mapping.get("renamed_classes"), int):
            raise ReleaseSummaryError("R8 class counts are malformed")
        if mapping["application_classes"] <= 0 or not 0 < mapping["renamed_classes"] <= mapping["application_classes"]:
            raise ReleaseSummaryError("R8 class counts are inconsistent")

    try:
        human = human_path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        raise ReleaseSummaryError(f"Could not read human release summary: {exc}") from exc
    if expected_candidate_sha not in human or EXPECTED_APPLICATION_ID not in human:
        raise ReleaseSummaryError("Human summary does not identify the candidate and application")
    if overrides["skip_build"]:
        if "build skipped" not in human.casefold():
            raise ReleaseSummaryError("Human summary does not disclose skipped build")
    else:
        bundle_hash = required_text(bundle, "sha256", "bundle")
        if bundle_hash not in human:
            raise ReleaseSummaryError("Human summary does not include the exact bundle hash")
        if str(bundle.get("signature_verified")).lower() not in human.casefold():
            raise ReleaseSummaryError("Human summary does not disclose signature status")
    return payload


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Verify Forest Run release summaries")
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--release-root", type=Path, default=ROOT / "release/google-play")
    parser.add_argument("--candidate-sha", required=True)
    args = parser.parse_args(argv)
    try:
        verify_release_summary(args.root, args.release_root, args.candidate_sha)
    except ReleaseSummaryError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    print(f"Verified release summaries for {args.candidate_sha.lower()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
