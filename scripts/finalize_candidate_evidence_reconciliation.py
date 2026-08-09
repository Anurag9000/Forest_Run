#!/usr/bin/env python3
"""Finalize candidate-evidence reconciliation after the intentional dual index anchor."""

from pathlib import Path

import migrate_candidate_evidence_layers as migration

ROOT = Path(__file__).resolve().parents[1]
SELF = Path(__file__)
V2_WORKFLOW = ROOT / ".github/workflows/candidate-evidence-reconciliation-v2.yml"
ORIGINAL_WORKFLOW = ROOT / ".github/workflows/candidate-evidence-reconciliation.yml"
ORIGINAL_SCRIPT = ROOT / "scripts/migrate_candidate_evidence_layers.py"


def replace_exact(path: Path, old: str, new: str, expected: int, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{label}: expected {expected} anchors in {path}, found {count}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def reconcile_acceptance_docs() -> None:
    device = ROOT / "docs/DEVICE_ACCEPTANCE.md"
    migration.replace_once(
        device,
        '''At least two distinct named reviewers are required for final visual/store approval.\n''',
        '''At least two distinct named reviewers are required for final visual/store approval.\n\nThese seven per-session manual fields are intentionally coarse physical-session gates. They do **not** replace the detailed human gameplay, fairness, TalkBack/accessibility, Garden/wardrobe/ghost, and presentation matrix. After this device manifest validates, compile and validate the separate candidate-bound human layer described in [`HUMAN_ACCEPTANCE.md`](HUMAN_ACCEPTANCE.md) with `scripts/compile_human_acceptance.py` and `scripts/validate_human_acceptance.py`. Every human review is bound back to one real session ID from this physical manifest.\n''',
        "device acceptance human layer",
    )

    index = ROOT / "docs/RELEASE_EVIDENCE_INDEX.md"
    migration.replace_once(
        index,
        '''- physical-device acceptance sessions;\n- performance profiles;\n- screenshot capture and curation;\n- graphics and metadata generation;\n- manual visual, audio, haptic, accessibility, privacy, audience, content-rating, and store-policy approvals.\n''',
        '''- physical-device acceptance sessions;\n- detailed human gameplay/accessibility/presentation acceptance;\n- performance profiles and physical diagnostics;\n- screenshot capture and curation;\n- graphics and metadata generation;\n- candidate-bound security/licensing/privacy/store/presentation governance and final approvals.\n''',
        "index pipeline inventory",
    )
    migration.replace_once(
        index,
        '''  --entry device_acceptance=release/evidence/device-acceptance.json \\\n  --entry device_aggregate=release/evidence/device-acceptance-aggregate.json \\\n  --entry screenshot_manifest=release/google-play/screenshots/screenshot_manifest.json \\\n''',
        '''  --entry device_acceptance=release/evidence/device-acceptance.json \\\n  --entry device_aggregate=release/evidence/device-acceptance-aggregate.json \\\n  --entry human_acceptance=release/evidence/human-acceptance.json \\\n  --entry release_governance=release/evidence/release-governance.json \\\n  --entry screenshot_manifest=release/google-play/screenshots/screenshot_manifest.json \\\n''',
        "index acceptance entries",
    )
    replace_exact(
        index,
        '''  --require-bound-kind device_acceptance \\\n  --require-bound-kind device_aggregate \\\n  --require-bound-kind screenshot_manifest \\\n''',
        '''  --require-bound-kind device_acceptance \\\n  --require-bound-kind device_aggregate \\\n  --require-bound-kind human_acceptance \\\n  --require-bound-kind release_governance \\\n  --require-bound-kind screenshot_manifest \\\n''',
        2,
        "index build/verify required kinds",
    )


def main() -> None:
    migration.harden_human_validator()
    migration.harden_governance_validator()
    migration.add_symlink_tests()
    migration.reconcile_performance_doc()
    reconcile_acceptance_docs()
    migration.reconcile_security_and_readme()

    for path in (ORIGINAL_WORKFLOW, ORIGINAL_SCRIPT, V2_WORKFLOW, SELF):
        if path.exists():
            path.unlink()

    for required in (
        ROOT / "docs/HUMAN_ACCEPTANCE.md",
        ROOT / "docs/RELEASE_GOVERNANCE_EVIDENCE.md",
        ROOT / "scripts/validate_human_acceptance.py",
        ROOT / "scripts/validate_release_governance.py",
    ):
        if not required.is_file():
            raise SystemExit(f"required evidence source missing: {required}")


if __name__ == "__main__":
    main()
