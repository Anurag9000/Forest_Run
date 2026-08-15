package com.anurag9000.forestrun.engine

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Central, preference-aware haptic feedback owner.
 *
 * New call sites should use the semantic cue methods instead of choosing raw
 * durations. Compatibility wrappers remain for older gameplay owners while
 * preserving their existing physical feedback.
 */
object HapticManager {
    private var vibrator: Vibrator? = null

    @Synchronized
    fun init(context: Context) {
        vibrator = runCatching {
            val appContext = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    /** Light confirmation for Seeds, small UI actions, and similarly quiet events. */
    fun lightTick() = pulse(40L)

    /** Medium physical interruption used by stumble-like nonterminal impacts. */
    fun stumbleImpact() = pulse(100L)

    /** Weighted terminal feedback; intentionally distinct from ordinary stumble. */
    fun terminalImpact() = pulse(200L)

    /** Soft paired acknowledgement for mercy and relationship-positive moments. */
    fun mercyAcknowledgement() {
        waveform(
            timings = longArrayOf(0, 30, 50, 30),
            amplitudes = intArrayOf(0, 180, 0, 220),
            legacyDurationMs = 110L
        )
    }

    /** Deliberate home/growth cadence for meaningful Garden progression. */
    fun gardenGrowth() {
        waveform(
            timings = longArrayOf(0, 35, 45, 55),
            amplitudes = intArrayOf(0, 140, 0, 205),
            legacyDurationMs = 135L
        )
    }

    /** Bloom uses a rising multi-step signature rather than an ordinary impact. */
    fun bloomSurge() {
        waveform(
            timings = longArrayOf(0, 40, 25, 70, 35, 140),
            amplitudes = intArrayOf(0, 140, 0, 200, 0, 255),
            legacyDurationMs = 240L
        )
    }

    // Compatibility vocabulary used by existing owners. These wrappers keep
    // old behavior stable while giving new code a semantic API to target.
    fun shortPulse() = lightTick()
    fun mediumPulse() = stumbleImpact()
    fun longPulse() = terminalImpact()
    fun doubleTap() = mercyAcknowledgement()

    private fun pulse(durationMs: Long) {
        if (durationMs <= 0L) return
        withAvailableVibrator { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        }
    }

    private fun waveform(
        timings: LongArray,
        amplitudes: IntArray,
        legacyDurationMs: Long
    ) {
        if (timings.isEmpty() || timings.size != amplitudes.size || legacyDurationMs <= 0L) return
        withAvailableVibrator { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
        }
    }

    private inline fun withAvailableVibrator(action: (Vibrator) -> Unit) {
        if (!FeedbackSettings.hapticsEnabled) return
        val vib = vibrator ?: return
        runCatching {
            if (vib.hasVibrator()) action(vib)
        }
    }

    /** Cancel current feedback while retaining the service for later re-enable. */
    fun cancel() {
        vibrator?.let { vib -> runCatching { vib.cancel() } }
    }

    /** Release process-owned service state during final Activity teardown. */
    @Synchronized
    fun release() {
        cancel()
        vibrator = null
    }

    internal fun hasServiceForTest(): Boolean = vibrator != null
}
