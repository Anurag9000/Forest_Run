package com.yourname.forest_run.engine

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * HapticManager — Phase 21.
 *
 * All haptic triggers from TECHNICAL_ARCHITECTURE.md Section 10.
 *
 * Patterns:
 *  shortPulse()  — 40ms  — jumps, seed collection
 *  longPulse()   — 200ms — Bloom activation, Game Over
 *  doubleTap()   — [0, 30, 50, 30] ms — Close Call / MERCY_MISS
 *  mediumPulse() — 100ms — 1000-point score milestone
 *
 * Graceful degradation:
 *  - No crash if the device has no vibrator.
 *  - No crash on emulators.
 *  - Uses [VibrationEffect] (API 26+) when available, falls back to deprecated
 *    vibrate(long) on older devices (API 24-25).
 *
 * Usage:
 *   HapticManager.init(context)
 *   HapticManager.shortPulse()
 */
object HapticManager {

    private var vibrator: Vibrator? = null

    // ── Init ──────────────────────────────────────────────────────────────

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+: VibratorManager
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // ── Patterns ──────────────────────────────────────────────────────────

    /** 40ms — jump, seed ping */
    fun shortPulse() = pulse(40)

    /** 200ms — Bloom activation, Game Over */
    fun longPulse() = pulse(200)

    /** 100ms — 1000-point milestone */
    fun mediumPulse() = pulse(100)

    /** Bloom activation surge with a stronger rising feel than a flat long pulse. */
    fun bloomSurge() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 40, 25, 70, 35, 140)
            val amplitudes = intArrayOf(0, 140, 0, 200, 0, 255)
            vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(240)
        }
    }

    /**
     * Double-tap pattern — [0, 30, 50, 30] ms — Close Call / MERCY_MISS.
     * timings array (API 26): [wait, vibrate, wait, vibrate] — all in ms.
     */
    fun doubleTap() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings    = longArrayOf(0, 30, 50, 30)
            val amplitudes = intArrayOf(0, 180, 0, 220)
            vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(LongArray(4) { longArrayOf(0, 30, 50, 30)[it] }, -1)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun pulse(durationMs: Long) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(durationMs)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /** Call from MainActivity.onDestroy() — cancels any ongoing vibration. */
    fun cancel() {
        vibrator?.cancel()
    }
}
