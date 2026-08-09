#!/usr/bin/env python3
"""Finish Play signing identity migration across aggregate and compiler fixtures."""

from pathlib import Path

import migrate_play_signing_identity as first

ROOT = Path(__file__).resolve().parents[1]
SELF = Path(__file__)
V1_SCRIPT = ROOT / "scripts/migrate_play_signing_identity.py"
V1_WORKFLOW = ROOT / ".github/workflows/play-signing-identity-migration.yml"
V2_WORKFLOW = ROOT / ".github/workflows/play-signing-identity-migration-v2.yml"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor in {path}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def patch_aggregate() -> None:
    producer = ROOT / "scripts/aggregate_device_acceptance.py"
    replace_once(producer, "SCHEMA_VERSION = 1\n", "SCHEMA_VERSION = 2\n", "aggregate schema")
    replace_once(
        producer,
        '''            "version_code": candidate["version_code"],\n            "certificate_sha256": candidate["certificate_sha256"],\n''',
        '''            "version_code": candidate["version_code"],\n            "upload_certificate_sha256": validation.upload_certificate_sha256,\n            "app_signing_certificate_sha256": validation.app_signing_certificate_sha256,\n''',
        "aggregate certificate identities",
    )

    validator = ROOT / "scripts/validate_device_acceptance_aggregate.py"
    replace_once(validator, "SCHEMA_VERSION = 1\n", "SCHEMA_VERSION = 2\n", "aggregate validator schema")
    replace_once(
        validator,
        '''            "version_code",\n            "certificate_sha256",\n''',
        '''            "version_code",\n            "upload_certificate_sha256",\n            "app_signing_certificate_sha256",\n''',
        "aggregate validator candidate keys",
    )
    replace_once(
        validator,
        '''    certificate_sha256 = _sha(\n        candidate["certificate_sha256"],\n        f"{label}.candidate.certificate_sha256",\n        SHA256_RE,\n    )\n''',
        '''    upload_certificate_sha256 = _sha(\n        candidate["upload_certificate_sha256"],\n        f"{label}.candidate.upload_certificate_sha256",\n        SHA256_RE,\n    )\n    app_signing_certificate_sha256 = _sha(\n        candidate["app_signing_certificate_sha256"],\n        f"{label}.candidate.app_signing_certificate_sha256",\n        SHA256_RE,\n    )\n''',
        "aggregate validator cert parse",
    )
    replace_once(
        validator,
        '''            "version_code": version_code,\n            "certificate_sha256": certificate_sha256,\n''',
        '''            "version_code": version_code,\n            "upload_certificate_sha256": upload_certificate_sha256,\n            "app_signing_certificate_sha256": app_signing_certificate_sha256,\n''',
        "aggregate validator normalized candidate",
    )


def patch_compile_fixture() -> None:
    path = ROOT / "scripts/test_compile_device_acceptance.py"
    replace_once(
        path,
        '''CERTIFICATE_SHA = "3" * 64\nCOMMIT_SHA = "1" * 40\n''',
        '''UPLOAD_CERTIFICATE_SHA = "3" * 64\nAPP_SIGNING_CERTIFICATE_SHA = "4" * 64\nCOMMIT_SHA = "1" * 40\n''',
        "compile fixture cert constants",
    )
    replace_once(
        path,
        '''        "certificate_sha256": CERTIFICATE_SHA,\n        "signed": True,\n''',
        '''        "app_signing_certificate_sha256": APP_SIGNING_CERTIFICATE_SHA,\n        "signed": True,\n''',
        "compile session cert",
    )
    replace_once(
        path,
        '''            "certificate_sha256": CERTIFICATE_SHA,\n            "store_delivery": {\n''',
        '''            "upload_certificate_sha256": UPLOAD_CERTIFICATE_SHA,\n            "store_delivery": {\n''',
        "compile candidate upload cert",
    )
    replace_once(
        path,
        '''                "certificate_sha256": CERTIFICATE_SHA,\n''',
        '''                "app_signing_certificate_sha256": APP_SIGNING_CERTIFICATE_SHA,\n''',
        "compile store app cert",
    )


def main() -> None:
    first.migrate_device_validator()
    first.migrate_device_compiler()
    first.migrate_device_fixture()
    first.migrate_human()
    first.migrate_governance()
    first.migrate_readiness()
    first.migrate_docs()
    patch_aggregate()
    patch_compile_fixture()

    for path in (V1_SCRIPT, V1_WORKFLOW, V2_WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
