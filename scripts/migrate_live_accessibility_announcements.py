#!/usr/bin/env python3
"""One-shot exact migration to adopt AccessibilityAnnouncementPolicy in GameView."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GAME_VIEW = ROOT / "app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt"
WORKFLOW = ROOT / ".github/workflows/live-accessibility-announcement-migration.yml"
SELF = Path(__file__)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    source = GAME_VIEW.read_text(encoding="utf-8")
    source = replace_once(
        source,
        "import android.os.Bundle\n" if "import android.os.Bundle\n" in source else "import android.graphics.Typeface\n",
        "import android.os.Bundle\nimport android.os.SystemClock\n" if "import android.os.Bundle\n" in source else "import android.graphics.Typeface\nimport android.os.SystemClock\n",
        "SystemClock import",
    )
    source = replace_once(
        source,
        'private const val TAG = "ForestRun"\n',
        'private const val TAG = "ForestRun"\nprivate const val ACCESSIBILITY_ANNOUNCEMENT_POLL_FRAMES = 30L\n',
        "announcement poll constant",
    )
    source = replace_once(
        source,
        "    private var accessibilitySettingsOpen = false\n    private val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)\n",
        "    private var accessibilitySettingsOpen = false\n"
        "    private val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)\n"
        "    private val accessibilityAnnouncementPolicy = AccessibilityAnnouncementPolicy()\n",
        "announcement policy field",
    )
    source = replace_once(
        source,
        '''    private fun notifyAccessibilityTreeChanged() {
        if (accessibilityManager?.isEnabled == true) {
            gameAccessibilityNodeProvider.notifySemanticTreeChanged()
        }
    }
''',
        '''    private fun notifyAccessibilityTreeChanged() {
        val manager = accessibilityManager ?: return
        if (!manager.isEnabled) return
        gameAccessibilityNodeProvider.notifySemanticTreeChanged()
        if (manager.isTouchExplorationEnabled) {
            announceAccessibilitySnapshot(buildAccessibilitySnapshot())
        }
    }

    private fun updateAccessibilityAnnouncements() {
        val manager = accessibilityManager ?: return
        if (!manager.isEnabled || !manager.isTouchExplorationEnabled) return
        if (debugFrameCounter % ACCESSIBILITY_ANNOUNCEMENT_POLL_FRAMES != 0L) return
        announceAccessibilitySnapshot(buildAccessibilitySnapshot())
    }

    private fun announceAccessibilitySnapshot(snapshot: AccessibilitySemanticSnapshot) {
        accessibilityAnnouncementPolicy.next(
            snapshot = snapshot,
            nowMs = SystemClock.uptimeMillis().coerceAtLeast(0L)
        )?.let(::announceForAccessibility)
    }
''',
        "announcement publication methods",
    )
    source = replace_once(
        source,
        '''        // Flavor text float animation
        FlavorTextManager.update(deltaTime)
        DialogueBubbleManager.update(deltaTime)

        // Phase 14: Update all particles
        ParticleManager.update(deltaTime)
    }
''',
        '''        // Flavor text float animation
        FlavorTextManager.update(deltaTime)
        DialogueBubbleManager.update(deltaTime)

        // Phase 14: Update all particles
        ParticleManager.update(deltaTime)

        // Screen-reader run status is sampled, then coalesced by the policy into
        // surface/Bloom changes or spaced distance milestones. Never announce
        // frame-driven score/distance mutations directly.
        updateAccessibilityAnnouncements()
    }
''',
        "run-loop announcement sampling",
    )

    required = (
        "AccessibilityAnnouncementPolicy()",
        "SystemClock.uptimeMillis()",
        "manager.isTouchExplorationEnabled",
        "updateAccessibilityAnnouncements()",
        "announceAccessibilitySnapshot(buildAccessibilitySnapshot())",
        "ACCESSIBILITY_ANNOUNCEMENT_POLL_FRAMES",
    )
    for token in required:
        if token not in source:
            raise SystemExit(f"missing migrated token: {token}")
    if "announceForAccessibility" not in source:
        raise SystemExit("announcement sink missing")

    GAME_VIEW.write_text(source, encoding="utf-8")
    for path in (WORKFLOW, SELF):
        if path.exists():
            path.unlink()


if __name__ == "__main__":
    main()
