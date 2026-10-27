#!/usr/bin/env python3
from __future__ import annotations

import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from training_control.forest_no_trainable_authority import audit  # noqa: E402
from stable_file_reader import (  # noqa: E402
    StableFileError,
    atomic_write_bytes,
    ensure_directory_exists,
)


def _publish_report(output: Path, payload: dict[str, object]) -> None:
    relative_parent = output.parent.relative_to(ROOT)
    if any(part in {".", ".."} for part in relative_parent.parts):
        raise OSError("report directory must remain inside the repository root")

    if output.is_symlink():
        raise OSError(f"report output must not be a symbolic link: {output}")
    if output.exists() and not output.is_file():
        raise OSError(f"report output must be a regular file: {output}")

    try:
        ensure_directory_exists(output.parent, "no-trainable-surface report parent directory")
        if output.is_symlink():
            raise OSError(f"report output must not be a symbolic link: {output}")
        if output.exists() and not output.is_file():
            raise OSError(f"report output must be a regular file: {output}")
        encoded = (json.dumps(payload, indent=2, sort_keys=True) + "\n").encode("utf-8")
        atomic_write_bytes(
            output,
            encoded,
            "no-trainable-surface report",
            reject_symlink_leaf=True,
        )
    except StableFileError as exc:
        raise OSError(f"could not safely publish report: {exc}") from exc


def main() -> int:
    result = audit(ROOT)
    output = ROOT / "artifacts" / "training_control" / "no_trainable_surface.json"
    payload = result.to_dict()
    try:
        _publish_report(output, payload)
    except OSError as exc:
        print(json.dumps({"status": "invalid", "error": str(exc)}, sort_keys=True))
        return 2
    print(json.dumps(payload, sort_keys=True))
    return 0 if result.complete else 2


if __name__ == "__main__":
    raise SystemExit(main())
