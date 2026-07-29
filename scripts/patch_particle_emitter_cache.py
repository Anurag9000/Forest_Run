#!/usr/bin/env python3
"""Remove event-time ParticleEmitter allocation for burst presets."""

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

    // Burst effects are stateless after configure() returns, so one emitter per
    // preset can be reused safely on the single gameplay thread. Continuous
    // emitters keep independent timers and are therefore never cached here.
    private val burstEmitterCache = arrayOfNulls<ParticleEmitter>(FxPreset.entries.size)
    private var burstEmitterBuildCount = 0

    // ── Update ────────────────────────────────────────────────────────────
""",
        "burst emitter cache fields",
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
     * Emit a named preset at screen position (x, y).
     *
     * Burst presets reuse one emitter instance after first use. Continuous
     * presets deliberately bypass the cache because their emission timer is
     * mutable and must remain private to the owning effect.
     */
    fun emit(preset: FxPreset, x: Float, y: Float) {
        val index = preset.ordinal
        val cached = burstEmitterCache[index]
        if (cached != null) {
            cached.x = x
            cached.y = y
            emit(cached)
            return
        }

        val created = preset.build(x, y)
        if (created.isBurst) {
            burstEmitterCache[index] = created
            burstEmitterBuildCount++
        }
        emit(created)
    }

    internal fun cachedBurstEmitterForTest(preset: FxPreset): ParticleEmitter? =
        burstEmitterCache[preset.ordinal]

    internal fun burstEmitterBuildCountForTest(): Int = burstEmitterBuildCount

    internal fun resetBurstEmitterCacheForTests() {
        burstEmitterCache.fill(null)
        burstEmitterBuildCount = 0
        clear()
    }

    /** Register a continuous emitter (e.g. Bloom aura). Returns a handle to stop it. */
""",
        "allocation-free named burst emission",
    )


if __name__ == "__main__":
    main()
