package com.yourname.forest_run.engine

import java.util.Locale

enum class BloomPresentationMode {
    CHARGING,
    READY,
    ACTIVE,
    AFTERGLOW
}

data class BloomHudPresentation(
    val mode: BloomPresentationMode,
    val labelText: String,
    val statusText: String,
    val emphasis: Float
)

object BloomPresentation {

    fun hudPresentation(
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

        return when {
            isActive -> {
                val timeFraction = (secondsRemaining / GameConstants.BLOOM_DURATION_S).coerceIn(0f, 1f)
                BloomHudPresentation(
                    mode = BloomPresentationMode.ACTIVE,
                    labelText = "BLOOM",
                    statusText = when {
                        safeTotalConversions > 0 ->
                            "${formatSeconds(secondsRemaining)}  •  $safeTotalConversions converts  •  hold the light"
                        else -> "${formatSeconds(secondsRemaining)}  •  world open"
                    },
                    emphasis = 0.72f + timeFraction * 0.28f
                )
            }

            safeAfterglow > 0.01f -> {
                BloomHudPresentation(
                    mode = BloomPresentationMode.AFTERGLOW,
                    labelText = "AFTERGLOW",
                    statusText = when {
                        safeBurstConversions > 0 ->
                            "$safeBurstConversions converts  •  the light is still hanging here"
                        else -> "Bloom has eased, but the light has not fully left"
                    },
                    emphasis = safeAfterglow
                )
            }

            safeMeter >= safeTarget - 1 -> {
                BloomHudPresentation(
                    mode = BloomPresentationMode.READY,
                    labelText = "READY",
                    statusText = "1 more seed  •  Bloom is waiting",
                    emphasis = 0.76f
                )
            }

            else -> {
                BloomHudPresentation(
                    mode = BloomPresentationMode.CHARGING,
                    labelText = "bloom",
                    statusText = "$safeMeter/$safeTarget",
                    emphasis = safeMeter / safeTarget.toFloat()
                )
            }
        }
    }

    private fun formatSeconds(secondsRemaining: Float): String =
        String.format(Locale.US, "%.1fs", secondsRemaining.coerceAtLeast(0f))
}
