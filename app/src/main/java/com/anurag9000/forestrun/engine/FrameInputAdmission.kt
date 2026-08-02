package com.anurag9000.forestrun.engine

/**
 * Allocation-free admission and bounding for public per-frame timing inputs.
 *
 * Callers must reject a frame with [accepts] before using the two bounding
 * functions. A positive finite delta is capped to one 20 Hz recovery step so
 * a resumed or stalled frame cannot fast-forward simulation state. Scroll
 * speed is constrained to the canonical gameplay ceiling.
 */
internal object FrameInputAdmission {
    const val MAX_DELTA_SECONDS = 0.05f

    fun accepts(deltaSeconds: Float, scrollSpeed: Float): Boolean =
        deltaSeconds.isFinite() &&
            deltaSeconds > 0f &&
            scrollSpeed.isFinite() &&
            scrollSpeed >= 0f

    fun boundedDeltaSeconds(deltaSeconds: Float): Float {
        require(deltaSeconds.isFinite() && deltaSeconds > 0f) {
            "deltaSeconds must be finite and positive"
        }
        return deltaSeconds.coerceAtMost(MAX_DELTA_SECONDS)
    }

    fun boundedScrollSpeed(scrollSpeed: Float): Float {
        require(scrollSpeed.isFinite() && scrollSpeed >= 0f) {
            "scrollSpeed must be finite and non-negative"
        }
        return scrollSpeed.coerceAtMost(GameConstants.MAX_SCROLL_SPEED)
    }
}
