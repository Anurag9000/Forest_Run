package com.anurag9000.forestrun.engine

enum class BloomPresentationMode {
    CHARGING,
    READY,
    ACTIVE,
    AFTERGLOW
}

data class BloomHudPresentation(
    var mode: BloomPresentationMode = BloomPresentationMode.CHARGING,
    var labelText: String = "bloom",
    var statusText: String = "0/1",
    var emphasis: Float = 0f
) {
    internal var cachedModeOrdinal: Int = -1
    internal var cachedMeter: Int = Int.MIN_VALUE
    internal var cachedTarget: Int = Int.MIN_VALUE
    internal var cachedSecondsTenths: Int = Int.MIN_VALUE
    internal var cachedTotalConversions: Int = Int.MIN_VALUE
    internal var cachedBurstConversions: Int = Int.MIN_VALUE
}

object BloomPresentation {

    fun resolveInto(
        target: BloomHudPresentation,
        bloomMeter: Int,
        seedTarget: Int,
        isActive: Boolean,
        secondsRemaining: Float,
        totalConversions: Int,
        burstConversions: Int,
        recentAfterglow: Float
    ): BloomHudPresentation {
        val safeTarget = seedTarget.coerceAtLeast(1)
        val safeMeter = bloomMeter.coerceIn(0, safeTarget)
        val safeAfterglow = recentAfterglow.coerceIn(0f, 1f)
        val safeBurstConversions = burstConversions.coerceAtLeast(0)
        val safeTotalConversions = totalConversions.coerceAtLeast(0)
        val secondsTenths = (secondsRemaining.coerceAtLeast(0f) * 10f + 0.5f).toInt()
        val mode = when {
            isActive -> BloomPresentationMode.ACTIVE
            safeAfterglow > 0.01f -> BloomPresentationMode.AFTERGLOW
            safeMeter >= safeTarget - 1 -> BloomPresentationMode.READY
            else -> BloomPresentationMode.CHARGING
        }
        val textChanged =
            target.cachedModeOrdinal != mode.ordinal ||
                target.cachedMeter != safeMeter ||
                target.cachedTarget != safeTarget ||
                target.cachedSecondsTenths != secondsTenths ||
                target.cachedTotalConversions != safeTotalConversions ||
                target.cachedBurstConversions != safeBurstConversions

        if (textChanged) {
            when (mode) {
                BloomPresentationMode.ACTIVE -> {
                    target.labelText = "BLOOM"
                    target.statusText = if (safeTotalConversions > 0) {
                        "${formatTenths(secondsTenths)}  •  $safeTotalConversions converts  •  hold the light"
                    } else {
                        "${formatTenths(secondsTenths)}  •  world open"
                    }
                }
                BloomPresentationMode.AFTERGLOW -> {
                    target.labelText = "AFTERGLOW"
                    target.statusText = if (safeBurstConversions > 0) {
                        "$safeBurstConversions converts  •  the light is still hanging here"
                    } else {
                        "Bloom has eased, but the light has not fully left"
                    }
                }
                BloomPresentationMode.READY -> {
                    target.labelText = "READY"
                    target.statusText = "1 more seed  •  Bloom is waiting"
                }
                BloomPresentationMode.CHARGING -> {
                    target.labelText = "bloom"
                    target.statusText = "$safeMeter/$safeTarget"
                }
            }
            target.cachedModeOrdinal = mode.ordinal
            target.cachedMeter = safeMeter
            target.cachedTarget = safeTarget
            target.cachedSecondsTenths = secondsTenths
            target.cachedTotalConversions = safeTotalConversions
            target.cachedBurstConversions = safeBurstConversions
        }

        target.mode = mode
        target.emphasis = when (mode) {
            BloomPresentationMode.ACTIVE -> {
                val timeFraction =
                    (secondsRemaining / GameConstants.BLOOM_DURATION_S).coerceIn(0f, 1f)
                0.72f + timeFraction * 0.28f
            }
            BloomPresentationMode.AFTERGLOW -> safeAfterglow
            BloomPresentationMode.READY -> 0.76f
            BloomPresentationMode.CHARGING -> safeMeter / safeTarget.toFloat()
        }
        return target
    }

    fun hudPresentation(
        bloomMeter: Int,
        seedTarget: Int,
        isActive: Boolean,
        secondsRemaining: Float,
        totalConversions: Int,
        burstConversions: Int,
        recentAfterglow: Float
    ): BloomHudPresentation = resolveInto(
        target = BloomHudPresentation(),
        bloomMeter = bloomMeter,
        seedTarget = seedTarget,
        isActive = isActive,
        secondsRemaining = secondsRemaining,
        totalConversions = totalConversions,
        burstConversions = burstConversions,
        recentAfterglow = recentAfterglow
    )

    private fun formatTenths(tenths: Int): String =
        "${tenths / 10}.${tenths % 10}s"
}
