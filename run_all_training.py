#!/usr/bin/env python3
"""Forest_Run training-control applicability authority.

Forest Run is a native Android game with no retained ML/training surface. The
only truthful repository-level training-control action is therefore a fail-closed
source/dependency audit. If a genuine trainable surface appears later this
command fails until the repository gains a real, repository-specific training
architecture; it never manufactures synthetic jobs, registries, datasets, or
experiment combinations for ordinary game/release code.
"""
from __future__ import annotations

import argparse
import json
import os
import stat
from pathlib import Path

from scripts.stable_file_reader import (
    absolute_lexical_path,
    atomic_write_bytes,
    ensure_directory_exists,
    open_directory_descriptor,
)
from training_control.dataset_cohort_not_applicable_v1 import certificate as dataset_certificate
from training_control.forest_no_trainable_authority import require_no_trainable_surface

ROOT = Path(__file__).resolve().parent
REPOSITORY = "Anurag9000/Forest_Run"
DEFAULT_OUTPUT = ROOT / ".training_control" / "coverage_report.json"

NOT_APPLICABLE_CONTROLS = (
    "model_training",
    "training_datasets",
    "dataset_cohorts",
    "gpu_first_training",
    "cpu_gpu_training_parity",
    "optimizer_state",
    "loss_functions",
    "early_stopping",
    "training_checkpoint_resume",
    "distillation",
    "qat_ptq_training_stages",
    "pruning_finetuning",
    "trainable_model_registries",
    "ml_experiment_matrices",
    "training_dags",
    "scientific_training_orchestration",
    "model_family_coverage",
    "training_workload_combinations",
    "ml_architecture_search",
)


def _within_root(path: Path) -> Path:
    candidate = path.expanduser()
    if not candidate.is_absolute():
        candidate = ROOT / candidate
    lexical = absolute_lexical_path(candidate)
    root = absolute_lexical_path(ROOT)
    if lexical != root and root not in lexical.parents:
        raise ValueError(f"certificate output must stay inside the repository: {lexical}")
    return lexical


def _atomic_json(path: Path, payload: dict[str, object]) -> None:
    ensure_directory_exists(path.parent, "certificate output parent directory")
    atomic_write_bytes(
        path,
        (json.dumps(payload, indent=2, sort_keys=True) + "\n").encode("utf-8"),
        "certificate output",
        reject_symlink_leaf=True,
    )


def _invalidate_previous_output(path: Path) -> None:
    """Remove stale evidence by leaf name through a held no-follow directory."""
    ensure_directory_exists(path.parent, "certificate output parent directory")
    directory_fd = open_directory_descriptor(
        path.parent, "certificate output parent directory"
    )
    try:
        try:
            current = os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
        except FileNotFoundError:
            return
        if stat.S_ISDIR(current.st_mode):
            raise ValueError("certificate output must be a file inside the repository")
        os.unlink(path.name, dir_fd=directory_fd)
        os.fsync(directory_fd)
    finally:
        os.close(directory_fd)


def build_certificate() -> dict[str, object]:
    audit = require_no_trainable_surface(ROOT)
    dataset = dataset_certificate(ROOT, audit_result=audit)
    if dataset["source_manifest_sha256"] != audit.source_manifest_sha256:
        raise RuntimeError("Forest Run certificate source snapshot mismatch")
    if dataset["scope_manifest_sha256"] != audit.scope_manifest_sha256:
        raise RuntimeError("Forest Run certificate scope inventory mismatch")
    if dataset["authority_manifest_sha256"] != audit.authority_manifest_sha256:
        raise RuntimeError("Forest Run certificate authority-code snapshot mismatch")
    return {
        "schema_version": 2,
        "repository": REPOSITORY,
        "status": "pass",
        "classification": "no_retained_trainable_surface",
        "ml_training_applicable": False,
        "authority": "training_control/forest_no_trainable_authority.py",
        "scan": audit.to_dict(),
        "dataset_cohorts": dataset,
        "not_applicable_controls": list(NOT_APPLICABLE_CONTROLS),
        "ordinary_application_registries_are_training_surfaces": False,
        "execution_claim_emitted": False,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Certify Forest_Run's no-retained-trainable-surface invariant."
    )
    parser.add_argument(
        "--training-control-audit",
        action="store_true",
        help="run the fail-closed applicability audit (the default operation)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help="repository-local JSON certificate path",
    )
    args = parser.parse_args(argv)

    output = _within_root(args.output)
    if output == absolute_lexical_path(ROOT):
        raise ValueError("certificate output must be a file inside the repository")
    # An earlier PASS is not valid evidence for a later failed source audit.
    # Clear it before reading the current source, so even an unexpected audit
    # or publication error cannot leave an old success at this path.
    _invalidate_previous_output(output)
    try:
        payload = build_certificate()
    except Exception as exc:
        payload = {
            "schema_version": 2,
            "repository": REPOSITORY,
            "status": "fail",
            "classification": "unresolved",
            "ml_training_applicable": None,
            "authority": "training_control/forest_no_trainable_authority.py",
            "error": str(exc),
            "execution_claim_emitted": False,
        }
        _atomic_json(output, payload)
        print(json.dumps(payload, sort_keys=True))
        return 2
    _atomic_json(output, payload)
    print(json.dumps(payload, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
