#!/usr/bin/env python3
"""Independently verify a published Forest Run release-evidence index."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Iterable, Mapping, Sequence

import strict_json

SCHEMA_VERSION = 1
MAX_INDEX_BYTES = 4 * 1024 * 1024
MAX_EVIDENCE_FILE_BYTES = 512 * 1024 * 1024
MAX_JSON_EVIDENCE_BYTES = 16 * 1024 * 1024
MAX_ENTRIES = 128
SHA40 = re.compile(r"^[0-9a-f]{40}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
KIND = re.compile(r"^[a-z][a-z0-9_]{0,47}$")
SUPPORTED_SUFFIXES = {
    ".aab",
    ".apk",
    ".csv",
    ".jpeg",
    ".jpg",
    ".json",
    ".md",
    ".png",
    ".txt",
    ".webp",
    ".zip",
}
ROOT_KEYS = {
    "schemaVersion",
    "candidateSha",
    "generatedAtUtc",
    "entryCount",
    "candidateBoundEntryCount",
    "evidenceSetSha256",
    "entries",
}
ENTRY_KEYS = {
    "kind",
    "path",
    "bytes",
    "sha256",
    "candidateBound",
    "candidateBindings",
}
EXPLICIT_CANDIDATE_KEYS = {"candidateSha", "candidate_sha"}
CANDIDATE_OBJECT_KEYS = {"candidate", "build"}
COMMIT_KEYS = {"commitSha", "commit_sha"}


class EvidenceIndexVerificationError(ValueError):
    """Raised when a release-evidence index or indexed file is invalid."""


def _canonical_bytes(value: object) -> bytes:
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        + "\n"
    ).encode("utf-8")


def _exact_keys(value: Mapping[str, Any], expected: set[str], *, label: str) -> None:
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        extra = sorted(actual - expected)
        details = []
        if missing:
            details.append("missing=" + ",".join(missing))
        if extra:
            details.append("extra=" + ",".join(extra))
        raise EvidenceIndexVerificationError(
            f"{label} keys do not match the schema ({'; '.join(details)})"
        )


def _require_int(value: object, *, label: str, minimum: int = 0) -> int:
    if type(value) is not int or value < minimum:
        raise EvidenceIndexVerificationError(
            f"{label} must be an integer greater than or equal to {minimum}"
        )
    return value


def _require_bool(value: object, *, label: str) -> bool:
    if type(value) is not bool:
        raise EvidenceIndexVerificationError(f"{label} must be a boolean")
    return value


def _require_string(value: object, *, label: str) -> str:
    if not isinstance(value, str):
        raise EvidenceIndexVerificationError(f"{label} must be a string")
    return value


def _candidate_sha(value: object, *, label: str) -> str:
    candidate = _require_string(value, label=label)
    if not SHA40.fullmatch(candidate):
        raise EvidenceIndexVerificationError(
            f"{label} must be exactly 40 lowercase hexadecimal characters"
        )
    return candidate


def _sha256(value: object, *, label: str) -> str:
    digest = _require_string(value, label=label)
    if not SHA256.fullmatch(digest):
        raise EvidenceIndexVerificationError(
            f"{label} must be exactly 64 lowercase hexadecimal characters"
        )
    return digest


def _generated_at(value: object) -> str:
    timestamp = _require_string(value, label="generatedAtUtc")
    if not timestamp.endswith("Z"):
        raise EvidenceIndexVerificationError(
            "generatedAtUtc must use canonical UTC Z notation"
        )
    try:
        parsed = datetime.fromisoformat(timestamp[:-1] + "+00:00")
    except ValueError as exc:
        raise EvidenceIndexVerificationError(
            "generatedAtUtc is not valid ISO-8601"
        ) from exc
    canonical = parsed.astimezone(timezone.utc).isoformat(
        timespec="seconds"
    ).replace("+00:00", "Z")
    if parsed.tzinfo != timezone.utc or timestamp != canonical:
        raise EvidenceIndexVerificationError(
            "generatedAtUtc must be canonical UTC with second precision"
        )
    return timestamp


def _safe_relative_path(value: object, *, label: str) -> str:
    raw = _require_string(value, label=label)
    if not raw or "\\" in raw or "\x00" in raw:
        raise EvidenceIndexVerificationError(
            f"{label} must be a non-empty POSIX relative path"
        )
    path = PurePosixPath(raw)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise EvidenceIndexVerificationError(f"{label} is unsafe: {raw!r}")
    normalized = path.as_posix()
    if Path(normalized).suffix.lower() not in SUPPORTED_SUFFIXES:
        raise EvidenceIndexVerificationError(
            f"{label} has an unsupported file type: {normalized}"
        )
    return normalized


def _candidate_bindings(
    value: object,
    *,
    candidate_context: bool = False,
) -> set[str]:
    bindings: set[str] = set()
    if isinstance(value, Mapping):
        for key, item in value.items():
            if key in EXPLICIT_CANDIDATE_KEYS and isinstance(item, str):
                bindings.add(item.strip())
            elif candidate_context and key in COMMIT_KEYS and isinstance(item, str):
                bindings.add(item.strip())
            bindings.update(
                _candidate_bindings(
                    item,
                    candidate_context=key in CANDIDATE_OBJECT_KEYS,
                )
            )
    elif isinstance(value, list):
        for item in value:
            bindings.update(
                _candidate_bindings(item, candidate_context=candidate_context)
            )
    return bindings


def _read_stable_file(
    path: Path,
    *,
    maximum_bytes: int,
    label: str,
) -> tuple[bytes, tuple[int, int]]:
    try:
        before = path.lstat()
    except FileNotFoundError as exc:
        raise EvidenceIndexVerificationError(f"{label} is missing: {path}") from exc
    except OSError as exc:
        raise EvidenceIndexVerificationError(
            f"could not inspect {label} {path}: {exc}"
        ) from exc
    if stat.S_ISLNK(before.st_mode):
        raise EvidenceIndexVerificationError(f"{label} must not be a symbolic link")
    if not stat.S_ISREG(before.st_mode):
        raise EvidenceIndexVerificationError(f"{label} must be a regular file")
    if before.st_size <= 0 or before.st_size > maximum_bytes:
        raise EvidenceIndexVerificationError(
            f"{label} must be between 1 and {maximum_bytes} bytes"
        )
    try:
        raw = path.read_bytes()
        after = path.lstat()
    except OSError as exc:
        raise EvidenceIndexVerificationError(
            f"could not read {label} {path}: {exc}"
        ) from exc
    before_identity = (
        before.st_dev,
        before.st_ino,
        before.st_size,
        before.st_mtime_ns,
    )
    after_identity = (
        after.st_dev,
        after.st_ino,
        after.st_size,
        after.st_mtime_ns,
    )
    if len(raw) != before.st_size or after_identity != before_identity:
        raise EvidenceIndexVerificationError(f"{label} changed while being read")
    return raw, (before.st_dev, before.st_ino)


def _load_evidence_bindings(path: Path, raw: bytes) -> tuple[str, ...]:
    if path.suffix.lower() != ".json":
        return ()
    if len(raw) > MAX_JSON_EVIDENCE_BYTES:
        raise EvidenceIndexVerificationError(
            f"JSON evidence exceeds {MAX_JSON_EVIDENCE_BYTES} bytes: {path}"
        )
    try:
        payload = strict_json.loads(
            raw,
            label=str(path),
            maximum_bytes=MAX_JSON_EVIDENCE_BYTES,
            maximum_depth=64,
        )
    except strict_json.StrictJsonError as exc:
        raise EvidenceIndexVerificationError(
            f"invalid JSON evidence {path}: {exc}"
        ) from exc
    bindings = tuple(sorted(_candidate_bindings(payload)))
    if any(not SHA40.fullmatch(binding) for binding in bindings):
        raise EvidenceIndexVerificationError(
            f"JSON evidence contains a malformed candidate SHA: {path}"
        )
    return bindings


def _parse_entry(
    raw_entry: object,
    *,
    index: int,
    candidate_sha: str,
) -> dict[str, object]:
    if not isinstance(raw_entry, Mapping):
        raise EvidenceIndexVerificationError(f"entries[{index}] must be an object")
    _exact_keys(raw_entry, ENTRY_KEYS, label=f"entries[{index}]")
    kind = _require_string(raw_entry["kind"], label=f"entries[{index}].kind")
    if not KIND.fullmatch(kind):
        raise EvidenceIndexVerificationError(
            f"entries[{index}].kind is not a lowercase identifier"
        )
    relative = _safe_relative_path(
        raw_entry["path"],
        label=f"entries[{index}].path",
    )
    size = _require_int(
        raw_entry["bytes"],
        label=f"entries[{index}].bytes",
        minimum=1,
    )
    if size > MAX_EVIDENCE_FILE_BYTES:
        raise EvidenceIndexVerificationError(
            f"entries[{index}].bytes exceeds {MAX_EVIDENCE_FILE_BYTES}"
        )
    digest = _sha256(
        raw_entry["sha256"],
        label=f"entries[{index}].sha256",
    )
    candidate_bound = _require_bool(
        raw_entry["candidateBound"],
        label=f"entries[{index}].candidateBound",
    )
    raw_bindings = raw_entry["candidateBindings"]
    if not isinstance(raw_bindings, list):
        raise EvidenceIndexVerificationError(
            f"entries[{index}].candidateBindings must be a list"
        )
    bindings = [
        _candidate_sha(
            binding,
            label=f"entries[{index}].candidateBindings[{binding_index}]",
        )
        for binding_index, binding in enumerate(raw_bindings)
    ]
    if bindings != sorted(set(bindings)):
        raise EvidenceIndexVerificationError(
            f"entries[{index}].candidateBindings must be sorted and unique"
        )
    if any(binding != candidate_sha for binding in bindings):
        raise EvidenceIndexVerificationError(
            f"entries[{index}] contains a foreign candidate binding"
        )
    if candidate_bound != (candidate_sha in bindings):
        raise EvidenceIndexVerificationError(
            f"entries[{index}].candidateBound disagrees with candidateBindings"
        )
    return {
        "kind": kind,
        "path": relative,
        "bytes": size,
        "sha256": digest,
        "candidateBound": candidate_bound,
        "candidateBindings": bindings,
    }


def verify_index(
    index_path: Path,
    *,
    root: Path,
    expected_candidate_sha: str | None = None,
    require_bound_kinds: Iterable[str] = (),
) -> dict[str, object]:
    root = root.expanduser().resolve()
    index_lexical = Path(os.path.abspath(os.fspath(index_path.expanduser())))
    try:
        index_lexical.relative_to(root)
    except ValueError as exc:
        raise EvidenceIndexVerificationError(
            "release evidence index must remain inside the evidence root"
        ) from exc
    index_resolved = index_lexical.resolve(strict=False)
    try:
        index_resolved.relative_to(root)
    except ValueError as exc:
        raise EvidenceIndexVerificationError(
            "release evidence index resolves outside the evidence root"
        ) from exc

    raw_index, index_identity = _read_stable_file(
        index_lexical,
        maximum_bytes=MAX_INDEX_BYTES,
        label="release evidence index",
    )
    try:
        payload = strict_json.loads(
            raw_index,
            label=str(index_lexical),
            maximum_bytes=MAX_INDEX_BYTES,
            maximum_depth=32,
            require_object=True,
        )
    except strict_json.StrictJsonError as exc:
        raise EvidenceIndexVerificationError(
            f"invalid release evidence index: {exc}"
        ) from exc
    assert isinstance(payload, dict)
    _exact_keys(payload, ROOT_KEYS, label="release evidence index")
    if payload["schemaVersion"] != SCHEMA_VERSION:
        raise EvidenceIndexVerificationError(
            f"schemaVersion must equal {SCHEMA_VERSION}"
        )
    candidate_sha = _candidate_sha(payload["candidateSha"], label="candidateSha")
    if expected_candidate_sha is not None:
        expected = _candidate_sha(
            expected_candidate_sha,
            label="expected candidate SHA",
        )
        if candidate_sha != expected:
            raise EvidenceIndexVerificationError(
                "candidateSha does not match the expected candidate"
            )
    generated_at = _generated_at(payload["generatedAtUtc"])
    entry_count = _require_int(
        payload["entryCount"],
        label="entryCount",
        minimum=1,
    )
    if entry_count > MAX_ENTRIES:
        raise EvidenceIndexVerificationError(
            f"entryCount exceeds {MAX_ENTRIES}"
        )
    bound_count = _require_int(
        payload["candidateBoundEntryCount"],
        label="candidateBoundEntryCount",
    )
    evidence_set_sha256 = _sha256(
        payload["evidenceSetSha256"],
        label="evidenceSetSha256",
    )
    raw_entries = payload["entries"]
    if not isinstance(raw_entries, list):
        raise EvidenceIndexVerificationError("entries must be a list")
    if len(raw_entries) != entry_count:
        raise EvidenceIndexVerificationError(
            "entryCount does not match entries length"
        )

    entries = [
        _parse_entry(entry, index=index, candidate_sha=candidate_sha)
        for index, entry in enumerate(raw_entries)
    ]
    ordering = [(entry["kind"], entry["path"]) for entry in entries]
    if ordering != sorted(ordering):
        raise EvidenceIndexVerificationError(
            "entries must be sorted by kind and path"
        )
    if len({entry["kind"] for entry in entries}) != len(entries):
        raise EvidenceIndexVerificationError("evidence kinds must be unique")
    if len({entry["path"] for entry in entries}) != len(entries):
        raise EvidenceIndexVerificationError("evidence paths must be unique")
    if bound_count != sum(bool(entry["candidateBound"]) for entry in entries):
        raise EvidenceIndexVerificationError(
            "candidateBoundEntryCount does not match entries"
        )
    if evidence_set_sha256 != hashlib.sha256(_canonical_bytes(entries)).hexdigest():
        raise EvidenceIndexVerificationError(
            "evidenceSetSha256 does not match the canonical entries"
        )

    required = set(require_bound_kinds)
    if any(not KIND.fullmatch(kind) for kind in required):
        raise EvidenceIndexVerificationError(
            "required candidate-bound kinds must be lowercase identifiers"
        )
    by_kind = {entry["kind"]: entry for entry in entries}
    missing = required - set(by_kind)
    if missing:
        raise EvidenceIndexVerificationError(
            "required evidence kinds are absent: " + ", ".join(sorted(missing))
        )
    unbound = sorted(
        kind for kind in required if not bool(by_kind[kind]["candidateBound"])
    )
    if unbound:
        raise EvidenceIndexVerificationError(
            "required evidence kinds are not candidate-bound: " + ", ".join(unbound)
        )

    physical_identities: set[tuple[int, int]] = set()
    reconstructed: list[dict[str, object]] = []
    total_bytes = 0
    for entry in entries:
        relative = str(entry["path"])
        path = root / relative
        resolved = path.resolve(strict=False)
        try:
            resolved.relative_to(root)
        except ValueError as exc:
            raise EvidenceIndexVerificationError(
                f"indexed evidence escapes the root: {relative}"
            ) from exc
        file_raw, identity = _read_stable_file(
            path,
            maximum_bytes=MAX_EVIDENCE_FILE_BYTES,
            label=f"indexed evidence {relative}",
        )
        if identity == index_identity:
            raise EvidenceIndexVerificationError(
                "release evidence index aliases an indexed evidence file"
            )
        if identity in physical_identities:
            raise EvidenceIndexVerificationError(
                f"one physical evidence file is reused through a hard link: {relative}"
            )
        physical_identities.add(identity)
        bindings = _load_evidence_bindings(path, file_raw)
        if any(binding != candidate_sha for binding in bindings):
            raise EvidenceIndexVerificationError(
                f"evidence candidate binding does not match {candidate_sha}: {relative}"
            )
        reconstructed_entry = {
            "kind": entry["kind"],
            "path": relative,
            "bytes": len(file_raw),
            "sha256": hashlib.sha256(file_raw).hexdigest(),
            "candidateBound": candidate_sha in bindings,
            "candidateBindings": list(bindings),
        }
        if reconstructed_entry != entry:
            raise EvidenceIndexVerificationError(
                f"indexed metadata does not match the evidence file: {relative}"
            )
        reconstructed.append(reconstructed_entry)
        total_bytes += len(file_raw)

    confirmed_index, confirmed_identity = _read_stable_file(
        index_lexical,
        maximum_bytes=MAX_INDEX_BYTES,
        label="release evidence index",
    )
    if confirmed_identity != index_identity or confirmed_index != raw_index:
        raise EvidenceIndexVerificationError(
            "release evidence index changed during verification"
        )

    return {
        "status": "valid",
        "candidateSha": candidate_sha,
        "generatedAtUtc": generated_at,
        "entryCount": entry_count,
        "candidateBoundEntryCount": bound_count,
        "totalEvidenceBytes": total_bytes,
        "evidenceSetSha256": hashlib.sha256(
            _canonical_bytes(reconstructed)
        ).hexdigest(),
        "indexSha256": hashlib.sha256(raw_index).hexdigest(),
    }


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("index", type=Path)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--expected-candidate-sha")
    parser.add_argument("--require-bound-kind", action="append", default=[])
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        summary = verify_index(
            args.index,
            root=args.root,
            expected_candidate_sha=args.expected_candidate_sha,
            require_bound_kinds=args.require_bound_kind,
        )
    except EvidenceIndexVerificationError as exc:
        print(
            json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True),
            file=os.sys.stderr,
        )
        return 1
    print(json.dumps(summary, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
