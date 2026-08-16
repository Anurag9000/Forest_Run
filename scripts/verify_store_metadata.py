#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import sys
import tempfile
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

import strict_json

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_METADATA_DIR = ROOT / "release/google-play/metadata/en-US"
SCHEMA_VERSION = 1
LOCALE = "en-US"
HEX_40 = re.compile(r"[0-9a-f]{40}")
HEX_64 = re.compile(r"[0-9a-f]{64}")
FILES = {
    "title.txt": (1, 80, False),
    "short-description.txt": (10, 160, False),
    "full-description.txt": (100, 10_000, True),
}
PLACEHOLDERS = (
    "todo",
    "tba",
    "yourname",
    "change me",
    "changeme",
    "lorem ipsum",
    "insert description",
    "example.com",
)
MAX_METADATA_FILE_BYTES = 64 * 1024
MAX_MANIFEST_BYTES = 64 * 1024
MANIFEST_KEYS = {"schemaVersion", "locale", "candidateSha", "files"}
FILE_EVIDENCE_KEYS = {"file", "characters", "bytes", "lines", "sha256"}


class StoreMetadataError(ValueError):
    pass


@dataclass(frozen=True)
class MetadataFacts:
    file: str
    characters: int
    bytes: int
    lines: int
    sha256: str


def _absolute_lexical(path: Path) -> Path:
    return Path(os.path.abspath(os.fspath(path.expanduser())))


def _admit_metadata_directory(path: Path) -> Path:
    lexical = _absolute_lexical(path)
    try:
        metadata = lexical.lstat()
    except FileNotFoundError as exc:
        raise StoreMetadataError(f"Store metadata directory is missing: {lexical}") from exc
    except OSError as exc:
        raise StoreMetadataError(f"Could not inspect store metadata directory {lexical}: {exc}") from exc
    if stat.S_ISLNK(metadata.st_mode):
        raise StoreMetadataError(f"Store metadata directory must not be a symbolic link: {lexical}")
    if not stat.S_ISDIR(metadata.st_mode):
        raise StoreMetadataError(f"Store metadata path is not a directory: {lexical}")
    return lexical


def _read_regular_bytes(path: Path, *, maximum_bytes: int, label: str) -> bytes:
    try:
        before = path.lstat()
    except FileNotFoundError as exc:
        raise StoreMetadataError(f"Missing {label}: {path}") from exc
    except OSError as exc:
        raise StoreMetadataError(f"Could not inspect {label} {path}: {exc}") from exc
    if stat.S_ISLNK(before.st_mode):
        raise StoreMetadataError(f"{label} must not be a symbolic link: {path}")
    if not stat.S_ISREG(before.st_mode):
        raise StoreMetadataError(f"{label} must be a regular file: {path}")
    if before.st_size <= 0 or before.st_size > maximum_bytes:
        raise StoreMetadataError(f"{label} has invalid byte length: {path}")
    try:
        raw = path.read_bytes()
        after = path.stat()
    except OSError as exc:
        raise StoreMetadataError(f"Could not read {label} {path}: {exc}") from exc
    if (
        len(raw) != before.st_size
        or after.st_size != before.st_size
        or after.st_mtime_ns != before.st_mtime_ns
        or (before.st_ino and after.st_ino != before.st_ino)
    ):
        raise StoreMetadataError(f"{label} changed while being read: {path}")
    return raw


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_metadata_file(path: Path, minimum: int, maximum: int, multiline: bool) -> MetadataFacts:
    raw = _read_regular_bytes(
        path,
        maximum_bytes=MAX_METADATA_FILE_BYTES,
        label="store metadata",
    )
    if raw.startswith(b"\xef\xbb\xbf"):
        raise StoreMetadataError(f"Metadata must not contain a UTF-8 BOM: {path}")
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise StoreMetadataError(f"Metadata is not valid UTF-8: {path}: {exc}") from exc
    if "\r" in text:
        raise StoreMetadataError(f"Metadata must use LF line endings: {path}")
    if unicodedata.normalize("NFC", text) != text:
        raise StoreMetadataError(f"Metadata must be NFC-normalized Unicode: {path}")
    if text != text.strip():
        raise StoreMetadataError(f"Metadata has leading or trailing whitespace: {path}")
    if any(
        (ord(character) < 32 and character != "\n") or ord(character) == 127
        for character in text
    ):
        raise StoreMetadataError(f"Metadata contains control characters: {path}")
    if not multiline and "\n" in text:
        raise StoreMetadataError(f"Metadata must be exactly one line: {path}")
    if multiline:
        lines = text.split("\n")
        if any(line != line.rstrip() for line in lines):
            raise StoreMetadataError(f"Metadata contains trailing line whitespace: {path}")
        if "\n\n\n" in text:
            raise StoreMetadataError(f"Metadata contains excessive blank lines: {path}")
    else:
        lines = [text]
    if not minimum <= len(text) <= maximum:
        raise StoreMetadataError(
            f"Metadata length {len(text)} is outside {minimum}..{maximum}: {path}"
        )
    folded = text.casefold()
    if any(token in folded for token in PLACEHOLDERS):
        raise StoreMetadataError(f"Placeholder text remains in metadata: {path}")
    if re.search(r"<[^>]+>|\[(?:insert|replace|your)[^]]*]", text, re.IGNORECASE):
        raise StoreMetadataError(f"Template markers remain in metadata: {path}")
    return MetadataFacts(
        file=path.name,
        characters=len(text),
        bytes=len(raw),
        lines=len(lines),
        sha256=hashlib.sha256(raw).hexdigest(),
    )


