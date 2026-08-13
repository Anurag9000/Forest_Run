#!/usr/bin/env python3
"""Revalidate every final Forest Run release-evidence layer as one candidate.

This is an orchestration gate, not a new source of truth. It delegates physical,
human, governance, and release-index validation to their independent validators,
then checks that the same files, digests, signed artifact, and candidate identity
are actually represented in the final evidence index.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any, Mapping, Sequence

import strict_json
import validate_device_acceptance as device_acceptance
import validate_human_acceptance as human_acceptance
import validate_installed_identity_matrix as installed_matrix
import validate_play_delivery_evidence as play_delivery
import validate_release_governance as governance
import verify_release_evidence_index as evidence_index

SHA40_RE = re.compile(r"^[0-9a-f]{40}$")
MAX_INDEX_BYTES = evidence_index.MAX_INDEX_BYTES

REQUIRED_BOUND_KINDS = frozenset(
    {
        "artifact_verification",
        "declared_dependencies",
        "sbom",
        "device_acceptance",
        "device_aggregate",
        "human_acceptance",
        "installed_identity_matrix",
        "play_delivery",
        "release_governance",
        "screenshot_manifest",
        "graphics_manifest",
    }
)
REQUIRED_UNBOUND_KINDS = frozenset({"signed_bundle"})


class ReleaseReadinessError(ValueError):
    """Raised when final release evidence is incomplete or cross-candidate."""


@dataclass(frozen=True)
class ReleaseReadinessSummary:
    candidate_sha: str
    artifact_sha256: str
    upload_certificate_sha256: str
    app_signing_certificate_sha256: str
    device_acceptance_sha256: str
    human_acceptance_sha256: str
    installed_identity_matrix_sha256: str
    play_delivery_sha256: str
    governance_sha256: str
    evidence_index_sha256: str
    evidence_set_sha256: str
    evidence_entry_count: int

    def to_json(self) -> dict[str, object]:
        return {
            "status": "valid",
            "candidate_sha": self.candidate_sha,
            "artifact_sha256": self.artifact_sha256,
            "upload_certificate_sha256": self.upload_certificate_sha256,
            "app_signing_certificate_sha256": self.app_signing_certificate_sha256,
            "device_acceptance_sha256": self.device_acceptance_sha256,
            "human_acceptance_sha256": self.human_acceptance_sha256,
            "installed_identity_matrix_sha256": self.installed_identity_matrix_sha256,
            "play_delivery_sha256": self.play_delivery_sha256,
            "governance_sha256": self.governance_sha256,
            "evidence_index_sha256": self.evidence_index_sha256,
            "evidence_set_sha256": self.evidence_set_sha256,
            "evidence_entry_count": self.evidence_entry_count,
        }


def _expected_sha(value: str) -> str:
    normalized = value.strip()
    if not SHA40_RE.fullmatch(normalized):
        raise ReleaseReadinessError(
            "expected candidate SHA must be exactly 40 lowercase hexadecimal characters"
        )
    return normalized


def _safe_relative_path(path: Path, root: Path, label: str) -> tuple[str, Path]:
    root = root.expanduser().resolve()
    lexical = Path(os.path.abspath(os.fspath(path.expanduser())))
    try:
        relative = lexical.relative_to(root)
    except ValueError as exc:
        raise ReleaseReadinessError(f"{label} must remain inside the evidence root") from exc
    if not relative.parts or any(part in {"", ".", ".."} for part in relative.parts):
        raise ReleaseReadinessError(f"{label} has an unsafe relative path")

    current = root
    for part in relative.parts:
        current = current / part
        try:
            metadata = current.lstat()
        except FileNotFoundError as exc:
            raise ReleaseReadinessError(f"{label} is missing: {current}") from exc
        except OSError as exc:
            raise ReleaseReadinessError(f"could not inspect {label}: {current}: {exc}") from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise ReleaseReadinessError(f"{label} must not traverse a symbolic link: {current}")

    resolved = lexical.resolve()
    try:
        resolved.relative_to(root)
    except ValueError as exc:
        raise ReleaseReadinessError(f"{label} resolves outside the evidence root") from exc
    if not resolved.is_file():
        raise ReleaseReadinessError(f"{label} must be a regular file")
    return PurePosixPath(*relative.parts).as_posix(), resolved


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _load_json_object(path: Path, label: str, maximum_bytes: int) -> Mapping[str, Any]:
    try:
        value = strict_json.load_file(
            path,
            maximum_bytes=maximum_bytes,
            require_object=True,
        )
    except strict_json.StrictJsonError as exc:
        raise ReleaseReadinessError(f"invalid {label}: {exc}") from exc
    assert isinstance(value, Mapping)
    return value


def _entry_map(index_payload: Mapping[str, Any]) -> dict[str, Mapping[str, Any]]:
    raw_entries = index_payload.get("entries")
    if not isinstance(raw_entries, list):
        raise ReleaseReadinessError("verified release evidence index has no entries array")
    entries: dict[str, Mapping[str, Any]] = {}
    for raw_entry in raw_entries:
        if not isinstance(raw_entry, Mapping):
            raise ReleaseReadinessError("verified release evidence index contains a non-object entry")
        kind = raw_entry.get("kind")
        if not isinstance(kind, str):
            raise ReleaseReadinessError("verified release evidence index contains an invalid kind")
        if kind in entries:
            raise ReleaseReadinessError(f"verified release evidence index duplicates kind: {kind}")
        entries[kind] = raw_entry
    return entries


def _require_index_reference(
    entries: Mapping[str, Mapping[str, Any]],
    *,
    kind: str,
    expected_path: str,
    expected_sha256: str,
) -> None:
    entry = entries.get(kind)
    if entry is None:
        raise ReleaseReadinessError(f"release evidence index is missing {kind}")
    if entry.get("path") != expected_path:
        raise ReleaseReadinessError(
            f"release evidence index {kind} path does not match the validated input"
        )
    if entry.get("sha256") != expected_sha256:
        raise ReleaseReadinessError(
            f"release evidence index {kind} digest does not match the validated input"
        )


def validate_readiness(
    *,
    root: Path,
    expected_candidate_sha: str,
    device_manifest: Path,
    human_manifest: Path,
    installed_identity_matrix: Path,
    play_delivery_manifest: Path,
    governance_manifest: Path,
    release_index: Path,
) -> ReleaseReadinessSummary:
    root = root.expanduser().resolve()
    expected = _expected_sha(expected_candidate_sha)
    device_relative, device_path = _safe_relative_path(
        device_manifest, root, "device acceptance manifest"
    )
    human_relative, human_path = _safe_relative_path(
        human_manifest, root, "human acceptance manifest"
    )
    installed_matrix_relative, installed_matrix_path = _safe_relative_path(
        installed_identity_matrix, root, "installed identity matrix"
    )
    play_delivery_relative, play_delivery_path = _safe_relative_path(
        play_delivery_manifest, root, "Play delivery manifest"
    )
    governance_relative, governance_path = _safe_relative_path(
        governance_manifest, root, "release governance manifest"
    )
    _, index_path = _safe_relative_path(release_index, root, "release evidence index")

    try:
        device_summary = device_acceptance.load_and_validate(device_path)
    except (OSError, device_acceptance.EvidenceError) as exc:
        raise ReleaseReadinessError(f"device acceptance failed: {exc}") from exc
    try:
        human_summary = human_acceptance.load_and_validate(human_path)
    except (OSError, human_acceptance.HumanAcceptanceError) as exc:
        raise ReleaseReadinessError(f"human acceptance failed: {exc}") from exc
    try:
        installed_matrix_summary = installed_matrix.load_and_validate(installed_matrix_path)
    except (OSError, installed_matrix.InstalledIdentityMatrixError) as exc:
        raise ReleaseReadinessError(f"installed identity matrix failed: {exc}") from exc
    try:
        play_delivery_summary = play_delivery.load_and_validate(play_delivery_path)
    except (OSError, play_delivery.PlayDeliveryError) as exc:
        raise ReleaseReadinessError(f"Play delivery evidence failed: {exc}") from exc
    try:
        governance_summary = governance.load_and_validate(governance_path)
    except (OSError, governance.GovernanceError) as exc:
        raise ReleaseReadinessError(f"release governance failed: {exc}") from exc

    candidate_values = {
        device_summary.candidate_sha,
        human_summary.candidate_sha,
        installed_matrix_summary.candidate_sha,
        play_delivery_summary.candidate_sha,
        governance_summary.candidate_sha,
    }
    if candidate_values != {expected}:
        raise ReleaseReadinessError(
            "device, human, governance, and expected candidate SHA must all match"
        )
    artifact_values = {
        device_summary.artifact_sha256,
        human_summary.artifact_sha256,
        installed_matrix_summary.artifact_sha256,
        play_delivery_summary.artifact_sha256,
        governance_summary.artifact_sha256,
    }
    if len(artifact_values) != 1:
        raise ReleaseReadinessError(
            "device, human, and governance artifact SHA-256 values do not match"
        )
    upload_certificates = {
        device_summary.upload_certificate_sha256,
        human_summary.upload_certificate_sha256,
        installed_matrix_summary.upload_certificate_sha256,
        play_delivery_summary.upload_certificate_sha256,
        governance_summary.upload_certificate_sha256,
    }
    if len(upload_certificates) != 1:
        raise ReleaseReadinessError(
            "device, human, and governance upload-certificate SHA-256 values do not match"
        )
    app_signing_certificates = {
        device_summary.app_signing_certificate_sha256,
        human_summary.app_signing_certificate_sha256,
        installed_matrix_summary.app_signing_certificate_sha256,
        play_delivery_summary.app_signing_certificate_sha256,
        governance_summary.app_signing_certificate_sha256,
    }
    if len(app_signing_certificates) != 1:
        raise ReleaseReadinessError(
            "device, human, and governance app-signing-certificate SHA-256 values do not match"
        )

    device_digest = _sha256_file(device_path)
    human_digest = _sha256_file(human_path)
    installed_matrix_digest = _sha256_file(installed_matrix_path)
    play_delivery_digest = _sha256_file(play_delivery_path)
    governance_digest = _sha256_file(governance_path)
    if device_digest != device_summary.bundle_sha256:
        raise ReleaseReadinessError("device acceptance digest changed after validation")
    if device_digest != human_summary.device_acceptance_sha256:
        raise ReleaseReadinessError("human acceptance references a different device manifest")
    if device_digest != governance_summary.device_acceptance_sha256:
        raise ReleaseReadinessError("governance references a different device manifest")
    if human_digest != human_summary.manifest_sha256:
        raise ReleaseReadinessError("human acceptance digest changed after validation")
    if human_digest != governance_summary.human_acceptance_sha256:
        raise ReleaseReadinessError("governance references a different human manifest")
    if installed_matrix_digest != installed_matrix_summary.manifest_sha256:
        raise ReleaseReadinessError("installed identity matrix digest changed after validation")
    if installed_matrix_summary.device_acceptance_sha256 != device_digest:
        raise ReleaseReadinessError("installed identity matrix references a different device manifest")
    if installed_matrix_digest != governance_summary.installed_identity_matrix_sha256:
        raise ReleaseReadinessError("governance references a different installed identity matrix")
    if play_delivery_digest != play_delivery_summary.manifest_sha256:
        raise ReleaseReadinessError("Play delivery digest changed after validation")
    if play_delivery_summary.installed_identity_matrix_sha256 != installed_matrix_digest:
        raise ReleaseReadinessError("Play delivery references a different installed identity matrix")
    if play_delivery_digest != governance_summary.play_delivery_sha256:
        raise ReleaseReadinessError("governance references a different Play delivery manifest")
    if governance_digest != governance_summary.manifest_sha256:
        raise ReleaseReadinessError("governance digest changed after validation")

    required_bound = sorted(REQUIRED_BOUND_KINDS)
    try:
        index_summary = evidence_index.verify_index(
            index_path,
            root=root,
            expected_candidate_sha=expected,
            require_bound_kinds=required_bound,
        )
    except evidence_index.EvidenceIndexVerificationError as exc:
        raise ReleaseReadinessError(f"release evidence index failed: {exc}") from exc
    index_payload = _load_json_object(index_path, "release evidence index", MAX_INDEX_BYTES)
    entries = _entry_map(index_payload)
    missing_unbound = sorted(REQUIRED_UNBOUND_KINDS - set(entries))
    if missing_unbound:
        raise ReleaseReadinessError(
            "release evidence index is missing required artifact kinds: "
            + ", ".join(missing_unbound)
        )

    _require_index_reference(
        entries,
        kind="device_acceptance",
        expected_path=device_relative,
        expected_sha256=device_digest,
    )
    _require_index_reference(
        entries,
        kind="human_acceptance",
        expected_path=human_relative,
        expected_sha256=human_digest,
    )
    _require_index_reference(
        entries,
        kind="installed_identity_matrix",
        expected_path=installed_matrix_relative,
        expected_sha256=installed_matrix_digest,
    )
    _require_index_reference(
        entries,
        kind="play_delivery",
        expected_path=play_delivery_relative,
        expected_sha256=play_delivery_digest,
    )
    _require_index_reference(
        entries,
        kind="release_governance",
        expected_path=governance_relative,
        expected_sha256=governance_digest,
    )

    signed_bundle = entries["signed_bundle"]
    if signed_bundle.get("sha256") != governance_summary.artifact_sha256:
        raise ReleaseReadinessError(
            "indexed signed_bundle digest does not match the accepted signed artifact"
        )
    if signed_bundle.get("candidateBound") is not False:
        raise ReleaseReadinessError(
            "signed_bundle must remain binary/transitively bound rather than claiming JSON candidate binding"
        )

    return ReleaseReadinessSummary(
        candidate_sha=expected,
        artifact_sha256=governance_summary.artifact_sha256,
        upload_certificate_sha256=governance_summary.upload_certificate_sha256,
        app_signing_certificate_sha256=governance_summary.app_signing_certificate_sha256,
        device_acceptance_sha256=device_digest,
        human_acceptance_sha256=human_digest,
        installed_identity_matrix_sha256=installed_matrix_digest,
        play_delivery_sha256=play_delivery_digest,
        governance_sha256=governance_digest,
        evidence_index_sha256=str(index_summary["indexSha256"]),
        evidence_set_sha256=str(index_summary["evidenceSetSha256"]),
        evidence_entry_count=int(index_summary["entryCount"]),
    )


def _write_summary(path: Path, payload: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as handle:
            temporary = Path(handle.name)
            json.dump(payload, handle, indent=2, sort_keys=True, allow_nan=False)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
        temporary = None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--expected-candidate-sha", required=True)
    parser.add_argument("--device-acceptance", type=Path, required=True)
    parser.add_argument("--human-acceptance", type=Path, required=True)
    parser.add_argument("--installed-identity-matrix", type=Path, required=True)
    parser.add_argument("--play-delivery", type=Path, required=True)
    parser.add_argument("--release-governance", type=Path, required=True)
    parser.add_argument("--release-evidence-index", type=Path, required=True)
    parser.add_argument("--summary-output", type=Path)
    args = parser.parse_args(argv)
    try:
        summary = validate_readiness(
            root=args.root,
            expected_candidate_sha=args.expected_candidate_sha,
            device_manifest=args.device_acceptance,
            human_manifest=args.human_acceptance,
            installed_identity_matrix=args.installed_identity_matrix,
            play_delivery_manifest=args.play_delivery,
            governance_manifest=args.release_governance,
            release_index=args.release_evidence_index,
        )
    except ReleaseReadinessError as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 1
    payload = summary.to_json()
    if args.summary_output is not None:
        _write_summary(args.summary_output, payload)
    print(json.dumps(payload, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
