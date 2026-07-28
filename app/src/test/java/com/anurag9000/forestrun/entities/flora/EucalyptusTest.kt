package com.anurag9000.forestrun.entities.flora

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
class EucalyptusTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `eucalyptus keeps mercy band outside the true whip hitbox`() {
        val eucalyptus = Eucalyptus(
            context = context,
            startX = 560f,
            groundY = 885.6f,
            sprite = spriteManager.eucalyptusSprite.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        val hitRect = RectF(eucalyptus.hitbox)
        player.hitbox.set(hitRect)
        assertEquals(CollisionResult.HIT, eucalyptus.onCollision(player, gameState))

        player.hitbox.set(
            hitRect.left - 10f,
            hitRect.top - 10f,
            hitRect.right + 10f,
            hitRect.top - 2f
        )
        assertEquals(CollisionResult.MERCY_MISS, eucalyptus.onCollision(player, gameState))
    }
}
