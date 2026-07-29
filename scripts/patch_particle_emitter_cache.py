#!/usr/bin/env python3
"""Cache named one-shot emitters and keep particle mutation on the game thread."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    particle_manager = Path(
        "app/src/main/java/com/anurag9000/forestrun/systems/ParticleManager.kt"
    )

    replace_once(
        particle_manager,
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
        particle_manager,
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

    garden = Path(
        "app/src/main/java/com/anurag9000/forestrun/ui/GardenScreen.kt"
    )
    replace_once(
        garden,
        """    private var unlockAnim: Float = -1f   // -1 = none; 0..1 = progress
    private var unlockIdx:  Int   = -1

    private var elapsed = 0f
""",
        """    private var unlockAnim: Float = -1f   // -1 = none; 0..1 = progress
    private var unlockIdx:  Int   = -1
    @Volatile private var pendingUnlockParticle = false
    private var pendingUnlockParticleX = 0f
    private var pendingUnlockParticleY = 0f

    private var elapsed = 0f
""",
        "Garden pending particle state",
    )
    replace_once(
        garden,
        """        catalogueSprites.forEach { it.update(deltaTime) }
        returnVisitorSprite?.update(deltaTime)
        ParticleManager.update(deltaTime)
""",
        """        catalogueSprites.forEach { it.update(deltaTime) }
        returnVisitorSprite?.update(deltaTime)
        if (pendingUnlockParticle) {
            val effectX = pendingUnlockParticleX
            val effectY = pendingUnlockParticleY
            pendingUnlockParticle = false
            ParticleManager.emit(FxPreset.SEED_COLLECT, effectX, effectY)
        }
        ParticleManager.update(deltaTime)
""",
        "Garden game-thread particle flush",
    )
    replace_once(
        garden,
        """                    // Bloom burst (using SEED_COLLECT preset for a nice golden unlock pop)
                    ParticleManager.emit(FxPreset.SEED_COLLECT, cx, cy)
                    // Persist
""",
        """                    // Touch callbacks run on the Android UI thread. Queue the
                    // visual burst for update(), which owns the particle pool.
                    pendingUnlockParticleX = cx
                    pendingUnlockParticleY = cy
                    pendingUnlockParticle = true
                    // Persist
""",
        "Garden UI-thread particle deferral",
    )


if __name__ == "__main__":
    main()
