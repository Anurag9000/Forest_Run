package com.anurag9000.forestrun.engine

/** Finite admission boundary for visual-only Bloom strength channels. */
internal object BloomPresentationAdmission {
    fun level(value: Float): Float =
        value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
}
