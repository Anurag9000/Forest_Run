package com.yourname.forest_run.entities.animals

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.PersistentMemoryManager
import com.yourname.forest_run.engine.SpriteManager
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WolfTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `wolf repeated spare history strengthens stand down flag and spare reward`() {
        val baselineWolf = Wolf(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            screenWidth = 1920f,
            sprite = spriteManager.wolfSprite.copy()
        )
        val baselineState = GameStateManager(context)

        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.WOLF) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.WOLF) }

        val respectWolf = Wolf(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            screenWidth = 1920f,
            sprite = spriteManager.wolfSprite.copy()
        )
        val respectState = GameStateManager(context)
        val player = Player(1920, 1080, spriteManager)

        repeat(8) {
            baselineState.addMercyHeart()
            respectState.addMercyHeart()
        }

        baselineWolf.performUniqueAction(player, baselineState)
        respectWolf.performUniqueAction(player, respectState)

        assertTrue(booleanField(respectWolf, "respectStandDownHistory"))
        assertEquals("SPARED", enumFieldName(respectWolf, "wolfState"))
        assertTrue(respectState.seedsThisRun > baselineState.seedsThisRun)
        assertTrue(respectState.score > baselineState.score)
    }

    private fun booleanField(wolf: Wolf, name: String): Boolean {
        val field = Wolf::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(wolf)
    }

    private fun enumFieldName(wolf: Wolf, name: String): String {
        val field = Wolf::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(wolf).toString()
    }
}
