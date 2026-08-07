#!/usr/bin/env python3
"""One-shot correction of the final stale recovery-UI debt claim."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ARCH = ROOT / "docs/ARCHITECTURE.md"
WORKFLOW = ROOT / ".github/workflows/architecture-recovery-ui-reconciliation.yml"
SELF = Path(__file__)


def main() -> None:
    text = ARCH.read_text(encoding="utf-8")
    old = "- Automatic recovery remains fail-closed; deliberate remediation is debug/support-only with no end-user UI.\n"
    new = "- Automatic recovery remains fail-closed; ordinary players now have a privacy-safe retry/discard UI with explicit destructive confirmation, while debug/support maintenance remains available for acceptance and diagnosis.\n"
    if text.count(old) != 1:
        raise SystemExit(f"expected one stale recovery UI debt claim, found {text.count(old)}")
    text = text.replace(old, new, 1)
    if "debug/support-only with no end-user UI" in text:
        raise SystemExit("stale recovery UI claim survived")
    ARCH.write_text(text, encoding="utf-8")
    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
