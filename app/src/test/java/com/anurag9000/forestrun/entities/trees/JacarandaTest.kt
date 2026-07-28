package com.anurag9000.forestrun.entities.trees

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JacarandaTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `jacaranda keeps a readable underside lane below the petal veil`() {
        val jacaranda = Jacaranda(
            context = context,
            startX = 660f,
            screenHeight = 1080f,
            groundY = 885.6f,
            sprite = spriteManager.jacarandaSprite.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        val branchHitbox = rectField(jacaranda, "branchHitbox")
        val undersideLaneRect = rectField(jacaranda, "undersideLaneRect")

        player.hitbox.set(
            undersideLaneRect.left + 8f,
            undersideLaneRect.top + 4f,
            undersideLaneRect.right - 8f,
            undersideLaneRect.bottom - 4f
        )
        assertEquals(CollisionResult.NONE, jacaranda.onCollision(player, gameState))

        player.hitbox.set(
            branchHitbox.left + 16f,
            branchHitbox.top + 12f,
            branchHitbox.right - 16f,
            branchHitbox.bottom - 12f
        )
        assertEquals(CollisionResult.HIT, jacaranda.onCollision(player, gameState))

        player.hitbox.set(
            undersideLaneRect.left + 8f,
            branchHitbox.bottom + 2f,
            undersideLaneRect.right - 8f,
            undersideLaneRect.top - 2f
        )
        assertEquals(CollisionResult.MERCY_MISS, jacaranda.onCollision(player, gameState))
    }

    private fun rectField(jacaranda: Jacaranda, name: String): RectF {
        val field = Jacaranda::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(jacaranda) as RectF)
    }
}
