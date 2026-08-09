#!/usr/bin/env python3
"""One-shot exact migration that marks the remediation ledger as historical."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEDGER = ROOT / "docs/AUDIT_LEDGER.md"
WORKFLOW = ROOT / ".github/workflows/audit-ledger-reconciliation.yml"
SELF = Path(__file__)

OLD = '''# Forest Run — Remediation Audit Ledger

This ledger records the current state of the exhaustive repository remediation on canonical `main`. It distinguishes implemented behavior, automated contracts, validation evidence, and work that still requires code, CI visibility, physical hardware, signing material, or product approval.

The ledger is intentionally conservative: a source change is not called validated merely because it was committed, and automated validation is not treated as physical-device or store acceptance.

## 1. Repository and delivery policy
'''

NEW = '''# Forest Run — Remediation Audit Ledger

This file is the chronological remediation ledger. It preserves tranche-local findings and debt statements as historical provenance rather than rewriting them after later work closes them. **Statements below the current reconciliation section are therefore historical unless the reconciliation or a newer canonical document repeats them as current.** Current source/tests, `README.md`, `docs/ARCHITECTURE.md`, and the newest dated audit take precedence.

The ledger remains intentionally conservative: a source change is not called validated merely because it was committed, and automated validation is not treated as physical-device or store acceptance.

## Current reconciliation — 2026-08-09

The source-architecture queue reconstructed in the 2026-08-06 documentation audit is now closed on canonical `main` at source-bearing checkpoint `414bf30b36ce051f0d5ef75f6143ed6bf8fa5884`. Android validation run `31297723150` completed successfully for both the full host/release/lint/package/R8 job and API-35 connected behavior.

Closed after the historical tranches below:

- `CollisionOutcomeDispatcher` is the sole collision-result dispatcher; terminal/nonterminal sequencing is coordinator-owned and one shared `LiveCollisionEffects` adapter replaced the former private `GameView` effect adapters.
- `RunSessionTransitionPlanner` and `RunSessionTransitionCoordinator` own ordinary top-level transitions. Debug scenario/autostart publication also routes through the explicit `DEBUG_PLAYING_STATE_REQUESTED` event, which distinguishes an accepted idempotent request from an invalid stale no-op.
- `ApplicationPersistenceFacade` is adopted by the live gameplay/UI mutation paths for terminal outcomes, encounter/pass/hit/relationship memory, Garden purchases, wardrobe writes, feedback settings, and recovery operations while preserving independent durability domains.
- The custom Canvas UI has a real `AccessibilityNodeProvider` virtual hierarchy with stable semantic nodes/actions, truthful Garden/wardrobe state, stale-ID rejection, accessibility focus/content change behavior, and throttled TalkBack announcements. Framework events are suppressed safely while Android accessibility is disabled.
- `EncounterFamilyCatalogue` is the single canonical 19-family structural/derived catalogue with biome reachability, deterministic/focused scenario coverage, relationship capability, authored variants, factory wiring, and asset drift contracts.
- Ordinary players have privacy-safe recovery inspection/retry/discard UI with revalidation and two-step destructive confirmation; debug/ADB maintenance remains separate.
- The exact API-35 failures discovered while adopting the final accessibility/session coverage were fixed at their owners: accessibility-off event emission and repeated idempotent debug state publication.

No source-addressable item from the prior collision/session/persistence/catalogue/accessibility/recovery architecture queue remains open after this checkpoint. Remaining blockers are candidate-bound or decision-bound: representative physical hardware, human fairness/accessibility review, performance/thermal/battery evidence, real signing and signed-install/store delivery, final art/audio/haptic approval, privacy/store policy, licensing/security/provenance decisions, and independent final evidence review.

Intentional compatibility limitations remain documented separately: pre-manifest ghost mismatches are not reconstructable, healthy legacy sidecars may stay legacy until validation is needed, SHA-256 identity is not trusted-writer authentication, and ghost/non-ghost recovery remain independent durability domains.

## Historical tranche ledger

Everything below this heading records the state when that tranche was written. In particular, older phrases such as “dispatcher remains,” “private effect adapter remains,” “persistence should be consolidated,” or “observe exact-head CI” are superseded by the current reconciliation above and must not be read as present-day debt.

## 1. Repository and delivery policy
'''


def main() -> None:
    text = LEDGER.read_text(encoding="utf-8")
    count = text.count(OLD)
    if count != 1:
        raise SystemExit(f"expected one ledger header anchor, found {count}")
    text = text.replace(OLD, NEW, 1)
    if "## Current reconciliation — 2026-08-09" not in text:
        raise SystemExit("current reconciliation missing after replacement")
    if "## Historical tranche ledger" not in text:
        raise SystemExit("historical tranche marker missing after replacement")
    LEDGER.write_text(text, encoding="utf-8")

    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
