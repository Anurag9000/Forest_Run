#!/usr/bin/env python3
"""One-shot exact reconciliation of the canonical validation-status debt line."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ARCH = ROOT / "docs/ARCHITECTURE.md"
WORKFLOW = ROOT / ".github/workflows/architecture-validation-reconciliation.yml"
SELF = Path(__file__)

OLD = "- Exact-head Gradle, lint, build, emulator, physical-device, ADB, signing, installation, store path, screenshots, metadata, privacy/data-safety, content rating, and current Play-policy evidence remain unresolved."
NEW = "- Exact source/build/emulator validation is green at source-bearing checkpoint `414bf30b36ce051f0d5ef75f6143ed6bf8fa5884` in Android validation run `31297723150`; representative physical-device evidence, hardware ADB maintenance, signing, installation/update, store delivery, screenshots/metadata approval, privacy/Data Safety, content rating, and current Play-policy evidence remain unresolved."


def main() -> None:
    text = ARCH.read_text(encoding="utf-8")
    count = text.count(OLD)
    if count != 1:
        raise SystemExit(f"expected one stale validation-status line, found {count}")
    text = text.replace(OLD, NEW, 1)
    if OLD in text or NEW not in text:
        raise SystemExit("architecture validation reconciliation incomplete")
    ARCH.write_text(text, encoding="utf-8")
    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