def validate_metadata_directory(metadata_dir: Path) -> list[MetadataFacts]:
    metadata_dir = _admit_metadata_directory(metadata_dir)
    expected = set(FILES) | {"metadata_manifest.json"}
    actual: set[str] = set()
    try:
        entries = list(metadata_dir.iterdir())
    except OSError as exc:
        raise StoreMetadataError(f"Could not enumerate store metadata directory {metadata_dir}: {exc}") from exc
    for path in entries:
        try:
            metadata = path.lstat()
        except OSError as exc:
            raise StoreMetadataError(f"Could not inspect store metadata entry {path}: {exc}") from exc
        if stat.S_ISLNK(metadata.st_mode):
            raise StoreMetadataError(f"Store metadata entry must not be a symbolic link: {path}")
        if not stat.S_ISREG(metadata.st_mode):
            raise StoreMetadataError(f"Store metadata directory contains a non-file entry: {path.name}")
        actual.add(path.name)
    unexpected = actual - expected
    if unexpected:
        raise StoreMetadataError(
            "Store metadata directory contains unrecognized files: "
            + ", ".join(sorted(unexpected))
        )
    return [
        validate_metadata_file(metadata_dir / name, minimum, maximum, multiline)
        for name, (minimum, maximum, multiline) in FILES.items()
    ]


def _atomic_write(path: Path, payload: dict[str, object]) -> None:
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as stream:
            temporary = Path(stream.name)
            json.dump(payload, stream, indent=2, sort_keys=True, allow_nan=False)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        temporary = None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def finalize_metadata(metadata_dir: Path, candidate_sha: str) -> Path:
    candidate_sha = candidate_sha.lower()
    if HEX_40.fullmatch(candidate_sha) is None:
        raise StoreMetadataError("candidate SHA must be 40 hexadecimal characters")
    metadata_dir = _absolute_lexical(metadata_dir)
    if not metadata_dir.exists():
        metadata_dir.mkdir(parents=True, exist_ok=True)
    metadata_dir = _admit_metadata_directory(metadata_dir)
    manifest = metadata_dir / "metadata_manifest.json"
    try:
        stale = manifest.lstat()
    except FileNotFoundError:
        stale = None
    except OSError as exc:
        raise StoreMetadataError(f"Could not inspect stale metadata manifest {manifest}: {exc}") from exc
    if stale is not None:
        if stat.S_ISLNK(stale.st_mode):
            raise StoreMetadataError(f"Metadata manifest must not be a symbolic link: {manifest}")
        if not stat.S_ISREG(stale.st_mode):
            raise StoreMetadataError(f"Metadata manifest must be a regular file: {manifest}")
        manifest.unlink()
    facts = validate_metadata_directory(metadata_dir)
    _atomic_write(
        manifest,
        {
            "schemaVersion": SCHEMA_VERSION,
            "locale": LOCALE,
            "candidateSha": candidate_sha,
            "files": [facts_item.__dict__ for facts_item in facts],
        },
    )
    return manifest


