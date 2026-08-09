#!/usr/bin/env python3
"""One-shot exact migration requiring installed identity matrix downstream."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SELF = Path(__file__)
WORKFLOW = ROOT / ".github/workflows/installed-identity-release-integration.yml"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor in {path}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def migrate_governance() -> None:
    path = ROOT / "scripts/validate_release_governance.py"
    replace_once(
        path,
        "import validate_human_acceptance as human_acceptance\n",
        "import validate_human_acceptance as human_acceptance\nimport validate_installed_identity_matrix as installed_matrix\n",
        "governance matrix import",
    )
    replace_once(
        path,
        '''        "dependency_verification_report",\n        "asset_provenance",\n''',
        '''        "dependency_verification_report",\n        "installed_identity_matrix",\n        "asset_provenance",\n''',
        "governance required matrix evidence",
    )
    replace_once(
        path,
        '''    human_acceptance_sha256: str\n    decision_count: int\n''',
        '''    human_acceptance_sha256: str\n    installed_identity_matrix_sha256: str\n    decision_count: int\n''',
        "governance matrix summary field",
    )
    replace_once(
        path,
        '''            "human_acceptance_sha256": self.human_acceptance_sha256,\n            "decision_count": self.decision_count,\n''',
        '''            "human_acceptance_sha256": self.human_acceptance_sha256,\n            "installed_identity_matrix_sha256": self.installed_identity_matrix_sha256,\n            "decision_count": self.decision_count,\n''',
        "governance matrix summary json",
    )
    replace_once(
        path,
        '''    seen_paths: dict[str, tuple[str, tuple[int, int] | None]] = {}\n    for kind in sorted(REQUIRED_EVIDENCE_KINDS):\n        _validate_file_reference(\n            evidence[kind],\n            label=f"evidence.{kind}",\n            base=evidence_base,\n            seen_paths=seen_paths,\n        )\n\n    decisions = _mapping(root["decisions"], "decisions")\n''',
        '''    seen_paths: dict[str, tuple[str, tuple[int, int] | None]] = {}\n    evidence_results: dict[str, tuple[str, str, Path]] = {}\n    for kind in sorted(REQUIRED_EVIDENCE_KINDS):\n        evidence_results[kind] = _validate_file_reference(\n            evidence[kind],\n            label=f"evidence.{kind}",\n            base=evidence_base,\n            seen_paths=seen_paths,\n        )\n\n    _, installed_matrix_digest, installed_matrix_path = evidence_results["installed_identity_matrix"]\n    try:\n        installed_matrix_summary = installed_matrix.load_and_validate(installed_matrix_path)\n    except installed_matrix.InstalledIdentityMatrixError as exc:\n        raise GovernanceError(f"installed identity matrix is invalid: {exc}") from exc\n    if installed_matrix_summary.candidate_sha != candidate_sha:\n        raise GovernanceError("installed identity matrix candidate does not match governance candidate")\n    if installed_matrix_summary.artifact_sha256 != artifact_sha:\n        raise GovernanceError("installed identity matrix artifact does not match governance candidate")\n    if installed_matrix_summary.upload_certificate_sha256 != upload_certificate_sha:\n        raise GovernanceError("installed identity matrix upload certificate does not match governance candidate")\n    if installed_matrix_summary.app_signing_certificate_sha256 != app_signing_certificate_sha:\n        raise GovernanceError("installed identity matrix app-signing certificate does not match governance candidate")\n    if installed_matrix_summary.device_acceptance_sha256 != device_digest:\n        raise GovernanceError("installed identity matrix references a different device acceptance manifest")\n\n    decisions = _mapping(root["decisions"], "decisions")\n''',
        "governance matrix validation",
    )
    replace_once(
        path,
        '''        human_acceptance_sha256=human_digest,\n        decision_count=len(decisions),\n''',
        '''        human_acceptance_sha256=human_digest,\n        installed_identity_matrix_sha256=installed_matrix_digest,\n        decision_count=len(decisions),\n''',
        "governance matrix summary construction",
    )


def migrate_governance_fixture() -> None:
    path = ROOT / "scripts/test_validate_release_governance.py"
    replace_once(
        path,
        '''import compile_human_acceptance\nimport compile_release_governance as compiler\nimport test_validate_human_acceptance as human_fixture\n''',
        '''import compile_human_acceptance\nimport compile_installed_identity_matrix\nimport compile_release_governance as compiler\nimport test_installed_identity_matrix as matrix_fixture\nimport test_validate_human_acceptance as human_fixture\n''',
        "governance fixture matrix imports",
    )
    replace_once(
        path,
        '''def _governance_draft(root: Path) -> dict:\n    candidate, _, _ = _prepare_human(root)\n    evidence: dict[str, str] = {}\n    for kind in sorted(governance.REQUIRED_EVIDENCE_KINDS):\n        suffix = ".md" if kind in {"release_notes", "changelog", "third_party_notices"} else ".txt"\n''',
        '''def _governance_draft(root: Path) -> dict:\n    candidate, _, _ = _prepare_human(root)\n    matrix_draft, _ = matrix_fixture.prepare(root)\n    matrix_draft_path = root / "installed-identity-matrix-draft.json"\n    matrix_path = root / "installed-identity-matrix.json"\n    matrix_draft_path.write_text(\n        json.dumps(matrix_draft, indent=2, sort_keys=True) + "\\n",\n        encoding="utf-8",\n    )\n    compile_installed_identity_matrix.compile_file(matrix_draft_path, matrix_path)\n\n    evidence: dict[str, str] = {}\n    for kind in sorted(governance.REQUIRED_EVIDENCE_KINDS):\n        if kind == "installed_identity_matrix":\n            evidence[kind] = "installed-identity-matrix.json"\n            continue\n        suffix = ".md" if kind in {"release_notes", "changelog", "third_party_notices"} else ".txt"\n''',
        "governance fixture real matrix",
    )
    replace_once(
        path,
        '''            self.assertEqual(2, summary.reviewer_count)\n            self.assertRegex(compiled["device_acceptance"]["sha256"], r"^[0-9a-f]{64}$")\n''',
        '''            self.assertEqual(2, summary.reviewer_count)\n            self.assertRegex(summary.installed_identity_matrix_sha256, r"^[0-9a-f]{64}$")\n            self.assertRegex(compiled["device_acceptance"]["sha256"], r"^[0-9a-f]{64}$")\n''',
        "governance fixture matrix assertion",
    )


def migrate_readiness() -> None:
    path = ROOT / "scripts/validate_release_readiness.py"
    replace_once(
        path,
        "import validate_human_acceptance as human_acceptance\n",
        "import validate_human_acceptance as human_acceptance\nimport validate_installed_identity_matrix as installed_matrix\n",
        "readiness matrix import",
    )
    replace_once(
        path,
        '''        "human_acceptance",\n        "release_governance",\n''',
        '''        "human_acceptance",\n        "installed_identity_matrix",\n        "release_governance",\n''',
        "readiness required matrix kind",
    )
    replace_once(
        path,
        '''    human_acceptance_sha256: str\n    governance_sha256: str\n''',
        '''    human_acceptance_sha256: str\n    installed_identity_matrix_sha256: str\n    governance_sha256: str\n''',
        "readiness matrix summary field",
    )
    replace_once(
        path,
        '''            "human_acceptance_sha256": self.human_acceptance_sha256,\n            "governance_sha256": self.governance_sha256,\n''',
        '''            "human_acceptance_sha256": self.human_acceptance_sha256,\n            "installed_identity_matrix_sha256": self.installed_identity_matrix_sha256,\n            "governance_sha256": self.governance_sha256,\n''',
        "readiness matrix summary json",
    )
    replace_once(
        path,
        '''    human_manifest: Path,\n    governance_manifest: Path,\n''',
        '''    human_manifest: Path,\n    installed_identity_matrix: Path,\n    governance_manifest: Path,\n''',
        "readiness matrix function arg",
    )
    replace_once(
        path,
        '''    human_relative, human_path = _safe_relative_path(\n        human_manifest, root, "human acceptance manifest"\n    )\n    governance_relative, governance_path = _safe_relative_path(\n''',
        '''    human_relative, human_path = _safe_relative_path(\n        human_manifest, root, "human acceptance manifest"\n    )\n    installed_matrix_relative, installed_matrix_path = _safe_relative_path(\n        installed_identity_matrix, root, "installed identity matrix"\n    )\n    governance_relative, governance_path = _safe_relative_path(\n''',
        "readiness matrix path",
    )
    replace_once(
        path,
        '''    try:\n        governance_summary = governance.load_and_validate(governance_path)\n''',
        '''    try:\n        installed_matrix_summary = installed_matrix.load_and_validate(installed_matrix_path)\n    except (OSError, installed_matrix.InstalledIdentityMatrixError) as exc:\n        raise ReleaseReadinessError(f"installed identity matrix failed: {exc}") from exc\n    try:\n        governance_summary = governance.load_and_validate(governance_path)\n''',
        "readiness matrix validate",
    )
    replace_once(
        path,
        '''        human_summary.candidate_sha,\n        governance_summary.candidate_sha,\n''',
        '''        human_summary.candidate_sha,\n        installed_matrix_summary.candidate_sha,\n        governance_summary.candidate_sha,\n''',
        "readiness candidate set",
    )
    replace_once(
        path,
        '''        human_summary.artifact_sha256,\n        governance_summary.artifact_sha256,\n''',
        '''        human_summary.artifact_sha256,\n        installed_matrix_summary.artifact_sha256,\n        governance_summary.artifact_sha256,\n''',
        "readiness artifact set",
    )
    replace_once(
        path,
        '''        human_summary.upload_certificate_sha256,\n        governance_summary.upload_certificate_sha256,\n''',
        '''        human_summary.upload_certificate_sha256,\n        installed_matrix_summary.upload_certificate_sha256,\n        governance_summary.upload_certificate_sha256,\n''',
        "readiness upload cert set",
    )
    replace_once(
        path,
        '''        human_summary.app_signing_certificate_sha256,\n        governance_summary.app_signing_certificate_sha256,\n''',
        '''        human_summary.app_signing_certificate_sha256,\n        installed_matrix_summary.app_signing_certificate_sha256,\n        governance_summary.app_signing_certificate_sha256,\n''',
        "readiness app cert set",
    )
    replace_once(
        path,
        '''    human_digest = _sha256_file(human_path)\n    governance_digest = _sha256_file(governance_path)\n''',
        '''    human_digest = _sha256_file(human_path)\n    installed_matrix_digest = _sha256_file(installed_matrix_path)\n    governance_digest = _sha256_file(governance_path)\n''',
        "readiness matrix digest",
    )
    replace_once(
        path,
        '''    if human_digest != governance_summary.human_acceptance_sha256:\n        raise ReleaseReadinessError("governance references a different human manifest")\n    if governance_digest != governance_summary.manifest_sha256:\n''',
        '''    if human_digest != governance_summary.human_acceptance_sha256:\n        raise ReleaseReadinessError("governance references a different human manifest")\n    if installed_matrix_digest != installed_matrix_summary.manifest_sha256:\n        raise ReleaseReadinessError("installed identity matrix digest changed after validation")\n    if installed_matrix_summary.device_acceptance_sha256 != device_digest:\n        raise ReleaseReadinessError("installed identity matrix references a different device manifest")\n    if installed_matrix_digest != governance_summary.installed_identity_matrix_sha256:\n        raise ReleaseReadinessError("governance references a different installed identity matrix")\n    if governance_digest != governance_summary.manifest_sha256:\n''',
        "readiness matrix cross binding",
    )
    replace_once(
        path,
        '''    _require_index_reference(\n        entries,\n        kind="release_governance",\n''',
        '''    _require_index_reference(\n        entries,\n        kind="installed_identity_matrix",\n        expected_path=installed_matrix_relative,\n        expected_sha256=installed_matrix_digest,\n    )\n    _require_index_reference(\n        entries,\n        kind="release_governance",\n''',
        "readiness matrix index binding",
    )
    replace_once(
        path,
        '''        human_acceptance_sha256=human_digest,\n        governance_sha256=governance_digest,\n''',
        '''        human_acceptance_sha256=human_digest,\n        installed_identity_matrix_sha256=installed_matrix_digest,\n        governance_sha256=governance_digest,\n''',
        "readiness matrix summary construction",
    )
    replace_once(
        path,
        '''    parser.add_argument("--human-acceptance", type=Path, required=True)\n    parser.add_argument("--release-governance", type=Path, required=True)\n''',
        '''    parser.add_argument("--human-acceptance", type=Path, required=True)\n    parser.add_argument("--installed-identity-matrix", type=Path, required=True)\n    parser.add_argument("--release-governance", type=Path, required=True)\n''',
        "readiness matrix cli",
    )
    replace_once(
        path,
        '''            human_manifest=args.human_acceptance,\n            governance_manifest=args.release_governance,\n''',
        '''            human_manifest=args.human_acceptance,\n            installed_identity_matrix=args.installed_identity_matrix,\n            governance_manifest=args.release_governance,\n''',
        "readiness matrix cli call",
    )


def migrate_readiness_tests() -> None:
    path = ROOT / "scripts/test_validate_release_readiness.py"
    replace_once(
        path,
        '''        "human_acceptance=human-acceptance.json",\n        "release_governance=release-governance.json",\n''',
        '''        "human_acceptance=human-acceptance.json",\n        "installed_identity_matrix=installed-identity-matrix.json",\n        "release_governance=release-governance.json",\n''',
        "readiness fixture matrix index spec",
    )
    replace_once(
        path,
        '''        "human": root / "human-acceptance.json",\n        "governance": governance_path,\n''',
        '''        "human": root / "human-acceptance.json",\n        "matrix": root / "installed-identity-matrix.json",\n        "governance": governance_path,\n''',
        "readiness fixture matrix path",
    )
    replace_once(
        path,
        '''            human_manifest=paths["human"],\n            governance_manifest=paths["governance"],\n''',
        '''            human_manifest=paths["human"],\n            installed_identity_matrix=paths["matrix"],\n            governance_manifest=paths["governance"],\n''',
        "readiness validate helper matrix",
    )
    replace_once(
        path,
        '''                    human_manifest=paths["human"],\n                    governance_manifest=paths["governance"],\n''',
        '''                    human_manifest=paths["human"],\n                    installed_identity_matrix=paths["matrix"],\n                    governance_manifest=paths["governance"],\n''',
        "readiness direct mismatch matrix",
    )
    replace_once(
        path,
        '''                "human_acceptance=human-acceptance.json",\n                "release_governance=release-governance.json",\n''',
        '''                "human_acceptance=human-acceptance.json",\n                "installed_identity_matrix=installed-identity-matrix.json",\n                "release_governance=release-governance.json",\n''',
        "readiness rebuilt index matrix spec",
    )
    replace_once(
        path,
        '''                    human_manifest=paths["human"],\n                    governance_manifest=paths["governance"],\n                    release_index=paths["index"],\n''',
        '''                    human_manifest=paths["human"],\n                    installed_identity_matrix=paths["matrix"],\n                    governance_manifest=paths["governance"],\n                    release_index=paths["index"],\n''',
        "readiness symlink direct call matrix",
    )
    replace_once(
        path,
        '''            self.assertEqual(device_fixture.APP_SIGNING_CERT_SHA, summary.app_signing_certificate_sha256)\n            self.assertGreaterEqual(summary.evidence_entry_count, 10)\n''',
        '''            self.assertEqual(device_fixture.APP_SIGNING_CERT_SHA, summary.app_signing_certificate_sha256)\n            self.assertRegex(summary.installed_identity_matrix_sha256, r"^[0-9a-f]{64}$")\n            self.assertGreaterEqual(summary.evidence_entry_count, 11)\n''',
        "readiness matrix summary assertion",
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
