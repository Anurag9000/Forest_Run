#!/usr/bin/env python3
"""Remove event-time ParticleEmitter allocation from named one-shot effects."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    path = Path(
        "app/src/main/java/com/anurag9000/forestrun/systems/ParticleManager.kt"
    )

    replace_once(
        path,
        """    private val continuousEmitters = mutableListOf<ParticleEmitter>()

    // ── Update ────────────────────────────────────────────────────────────
""",
        """    private val continuousEmitters = mutableListOf<ParticleEmitter>()

    // Calls through emit(FxPreset, x, y) are immediate one-shot effects, even
    // when the preset can also describe a continuous stream. Reuse one emitter
    // per named one-shot preset; continuous owners still call preset.build(...)
    // and retain their own independent emission timers.
    private val oneShotEmitterCache = arrayOfNulls<ParticleEmitter>(FxPreset.entries.size)
    private var oneShotEmitterBuildCount = 0

    // ── Update ────────────────────────────────────────────────────────────
""",
        "one-shot emitter cache fields",
    )

    replace_once(
        path,
        """    /** Emit a burst from a named preset at screen position (x, y). */
    fun emit(preset: FxPreset, x: Float, y: Float) {
        val emitter = preset.build(x, y)
        emit(emitter)
    }

    /** Register a continuous emitter (e.g. Bloom aura). Returns a handle to stop it. */
""",
        """    /**
     * Emit a named one-shot effect at screen position (x, y).
     *
     * The emitter is cached after first use because configure() copies every
     * particle property into the fixed pool before this method returns. A
     * continuous owner must instead call [FxPreset.build] and [addContinuous]
     * so its mutable timer is never shared.
     */
    fun emit(preset: FxPreset, x: Float, y: Float) {
        val index = preset.ordinal
        val emitter = oneShotEmitterCache[index] ?: preset.build(x, y).also {
            oneShotEmitterCache[index] = it
            oneShotEmitterBuildCount++
        }
        emitter.x = x
        emitter.y = y
        emit(emitter)
    }

    internal fun cachedOneShotEmitterForTest(preset: FxPreset): ParticleEmitter? =
        oneShotEmitterCache[preset.ordinal]

    internal val oneShotEmitterBuildCountForTest: Int
        get() = oneShotEmitterBuildCount

    internal fun resetOneShotEmitterCacheForTests() {
        oneShotEmitterCache.fill(null)
        oneShotEmitterBuildCount = 0
        clear()
    }

    /** Register a continuous emitter (e.g. Bloom aura). Returns a handle to stop it. */
""",
        "allocation-free named one-shot emission",
    )


if __name__ == "__main__":
    main()
