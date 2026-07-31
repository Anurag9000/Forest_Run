package com.anurag9000.forestrun.systems

import com.anurag9000.forestrun.entities.PlayerState

/** Shared validation contract for in-memory publication and atomic persistence. */
object GhostRunValidator {
    fun isValid(frames: List<GhostFrame>): Boolean {
        if (frames.isEmpty() || frames.size > GhostRecorder.MAX_FRAMES) return false

        var previousTime = Float.NEGATIVE_INFINITY
        for (frame in frames) {
            if (!frame.t.isFinite() || frame.t < 0f || frame.t < previousTime) return false
            if (frame.t > GhostRecorder.MAX_DURATION_S.toFloat() + GhostRecorder.SAMPLE_INTERVAL_S) {
                return false
            }
            if (!frame.x.isFinite() || !frame.y.isFinite()) return false
            if (frame.stateOrdinal !in PlayerState.entries.indices) return false
            if (GhostStateCodec.encodeOrdinal(frame.stateOrdinal) == null) return false
            if (!frame.scaleX.isFinite() || frame.scaleX !in 0.1f..4f) return false
            if (!frame.scaleY.isFinite() || frame.scaleY !in 0.1f..4f) return false
            previousTime = frame.t
        }
        return true
    }
}
