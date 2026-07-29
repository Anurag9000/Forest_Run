#!/usr/bin/env python3
"""Publish manager pressure into physical performance reports."""

from pathlib import Path

ENTITY = Path("app/src/main/java/com/anurag9000/forestrun/engine/EntityManager.kt")
ORBS = Path("app/src/main/java/com/anurag9000/forestrun/systems/SeedOrbManager.kt")
PARTICLES = Path("app/src/main/java/com/anurag9000/forestrun/systems/ParticleManager.kt")
DIALOGUE = Path("app/src/main/java/com/anurag9000/forestrun/ui/DialogueBubbleManager.kt")
FLAVOR = Path("app/src/main/java/com/anurag9000/forestrun/ui/FlavorTextManager.kt")
TELEMETRY = Path("app/src/main/java/com/anurag9000/forestrun/engine/FramePerformanceTelemetry.kt")
REPORT = Path("app/src/main/java/com/anurag9000/forestrun/engine/FramePerformanceReport.kt")
HARDWARE = Path("app/src/androidTest/java/com/anurag9000/forestrun/HardwarePerformanceProfileTest.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def patch_entity_manager() -> None:
    text = ENTITY.read_text(encoding="utf-8")
    text = replace_once(
        text,
        '''        seedOrbManager.update(deltaTime, gameState, player)
        debugActiveEntityCount = activeEntities.size
''',
        '''        seedOrbManager.update(deltaTime, gameState, player)
        debugActiveEntityCount = activeEntities.size
        RuntimeWorkloadTelemetry.publishEntities(activeEntities.size)
''',
        "entity workload publish",
    )
    text = replace_once(
        text,
        '''        bloomReactedEntities.clear()
        debugActiveEntityCount = 0
    }
''',
        '''        bloomReactedEntities.clear()
        debugActiveEntityCount = 0
        RuntimeWorkloadTelemetry.publishEntities(0)
    }
''',
        "entity workload reset",
    )
    ENTITY.write_text(text, encoding="utf-8")


def patch_orbs() -> None:
    text = ORBS.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "import com.anurag9000.forestrun.engine.SfxManager\n",
        "import com.anurag9000.forestrun.engine.SfxManager\nimport com.anurag9000.forestrun.engine.RuntimeWorkloadTelemetry\n",
        "orb workload import",
    )
    text = replace_once(
        text,
        '''            }
        }
    }

    fun draw(canvas: Canvas, bloomFraction: Float) {
''',
        '''            }
        }
        RuntimeWorkloadTelemetry.publishSeedOrbs(orbs.size)
    }

    fun draw(canvas: Canvas, bloomFraction: Float) {
''',
        "orb workload publish",
    )
    text = replace_once(
        text,
        "    fun reset() = orbs.clear()\n",
        '''    fun reset() {
        orbs.clear()
        RuntimeWorkloadTelemetry.publishSeedOrbs(0)
    }
''',
        "orb workload reset",
    )
    ORBS.write_text(text, encoding="utf-8")


def patch_particles() -> None:
    text = PARTICLES.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "import com.anurag9000.forestrun.engine.FeedbackSettings\n",
        "import com.anurag9000.forestrun.engine.FeedbackSettings\nimport com.anurag9000.forestrun.engine.RuntimeWorkloadTelemetry\n",
        "particle workload import",
    )
    text = replace_once(
        text,
        '''        // Update all particles
        for (p in pool) {
            if (p.isActive) {
                p.update(deltaTime)
                if (p.isDead) p.isActive = false
            }
        }
''',
        '''        // Update all particles and publish pressure without a second scan.
        var activeParticleCount = 0
        for (p in pool) {
            if (p.isActive) {
                p.update(deltaTime)
                if (p.isDead) {
                    p.isActive = false
                } else {
                    activeParticleCount++
                }
            }
        }
        RuntimeWorkloadTelemetry.publishParticles(activeParticleCount)
''',
        "particle workload publish",
    )
    text = replace_once(
        text,
        '''        poolHead = 0
    }
''',
        '''        poolHead = 0
        RuntimeWorkloadTelemetry.publishParticles(0)
    }
''',
        "particle workload reset",
    )
    PARTICLES.write_text(text, encoding="utf-8")


def patch_dialogue() -> None:
    text = DIALOGUE.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "import com.anurag9000.forestrun.engine.AssetPaths\n",
        "import com.anurag9000.forestrun.engine.AssetPaths\nimport com.anurag9000.forestrun.engine.RuntimeWorkloadTelemetry\n",
        "dialogue workload import",
    )
    text = replace_once(
        text,
        '''            }
        }
    }

    fun draw(canvas: Canvas) {
''',
        '''            }
        }
        RuntimeWorkloadTelemetry.publishDialogueBubbles(active.size)
    }

    fun draw(canvas: Canvas) {
''',
        "dialogue workload publish",
    )
    text = replace_once(
        text,
        '''        variantCounts.clear()
        lineMeasurementCountForTest = 0
''',
        '''        variantCounts.clear()
        lineMeasurementCountForTest = 0
        RuntimeWorkloadTelemetry.publishDialogueBubbles(0)
''',
        "dialogue workload reset",
    )
    DIALOGUE.write_text(text, encoding="utf-8")


