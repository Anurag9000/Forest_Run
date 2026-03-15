package com.yourname.forest_run.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.engine.Biome
import com.yourname.forest_run.engine.PersistentMemoryManager
import com.yourname.forest_run.entities.EntityType
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestQuoteManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `repeat killer quotes acknowledge repeated deaths`() {
        PersistentMemoryManager.recordHit(context, EntityType.WOLF)
        PersistentMemoryManager.recordHit(context, EntityType.WOLF)

        val quote = RestQuoteManager.quoteFor(context, Biome.DUSK_CANYON, EntityType.WOLF)

        assertTrue(quote.contains("same weak moment") || quote.contains("pattern"))
    }

    @Test
    fun `biome fallback quote reflects biome mood when killer is unknown`() {
        val quote = RestQuoteManager.quoteFor(context, Biome.NIGHT_FOREST, null)

        assertTrue(quote.contains("Night"))
    }
}
