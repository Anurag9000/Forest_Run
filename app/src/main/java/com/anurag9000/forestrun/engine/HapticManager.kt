package com.anurag9000.forestrun.engine

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Central, preference-aware haptic feedback owner. */
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

    fun shortPulse() = pulse(40L)
    fun longPulse() = pulse(200L)
    fun mediumPulse() = pulse(100L)

    fun bloomSurge() {
        withAvailableVibrator { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 40, 25, 70, 35, 140),
                        intArrayOf(0, 140, 0, 200, 0, 255),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(240L)
            }
        }
    }

    fun doubleTap() {
        withAvailableVibrator { vib ->
            val timings = longArrayOf(0, 30, 50, 30)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    VibrationEffect.createWaveform(
                        timings,
                        intArrayOf(0, 180, 0, 220),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
        }
    }

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
