#!/usr/bin/env python3
"""One-shot exact migration adding version-code identity to installed matrix summaries."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MATRIX = ROOT / "scripts/validate_installed_identity_matrix.py"
MATRIX_TESTS = ROOT / "scripts/test_installed_identity_matrix.py"
PLAY = ROOT / "scripts/validate_play_delivery_evidence.py"
SELF = Path(__file__)
WORKFLOW = ROOT / ".github/workflows/installed-matrix-version-binding.yml"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor in {path}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    replace_once(
        MATRIX,
        '''class InstalledIdentityMatrixSummary:\n    candidate_sha: str\n    artifact_sha256: str\n''',
        '''class InstalledIdentityMatrixSummary:\n    candidate_sha: str\n    version_code: int\n    artifact_sha256: str\n''',
        "matrix summary version field",
    )
    replace_once(
        MATRIX,
        '''            "candidate_sha": self.candidate_sha,\n            "artifact_sha256": self.artifact_sha256,\n''',
        '''            "candidate_sha": self.candidate_sha,\n            "version_code": self.version_code,\n            "artifact_sha256": self.artifact_sha256,\n''',
        "matrix summary version json",
    )
    replace_once(
        MATRIX,
        '''        candidate_sha=device_summary.candidate_sha,\n        artifact_sha256=device_summary.artifact_sha256,\n''',
        '''        candidate_sha=device_summary.candidate_sha,\n        version_code=_integer(device_candidate.get("version_code"), "device candidate.version_code", minimum=1),\n        artifact_sha256=device_summary.artifact_sha256,\n''',
        "matrix summary version construction",
    )
    replace_once(
        MATRIX_TESTS,
        '''            self.assertEqual(device_fixture.SHA, summary.candidate_sha)\n            self.assertEqual(device_fixture.ARTIFACT_SHA, summary.artifact_sha256)\n''',
        '''            self.assertEqual(device_fixture.SHA, summary.candidate_sha)\n            self.assertEqual(7, summary.version_code)\n            self.assertEqual(device_fixture.ARTIFACT_SHA, summary.artifact_sha256)\n''',
        "matrix version test",
    )
    replace_once(
        PLAY,
        '''        "version_code": None,\n        "artifact_sha256": matrix_summary.artifact_sha256,\n''',
        '''        "version_code": matrix_summary.version_code,\n        "artifact_sha256": matrix_summary.artifact_sha256,\n''',
        "Play expected version",
    )
    replace_once(
        PLAY,
        '''    version_code = _integer(candidate["version_code"], "candidate.version_code", minimum=1)\n    expected_candidate["version_code"] = version_code\n    for key in (\n''',
        '''    version_code = _integer(candidate["version_code"], "candidate.version_code", minimum=1)\n    if version_code != matrix_summary.version_code:\n        raise PlayDeliveryError("candidate.version_code does not match installed identity matrix")\n    for key in (\n''',
        "Play version comparison",
    )
    replace_once(
        PLAY,
        '''    # The matrix intentionally does not carry version code in its summary; read it\n    # from its bound device manifest indirectly by requiring the delivery record's\n    # release version to be positive and cross-checking the same value again in the\n    # governance/device layer. This schema does not invent it.\n\n''',
        '''''',
        "remove obsolete version limitation",
    )

    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
