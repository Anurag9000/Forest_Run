#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path, PurePosixPath
from typing import Any, Sequence

ROOT = Path(__file__).resolve().parent.parent
HEX_40 = re.compile(r"[0-9a-f]{40}")
HEX_64 = re.compile(r"[0-9a-f]{64}")
MAX_SUMMARY_BYTES = 2 * 1024 * 1024
EXPECTED_APPLICATION_ID = "com.anurag9000.forestrun"


class ReleaseSummaryError(ValueError):
    pass


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


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


def verify_file_fact(root: Path, facts: dict[str, Any], label: str) -> Path:
    relative = required_text(facts, "path", label)
    if any(ord(character) < 32 or ord(character) == 127 for character in relative):
        raise ReleaseSummaryError(f"{label}.path is unsafe")
    normalized = relative.replace("\\", "/")
    if normalized.startswith("/") or re.match(r"^[A-Za-z]:", normalized):
        raise ReleaseSummaryError(f"{label}.path is unsafe")
    pure = PurePosixPath(normalized)
    if not pure.parts or any(part in {"", ".", ".."} for part in pure.parts):
        raise ReleaseSummaryError(f"{label}.path is unsafe")
    absolute = root.joinpath(*pure.parts)
    if not absolute.is_file():
        raise ReleaseSummaryError(f"{label} file is missing: {relative}")
    size = absolute.stat().st_size
    if facts.get("bytes") != size:
        raise ReleaseSummaryError(f"{label} byte count is stale")
    recorded_hash = facts.get("sha256")
    if not isinstance(recorded_hash, str) or HEX_64.fullmatch(recorded_hash) is None:
        raise ReleaseSummaryError(f"{label} SHA-256 is malformed")
    if digest(absolute) != recorded_hash:
        raise ReleaseSummaryError(f"{label} SHA-256 is stale")
    return absolute


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

    graphics = payload.get("graphics")
    metadata = payload.get("metadata")
    audio = payload.get("audio")
    if not isinstance(graphics, list) or len(graphics) != 2:
        raise ReleaseSummaryError("Release summary must contain exactly two graphics")
    if not isinstance(metadata, list) or len(metadata) != 3:
        raise ReleaseSummaryError("Release summary must contain exactly three metadata files")
    if not isinstance(audio, list) or len(audio) != 15 or len(audio) != len(set(audio)):
        raise ReleaseSummaryError("Release summary required-audio evidence is incomplete")

    screenshots = required_dict(payload, "screenshots")
    if screenshots.get("candidate_sha") != expected_candidate_sha:
        raise ReleaseSummaryError("Screenshot evidence candidate differs from release candidate")
    if screenshots.get("package_name") != "com.anurag9000.forestrun.debug":
        raise ReleaseSummaryError("Screenshot evidence package is invalid")
    images = screenshots.get("images")
    if not isinstance(images, list) or len(images) < 4:
        raise ReleaseSummaryError("Release summary contains too few screenshots")

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
