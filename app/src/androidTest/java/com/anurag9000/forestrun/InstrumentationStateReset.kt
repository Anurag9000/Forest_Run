package com.anurag9000.forestrun

import android.content.Context
import com.anurag9000.forestrun.engine.FeedbackSettings
import com.anurag9000.forestrun.engine.SaveIntegrityManager
import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.systems.GhostPersistenceManager

/** Clears every persistent namespace that can influence connected-test ordering. */
object InstrumentationStateReset {
    private val saveNamespaces = listOf(
        SaveManager.PREFS_NAME,
        "${SaveManager.PREFS_NAME}_compat_v${SaveIntegrityManager.CURRENT_SCHEMA_VERSION}"
    )
    private val preferenceFiles = buildList {
        addAll(saveNamespaces)
        add(FeedbackSettings.PREFS_NAME)
        saveNamespaces.forEach { namespace ->
            add("forest_run_outcome_recovery_${namespace}")
        }
    }

    fun clear(context: Context) {
        val appContext = context.applicationContext
        SaveManager.usePrimaryPreferences()
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
