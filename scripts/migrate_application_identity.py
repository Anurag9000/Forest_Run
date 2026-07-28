#!/usr/bin/env python3
"""Migrate the placeholder Android package to Forest Run's final identity."""

from pathlib import Path
import shutil

OLD = "com.yourname.forest_run"
NEW = "com.anurag9000.forestrun"
SELF = Path(__file__).resolve()
ROOT = Path(__file__).resolve().parents[1]
SKIP_DIRS = {".git", ".gradle", "build", ".idea", "captures", "release-output"}
TEXT_SUFFIXES = {
    ".kt", ".kts", ".xml", ".pro", ".properties", ".md", ".txt",
    ".py", ".ps1", ".sh", ".json", ".yml", ".yaml", ".bat"
}


def replace_identity() -> int:
    changed = 0
    for path in ROOT.rglob("*"):
        if not path.is_file() or path.resolve() == SELF:
            continue
        if any(part in SKIP_DIRS for part in path.relative_to(ROOT).parts):
            continue
        if path.suffix.lower() not in TEXT_SUFFIXES and path.name not in {"gradlew"}:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if OLD not in text:
            continue
        path.write_text(text.replace(OLD, NEW), encoding="utf-8")
        changed += 1
    return changed


def move_package_trees() -> int:
    moved = 0
    for source_root in (
        ROOT / "app/src/main/java",
        ROOT / "app/src/test/java",
        ROOT / "app/src/androidTest/java",
    ):
        old_dir = source_root / "com/yourname/forest_run"
        new_dir = source_root / "com/anurag9000/forestrun"
        if not old_dir.exists():
            continue
        if new_dir.exists():
            raise RuntimeError(f"Destination already exists: {new_dir}")
        new_dir.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(old_dir), str(new_dir))
        moved += 1

        parent = source_root / "com/yourname"
        while parent != source_root and parent.exists():
            try:
                parent.rmdir()
            except OSError:
                break
            parent = parent.parent
    return moved


def verify() -> None:
    offenders = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or path.resolve() == SELF:
            continue
        if any(part in SKIP_DIRS for part in path.relative_to(ROOT).parts):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        if OLD in text:
            offenders.append(str(path.relative_to(ROOT)))
    if offenders:
        raise RuntimeError("Placeholder package remains in: " + ", ".join(offenders))

    for source_root in (
        ROOT / "app/src/main/java",
        ROOT / "app/src/test/java",
        ROOT / "app/src/androidTest/java",
    ):
        if (source_root / "com/yourname/forest_run").exists():
            raise RuntimeError(f"Old package directory remains under {source_root}")


def main() -> None:
    changed = replace_identity()
    moved = move_package_trees()
    verify()
    print(f"Migrated {changed} text files and {moved} source trees to {NEW}")


if __name__ == "__main__":
    main()
