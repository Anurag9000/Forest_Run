#!/usr/bin/env python3
"""One-shot exact fix for internal persistence injection on public classes."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGETS = (
    ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/EntityManager.kt",
    ROOT / "app/src/main/java/com/anurag9000/forestrun/ui/MainMenuScreen.kt",
    ROOT / "app/src/main/java/com/anurag9000/forestrun/ui/GardenScreen.kt",
)
WORKFLOW = ROOT / ".github/workflows/internal-persistence-injection-visibility.yml"
SELF = Path(__file__)


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise SystemExit(f"{path}: expected one {old!r} anchor, found {text.count(old)}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    replace_once(TARGETS[0], "class EntityManager(\n", "class EntityManager internal constructor(\n")
    replace_once(TARGETS[1], "class MainMenuScreen(\n", "class MainMenuScreen internal constructor(\n")
    replace_once(TARGETS[2], "class GardenScreen(\n", "class GardenScreen internal constructor(\n")

    for path, token in zip(
        TARGETS,
        (
            "class EntityManager internal constructor(",
            "class MainMenuScreen internal constructor(",
            "class GardenScreen internal constructor(",
        ),
    ):
        if token not in path.read_text(encoding="utf-8"):
            raise SystemExit(f"visibility fix missing in {path}")

    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
