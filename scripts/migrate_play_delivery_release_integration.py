#!/usr/bin/env python3
"""One-shot integration making Play delivery a mandatory governance/readiness gate."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SELF = Path(__file__)
WORKFLOW = ROOT / ".github/workflows/play-delivery-release-integration.yml"


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


def migrate_governance() -> None:
    path = ROOT / "scripts/validate_release_governance.py"
    replace_once(
        path,
        "import validate_installed_identity_matrix as installed_matrix\n",
        "import validate_installed_identity_matrix as installed_matrix\nimport validate_play_delivery_evidence as play_delivery\n",
        "governance Play import",
    )
    replace_once(
        path,
        '''        "installed_identity_matrix",\n        "asset_provenance",\n''',
        '''        "installed_identity_matrix",\n        "play_delivery_record",\n        "asset_provenance",\n''',
        "governance Play evidence kind",
    )
    replace_once(
        path,
        '''    installed_identity_matrix_sha256: str\n    decision_count: int\n''',
        '''    installed_identity_matrix_sha256: str\n    play_delivery_sha256: str\n    decision_count: int\n''',
        "governance Play summary field",
    )
    replace_once(
        path,
        '''            "installed_identity_matrix_sha256": self.installed_identity_matrix_sha256,\n            "decision_count": self.decision_count,\n''',
        '''            "installed_identity_matrix_sha256": self.installed_identity_matrix_sha256,\n            "play_delivery_sha256": self.play_delivery_sha256,\n            "decision_count": self.decision_count,\n''',
        "governance Play summary json",
    )
    replace_once(
        path,
        '''    if installed_matrix_summary.device_acceptance_sha256 != device_digest:\n        raise GovernanceError("installed identity matrix references a different device acceptance manifest")\n\n    decisions = _mapping(root["decisions"], "decisions")\n''',
        '''    if installed_matrix_summary.device_acceptance_sha256 != device_digest:\n        raise GovernanceError("installed identity matrix references a different device acceptance manifest")\n\n    _, play_delivery_digest, play_delivery_path = evidence_results["play_delivery_record"]\n    try:\n        play_delivery_summary = play_delivery.load_and_validate(play_delivery_path)\n    except play_delivery.PlayDeliveryError as exc:\n        raise GovernanceError(f"Play delivery evidence is invalid: {exc}") from exc\n    if play_delivery_summary.candidate_sha != candidate_sha:\n        raise GovernanceError("Play delivery candidate does not match governance candidate")\n    if play_delivery_summary.artifact_sha256 != artifact_sha:\n        raise GovernanceError("Play delivery artifact does not match governance candidate")\n    if play_delivery_summary.upload_certificate_sha256 != upload_certificate_sha:\n        raise GovernanceError("Play delivery upload certificate does not match governance candidate")\n    if play_delivery_summary.app_signing_certificate_sha256 != app_signing_certificate_sha:\n        raise GovernanceError("Play delivery app-signing certificate does not match governance candidate")\n    if play_delivery_summary.installed_identity_matrix_sha256 != installed_matrix_digest:\n        raise GovernanceError("Play delivery references a different installed identity matrix")\n\n    decisions = _mapping(root["decisions"], "decisions")\n''',
        "governance Play validation",
    )
    replace_once(
        path,
        '''        installed_identity_matrix_sha256=installed_matrix_digest,\n        decision_count=len(decisions),\n''',
        '''        installed_identity_matrix_sha256=installed_matrix_digest,\n        play_delivery_sha256=play_delivery_digest,\n        decision_count=len(decisions),\n''',
        "governance Play summary construction",
    )


def migrate_governance_fixture() -> None:
    path = ROOT / "scripts/test_validate_release_governance.py"
    replace_once(
        path,
        '''import compile_installed_identity_matrix\nimport compile_release_governance as compiler\nimport test_installed_identity_matrix as matrix_fixture\n''',
        '''import compile_installed_identity_matrix\nimport compile_play_delivery_evidence\nimport compile_release_governance as compiler\nimport test_installed_identity_matrix as matrix_fixture\nimport test_play_delivery_evidence as play_fixture\n''',
        "governance Play fixture imports",
    )
    replace_once(
        path,
        '''    compile_installed_identity_matrix.compile_file(matrix_draft_path, matrix_path)\n\n    evidence: dict[str, str] = {}\n''',
        '''    compile_installed_identity_matrix.compile_file(matrix_draft_path, matrix_path)\n\n    play_draft = play_fixture.prepare(root)\n    play_draft_path = root / "play-delivery-draft.json"\n    play_path = root / "play-delivery.json"\n    play_draft_path.write_text(\n        json.dumps(play_draft, indent=2, sort_keys=True) + "\\n",\n        encoding="utf-8",\n    )\n    compile_play_delivery_evidence.compile_file(play_draft_path, play_path)\n\n    evidence: dict[str, str] = {}\n''',
        "governance Play fixture compile",
    )
    replace_once(
        path,
        '''        if kind == "installed_identity_matrix":\n            evidence[kind] = "installed-identity-matrix.json"\n            continue\n''',
        '''        if kind == "installed_identity_matrix":\n            evidence[kind] = "installed-identity-matrix.json"\n            continue\n        if kind == "play_delivery_record":\n            evidence[kind] = "play-delivery.json"\n            continue\n''',
        "governance Play fixture evidence",
    )
    replace_once(
        path,
        '''            self.assertRegex(summary.installed_identity_matrix_sha256, r"^[0-9a-f]{64}$")\n            self.assertRegex(compiled["device_acceptance"]["sha256"], r"^[0-9a-f]{64}$")\n''',
        '''            self.assertRegex(summary.installed_identity_matrix_sha256, r"^[0-9a-f]{64}$")\n            self.assertRegex(summary.play_delivery_sha256, r"^[0-9a-f]{64}$")\n            self.assertRegex(compiled["device_acceptance"]["sha256"], r"^[0-9a-f]{64}$")\n''',
        "governance Play summary assertion",
    )


def migrate_readiness() -> None:
    path = ROOT / "scripts/validate_release_readiness.py"
    replace_once(
        path,
        "import validate_installed_identity_matrix as installed_matrix\n",
        "import validate_installed_identity_matrix as installed_matrix\nimport validate_play_delivery_evidence as play_delivery\n",
        "readiness Play import",
    )
    replace_once(
        path,
        '''        "installed_identity_matrix",\n        "release_governance",\n''',
        '''        "installed_identity_matrix",\n        "play_delivery",\n        "release_governance",\n''',
        "readiness Play required kind",
    )
    replace_once(
        path,
        '''    installed_identity_matrix_sha256: str\n    governance_sha256: str\n''',
        '''    installed_identity_matrix_sha256: str\n    play_delivery_sha256: str\n    governance_sha256: str\n''',
        "readiness Play summary field",
    )
    replace_once(
        path,
        '''            "installed_identity_matrix_sha256": self.installed_identity_matrix_sha256,\n            "governance_sha256": self.governance_sha256,\n''',
        '''            "installed_identity_matrix_sha256": self.installed_identity_matrix_sha256,\n            "play_delivery_sha256": self.play_delivery_sha256,\n            "governance_sha256": self.governance_sha256,\n''',
        "readiness Play summary json",
    )
    replace_once(
        path,
        '''    installed_identity_matrix: Path,\n    governance_manifest: Path,\n''',
        '''    installed_identity_matrix: Path,\n    play_delivery_manifest: Path,\n    governance_manifest: Path,\n''',
        "readiness Play function arg",
    )
    replace_once(
        path,
        '''    installed_matrix_relative, installed_matrix_path = _safe_relative_path(\n        installed_identity_matrix, root, "installed identity matrix"\n    )\n    governance_relative, governance_path = _safe_relative_path(\n''',
        '''    installed_matrix_relative, installed_matrix_path = _safe_relative_path(\n        installed_identity_matrix, root, "installed identity matrix"\n    )\n    play_delivery_relative, play_delivery_path = _safe_relative_path(\n        play_delivery_manifest, root, "Play delivery manifest"\n    )\n    governance_relative, governance_path = _safe_relative_path(\n''',
        "readiness Play path",
    )
    replace_once(
        path,
        '''    try:\n        governance_summary = governance.load_and_validate(governance_path)\n''',
        '''    try:\n        play_delivery_summary = play_delivery.load_and_validate(play_delivery_path)\n    except (OSError, play_delivery.PlayDeliveryError) as exc:\n        raise ReleaseReadinessError(f"Play delivery evidence failed: {exc}") from exc\n    try:\n        governance_summary = governance.load_and_validate(governance_path)\n''',
        "readiness Play validation",
    )
    replace_once(
        path,
        '''        installed_matrix_summary.candidate_sha,\n        governance_summary.candidate_sha,\n''',
        '''        installed_matrix_summary.candidate_sha,\n        play_delivery_summary.candidate_sha,\n        governance_summary.candidate_sha,\n''',
        "readiness Play candidate set",
    )
    replace_once(
        path,
        '''        installed_matrix_summary.artifact_sha256,\n        governance_summary.artifact_sha256,\n''',
        '''        installed_matrix_summary.artifact_sha256,\n        play_delivery_summary.artifact_sha256,\n        governance_summary.artifact_sha256,\n''',
        "readiness Play artifact set",
    )
    replace_once(
        path,
        '''        installed_matrix_summary.upload_certificate_sha256,\n        governance_summary.upload_certificate_sha256,\n''',
        '''        installed_matrix_summary.upload_certificate_sha256,\n        play_delivery_summary.upload_certificate_sha256,\n        governance_summary.upload_certificate_sha256,\n''',
        "readiness Play upload cert set",
    )
    replace_once(
        path,
        '''        installed_matrix_summary.app_signing_certificate_sha256,\n        governance_summary.app_signing_certificate_sha256,\n''',
        '''        installed_matrix_summary.app_signing_certificate_sha256,\n        play_delivery_summary.app_signing_certificate_sha256,\n        governance_summary.app_signing_certificate_sha256,\n''',
        "readiness Play app cert set",
    )
    replace_once(
        path,
        '''    installed_matrix_digest = _sha256_file(installed_matrix_path)\n    governance_digest = _sha256_file(governance_path)\n''',
        '''    installed_matrix_digest = _sha256_file(installed_matrix_path)\n    play_delivery_digest = _sha256_file(play_delivery_path)\n    governance_digest = _sha256_file(governance_path)\n''',
        "readiness Play digest",
    )
    replace_once(
        path,
        '''    if installed_matrix_digest != governance_summary.installed_identity_matrix_sha256:\n        raise ReleaseReadinessError("governance references a different installed identity matrix")\n    if governance_digest != governance_summary.manifest_sha256:\n''',
        '''    if installed_matrix_digest != governance_summary.installed_identity_matrix_sha256:\n        raise ReleaseReadinessError("governance references a different installed identity matrix")\n    if play_delivery_digest != play_delivery_summary.manifest_sha256:\n        raise ReleaseReadinessError("Play delivery digest changed after validation")\n    if play_delivery_summary.installed_identity_matrix_sha256 != installed_matrix_digest:\n        raise ReleaseReadinessError("Play delivery references a different installed identity matrix")\n    if play_delivery_digest != governance_summary.play_delivery_sha256:\n        raise ReleaseReadinessError("governance references a different Play delivery manifest")\n    if governance_digest != governance_summary.manifest_sha256:\n''',
        "readiness Play cross binding",
    )
    replace_once(
        path,
        '''    _require_index_reference(\n        entries,\n        kind="release_governance",\n''',
        '''    _require_index_reference(\n        entries,\n        kind="play_delivery",\n        expected_path=play_delivery_relative,\n        expected_sha256=play_delivery_digest,\n    )\n    _require_index_reference(\n        entries,\n        kind="release_governance",\n''',
        "readiness Play index binding",
    )
    replace_once(
        path,
        '''        installed_identity_matrix_sha256=installed_matrix_digest,\n        governance_sha256=governance_digest,\n''',
        '''        installed_identity_matrix_sha256=installed_matrix_digest,\n        play_delivery_sha256=play_delivery_digest,\n        governance_sha256=governance_digest,\n''',
        "readiness Play summary construction",
    )
    replace_once(
        path,
        '''    parser.add_argument("--installed-identity-matrix", type=Path, required=True)\n    parser.add_argument("--release-governance", type=Path, required=True)\n''',
        '''    parser.add_argument("--installed-identity-matrix", type=Path, required=True)\n    parser.add_argument("--play-delivery", type=Path, required=True)\n    parser.add_argument("--release-governance", type=Path, required=True)\n''',
        "readiness Play cli",
    )
    replace_once(
        path,
        '''            installed_identity_matrix=args.installed_identity_matrix,\n            governance_manifest=args.release_governance,\n''',
        '''            installed_identity_matrix=args.installed_identity_matrix,\n            play_delivery_manifest=args.play_delivery,\n            governance_manifest=args.release_governance,\n''',
        "readiness Play cli call",
    )


def migrate_readiness_tests() -> None:
    path = ROOT / "scripts/test_validate_release_readiness.py"
    replace_once(
        path,
        '''        "installed_identity_matrix=installed-identity-matrix.json",\n        "release_governance=release-governance.json",\n''',
        '''        "installed_identity_matrix=installed-identity-matrix.json",\n        "play_delivery=play-delivery.json",\n        "release_governance=release-governance.json",\n''',
        "readiness Play index spec",
    )
    replace_once(
        path,
        '''        "matrix": root / "installed-identity-matrix.json",\n        "governance": governance_path,\n''',
        '''        "matrix": root / "installed-identity-matrix.json",\n        "play": root / "play-delivery.json",\n        "governance": governance_path,\n''',
        "readiness Play path map",
    )
    replace_all(
        path,
        '''            installed_identity_matrix=paths["matrix"],\n            governance_manifest=paths["governance"],\n''',
        '''            installed_identity_matrix=paths["matrix"],\n            play_delivery_manifest=paths["play"],\n            governance_manifest=paths["governance"],\n''',
        3,
        "readiness Play validate calls",
    )
    replace_once(
        path,
        '''                "installed_identity_matrix=installed-identity-matrix.json",\n                "release_governance=release-governance.json",\n''',
        '''                "installed_identity_matrix=installed-identity-matrix.json",\n                "play_delivery=play-delivery.json",\n                "release_governance=release-governance.json",\n''',
        "readiness Play rebuilt index spec",
    )
    replace_once(
        path,
        '''            self.assertRegex(summary.installed_identity_matrix_sha256, r"^[0-9a-f]{64}$")\n            self.assertGreaterEqual(summary.evidence_entry_count, 11)\n''',
        '''            self.assertRegex(summary.installed_identity_matrix_sha256, r"^[0-9a-f]{64}$")\n            self.assertRegex(summary.play_delivery_sha256, r"^[0-9a-f]{64}$")\n            self.assertGreaterEqual(summary.evidence_entry_count, 12)\n''',
        "readiness Play summary assertion",
    )


def main() -> None:
    migrate_governance()
    migrate_governance_fixture()
    migrate_readiness()
    migrate_readiness_tests()
    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
