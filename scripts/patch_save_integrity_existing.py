#!/usr/bin/env python3
"""Wire startup save repair and harden existing persistence APIs."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    feedback = Path("app/src/main/java/com/anurag9000/forestrun/engine/FeedbackSettings.kt")
    replace_once(
        feedback,
        '''    @Synchronized
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        reducedMotion = prefs.getBoolean(KEY_REDUCED_MOTION, false)
        audioEnabled = prefs.getBoolean(KEY_AUDIO_ENABLED, true)
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        if (reducedMotion) CameraSystem.reset()
        if (!hapticsEnabled) HapticManager.cancel()
        LeitmotifManager.setAudioEnabled(audioEnabled)
    }
''',
        '''    @Synchronized
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
''',
        "type-safe feedback preference loading",
    )

    feedback_test = Path("app/src/test/java/com/anurag9000/forestrun/engine/FeedbackSettingsTest.kt")
    replace_once(
        feedback_test,
        '''    @Test
    fun `reduced motion prevents camera trauma`() {
''',
        '''    @Test
    fun `wrong typed stored preferences fall back safely and are repaired`() {
        val prefs = context.getSharedPreferences(FeedbackSettings.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("reduced_motion", "broken")
            .putInt("audio_enabled", 7)
            .putFloat("haptics_enabled", 1f)
            .commit()

        FeedbackSettings.init(context)

        assertEquals(FeedbackPreferences(), FeedbackSettings.snapshot())
        assertEquals(false, prefs.getBoolean("reduced_motion", true))
        assertEquals(true, prefs.getBoolean("audio_enabled", false))
        assertEquals(true, prefs.getBoolean("haptics_enabled", false))
    }

    @Test
    fun `reduced motion prevents camera trauma`() {
''',
        "feedback preference corruption regression",
    )

    main_activity = Path("app/src/main/java/com/anurag9000/forestrun/MainActivity.kt")
    replace_once(
        main_activity,
        '''import com.anurag9000.forestrun.engine.RuntimeAssetValidator
''',
        '''import com.anurag9000.forestrun.engine.RuntimeAssetValidator
import com.anurag9000.forestrun.engine.SaveIntegrityManager
''',
        "SaveIntegrityManager import",
    )
    replace_once(
        main_activity,
        '''        FeedbackSettings.init(this)
        RuntimeAssetValidator.validateRelease(this)
''',
        '''        SaveIntegrityManager.repair(this)
        FeedbackSettings.init(this)
        RuntimeAssetValidator.validateRelease(this)
''',
        "startup save repair",
    )

    save_manager = Path("app/src/main/java/com/anurag9000/forestrun/engine/SaveManager.kt")
    replace_once(
        save_manager,
        '''    private const val PREFS_NAME     = "forest_run_prefs"
''',
        '''    internal const val PREFS_NAME     = "forest_run_prefs"
    private const val COMPAT_PREFS_PREFIX = "forest_run_prefs_compat_v"

    @Volatile
    private var activePrefsName: String = PREFS_NAME

    internal val activePrefsNameForTests: String
        get() = activePrefsName

    internal fun usePrimaryPreferences() {
        activePrefsName = PREFS_NAME
    }

    internal fun useCompatibilityPreferences(schemaVersion: Int) {
        activePrefsName = "$COMPAT_PREFS_PREFIX${schemaVersion.coerceAtLeast(0)}"
    }
''',
        "expose preference file to integrity manager",
    )
    replace_once(
        save_manager,
        '''    fun saveHighScore(context: Context, score: Int) {
        prefs(context).edit().putInt(KEY_HIGH_SCORE, score).apply()
    }
''',
        '''    fun saveHighScore(context: Context, score: Int) {
        prefs(context).edit().putInt(KEY_HIGH_SCORE, score.coerceAtLeast(0)).apply()
    }
''',
        "clamp high score writes",
    )
    replace_once(
        save_manager,
        '''    fun saveBestDistance(context: Context, distanceM: Float) {
        prefs(context).edit().putFloat(KEY_BEST_DIST, distanceM).apply()
    }
''',
        '''    fun saveBestDistance(context: Context, distanceM: Float) {
        val safeDistance = distanceM.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        prefs(context).edit().putFloat(KEY_BEST_DIST, safeDistance).apply()
    }
''',
        "clamp best distance writes",
    )
    replace_once(
        save_manager,
        '''    fun saveLifetimeSeeds(context: Context, seeds: Int) {
        prefs(context).edit().putInt(KEY_LIFETIME_SEEDS, seeds).apply()
    }
''',
        '''    fun saveLifetimeSeeds(context: Context, seeds: Int) {
        prefs(context).edit().putInt(KEY_LIFETIME_SEEDS, seeds.coerceAtLeast(0)).apply()
    }
''',
        "clamp lifetime seed writes",
    )
    replace_once(
        save_manager,
        '''    fun saveGardenProgress(context: Context, unlockedCount: Int) {
        prefs(context).edit().putInt(KEY_GARDEN, unlockedCount).apply()
    }
''',
        '''    fun saveGardenProgress(context: Context, unlockedCount: Int) {
        prefs(context).edit().putInt(KEY_GARDEN, unlockedCount.coerceIn(1, 9)).apply()
    }
''',
        "clamp garden writes",
    )
    replace_once(
        save_manager,
        '''    private fun incrementInt(context: Context, key: String) {
        val prefs = prefs(context)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }
''',
        '''    private fun incrementInt(context: Context, key: String) {
        val prefs = prefs(context)
        val current = (prefs.all[key] as? Int)?.coerceAtLeast(0) ?: 0
        val next = if (current == Int.MAX_VALUE) Int.MAX_VALUE else current + 1
        prefs.edit().putInt(key, next).apply()
    }
''',
        "safe saturating counters",
    )


if __name__ == "__main__":
    main()
