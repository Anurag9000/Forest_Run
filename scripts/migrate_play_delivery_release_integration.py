#!/usr/bin/env python3
"""Repair and execute the staged Play-delivery migration exactly once.

The original migration at a49ae7d43687117bb1f030b2d1054fa4f5062401 is
retained in Git history. Its observed failure was a stale guarded cardinality
expectation in the readiness test migration: only the helper call matches the
historical exact-string anchor, while two direct validation calls use different
indentation. This wrapper executes the historical guarded migration with the
helper cardinality corrected from three to one, then inserts the missing Play
manifest argument into exactly those two residual direct calls.
"""

from __future__ import annotations

import subprocess
from pathlib import Path

SOURCE_COMMIT = "a49ae7d43687117bb1f030b2d1054fa4f5062401"
SELF = Path(__file__).resolve()
ROOT = SELF.parents[1]
OLD = '''        3,
        "readiness Play validate calls",
'''
NEW = '''        1,
        "readiness Play validate calls",
'''


def _complete_readiness_test_calls() -> None:
    path = ROOT / "scripts/test_validate_release_readiness.py"
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    output: list[str] = []
    insertions = 0
    for index, line in enumerate(lines):
        output.append(line)
        if line.strip() != 'installed_identity_matrix=paths["matrix"],':
            continue
        if index + 1 >= len(lines):
            continue
        if lines[index + 1].strip() != 'governance_manifest=paths["governance"],':
            continue
        indent = line[: len(line) - len(line.lstrip())]
        output.append(f'{indent}play_delivery_manifest=paths["play"],\n')
        insertions += 1
    if insertions != 2:
        raise SystemExit(
            f"readiness Play residual direct calls: expected 2 insertions, found {insertions}"
        )
    path.write_text("".join(output), encoding="utf-8")


def main() -> None:
    subprocess.run(
        ["git", "fetch", "--no-tags", "--depth=1", "origin", SOURCE_COMMIT],
        cwd=ROOT,
        check=True,
    )
    source = subprocess.check_output(
        ["git", "show", f"FETCH_HEAD:{SELF.relative_to(ROOT).as_posix()}"],
        cwd=ROOT,
        text=True,
    )
    if source.count(OLD) != 1:
        raise SystemExit("historical Play migration no longer has the expected stale guard")
    repaired = source.replace(OLD, NEW, 1)
    namespace = {
        "__name__": "__main__",
        "__file__": str(SELF),
        "__package__": None,
    }
    exec(compile(repaired, str(SELF), "exec"), namespace, namespace)
    _complete_readiness_test_calls()


if __name__ == "__main__":
    main()
