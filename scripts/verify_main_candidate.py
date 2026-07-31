#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import subprocess
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import NoReturn, Sequence

ROOT = Path(__file__).resolve().parent.parent
_SHA_PATTERN = re.compile(r"[0-9a-f]{40}")


class CandidateVerificationError(RuntimeError):
    """Raised when a working tree is not a releasable main candidate."""


@dataclass(frozen=True)
class MainCandidate:
    sha: str
    branch: str
    root: str


def _fail(message: str) -> NoReturn:
    raise CandidateVerificationError(message)


def _run_git(root: Path, arguments: Sequence[str]) -> str:
    result = subprocess.run(
        ["git", *arguments],
        cwd=root,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        details = (result.stderr or result.stdout).strip() or "no diagnostic output"
        _fail(f"git {' '.join(arguments)} failed: {details}")
    return result.stdout.strip()


def verify_main_candidate(
    root: Path = ROOT,
    expected_sha: str | None = None,
) -> MainCandidate:
    """Verify that *root* is a clean checkout at the exact local main tip.

    The release path is intentionally stricter than ordinary CI checkout:
    release preparation must start on the named ``main`` branch, not a feature
    branch or detached commit that merely happens to share some history.
    """

    resolved_root = root.expanduser().resolve()
    if not resolved_root.is_dir():
        _fail(f"Candidate root does not exist: {resolved_root}")

    repository_root = Path(
        _run_git(resolved_root, ["rev-parse", "--show-toplevel"])
    ).resolve()
    if repository_root != resolved_root:
        _fail(
            "Candidate verification must run at the repository root: "
            f"expected {repository_root}, received {resolved_root}"
        )

    status = _run_git(
        resolved_root,
        ["status", "--porcelain=v1", "--untracked-files=all"],
    )
    if status:
        _fail(
            "Release preparation requires a completely clean main worktree. "
            "Commit, remove, or ignore these paths:\n"
            f"{status}"
        )

    branch = _run_git(
        resolved_root,
        ["symbolic-ref", "--quiet", "--short", "HEAD"],
    )
    if branch != "main":
        _fail(
            "Release preparation is allowed only from the named main branch; "
            f"current branch is {branch!r}"
        )

    head_sha = _run_git(resolved_root, ["rev-parse", "HEAD"]).lower()
    main_sha = _run_git(resolved_root, ["rev-parse", "refs/heads/main"]).lower()
    if not _SHA_PATTERN.fullmatch(head_sha) or not _SHA_PATTERN.fullmatch(main_sha):
        _fail("git returned an invalid candidate SHA")
    if head_sha != main_sha:
        _fail(
            "HEAD is not the exact local main tip: "
            f"HEAD={head_sha}, refs/heads/main={main_sha}"
        )

    if expected_sha is not None:
        normalized_expected = expected_sha.strip().lower()
        if not _SHA_PATTERN.fullmatch(normalized_expected):
            _fail(f"Expected SHA is not a full 40-character commit ID: {expected_sha!r}")
        if head_sha != normalized_expected:
            _fail(
                "Candidate SHA does not match the requested frozen commit: "
                f"expected {normalized_expected}, found {head_sha}"
            )

    return MainCandidate(
        sha=head_sha,
        branch=branch,
        root=str(resolved_root),
    )


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify a clean Forest Run release candidate at the exact main tip."
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=ROOT,
        help="Repository root to verify (default: repository containing this script).",
    )
    parser.add_argument(
        "--expected-sha",
        help="Optional full SHA that the main tip must equal.",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit the verified candidate as JSON.",
    )
    return parser.parse_args()


def main() -> int:
    args = _parse_args()
    try:
        candidate = verify_main_candidate(args.root, args.expected_sha)
    except CandidateVerificationError as error:
        raise SystemExit(str(error)) from error

    if args.json:
        print(json.dumps(asdict(candidate), sort_keys=True))
    else:
        print(f"Verified clean main candidate {candidate.sha} at {candidate.root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
