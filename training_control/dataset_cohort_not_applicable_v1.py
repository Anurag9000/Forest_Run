"""Dataset-cohort applicability certificate for the non-ML Android game repository."""
from __future__ import annotations

from pathlib import Path
import sys
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from training_control.forest_no_trainable_authority import Audit, require_no_trainable_surface

SCHEMA = "forest-run-dataset-cohort-not-applicable/v2"
REPOSITORY = "Anurag9000/Forest_Run"
REASON = (
    "the repository-specific fail-closed authority certifies that Forest Run "
    "contains no retained machine-learning training surface"
)


def certificate(
    root: str | Path | None = None,
    *,
    audit_result: Audit | None = None,
) -> dict[str, Any]:
    repository_root = Path(root or ROOT).resolve()
    audit = audit_result if audit_result is not None else require_no_trainable_surface(repository_root)
    if not audit.complete:
        raise RuntimeError("dataset not-applicable certificate requires a complete source audit")
    return {
        "schema": SCHEMA,
        "repository": REPOSITORY,
        "applicable": False,
        "classification": "no_retained_trainable_surface",
        "reason": REASON,
        "authority": "training_control/forest_no_trainable_authority.py",
        "scanned_file_count": len(audit.scanned_files),
        "source_manifest_sha256": audit.source_manifest_sha256,
        "scope_manifest_sha256": audit.scope_manifest_sha256,
        "authority_manifest_sha256": audit.authority_manifest_sha256,
        "finding_count": len(audit.findings),
    }


if __name__ == "__main__":
    import json
    print(json.dumps(certificate(), sort_keys=True, separators=(",", ":")))
