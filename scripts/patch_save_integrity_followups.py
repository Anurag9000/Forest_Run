#!/usr/bin/env python3
"""Complete save namespace isolation and persistence-boundary hardening."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    integrity = Path("app/src/main/java/com/anurag9000/forestrun/engine/SaveIntegrityManager.kt")
    replace_once(
        integrity,
        '''        "last_run_pacifist_route"
    )

    internal fun repair(context: Context): SaveIntegrityReport {
''',
        '''        "last_run_pacifist_route"
    )
    private val requiredLastRunKeys = lastRunKeys - "last_run_killer"

    internal fun repair(context: Context): SaveIntegrityReport {
''',
        "required complete summary key set",
    )
    replace_once(
        integrity,
        '''        if (lastRunKeys.none(all::containsKey)) return
        if (all["last_run_score"] !is Int || all["last_run_quote"] !is String) {
''',
        '''        if (lastRunKeys.none(all::containsKey)) return
        if (requiredLastRunKeys.any { it !in all } ||
            all["last_run_score"] !is Int ||
            all["last_run_quote"] !is String
        ) {
''',
        "discard every incomplete last-run summary",
    )

    save_manager = Path("app/src/main/java/com/anurag9000/forestrun/engine/SaveManager.kt")
    replace_once(
        save_manager,
        '''    @Volatile
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
        '''    @Volatile
    private var activePrefsName: String = PREFS_NAME

    @Volatile
    private var activeGhostFilename: String = GHOST_FILENAME

    internal val activePrefsNameForTests: String
        get() = activePrefsName

    internal val activeGhostFilenameForTests: String
        get() = activeGhostFilename

    internal fun usePrimaryPreferences() {
        activePrefsName = PREFS_NAME
        activeGhostFilename = GHOST_FILENAME
    }

    internal fun useCompatibilityPreferences(schemaVersion: Int) {
        val safeVersion = schemaVersion.coerceAtLeast(0)
        activePrefsName = "$COMPAT_PREFS_PREFIX$safeVersion"
        activeGhostFilename = "ghost_run_compat_v$safeVersion.bin"
    }
''',
        "isolate future-schema ghost persistence",
    )
    replace_once(
        save_manager,
        '''    fun saveLastRunSummary(context: Context, summary: RunSummary) {
        prefs(context).edit()
            .putInt(KEY_LAST_RUN_SCORE, summary.score)
            .putFloat(KEY_LAST_RUN_DISTANCE, summary.distanceM)
            .putBoolean(KEY_LAST_RUN_NEW_HIGH, summary.isNewHighScore)
            .putInt(KEY_LAST_RUN_HIGH_SCORE, summary.highScore)
            .putInt(KEY_LAST_RUN_MERCY_HEARTS, summary.mercyHearts)
            .putInt(KEY_LAST_RUN_MERCY_MISSES, summary.mercyMisses)
            .putInt(KEY_LAST_RUN_KINDNESS_CHAIN, summary.kindnessChain)
            .putInt(KEY_LAST_RUN_CLEAN_PASSES, summary.cleanPasses)
            .putInt(KEY_LAST_RUN_SPARED, summary.sparedCount)
            .putInt(KEY_LAST_RUN_HITS, summary.hitsTaken)
            .putInt(KEY_LAST_RUN_SEEDS, summary.seedsCollected)
            .putInt(KEY_LAST_RUN_BLOOM_CONVERSIONS, summary.bloomConversions)
            .putString(KEY_LAST_RUN_QUOTE, summary.restQuote)
            .putString(KEY_LAST_RUN_KILLER, summary.lastKiller?.name)
            .putString(KEY_LAST_RUN_FOREST_MOOD, summary.forestMood.name)
            .putString(KEY_LAST_RUN_PACIFIST_ROUTE, summary.pacifistRouteTier.name)
            .apply()
        incrementRouteTierCount(context, summary.pacifistRouteTier)
    }
''',
        '''    fun saveLastRunSummary(context: Context, summary: RunSummary) {
        val safeDistance = summary.distanceM.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        prefs(context).edit()
            .putInt(KEY_LAST_RUN_SCORE, summary.score.coerceAtLeast(0))
            .putFloat(KEY_LAST_RUN_DISTANCE, safeDistance)
            .putBoolean(KEY_LAST_RUN_NEW_HIGH, summary.isNewHighScore)
            .putInt(KEY_LAST_RUN_HIGH_SCORE, summary.highScore.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_MERCY_HEARTS, summary.mercyHearts.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_MERCY_MISSES, summary.mercyMisses.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_KINDNESS_CHAIN, summary.kindnessChain.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_CLEAN_PASSES, summary.cleanPasses.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_SPARED, summary.sparedCount.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_HITS, summary.hitsTaken.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_SEEDS, summary.seedsCollected.coerceAtLeast(0))
            .putInt(KEY_LAST_RUN_BLOOM_CONVERSIONS, summary.bloomConversions.coerceAtLeast(0))
            .putString(KEY_LAST_RUN_QUOTE, summary.restQuote)
            .putString(KEY_LAST_RUN_KILLER, summary.lastKiller?.name)
            .putString(KEY_LAST_RUN_FOREST_MOOD, summary.forestMood.name)
            .putString(KEY_LAST_RUN_PACIFIST_ROUTE, summary.pacifistRouteTier.name)
            .apply()
        incrementRouteTierCount(context, summary.pacifistRouteTier)
    }
''',
        "clamp last-run summary writes",
    )
    replace_once(
        save_manager,
        '''    fun saveForestMoodState(context: Context, state: ForestMoodState) {
        prefs(context).edit()
            .putString(KEY_FOREST_MOOD, state.currentMood.name)
            .putInt(KEY_FOREST_MOOD_STREAK, state.moodStreak)
            .putInt(KEY_FOREST_TOTAL_RUNS, state.totalRuns)
            .putInt(KEY_FOREST_GENTLE_RUNS, state.gentleRuns)
            .putInt(KEY_FOREST_RECKLESS_RUNS, state.recklessRuns)
            .putInt(KEY_FOREST_FEARFUL_RUNS, state.fearfulRuns)
            .putInt(KEY_FOREST_STEADY_RUNS, state.steadyRuns)
            .apply()
    }
''',
        '''    fun saveForestMoodState(context: Context, state: ForestMoodState) {
        prefs(context).edit()
            .putString(KEY_FOREST_MOOD, state.currentMood.name)
            .putInt(KEY_FOREST_MOOD_STREAK, state.moodStreak.coerceAtLeast(0))
            .putInt(KEY_FOREST_TOTAL_RUNS, state.totalRuns.coerceAtLeast(0))
            .putInt(KEY_FOREST_GENTLE_RUNS, state.gentleRuns.coerceAtLeast(0))
            .putInt(KEY_FOREST_RECKLESS_RUNS, state.recklessRuns.coerceAtLeast(0))
            .putInt(KEY_FOREST_FEARFUL_RUNS, state.fearfulRuns.coerceAtLeast(0))
            .putInt(KEY_FOREST_STEADY_RUNS, state.steadyRuns.coerceAtLeast(0))
            .apply()
    }
''',
        "clamp forest mood writes",
    )
    replace_once(
        save_manager,
        '''    fun saveReturnMomentState(context: Context, state: ReturnMomentState) {
        prefs(context).edit()
            .putLong(KEY_LAST_ACTIVE_AT_MS, state.lastActiveAtMs)
            .putLong(KEY_LAST_GARDEN_GREETING_DAY, state.lastGardenGreetingDay)
            .putInt(KEY_ROUGH_RUN_STREAK, state.roughRunStreak)
            .apply()
    }
''',
        '''    fun saveReturnMomentState(context: Context, state: ReturnMomentState) {
        prefs(context).edit()
            .putLong(KEY_LAST_ACTIVE_AT_MS, state.lastActiveAtMs.coerceAtLeast(0L))
            .putLong(KEY_LAST_GARDEN_GREETING_DAY, state.lastGardenGreetingDay.coerceAtLeast(-1L))
            .putInt(KEY_ROUGH_RUN_STREAK, state.roughRunStreak.coerceAtLeast(0))
            .apply()
    }
''',
        "clamp return moment writes",
    )
    replace_once(
        save_manager,
        '''    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun ghostFile(context: Context) = File(context.filesDir, GHOST_FILENAME)
''',
        '''    private fun prefs(context: Context) =
        context.getSharedPreferences(activePrefsName, Context.MODE_PRIVATE)

    private fun ghostFile(context: Context) = File(context.filesDir, activeGhostFilename)
''',
        "route all persistence through active namespace",
    )

    test = Path("app/src/test/java/com/anurag9000/forestrun/engine/SaveIntegrityManagerTest.kt")
    replace_once(
        test,
        '''import com.anurag9000.forestrun.entities.EntityType
''',
        '''import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.systems.GhostFrame
import java.io.File
''',
        "future ghost test imports",
    )
    replace_once(
        test,
        '''        context.getSharedPreferences(
            "${SaveManager.PREFS_NAME}_compat_v${SaveIntegrityManager.CURRENT_SCHEMA_VERSION}",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @After
    fun tearDown() {
        SaveManager.usePrimaryPreferences()
    }
''',
        '''        context.getSharedPreferences(
            "${SaveManager.PREFS_NAME}_compat_v${SaveIntegrityManager.CURRENT_SCHEMA_VERSION}",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        deleteGhostFiles()
    }

    @After
    fun tearDown() {
        SaveManager.usePrimaryPreferences()
        deleteGhostFiles()
    }
''',
        "clean both ghost namespaces",
    )
    replace_once(
        test,
        '''    fun `future schema is preserved without destructive downgrade`() {
        prefs.edit()
            .putInt(SaveIntegrityManager.KEY_SCHEMA_VERSION, SaveIntegrityManager.CURRENT_SCHEMA_VERSION + 5)
            .putString("high_score", "future-owned-value")
            .putString("future_only_key", "keep")
            .commit()
        val before = prefs.all.toMap()

        val report = SaveIntegrityManager.repair(context)

        assertEquals(SaveIntegrityStatus.FUTURE_VERSION, report.status)
        assertEquals(before, prefs.all)
        assertEquals(
            "${SaveManager.PREFS_NAME}_compat_v${SaveIntegrityManager.CURRENT_SCHEMA_VERSION}",
            SaveManager.activePrefsNameForTests
        )
        assertEquals(0, SaveManager.loadHighScore(context))
    }
''',
        '''    fun `future schema is preserved without destructive downgrade`() {
        prefs.edit()
            .putInt(SaveIntegrityManager.KEY_SCHEMA_VERSION, SaveIntegrityManager.CURRENT_SCHEMA_VERSION + 5)
            .putString("high_score", "future-owned-value")
            .putString("future_only_key", "keep")
            .commit()
        val primaryGhost = File(context.filesDir, "ghost_run.bin")
        val futureGhostBytes = byteArrayOf(9, 8, 7, 6)
        primaryGhost.writeBytes(futureGhostBytes)
        val before = prefs.all.toMap()

        val report = SaveIntegrityManager.repair(context)

        assertEquals(SaveIntegrityStatus.FUTURE_VERSION, report.status)
        assertEquals(before, prefs.all)
        assertEquals(futureGhostBytes.toList(), primaryGhost.readBytes().toList())
        assertEquals(
            "${SaveManager.PREFS_NAME}_compat_v${SaveIntegrityManager.CURRENT_SCHEMA_VERSION}",
            SaveManager.activePrefsNameForTests
        )
        assertEquals(
            "ghost_run_compat_v${SaveIntegrityManager.CURRENT_SCHEMA_VERSION}.bin",
            SaveManager.activeGhostFilenameForTests
        )
        assertEquals(0, SaveManager.loadHighScore(context))

        val compatibilityFrames = listOf(GhostFrame(0.033f, 100f, 200f, 0, 1f, 1f))
        assertTrue(SaveManager.saveGhostRun(context, compatibilityFrames))
        assertEquals(futureGhostBytes.toList(), primaryGhost.readBytes().toList())
        assertEquals(
            compatibilityFrames,
            SaveManager.loadGhostRun(context)
        )
    }
''',
        "future schema preserves primary ghost",
    )
    replace_once(
        test,
        '''        prefs.edit()
            .putInt("last_run_score", 500)
            .putFloat("last_run_distance", 250f)
            .putString("last_run_killer", EntityType.WOLF.name)
            .commit()
''',
        '''        prefs.edit()
            .putInt("last_run_score", 500)
            .putFloat("last_run_distance", 250f)
            .putString("last_run_quote", "A partial summary must not be invented.")
            .putString("last_run_killer", EntityType.WOLF.name)
            .commit()
''',
        "prove score and quote are still insufficient",
    )
    replace_once(
        test,
        '''        assertEquals(Int.MAX_VALUE, SaveManager.loadEncounterCount(context, EntityType.FOX))
    }
}
''',
        '''        assertEquals(Int.MAX_VALUE, SaveManager.loadEncounterCount(context, EntityType.FOX))

        SaveManager.saveLastRunSummary(
            context,
            RunSummary(
                score = -1,
                distanceM = Float.NaN,
                isNewHighScore = false,
                highScore = -2,
                mercyHearts = -3,
                mercyMisses = -4,
                kindnessChain = -5,
                cleanPasses = -6,
                sparedCount = -7,
                hitsTaken = -8,
                seedsCollected = -9,
                bloomConversions = -10,
                lastKiller = null,
                restQuote = "safe",
                forestMood = ForestMood.STEADY,
                pacifistRouteTier = PacifistRouteTier.NONE
            )
        )
        val summary = requireNotNull(SaveManager.loadLastRunSummary(context))
        assertEquals(0, summary.score)
        assertEquals(0f, summary.distanceM, 0f)
        assertEquals(0, summary.seedsCollected)

        SaveManager.saveForestMoodState(
            context,
            ForestMoodState(
                currentMood = ForestMood.GENTLE,
                moodStreak = -1,
                totalRuns = -2,
                gentleRuns = -3,
                recklessRuns = -4,
                fearfulRuns = -5,
                steadyRuns = -6
            )
        )
        assertEquals(0, SaveManager.loadForestMoodState(context).totalRuns)

        SaveManager.saveReturnMomentState(
            context,
            ReturnMomentState(lastActiveAtMs = -1L, lastGardenGreetingDay = -9L, roughRunStreak = -2)
        )
        assertEquals(ReturnMomentState(0L, -1L, 0), SaveManager.loadReturnMomentState(context))
    }

    private fun deleteGhostFiles() {
        File(context.filesDir, "ghost_run.bin").delete()
        File(
            context.filesDir,
            "ghost_run_compat_v${SaveIntegrityManager.CURRENT_SCHEMA_VERSION}.bin"
        ).delete()
        File(context.filesDir, "ghost_run.bin.bak").delete()
        File(
            context.filesDir,
            "ghost_run_compat_v${SaveIntegrityManager.CURRENT_SCHEMA_VERSION}.bin.bak"
        ).delete()
    }
}
''',
        "persistence write boundary regressions",
    )


if __name__ == "__main__":
    main()