def patch_flavor() -> None:
    text = FLAVOR.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "import com.anurag9000.forestrun.engine.AssetPaths\n",
        "import com.anurag9000.forestrun.engine.AssetPaths\nimport com.anurag9000.forestrun.engine.RuntimeWorkloadTelemetry\n",
        "flavor workload import",
    )
    text = replace_once(
        text,
        '''            }
        }
    }

    fun draw(canvas: Canvas) {
''',
        '''            }
        }
        RuntimeWorkloadTelemetry.publishFlavorTexts(active.size)
    }

    fun draw(canvas: Canvas) {
''',
        "flavor workload publish",
    )
    text = replace_once(
        text,
        "    fun clear() = active.clear()\n",
        '''    fun clear() {
        active.clear()
        RuntimeWorkloadTelemetry.publishFlavorTexts(0)
    }
''',
        "flavor workload reset",
    )
    FLAVOR.write_text(text, encoding="utf-8")


def patch_telemetry() -> None:
    text = TELEMETRY.read_text(encoding="utf-8")
    text = replace_once(
        text,
        '''        monitor = FramePerformanceMonitor(windowSize, frameBudgetNs)
    }
''',
        '''        monitor = FramePerformanceMonitor(windowSize, frameBudgetNs)
        RuntimeWorkloadTelemetry.reset()
    }
''',
        "profiling workload reset",
    )
    TELEMETRY.write_text(text, encoding="utf-8")


def patch_report() -> None:
    text = REPORT.read_text(encoding="utf-8")
    text = replace_once(
        text,
        '''    val apiLevel: Int,
    val refreshRateHz: Float,
    val snapshot: FramePerformanceSnapshot
) {
''',
        '''    val apiLevel: Int,
    val refreshRateHz: Float,
    val snapshot: FramePerformanceSnapshot,
    val workload: RuntimeWorkloadSnapshot = RuntimeWorkloadSnapshot.EMPTY
) {
''',
        "report workload field",
    )
    text = replace_once(
        text,
        '''        append("  \\"usedHeapBytes\\": ").append(snapshot.usedHeapBytes).append(",\\n")
        append("  \\"maxHeapBytes\\": ").append(snapshot.maxHeapBytes).append("\\n")
''',
        '''        append("  \\"usedHeapBytes\\": ").append(snapshot.usedHeapBytes).append(",\\n")
        append("  \\"maxHeapBytes\\": ").append(snapshot.maxHeapBytes).append(",\\n")
        append("  \\"currentEntities\\": ").append(workload.currentEntities).append(",\\n")
        append("  \\"peakEntities\\": ").append(workload.peakEntities).append(",\\n")
        append("  \\"currentSeedOrbs\\": ").append(workload.currentSeedOrbs).append(",\\n")
        append("  \\"peakSeedOrbs\\": ").append(workload.peakSeedOrbs).append(",\\n")
        append("  \\"currentParticles\\": ").append(workload.currentParticles).append(",\\n")
        append("  \\"peakParticles\\": ").append(workload.peakParticles).append(",\\n")
        append("  \\"currentDialogueBubbles\\": ").append(workload.currentDialogueBubbles).append(",\\n")
        append("  \\"peakDialogueBubbles\\": ").append(workload.peakDialogueBubbles).append(",\\n")
        append("  \\"currentFlavorTexts\\": ").append(workload.currentFlavorTexts).append(",\\n")
        append("  \\"peakFlavorTexts\\": ").append(workload.peakFlavorTexts).append("\\n")
''',
        "report workload JSON",
    )
    REPORT.write_text(text, encoding="utf-8")


def patch_hardware() -> None:
    text = HARDWARE.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "import com.anurag9000.forestrun.engine.RunMode\n",
        "import com.anurag9000.forestrun.engine.RunMode\nimport com.anurag9000.forestrun.engine.RuntimeWorkloadTelemetry\n",
        "hardware workload import",
    )
    text = replace_once(
        text,
        '''                apiLevel = Build.VERSION.SDK_INT,
                refreshRateHz = refreshRateHz.coerceAtLeast(0f),
                snapshot = snapshot
''',
        '''                apiLevel = Build.VERSION.SDK_INT,
                refreshRateHz = refreshRateHz.coerceAtLeast(0f),
                snapshot = snapshot,
                workload = RuntimeWorkloadTelemetry.snapshot()
''',
        "hardware workload report",
    )
    HARDWARE.write_text(text, encoding="utf-8")


def verify() -> None:
    checks = {
        ENTITY: "publishEntities",
        ORBS: "publishSeedOrbs",
        PARTICLES: "publishParticles",
        DIALOGUE: "publishDialogueBubbles",
        FLAVOR: "publishFlavorTexts",
        TELEMETRY: "RuntimeWorkloadTelemetry.reset()",
        REPORT: "peakParticles",
        HARDWARE: "RuntimeWorkloadTelemetry.snapshot()",
    }
    missing = [str(path) for path, marker in checks.items() if marker not in path.read_text(encoding="utf-8")]
    if missing:
        raise RuntimeError(f"Missing workload integration: {missing}")


def main() -> None:
    patch_entity_manager()
    patch_orbs()
    patch_particles()
    patch_dialogue()
    patch_flavor()
    patch_telemetry()
    patch_report()
    patch_hardware()
    verify()
    print("Integrated runtime workload telemetry")


if __name__ == "__main__":
    main()
