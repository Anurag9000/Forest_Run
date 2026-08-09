#!/usr/bin/env python3
"""Complete installed-identity release integration after overlapping readiness test anchors."""

from pathlib import Path

import migrate_installed_identity_release_integration as migration

ROOT = Path(__file__).resolve().parents[1]
SELF = Path(__file__)
TEMPORARY = (
    ROOT / "scripts/migrate_installed_identity_release_integration.py",
    ROOT / "scripts/finalize_installed_identity_release_integration.py",
    ROOT / ".github/workflows/installed-identity-release-integration.yml",
    ROOT / ".github/workflows/installed-identity-release-integration-v2.yml",
    ROOT / ".github/workflows/installed-identity-release-integration-v3.yml",
)


def guarded_replace(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if label == "readiness direct mismatch matrix":
        expected = 2
        count = text.count(old)
        if count != expected:
            raise SystemExit(
                f"{label}: expected {expected} anchors in {path}, found {count}"
            )
        path.write_text(text.replace(old, new), encoding="utf-8")
        return
    if label == "readiness symlink direct call matrix":
        count = text.count(old)
        if count != 0:
            raise SystemExit(
                f"{label}: expected the earlier two-call migration to consume this anchor; found {count}"
            )
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor in {path}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    migration.replace_once = guarded_replace
    migration.main()
    for path in (*TEMPORARY, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
