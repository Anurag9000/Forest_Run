package com.anurag9000.forestrun

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anurag9000.forestrun.engine.SaveIntegrityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForestJournalLifecycleInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext.applicationContext

    @Before
    fun setUp() {
        InstrumentationStateReset.clear(targetContext)
    }

    @Test
    fun directJournalLaunchRepairsMalformedTypedPersistenceBeforeProjection() {
        val gamePrefs = targetContext.getSharedPreferences(
            "forest_run_prefs",
            Context.MODE_PRIVATE
        )
        gamePrefs.edit()
            .putString("encounter_cat", "not-an-int")
            .putString("garden_unlocked", "not-an-int")
            .commit()

        ActivityScenario.launch(ForestJournalActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(findButton(activity.window.decorView, "All"))
            }
        }

        assertEquals(0, gamePrefs.getInt("encounter_cat", -1))
        assertEquals(1, gamePrefs.getInt("garden_unlocked", -1))
    }

    @Test
    fun selectedSectionSurvivesActivityRecreationWithoutWritingGameProgress() {
        val gamePrefs = targetContext.getSharedPreferences(
            "forest_run_prefs",
            Context.MODE_PRIVATE
        )
        val before = gamePrefs.all.toMap()

        ActivityScenario.launch(ForestJournalActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val memories = findButton(activity.window.decorView, "Memories")
                assertNotNull(memories)
                assertTrue(memories!!.performClick())

                val selected = findButton(activity.window.decorView, "Memories")
                assertNotNull(selected)
                assertTrue(selected!!.isSelected)
                assertEquals(
                    "Memories Journal section, selected",
                    selected.contentDescription.toString()
                )
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val restored = findButton(activity.window.decorView, "Memories")
                assertNotNull(restored)
                assertTrue(restored!!.isSelected)
                assertEquals(
                    "Memories Journal section, selected",
                    restored.contentDescription.toString()
                )
            }
        }

        val after = gamePrefs.all.toMap()
        assertEquals(
            before - SaveIntegrityManager.KEY_SCHEMA_VERSION,
            after - SaveIntegrityManager.KEY_SCHEMA_VERSION
        )
        assertEquals(
            SaveIntegrityManager.CURRENT_SCHEMA_VERSION,
            gamePrefs.getInt(SaveIntegrityManager.KEY_SCHEMA_VERSION, -1)
        )
    }

    private fun findButton(root: View, label: String): Button? {
        if (root is Button && root.text.toString() == label) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            findButton(root.getChildAt(index), label)?.let { return it }
        }
        return null
    }
}
