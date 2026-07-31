package com.anurag9000.forestrun

import android.content.Context
import com.anurag9000.forestrun.systems.GhostPersistenceManager

/** Clears every persistent namespace that can influence connected-test ordering. */
object InstrumentationStateReset {
    private val preferenceFiles = listOf(
        "forest_run_prefs",
        "forest_run_prefs_compat_v1",
        "forest_run_feedback_settings"
    )

    fun clear(context: Context) {
        val appContext = context.applicationContext
        GhostPersistenceManager.clearMemoryForTests()
        preferenceFiles.forEach { name ->
            check(
                appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            ) { "Failed to clear connected-test preferences: $name" }
        }
        appContext.filesDir.listFiles()
            .orEmpty()
            .filter { it.name.startsWith("ghost_run") }
            .forEach { file -> check(file.delete() || !file.exists()) { "Failed to delete ${file.name}" } }
    }
}
