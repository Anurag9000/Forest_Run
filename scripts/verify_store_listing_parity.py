#!/usr/bin/env python3
"""Verify that Play listing text is an exact projection of docs/STORE_LISTING.md.

The checked-in Markdown remains the human-authored product truth. Candidate-bound
Play metadata is allowed to add evidence around those bytes, but not to silently
rewrite public copy. This gate therefore extracts the three fenced `text` blocks
owned by the canonical listing and compares them byte-for-text with the release
metadata before candidate finalization.
"""

from __future__ import annotations

import argparse
import sys
import unicodedata
from pathlib import Path
from typing import Mapping, Sequence

from verify_store_metadata import StoreMetadataError, validate_metadata_directory

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_LISTING_SOURCE = ROOT / "docs" / "STORE_LISTING.md"
DEFAULT_METADATA_DIR = ROOT / "release" / "google-play" / "metadata" / "en-US"
MAX_LISTING_BYTES = 256 * 1024

SECTION_TO_FILE = {
    "Google Play title": "title.txt",
    "Short description": "short-description.txt",
    "Full description": "full-description.txt",
}


class StoreListingParityError(ValueError):
    """Raised when canonical listing ownership or metadata parity is invalid."""


def _read_listing(path: Path) -> str:
    try:
        raw = path.read_bytes()
    except FileNotFoundError as exc:
        raise StoreListingParityError(f"Canonical store listing is missing: {path}") from exc
    except OSError as exc:
        raise StoreListingParityError(f"Could not read canonical store listing {path}: {exc}") from exc
    if not raw or len(raw) > MAX_LISTING_BYTES:
        raise StoreListingParityError("Canonical store listing has an invalid byte length")
    if raw.startswith(b"\xef\xbb\xbf"):
        raise StoreListingParityError("Canonical store listing must not contain a UTF-8 BOM")
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise StoreListingParityError(f"Canonical store listing is not valid UTF-8: {exc}") from exc
    if "\r" in text:
        raise StoreListingParityError("Canonical store listing must use LF line endings")
    if unicodedata.normalize("NFC", text) != text:
        raise StoreListingParityError("Canonical store listing must be NFC-normalized Unicode")
    return text


def extract_canonical_metadata(listing_source: Path) -> dict[str, str]:
    """Extract the one exact fenced text block owned by each canonical heading."""

    lines = _read_listing(listing_source).split("\n")
    extracted: dict[str, str] = {}

    for heading, filename in SECTION_TO_FILE.items():
        marker = f"## {heading}"
        heading_indexes = [index for index, line in enumerate(lines) if line == marker]
        if len(heading_indexes) != 1:
            raise StoreListingParityError(
                f"Canonical store listing must contain exactly one {marker!r} heading"
            )

        cursor = heading_indexes[0] + 1
        while cursor < len(lines) and lines[cursor] == "":
            cursor += 1
        if cursor >= len(lines) or lines[cursor] != "```text":
            raise StoreListingParityError(
                f"{marker} must be followed by exactly one fenced text block"
            )
        body_start = cursor + 1
        cursor = body_start
        while cursor < len(lines) and lines[cursor] != "```":
            if lines[cursor].startswith("```"):
                raise StoreListingParityError(
                    f"{marker} contains an unexpected nested or mismatched code fence"
                )
            cursor += 1
        if cursor >= len(lines):
            raise StoreListingParityError(f"{marker} text block is not closed")

        body = "\n".join(lines[body_start:cursor])
        if not body or body != body.strip():
            raise StoreListingParityError(
                f"{marker} text must be nonblank with no leading or trailing whitespace"
            )
        extracted[filename] = body

    return extracted


def verify_listing_parity(listing_source: Path, metadata_dir: Path) -> Mapping[str, str]:
    """Validate metadata syntax, then require exact equality with canonical copy."""

    canonical = extract_canonical_metadata(listing_source)
    try:
        validate_metadata_directory(metadata_dir)
    except StoreMetadataError as exc:
        raise StoreListingParityError(f"Play metadata is structurally invalid: {exc}") from exc

    for filename, expected in canonical.items():
        path = metadata_dir / filename
        try:
            actual = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as exc:
            raise StoreListingParityError(f"Could not read Play metadata {path}: {exc}") from exc
        if actual != expected:
            raise StoreListingParityError(
                f"{filename} does not exactly match the canonical {listing_source.name} text block"
            )

    return canonical


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Verify checked-in Play metadata against canonical store-listing copy"
    )
    parser.add_argument("--listing-source", type=Path, default=DEFAULT_LISTING_SOURCE)
    parser.add_argument("--metadata-dir", type=Path, default=DEFAULT_METADATA_DIR)
    args = parser.parse_args(argv)

    try:
        canonical = verify_listing_parity(args.listing_source, args.metadata_dir)
    except StoreListingParityError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    print(
        f"Verified {len(canonical)} Play metadata files against "
        f"{args.listing_source.resolve()}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
