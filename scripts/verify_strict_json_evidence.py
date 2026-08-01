#!/usr/bin/env python3
"""Strictly parse selected release evidence JSON files before semantic validation."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Sequence

from strict_json import StrictJsonError, load_file

MAX_FILES = 2_048


class EvidenceJsonPreflightError(ValueError):
    pass


def expand_paths(arguments: Sequence[Path]) -> list[Path]:
    expanded: list[Path] = []
    for argument in arguments:
        resolved = argument.expanduser().resolve()
        if resolved.is_file():
            if resolved.suffix.casefold() != ".json":
                raise EvidenceJsonPreflightError(
                    f"evidence path is not a JSON file: {resolved}"
                )
            expanded.append(resolved)
        elif resolved.is_dir():
            expanded.extend(
                path.resolve()
                for path in resolved.rglob("*.json")
                if path.is_file()
            )
        else:
            raise EvidenceJsonPreflightError(
                f"evidence JSON path does not exist: {resolved}"
            )
        if len(expanded) > MAX_FILES:
            raise EvidenceJsonPreflightError(
                f"evidence JSON preflight exceeds the {MAX_FILES}-file safety limit"
            )
    unique = sorted(set(expanded))
    if not unique:
        raise EvidenceJsonPreflightError("no JSON evidence files were supplied")
    return unique


def verify_json_evidence(arguments: Sequence[Path]) -> tuple[Path, ...]:
    paths = expand_paths(arguments)
    for path in paths:
        try:
            load_file(path, require_object=True)
        except StrictJsonError as exc:
            raise EvidenceJsonPreflightError(str(exc)) from exc
    return tuple(paths)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Reject ambiguous or non-standard JSON release evidence."
    )
    parser.add_argument("paths", nargs="+", type=Path)
    args = parser.parse_args(argv)
    try:
        paths = verify_json_evidence(args.paths)
    except EvidenceJsonPreflightError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    print(f"Strictly parsed {len(paths)} JSON evidence file(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
