#!/usr/bin/env python3
"""One-shot guarded reconciliation for the final candidate-evidence chain docs."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SELF = Path(__file__)
WORKFLOW = ROOT / ".github/workflows/final-release-evidence-docs.yml"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor in {path}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_all(path: Path, old: str, new: str, expected: int, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{label}: expected {expected} anchors in {path}, found {count}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def assert_source_ready() -> None:
    governance = (ROOT / "scripts/validate_release_governance.py").read_text(encoding="utf-8")
    readiness = (ROOT / "scripts/validate_release_readiness.py").read_text(encoding="utf-8")
    matrix = (ROOT / "scripts/validate_installed_identity_matrix.py").read_text(encoding="utf-8")
    for token in ("play_delivery_record", "installed_identity_matrix", "play_delivery.load_and_validate"):
        if token not in governance:
            raise SystemExit(f"governance integration missing {token}")
    for token in ("play_delivery", "installed_identity_matrix", "play_delivery.load_and_validate"):
        if token not in readiness:
            raise SystemExit(f"readiness integration missing {token}")
    if "version_code: int" not in matrix:
        raise SystemExit("installed identity matrix version binding is missing")
    for relative in (
        "scripts/migrate_installed_identity_release_integration.py",
        "scripts/finalize_installed_identity_release_integration.py",
        "scripts/complete_installed_identity_release_integration.py",
        "scripts/migrate_play_delivery_release_integration.py",
        ".github/workflows/installed-identity-release-integration.yml",
        ".github/workflows/installed-identity-release-integration-v2.yml",
        ".github/workflows/installed-identity-release-integration-v3.yml",
        ".github/workflows/play-delivery-release-integration.yml",
    ):
        if (ROOT / relative).exists():
            raise SystemExit(f"temporary integration mutator still exists: {relative}")


def reconcile_index() -> None:
    path = ROOT / "docs/RELEASE_EVIDENCE_INDEX.md"
    replace_once(
        path,
        '''  --entry device_aggregate=release/evidence/device-acceptance-aggregate.json \\\n  --entry human_acceptance=release/evidence/human-acceptance.json \\\n  --entry release_governance=release/evidence/release-governance.json \\\n''',
        '''  --entry device_aggregate=release/evidence/device-acceptance-aggregate.json \\\n  --entry installed_identity_matrix=release/evidence/installed-identity-matrix.json \\\n  --entry play_delivery=release/evidence/play-delivery.json \\\n  --entry human_acceptance=release/evidence/human-acceptance.json \\\n  --entry release_governance=release/evidence/release-governance.json \\\n''',
        "release index principal entries",
    )
    replace_all(
        path,
        '''  --require-bound-kind device_aggregate \\\n  --require-bound-kind human_acceptance \\\n  --require-bound-kind release_governance \\\n''',
        '''  --require-bound-kind device_aggregate \\\n  --require-bound-kind installed_identity_matrix \\\n  --require-bound-kind play_delivery \\\n  --require-bound-kind human_acceptance \\\n  --require-bound-kind release_governance \\\n''',
        2,
        "release index required evidence chain",
    )
    replace_once(
        path,
        '''- physical-device acceptance sessions;\n- detailed human gameplay/accessibility/presentation acceptance;\n''',
        '''- physical-device acceptance sessions;\n- objective installed-package identity on every physical device;\n- candidate-bound Play internal-track upload/install/update delivery evidence;\n- detailed human gameplay/accessibility/presentation acceptance;\n''',
        "release index pipeline prose",
    )
    replace_once(
        path,
        '''After the index independently verifies, run [`validate_release_readiness.py`](../scripts/validate_release_readiness.py) as described in [`RELEASE_READINESS.md`](RELEASE_READINESS.md). The readiness gate re-runs the device, human, governance, and index validators and then proves that the exact revalidated manifest paths/digests are the exact indexed files and that the indexed `signed_bundle` digest equals the artifact approved by all acceptance layers.\n''',
        '''After the index independently verifies, run [`validate_release_readiness.py`](../scripts/validate_release_readiness.py) as described in [`RELEASE_READINESS.md`](RELEASE_READINESS.md). The readiness gate re-runs the device, installed-identity-matrix, Play-delivery, human, governance, and index validators and then proves that the exact revalidated manifest paths/digests are the exact indexed files and that the indexed `signed_bundle` digest equals the artifact approved by all acceptance layers.\n''',
        "release index readiness prose",
    )


def reconcile_governance() -> None:
    path = ROOT / "docs/RELEASE_GOVERNANCE_EVIDENCE.md"
    replace_once(
        path,
        '''A governance manifest is downstream of both:\n\n1. a valid `device-acceptance.json`; and\n2. a valid `human-acceptance.json` that references that exact device manifest.\n\nThe validator revalidates both manifests and rejects disagreement in:\n''',
        '''A governance manifest is downstream of four independently valid principal manifests:\n\n1. `device-acceptance.json`;\n2. `installed-identity-matrix.json`, with one measured Play-delivered APK identity per physical session;\n3. `play-delivery.json`, with the external internal-track upload/install/update record bound to that matrix; and\n4. `human-acceptance.json` referencing the same physical candidate.\n\nThe validator revalidates all four layers and rejects disagreement in:\n''',
        "governance prerequisites",
    )
    replace_once(
        path,
        '''- `dependency_verification_report`;\n- `asset_provenance`;\n''',
        '''- `dependency_verification_report`;\n- `installed_identity_matrix`;\n- `play_delivery_record`;\n- `asset_provenance`;\n''',
        "governance evidence list",
    )
    replace_once(
        path,
        '''Governance therefore cannot be approved against one artifact while physical or human acceptance describes another.\n''',
        '''Governance therefore cannot be approved against one artifact while physical, installed-package, Play-delivery, or human evidence describes another. The Play-delivery validator explicitly requires the `internal` track plus upload/release/tester-install/update assertions and hashes the corresponding external Play Console/receipt evidence; it never infers a track from the package installer.\n''',
        "governance cross-layer prose",
    )


def reconcile_readiness() -> None:
    path = ROOT / "docs/RELEASE_READINESS.md"
    replace_once(
        path,
        '''- a compiled, valid physical/store `device-acceptance.json`;\n- a compiled, valid `human-acceptance.json`;\n- a compiled, valid `release-governance.json`;\n''',
        '''- a compiled, valid physical `device-acceptance.json`;\n- a compiled, valid `installed-identity-matrix.json`;\n- a compiled, valid `play-delivery.json`;\n- a compiled, valid `human-acceptance.json`;\n- a compiled, valid `release-governance.json`;\n''',
        "readiness required inputs",
    )
    replace_once(
        path,
        '''1. `validate_device_acceptance.load_and_validate(...)` revalidates physical/store acceptance.\n2. `validate_human_acceptance.load_and_validate(...)` revalidates detailed human gameplay/accessibility/presentation acceptance.\n3. `validate_release_governance.load_and_validate(...)` revalidates candidate-bound security/licensing/privacy/store/presentation governance.\n4. `verify_release_evidence_index.verify_index(...)` independently reconstructs and verifies the final evidence index.\n''',
        '''1. `validate_device_acceptance.load_and_validate(...)` revalidates physical acceptance.\n2. `validate_installed_identity_matrix.load_and_validate(...)` revalidates one measured Play-delivered package identity per physical session.\n3. `validate_play_delivery_evidence.load_and_validate(...)` revalidates the external internal-track upload/install/update evidence.\n4. `validate_human_acceptance.load_and_validate(...)` revalidates detailed human gameplay/accessibility/presentation acceptance.\n5. `validate_release_governance.load_and_validate(...)` revalidates candidate-bound security/licensing/privacy/store/presentation governance.\n6. `verify_release_evidence_index.verify_index(...)` independently reconstructs and verifies the final evidence index.\n''',
        "readiness delegated validators",
    )
    replace_once(
        path,
        '''  --device-acceptance release/evidence-root/device-acceptance.json \\\n  --human-acceptance release/evidence-root/human-acceptance.json \\\n  --release-governance release/evidence-root/release-governance.json \\\n''',
        '''  --device-acceptance release/evidence-root/device-acceptance.json \\\n  --installed-identity-matrix release/evidence-root/installed-identity-matrix.json \\\n  --play-delivery release/evidence-root/play-delivery.json \\\n  --human-acceptance release/evidence-root/human-acceptance.json \\\n  --release-governance release/evidence-root/release-governance.json \\\n''',
        "readiness invocation",
    )
    replace_once(
        path,
        '''- `human_acceptance`;\n- `release_governance`;\n''',
        '''- `installed_identity_matrix`;\n- `play_delivery`;\n- `human_acceptance`;\n- `release_governance`;\n''',
        "readiness required index kinds",
    )
    replace_once(
        path,
        '''4. run the physical device/scenario/performance matrix and compile `device-acceptance.json`;\n5. run detailed gameplay, TalkBack/accessibility, Garden/ghost, art/audio/haptic human review and compile `human-acceptance.json`;\n6. complete security/licensing/privacy/Play/presentation/provenance decisions and compile `release-governance.json`;\n7. build the stable release-evidence index containing the signed bundle and every material evidence file;\n8. independently verify the index;\n9. run `validate_release_readiness.py` against the same evidence root;\n10. have the final independent reviewer inspect the readiness summary, underlying evidence, and external consoles/records before the release/tag decision.\n''',
        '''4. run the physical device/scenario/performance matrix and compile `device-acceptance.json`;\n5. collect one installed-package identity record per physical session and compile `installed-identity-matrix.json`;\n6. retain Play Console/upload/tester/install/update evidence and compile `play-delivery.json`;\n7. run detailed gameplay, TalkBack/accessibility, Garden/ghost, art/audio/haptic human review and compile `human-acceptance.json`;\n8. complete security/licensing/privacy/Play/presentation/provenance decisions and compile `release-governance.json`;\n9. build the stable release-evidence index containing the signed bundle and every material evidence file;\n10. independently verify the index;\n11. run `validate_release_readiness.py` against the same evidence root;\n12. have the final independent reviewer inspect the readiness summary, underlying evidence, and external consoles/records before the release/tag decision.\n''',
        "readiness final order",
    )


def reconcile_device_and_readme() -> None:
    device = ROOT / "docs/DEVICE_ACCEPTANCE.md"
    marker = "## Installed-package identity follow-up\n"
    text = device.read_text(encoding="utf-8")
    if marker in text:
        raise SystemExit("device acceptance installed identity follow-up already exists")
    device.write_text(
        text.rstrip()
        + '''\n\n## Installed-package identity follow-up\n\nThe physical manifest records the expected internal-store and delivered app-signing identity, but those fields remain operator-entered acceptance facts. For every accepted physical session, additionally run `scripts/collect_installed_candidate_identity.py` and then compile all records with `scripts/compile_installed_identity_matrix.py`. See [`INSTALLED_CANDIDATE_IDENTITY.md`](INSTALLED_CANDIDATE_IDENTITY.md). The installed layer objectively verifies Google Play installer attribution, package/version, pulled base/split APK bytes, and the Play app-signing certificate; it deliberately does not infer the specific Play track. The separate `play-delivery.json` layer retains and reviews the external Play Console internal-track/upload/install/update evidence.\n''',
        encoding="utf-8",
    )

    readme = ROOT / "README.md"
    replace_once(
        readme,
        '''| [`docs/HUMAN_ACCEPTANCE.md`](docs/HUMAN_ACCEPTANCE.md) | Candidate-bound gameplay, TalkBack/accessibility, and presentation review matrix |\n| [`docs/RELEASE_GOVERNANCE_EVIDENCE.md`](docs/RELEASE_GOVERNANCE_EVIDENCE.md) | Security, licensing, privacy, store, provenance, release-note, and final decision evidence |\n| [`docs/RELEASE.md`](docs/RELEASE.md) | Correctness, validation, packaging, hardware, signing, and store checklist |\n''',
        '''| [`docs/HUMAN_ACCEPTANCE.md`](docs/HUMAN_ACCEPTANCE.md) | Candidate-bound gameplay, TalkBack/accessibility, and presentation review matrix |\n| [`docs/INSTALLED_CANDIDATE_IDENTITY.md`](docs/INSTALLED_CANDIDATE_IDENTITY.md) | Measured Play-delivered package/split/signing identity and five-device installed matrix |\n| [`docs/RELEASE_GOVERNANCE_EVIDENCE.md`](docs/RELEASE_GOVERNANCE_EVIDENCE.md) | Security, licensing, privacy, store, provenance, release-note, and final decision evidence |\n| [`docs/RELEASE_READINESS.md`](docs/RELEASE_READINESS.md) | Final cross-layer physical/install/Play/human/governance/index readiness gate |\n| [`docs/RELEASE.md`](docs/RELEASE.md) | Correctness, validation, packaging, hardware, signing, and store checklist |\n''',
        "README evidence docs table",
    )


def main() -> None:
    assert_source_ready()
    reconcile_index()
    reconcile_governance()
    reconcile_readiness()
    reconcile_device_and_readme()
    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
