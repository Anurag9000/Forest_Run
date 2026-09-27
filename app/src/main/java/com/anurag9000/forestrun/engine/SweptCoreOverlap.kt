package com.anurag9000.forestrun.engine

import android.graphics.RectF

/**
 * Checks whether two linearly interpolated, axis-aligned physical rectangles
 * overlap at the SAME instant in the last simulation interval.
 *
 * Each of the four strict edge inequalities is linear in time. Intersect their
 * admissible open intervals rather than unioning start/end AABBs, which would
 * report a false hit when vertical and horizontal paths cross at different
 * moments. This is scoped to the actual primary cores, not multi-part aggregate
 * boxes, animated painted silhouettes or mercy padding.
 */
internal object SweptCoreOverlap {
    fun intersects(
        playerBefore: RectF,
        playerAfter: RectF,
        coreBefore: RectF,
        coreAfter: RectF
    ): Boolean {
        if (!usable(playerBefore) || !usable(playerAfter) ||
            !usable(coreBefore) || !usable(coreAfter)
        ) return false

        var entry = 0.0
        var exit = 1.0
        fun admit(start: Double, end: Double): Boolean {
            if (start >= 0.0 && end >= 0.0) return false
            if (start < 0.0 && end < 0.0) return true
            val crossing = start / (start - end)
            if (start >= 0.0) {
                entry = maxOf(entry, crossing)
            } else {
                exit = minOf(exit, crossing)
            }
            return entry < exit
        }

        return admit(
            playerBefore.left.toDouble() - coreBefore.right.toDouble(),
            playerAfter.left.toDouble() - coreAfter.right.toDouble()
        ) && admit(
            coreBefore.left.toDouble() - playerBefore.right.toDouble(),
            coreAfter.left.toDouble() - playerAfter.right.toDouble()
        ) && admit(
            playerBefore.top.toDouble() - coreBefore.bottom.toDouble(),
            playerAfter.top.toDouble() - coreAfter.bottom.toDouble()
        ) && admit(
            coreBefore.top.toDouble() - playerBefore.bottom.toDouble(),
            coreAfter.top.toDouble() - playerAfter.bottom.toDouble()
        ) && entry < exit
    }

    private fun usable(rect: RectF): Boolean =
        rect.left.isFinite() && rect.top.isFinite() &&
            rect.right.isFinite() && rect.bottom.isFinite() &&
            rect.left < rect.right && rect.top < rect.bottom
}