def verify_metadata(metadata_dir: Path, candidate_sha: str) -> list[MetadataFacts]:
    candidate_sha = candidate_sha.lower()
    if HEX_40.fullmatch(candidate_sha) is None:
        raise StoreMetadataError("candidate SHA must be 40 hexadecimal characters")
    metadata_dir = _admit_metadata_directory(metadata_dir)
    facts = validate_metadata_directory(metadata_dir)
    manifest_path = metadata_dir / "metadata_manifest.json"
    raw_bytes = _read_regular_bytes(
        manifest_path,
        maximum_bytes=MAX_MANIFEST_BYTES,
        label="metadata manifest",
    )
    try:
        raw = strict_json.loads(
            raw_bytes,
            label="metadata manifest",
            maximum_bytes=MAX_MANIFEST_BYTES,
            require_object=True,
        )
    except strict_json.StrictJsonError as exc:
        raise StoreMetadataError(f"Invalid metadata manifest: {exc}") from exc
    assert isinstance(raw, dict)
    if set(raw) != MANIFEST_KEYS:
        raise StoreMetadataError("Metadata manifest fields are incomplete or contain extras")
    if raw.get("schemaVersion") != SCHEMA_VERSION:
        raise StoreMetadataError("Metadata manifest schemaVersion must equal 1")
    if raw.get("locale") != LOCALE:
        raise StoreMetadataError(f"Metadata manifest locale must equal {LOCALE}")
    if raw.get("candidateSha") != candidate_sha:
        raise StoreMetadataError("Metadata manifest candidate does not match release candidate")
    entries = raw.get("files")
    if not isinstance(entries, list):
        raise StoreMetadataError("Metadata manifest files must be an array")
    by_name: dict[str, dict[str, object]] = {}
    for entry in entries:
        if not isinstance(entry, dict) or set(entry) != FILE_EVIDENCE_KEYS:
            raise StoreMetadataError("Metadata manifest contains an invalid file entry")
        if not isinstance(entry.get("file"), str):
            raise StoreMetadataError("Metadata manifest contains an invalid file entry")
        filename = entry["file"]
        if filename in by_name:
            raise StoreMetadataError(f"Duplicate metadata manifest entry: {filename}")
        by_name[filename] = entry
    if set(by_name) != set(FILES):
        raise StoreMetadataError("Metadata manifest file set is incomplete or contains extras")
    for item in facts:
        entry = by_name[item.file]
        expected = item.__dict__
        if any(entry.get(key) != value for key, value in expected.items()):
            raise StoreMetadataError(f"Metadata manifest evidence is stale: {item.file}")
        if HEX_64.fullmatch(str(entry.get("sha256", ""))) is None:
            raise StoreMetadataError(f"Metadata digest is malformed: {item.file}")
    return facts


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Finalize or verify candidate-bound Play metadata")
    parser.add_argument("--metadata-dir", type=Path, default=DEFAULT_METADATA_DIR)
    parser.add_argument("--candidate-sha", required=True)
    parser.add_argument("--finalize", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.finalize:
            path = finalize_metadata(args.metadata_dir, args.candidate_sha)
            print(path)
        else:
            facts = verify_metadata(args.metadata_dir, args.candidate_sha)
            print(f"Verified {len(facts)} metadata files for {args.candidate_sha.lower()}")
    except StoreMetadataError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
