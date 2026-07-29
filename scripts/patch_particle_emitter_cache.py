#!/usr/bin/env python3
"""Cache named one-shot emitters and marshal foreign-thread requests to GameThread."""

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

    // Named effects may be requested from Android UI callbacks while the
    // fixed particle pool is owned by GameThread. Keep those requests in a
    // bounded, preallocated command queue and apply them during update().
    private const val MAX_PENDING_ONE_SHOTS = 32
    private val pendingPresetOrdinals = IntArray(MAX_PENDING_ONE_SHOTS)
    private val pendingXs = FloatArray(MAX_PENDING_ONE_SHOTS)
    private val pendingYs = FloatArray(MAX_PENDING_ONE_SHOTS)
    private val pendingLock = Any()
    private var pendingHead = 0
    private var pendingSize = 0

    @Volatile
    private var ownerThreadId = 0L

    // Named one-shot effects are stateless after configure() copies their
    // values into a pooled Particle, so one emitter per preset is sufficient.
    // Continuous owners still call FxPreset.build(...) and addContinuous().
    private val oneShotEmitterCache = arrayOfNulls<ParticleEmitter>(FxPreset.entries.size)
    private var oneShotEmitterBuildCount = 0

    // ── Update ────────────────────────────────────────────────────────────
""",
        "particle ownership and queue state",
    )

    replace_once(
        path,
        """    fun update(deltaTime: Float) {
        // Update continuous emitters
""",
        """    fun update(deltaTime: Float) {
        ownerThreadId = Thread.currentThread().id
        flushPendingOneShots()

        // Update continuous emitters
""",
        "bind particle owner and flush commands",
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
     * Requests from the particle-owner thread use the cached emitter directly.
     * Requests from any other thread are copied into a bounded primitive queue
     * and applied by the next [update], so the particle pool remains single-threaded.
     */
    fun emit(preset: FxPreset, x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        val owner = ownerThreadId
        if (owner != 0L && owner == Thread.currentThread().id) {
            emitOneShotNow(preset, x, y)
        } else {
            enqueueOneShot(preset, x, y)
        }
    }

    private fun emitOneShotNow(preset: FxPreset, x: Float, y: Float) {
        val index = preset.ordinal
        val emitter = oneShotEmitterCache[index] ?: preset.build(x, y).also {
            oneShotEmitterCache[index] = it
            oneShotEmitterBuildCount++
        }
        emitter.x = x
        emitter.y = y
        emit(emitter)
    }

    private fun enqueueOneShot(preset: FxPreset, x: Float, y: Float) {
        synchronized(pendingLock) {
            if (pendingSize == MAX_PENDING_ONE_SHOTS) {
                pendingHead = (pendingHead + 1) % MAX_PENDING_ONE_SHOTS
                pendingSize--
            }
            val tail = (pendingHead + pendingSize) % MAX_PENDING_ONE_SHOTS
            pendingPresetOrdinals[tail] = preset.ordinal
            pendingXs[tail] = x
            pendingYs[tail] = y
            pendingSize++
        }
    }

    private fun flushPendingOneShots() {
        while (true) {
            var ordinal = -1
            var x = 0f
            var y = 0f
            synchronized(pendingLock) {
                if (pendingSize == 0) return
                ordinal = pendingPresetOrdinals[pendingHead]
                x = pendingXs[pendingHead]
                y = pendingYs[pendingHead]
                pendingHead = (pendingHead + 1) % MAX_PENDING_ONE_SHOTS
                pendingSize--
            }
            emitOneShotNow(FxPreset.entries[ordinal], x, y)
        }
    }

    internal fun cachedOneShotEmitterForTest(preset: FxPreset): ParticleEmitter? =
        oneShotEmitterCache[preset.ordinal]

    internal val oneShotEmitterBuildCountForTest: Int
        get() = oneShotEmitterBuildCount

    internal fun pendingOneShotCountForTest(): Int = synchronized(pendingLock) { pendingSize }

    internal fun resetOneShotEmitterCacheForTests() {
        oneShotEmitterCache.fill(null)
        oneShotEmitterBuildCount = 0
        ownerThreadId = 0L
        synchronized(pendingLock) {
            pendingHead = 0
            pendingSize = 0
        }
        clear()
    }

    /** Register a continuous emitter (e.g. Bloom aura). Returns a handle to stop it. */
""",
        "cached and thread-safe named one-shot emission",
    )

    replace_once(
        path,
        """    fun clear() {
        for (p in pool) p.isActive = false
        continuousEmitters.clear()
        poolHead = 0
    }
""",
        """    fun clear() {
        for (p in pool) p.isActive = false
        continuousEmitters.clear()
        synchronized(pendingLock) {
            pendingHead = 0
            pendingSize = 0
        }
        poolHead = 0
    }
""",
        "clear pending particle commands",
    )


if __name__ == "__main__":
    main()
