#!/usr/bin/env python3
"""Repair and execute the staged Play-delivery migration exactly once.

The original migration at a49ae7d43687117bb1f030b2d1054fa4f5062401 is
retained in Git history. Its only observed failure was a stale guarded cardinality
expectation in the readiness test migration: the live test has one matching call,
not three. This wrapper retrieves that exact historical migration, changes only
that expectation from 3 to 1, and executes it with this path as __file__ so the
original self-cleanup semantics remain intact.
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


if __name__ == "__main__":
    main()
