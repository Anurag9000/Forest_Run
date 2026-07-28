package com.anurag9000.forestrun.entities.animals

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.PersistentMemoryManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
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
