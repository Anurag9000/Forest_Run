#!/usr/bin/env python3
"""Build an immutable, candidate-bound index over release evidence files."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Iterable, Mapping, Sequence

SCHEMA_VERSION = 1
MAX_ENTRIES = 128
MAX_FILE_BYTES = 512 * 1024 * 1024
SHA40 = re.compile(r"^[0-9a-f]{40}$")
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
EXPLICIT_CANDIDATE_KEYS = {"candidateSha", "candidate_sha"}
CANDIDATE_OBJECT_KEYS = {"candidate", "build"}
COMMIT_KEYS = {"commitSha", "commit_sha"}


class EvidenceIndexError(ValueError):
    """Raised when release evidence is unsafe, stale, or internally inconsistent."""


@dataclass(frozen=True)
class EvidenceEntry:
    kind: str
    path: str
    bytes: int
    sha256: str
    candidate_bound: bool
    candidate_bindings: tuple[str, ...]

    def as_json(self) -> dict[str, object]:
        return {
            "kind": self.kind,
            "path": self.path,
            "bytes": self.bytes,
            "sha256": self.sha256,
            "candidateBound": self.candidate_bound,
            "candidateBindings": list(self.candidate_bindings),
        }


def _canonical_bytes(value: object) -> bytes:
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        + "\n"
    ).encode("utf-8")


def _validate_candidate_sha(candidate_sha: str) -> str:
    normalized = candidate_sha.strip()
    if normalized != normalized.lower() or not SHA40.fullmatch(normalized):
        raise EvidenceIndexError(
            "candidate SHA must be exactly 40 lowercase hexadecimal characters"
        )
    return normalized


def _validate_generated_at(value: str) -> str:
    if not value.endswith("Z"):
        raise EvidenceIndexError("generated-at timestamp must use canonical UTC Z notation")
    try:
        parsed = datetime.fromisoformat(value[:-1] + "+00:00")
    except ValueError as exc:
        raise EvidenceIndexError("generated-at timestamp is not valid ISO-8601") from exc
    if parsed.tzinfo != timezone.utc:
        raise EvidenceIndexError("generated-at timestamp must be UTC")
    return parsed.isoformat(timespec="seconds").replace("+00:00", "Z")


def _safe_relative_path(value: str) -> str:
    if not value or "\\" in value or "\x00" in value:
        raise EvidenceIndexError("evidence paths must be non-empty POSIX relative paths")
    path = PurePosixPath(value)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise EvidenceIndexError(f"unsafe evidence path: {value!r}")
    normalized = path.as_posix()
    if Path(normalized).suffix.lower() not in SUPPORTED_SUFFIXES:
        raise EvidenceIndexError(f"unsupported evidence file type: {normalized}")
    return normalized


def _parse_spec(spec: str) -> tuple[str, str]:
    kind, separator, path = spec.partition("=")
    if not separator or not KIND.fullmatch(kind):
        raise EvidenceIndexError(
            "each entry must use kind=relative/path with a lowercase identifier kind"
        )
    return kind, _safe_relative_path(path)


def _candidate_bindings(
    value: object,
    *,
    candidate_context: bool = False,
) -> set[str]:
    """Collect only explicit candidate identities, excluding baseline comparisons."""
    bindings: set[str] = set()
    if isinstance(value, Mapping):
        for key, item in value.items():
            if key in EXPLICIT_CANDIDATE_KEYS and isinstance(item, str):
                bindings.add(item.strip())
            elif candidate_context and key in COMMIT_KEYS and isinstance(item, str):
                bindings.add(item.strip())
            child_context = key in CANDIDATE_OBJECT_KEYS
            bindings.update(
                _candidate_bindings(item, candidate_context=child_context)
            )
    elif isinstance(value, list):
        for item in value:
            bindings.update(
                _candidate_bindings(item, candidate_context=candidate_context)
            )
    return bindings


def _load_json_bindings(path: Path) -> tuple[str, ...]:
    if path.suffix.lower() != ".json":
        return ()
    try:
        raw = path.read_bytes()
        if raw.startswith(b"\xef\xbb\xbf"):
            raise EvidenceIndexError(f"JSON evidence must not contain a UTF-8 BOM: {path}")
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise EvidenceIndexError(f"invalid UTF-8 JSON evidence: {path}") from exc
    bindings = tuple(sorted(_candidate_bindings(value)))
    if any(not SHA40.fullmatch(binding) for binding in bindings):
        raise EvidenceIndexError(f"JSON evidence contains a malformed candidate SHA: {path}")
    return bindings


def _digest(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _lexical_absolute(path: Path) -> Path:
    """Return an absolute normalized path without resolving symbolic links."""
    return Path(os.path.abspath(os.fspath(path.expanduser())))


def _assert_no_symlink_components(root: Path, path: Path, *, label: str) -> None:
    """Reject any existing symbolic-link component between root and path."""
    try:
        relative = path.relative_to(root)
    except ValueError as exc:
        raise EvidenceIndexError(f"{label} escapes the evidence root") from exc
    current = root
    for part in relative.parts:
        current = current / part
        try:
            metadata = current.lstat()
        except FileNotFoundError:
            # Once a component is absent, no descendant can currently be a symlink.
            break
        except OSError as exc:
            raise EvidenceIndexError(f"could not inspect {label}: {current}: {exc}") from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise EvidenceIndexError(f"{label} must not traverse a symbolic link: {current}")


def _validate_output_path(root: Path, output: Path) -> Path:
    """Require a lexical and resolved output path inside root without symlinks."""
    root_resolved = root.expanduser().resolve()
    output_lexical = _lexical_absolute(output)
    try:
        output_lexical.relative_to(root_resolved)
    except ValueError as exc:
        raise EvidenceIndexError("output index must remain inside the evidence root") from exc
    _assert_no_symlink_components(
        root_resolved,
        output_lexical,
        label="output index",
    )
    output_resolved = output_lexical.resolve(strict=False)
    try:
        output_resolved.relative_to(root_resolved)
    except ValueError as exc:
        raise EvidenceIndexError("output index resolves outside the evidence root") from exc
    return output_resolved


def _existing_regular_identity(path: Path, *, label: str) -> tuple[int, int] | None:
    try:
        metadata = path.lstat()
    except FileNotFoundError:
        return None
    except OSError as exc:
        raise EvidenceIndexError(f"could not inspect {label}: {path}: {exc}") from exc
    if stat.S_ISLNK(metadata.st_mode):
        raise EvidenceIndexError(f"{label} must not be a symbolic link")
    if not stat.S_ISREG(metadata.st_mode):
        raise EvidenceIndexError(f"{label} must be a regular file when it exists")
    return metadata.st_dev, metadata.st_ino


def _payload_evidence_identities(
    root: Path,
    payload: Mapping[str, object],
) -> set[tuple[int, int]]:
    entries = payload.get("entries")
    if not isinstance(entries, list):
        raise EvidenceIndexError("release evidence index payload must contain an entries list")
    identities: set[tuple[int, int]] = set()
    root_resolved = root.expanduser().resolve()
    for entry in entries:
        if not isinstance(entry, Mapping) or not isinstance(entry.get("path"), str):
            raise EvidenceIndexError("release evidence index payload contains an invalid entry")
        relative = _safe_relative_path(entry["path"])
        path = root_resolved / relative
        try:
            metadata = path.lstat()
        except FileNotFoundError as exc:
            raise EvidenceIndexError(
                f"indexed evidence file is missing before publication: {relative}"
            ) from exc
        except OSError as exc:
            raise EvidenceIndexError(
                f"could not inspect indexed evidence before publication: {relative}: {exc}"
            ) from exc
        if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISREG(metadata.st_mode):
            raise EvidenceIndexError(
                f"indexed evidence must remain a regular non-symlink file: {relative}"
            )
        resolved = path.resolve()
        try:
            resolved.relative_to(root_resolved)
        except ValueError as exc:
            raise EvidenceIndexError(
                f"indexed evidence escapes the root before publication: {relative}"
            ) from exc
        identities.add((metadata.st_dev, metadata.st_ino))
    return identities


def collect_entries(
    root: Path,
    candidate_sha: str,
    specs: Sequence[str],
    *,
    require_bound_kinds: Iterable[str] = (),
    output: Path | None = None,
) -> tuple[EvidenceEntry, ...]:
    candidate_sha = _validate_candidate_sha(candidate_sha)
    root = root.resolve()
    required = set(require_bound_kinds)
    if any(not KIND.fullmatch(kind) for kind in required):
        raise EvidenceIndexError(
            "required candidate-bound kinds must be lowercase identifiers"
        )
    if not specs:
        raise EvidenceIndexError("at least one release evidence entry is required")
    if len(specs) > MAX_ENTRIES:
        raise EvidenceIndexError(
            f"at most {MAX_ENTRIES} release evidence entries are allowed"
        )

    output_resolved = _validate_output_path(root, output) if output is not None else None
    output_identity = (
        _existing_regular_identity(output_resolved, label="output index")
        if output_resolved is not None
        else None
    )
    seen_kinds: set[str] = set()
    seen_paths: set[str] = set()
    seen_files: set[tuple[int, int]] = set()
    entries: list[EvidenceEntry] = []

    for spec in specs:
        kind, relative = _parse_spec(spec)
        if kind in seen_kinds:
            raise EvidenceIndexError(f"duplicate evidence kind: {kind}")
        if relative in seen_paths:
            raise EvidenceIndexError(f"duplicate evidence path: {relative}")
        seen_kinds.add(kind)
        seen_paths.add(relative)

        path = root / relative
        try:
            metadata = path.lstat()
        except FileNotFoundError as exc:
            raise EvidenceIndexError(f"evidence file is missing: {relative}") from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise EvidenceIndexError(
                f"evidence file must not be a symbolic link: {relative}"
            )
        if not stat.S_ISREG(metadata.st_mode):
            raise EvidenceIndexError(f"evidence path is not a regular file: {relative}")
        resolved = path.resolve()
        try:
            resolved.relative_to(root)
        except ValueError as exc:
            raise EvidenceIndexError(f"evidence file escapes the root: {relative}") from exc
        identity = (metadata.st_dev, metadata.st_ino)
        if output_resolved is not None and resolved == output_resolved:
            raise EvidenceIndexError(
                "the output index cannot also be an evidence input"
            )
        if output_identity is not None and identity == output_identity:
            raise EvidenceIndexError(
                "the output index cannot reuse an evidence input through a hard link"
            )
        if metadata.st_size <= 0 or metadata.st_size > MAX_FILE_BYTES:
            raise EvidenceIndexError(
                f"evidence file size must be between 1 and {MAX_FILE_BYTES} bytes: {relative}"
            )
        if identity in seen_files:
            raise EvidenceIndexError(
                f"one physical evidence file is reused through a hard link: {relative}"
            )
        seen_files.add(identity)

        bindings = _load_json_bindings(path)
        mismatches = [binding for binding in bindings if binding != candidate_sha]
        if mismatches:
            raise EvidenceIndexError(
                f"evidence candidate binding does not match {candidate_sha}: {relative}"
            )
        candidate_bound = candidate_sha in bindings
        if kind in required and not candidate_bound:
            raise EvidenceIndexError(
                f"evidence kind {kind} must contain an explicit candidate SHA binding"
            )
        entries.append(
            EvidenceEntry(
                kind=kind,
                path=relative,
                bytes=metadata.st_size,
                sha256=_digest(path),
                candidate_bound=candidate_bound,
                candidate_bindings=bindings,
            )
        )

    missing_required = required - seen_kinds
    if missing_required:
        raise EvidenceIndexError(
            "required candidate-bound evidence kinds are absent: "
            + ", ".join(sorted(missing_required))
        )
    return tuple(sorted(entries, key=lambda entry: (entry.kind, entry.path)))


def build_index(
    root: Path,
    candidate_sha: str,
    specs: Sequence[str],
    *,
    generated_at_utc: str,
    require_bound_kinds: Iterable[str] = (),
    output: Path | None = None,
) -> dict[str, object]:
    candidate_sha = _validate_candidate_sha(candidate_sha)
    generated_at_utc = _validate_generated_at(generated_at_utc)
    entries = collect_entries(
        root,
        candidate_sha,
        specs,
        require_bound_kinds=require_bound_kinds,
        output=output,
    )
    entry_payload = [entry.as_json() for entry in entries]
    evidence_set_sha256 = hashlib.sha256(
        _canonical_bytes(entry_payload)
    ).hexdigest()
    return {
        "schemaVersion": SCHEMA_VERSION,
        "candidateSha": candidate_sha,
        "generatedAtUtc": generated_at_utc,
        "entryCount": len(entry_payload),
        "candidateBoundEntryCount": sum(
            1 for entry in entries if entry.candidate_bound
        ),
        "evidenceSetSha256": evidence_set_sha256,
        "entries": entry_payload,
    }


def publish_index(
    output: Path,
    payload: Mapping[str, object],
    *,
    root: Path | None = None,
) -> None:
    publication_root = (
        root.expanduser().resolve()
        if root is not None
        else output.expanduser().resolve(strict=False).parent
    )
    output_resolved = _validate_output_path(publication_root, output)
    protected_identities = (
        _payload_evidence_identities(publication_root, payload)
        if root is not None
        else set()
    )
    output_identity = _existing_regular_identity(output_resolved, label="output index")
    if output_identity is not None and output_identity in protected_identities:
        raise EvidenceIndexError(
            "the output index cannot reuse indexed evidence through a hard link"
        )

    output_resolved.parent.mkdir(parents=True, exist_ok=True)
    # Recheck after directory creation so a symlinked parent cannot be introduced
    # through a pre-existing path component and silently redirect publication.
    output_resolved = _validate_output_path(publication_root, output)
    encoded = _canonical_bytes(payload)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{output_resolved.name}.",
        suffix=".tmp",
        dir=output_resolved.parent,
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(encoded)
            handle.flush()
            os.fsync(handle.fileno())

        output_resolved = _validate_output_path(publication_root, output)
        output_identity = _existing_regular_identity(
            output_resolved,
            label="output index",
        )
        if output_identity is not None and output_identity in protected_identities:
            raise EvidenceIndexError(
                "the output index became a hard-link alias of indexed evidence"
            )
        os.replace(temporary, output_resolved)
        directory_fd = os.open(output_resolved.parent, os.O_RDONLY)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
    except (OSError, EvidenceIndexError) as exc:
        temporary.unlink(missing_ok=True)
        if isinstance(exc, EvidenceIndexError):
            raise
        raise EvidenceIndexError(f"could not publish evidence index: {exc}") from exc


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--candidate-sha", required=True)
    parser.add_argument("--entry", action="append", default=[], metavar="KIND=PATH")
    parser.add_argument("--require-bound-kind", action="append", default=[])
    parser.add_argument("--generated-at-utc", required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    root = args.root.resolve()
    output = args.output if args.output.is_absolute() else root / args.output
    try:
        payload = build_index(
            root,
            args.candidate_sha,
            args.entry,
            generated_at_utc=args.generated_at_utc,
            require_bound_kinds=args.require_bound_kind,
            output=output,
        )
        publish_index(output, payload, root=root)
    except EvidenceIndexError as exc:
        print(f"release evidence index error: {exc}", file=os.sys.stderr)
        return 1
    print(
        f"indexed {payload['entryCount']} release evidence files for "
        f"{payload['candidateSha']} ({payload['evidenceSetSha256']})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
