#!/usr/bin/env python3
"""Complete Play signing identity migration through the aggregate test surface."""

from pathlib import Path

import finalize_play_signing_identity as second

ROOT = Path(__file__).resolve().parents[1]
SELF = Path(__file__)
TEMPORARY = (
    ROOT / "scripts/migrate_play_signing_identity.py",
    ROOT / "scripts/finalize_play_signing_identity.py",
    ROOT / ".github/workflows/play-signing-identity-migration.yml",
    ROOT / ".github/workflows/play-signing-identity-migration-v2.yml",
    ROOT / ".github/workflows/play-signing-identity-migration-v3.yml",
)


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor in {path}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    second.main()

    aggregate_tests = ROOT / "scripts/test_validate_device_acceptance_aggregate.py"
    replace_once(
        aggregate_tests,
        '''                "version_code": 1,\n                "certificate_sha256": digest("certificate"),\n''',
        '''                "version_code": 1,\n                "upload_certificate_sha256": digest("upload-certificate"),\n                "app_signing_certificate_sha256": digest("app-signing-certificate"),\n''',
        "aggregate test candidate signing identities",
    )

    for path in (*TEMPORARY, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
