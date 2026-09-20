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
from pathlib import Path

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
    resolved = path.expanduser()
    if not resolved.is_absolute():
        resolved = ROOT / resolved
    resolved = resolved.resolve()
    if resolved != ROOT and ROOT not in resolved.parents:
        raise ValueError(f"certificate output must stay inside the repository: {resolved}")
    return resolved


def _atomic_json(path: Path, payload: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary, path)


def build_certificate() -> dict[str, object]:
    audit = require_no_trainable_surface(ROOT)
    dataset = dataset_certificate(ROOT)
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

    payload = build_certificate()
    output = _within_root(args.output)
    _atomic_json(output, payload)
    print(json.dumps(payload, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
