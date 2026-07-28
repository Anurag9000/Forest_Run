package com.anurag9000.forestrun.engine

import android.content.Context
import kotlin.math.roundToInt

/** Persisted comfort and feedback preferences, independent of game progression. */
data class FeedbackPreferences(
    val reducedMotion: Boolean = false,
    val audioEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true
)

object FeedbackSettings {
    internal const val PREFS_NAME = "forest_run_feedback_settings"
    private const val KEY_REDUCED_MOTION = "reduced_motion"
    private const val KEY_AUDIO_ENABLED = "audio_enabled"
    private const val KEY_HAPTICS_ENABLED = "haptics_enabled"

    @Volatile
    var reducedMotion: Boolean = false
        private set

    @Volatile
    var audioEnabled: Boolean = true
        private set

    @Volatile
    var hapticsEnabled: Boolean = true
        private set

    @Synchronized
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val all = prefs.all
        reducedMotion = all[KEY_REDUCED_MOTION] as? Boolean ?: false
        audioEnabled = all[KEY_AUDIO_ENABLED] as? Boolean ?: true
        hapticsEnabled = all[KEY_HAPTICS_ENABLED] as? Boolean ?: true

        val editor = prefs.edit()
        var repaired = false
        if (all.containsKey(KEY_REDUCED_MOTION) && all[KEY_REDUCED_MOTION] !is Boolean) {
            editor.putBoolean(KEY_REDUCED_MOTION, reducedMotion)
            repaired = true
        }
        if (all.containsKey(KEY_AUDIO_ENABLED) && all[KEY_AUDIO_ENABLED] !is Boolean) {
            editor.putBoolean(KEY_AUDIO_ENABLED, audioEnabled)
            repaired = true
        }
        if (all.containsKey(KEY_HAPTICS_ENABLED) && all[KEY_HAPTICS_ENABLED] !is Boolean) {
            editor.putBoolean(KEY_HAPTICS_ENABLED, hapticsEnabled)
            repaired = true
        }
        if (repaired) editor.commit()

        if (reducedMotion) CameraSystem.reset()
        if (!hapticsEnabled) HapticManager.cancel()
        LeitmotifManager.setAudioEnabled(audioEnabled)
    }

    fun snapshot(): FeedbackPreferences = FeedbackPreferences(
        reducedMotion = reducedMotion,
        audioEnabled = audioEnabled,
        hapticsEnabled = hapticsEnabled
    )

    @Synchronized
    fun setReducedMotion(context: Context, enabled: Boolean) {
        reducedMotion = enabled
        persist(context, KEY_REDUCED_MOTION, enabled)
        if (enabled) CameraSystem.reset()
    }

    @Synchronized
    fun setAudioEnabled(context: Context, enabled: Boolean) {
        audioEnabled = enabled
        persist(context, KEY_AUDIO_ENABLED, enabled)
        LeitmotifManager.setAudioEnabled(enabled)
    }

    @Synchronized
    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        hapticsEnabled = enabled
        persist(context, KEY_HAPTICS_ENABLED, enabled)
        if (!enabled) HapticManager.cancel()
    }

    private fun persist(context: Context, key: String, value: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    internal fun resetMemoryForTests() {
        reducedMotion = false
        audioEnabled = true
        hapticsEnabled = true
        CameraSystem.reset()
        LeitmotifManager.setAudioEnabled(true)
    }
}

internal fun adjustedParticleCount(baseCount: Int, reducedMotion: Boolean): Int {
    if (baseCount <= 0) return 0
    if (!reducedMotion) return baseCount
    return (baseCount * 0.35f).roundToInt().coerceIn(1, baseCount)
}

internal fun cinematicShimmerPulse(
    elapsedSeconds: Float,
    shimmerStrength: Float,
    reducedMotion: Boolean
): Float {
    if (reducedMotion) return 0.55f
    return 0.55f + 0.45f * kotlin.math.sin(elapsedSeconds * (1.2f + shimmerStrength))
}
