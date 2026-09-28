package com.anurag9000.forestrun.engine

/**
 * Frame-cadence-independent clock for non-physics presentation work.
 *
 * GameThread targets 60 Hz, but real processing can miss that target and
 * GameView therefore receives variable, bounded frame deltas. Presentation
 * phase and accessibility sampling must follow admitted elapsed seconds rather
 * than assuming every update was exactly 1/60 second.
 */
internal class RuntimeCadenceClock(
    private val accessibilityPollIntervalSeconds: Float =
        DEFAULT_ACCESSIBILITY_POLL_INTERVAL_SECONDS
) {
    init {
        require(
            accessibilityPollIntervalSeconds.isFinite() &&
                accessibilityPollIntervalSeconds > 0f
        ) { "accessibility poll interval must be finite and positive" }
    }

    var elapsedSeconds: Float = 0f
        private set

    private var accessibilityPollElapsedSeconds: Float = 0f

    fun advance(deltaSeconds: Float) {
        if (!deltaSeconds.isFinite() || deltaSeconds <= 0f) return
        elapsedSeconds = finiteSaturatingAdd(elapsedSeconds, deltaSeconds)
        accessibilityPollElapsedSeconds = finiteSaturatingAdd(
            accessibilityPollElapsedSeconds,
            deltaSeconds
        ).coerceAtMost(accessibilityPollIntervalSeconds)
    }

    fun consumeAccessibilityPoll(): Boolean {
        if (accessibilityPollElapsedSeconds + EPSILON_SECONDS <
            accessibilityPollIntervalSeconds
        ) return false
        accessibilityPollElapsedSeconds = 0f
        return true
    }

    internal fun reset() {
        elapsedSeconds = 0f
        accessibilityPollElapsedSeconds = 0f
    }

    private fun finiteSaturatingAdd(value: Float, delta: Float): Float {
        val safeValue = value.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val sum = safeValue.toDouble() + delta.toDouble()
        return sum.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
    }

    companion object {
        internal const val DEFAULT_ACCESSIBILITY_POLL_INTERVAL_SECONDS = 0.5f
        private const val EPSILON_SECONDS = 0.000001f
    }
}
