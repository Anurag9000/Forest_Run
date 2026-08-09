#!/usr/bin/env python3
"""Finalize installed-identity downstream integration with one intentional repeated test anchor."""

from pathlib import Path

import migrate_installed_identity_release_integration as first

ROOT = Path(__file__).resolve().parents[1]
SELF = Path(__file__)
V2_WORKFLOW = ROOT / ".github/workflows/installed-identity-release-integration-v2.yml"


def guarded_replace(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    expected = 2 if label == "readiness direct mismatch matrix" else 1
    count = text.count(old)
    if count != expected:
        raise SystemExit(
            f"{label}: expected {expected} anchor(s) in {path}, found {count}"
        )
    path.write_text(text.replace(old, new), encoding="utf-8")


def main() -> None:
    first.replace_once = guarded_replace
    first.main()
    for path in (V2_WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
