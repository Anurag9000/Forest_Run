package com.anurag9000.forestrun.entities.animals

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HedgehogTest {

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
    fun `hedgehog grants a grace mercy miss during warning arm window`() {
        val hedgehog = Hedgehog(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.hedgehogSprite.copy()
        )
        val gameState = GameStateManager(context)
        val player = Player(1920, 1080, spriteManager)

        player.hitbox.set(hedgehog.hitbox)
        hedgehog.updatePlayerInteraction(player, gameState)

        val result = hedgehog.onCollision(player, gameState)

        assertEquals(CollisionResult.MERCY_MISS, result)
        assertTrue(booleanField(hedgehog, "warned"))
        assertTrue(!booleanField(hedgehog, "armed"))
    }

    @Test
    fun `hedgehog clear read after warning gives stronger pass reward`() {
        val baselineHedgehog = Hedgehog(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.hedgehogSprite.copy()
        )
        val clearedHedgehog = Hedgehog(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.hedgehogSprite.copy()
        )
        val baselineState = GameStateManager(context)
        val clearedState = GameStateManager(context)
        val player = Player(1920, 1080, spriteManager)

        setBooleanField(clearedHedgehog, "warned", true)

        baselineHedgehog.performUniqueAction(player, baselineState)
        clearedHedgehog.performUniqueAction(player, clearedState)

        assertTrue(clearedState.score > baselineState.score)
        assertTrue(clearedState.seedsThisRun > baselineState.seedsThisRun)
    }

    private fun booleanField(hedgehog: Hedgehog, name: String): Boolean {
        val field = Hedgehog::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(hedgehog)
    }

    private fun setBooleanField(hedgehog: Hedgehog, name: String, value: Boolean) {
        val field = Hedgehog::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setBoolean(hedgehog, value)
    }
}
