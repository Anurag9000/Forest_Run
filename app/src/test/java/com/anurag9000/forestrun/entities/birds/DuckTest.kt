package com.anurag9000.forestrun.entities.birds

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameConstants
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
class DuckTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `duck rewards answering the staged low-lane call`() {
        val duck = Duck(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.duckFlying.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        duck.update(deltaTime = 0f, scrollSpeed = 0f)

        val hitbox = RectF(duck.hitbox)
        val duckLaneRect = rectField(duck, "duckLaneRect")

        player.hitbox.set(
            hitbox.left - 72f,
            hitbox.top + 8f,
            hitbox.left - 12f,
            hitbox.bottom - 8f
        )
        duck.updatePlayerInteraction(player, gameState)
        assertTrue(duck.onCollision(player, gameState) != CollisionResult.HIT)
        assertTrue(booleanField(duck, "quackCalled"))

        player.onDuckPressed()
        player.hitbox.set(
            duckLaneRect.left + 6f,
            duckLaneRect.top + 4f,
            duckLaneRect.right - 18f,
            duckLaneRect.bottom - 4f
        )
        duck.updatePlayerInteraction(player, gameState)
        assertTrue(duck.onCollision(player, gameState) != CollisionResult.HIT)

        assertTrue(booleanField(duck, "answeredQuack"))
        assertTrue(booleanField(duck, "stayedLow"))

        player.hitbox.set(
            hitbox.left + 4f,
            hitbox.top + 4f,
            hitbox.right - 4f,
            hitbox.bottom - 4f
        )
        assertEquals(CollisionResult.HIT, duck.onCollision(player, gameState))
    }


    @Test
    fun `real standing player hits Duck but real grounded crouch clears at every reference height`() {
        for (height in listOf(720, 760, 1_080, 1_320, 1_440)) {
            val player = Player(1_920, height, spriteManager)
            val duck = Duck(
                context = context,
                startX = player.x,
                groundY = player.groundY,
                sprite = spriteManager.duckFlying.copy()
            )
            val state = GameStateManager(context)
            duck.update(0.016f, 0f)

            assertEquals(
                "height=$height standing should meet the actual flight band",
                CollisionResult.HIT,
                duck.onCollision(player, state)
            )
            val standingTop = player.hitbox.top
            player.onDuckPressed()
            player.update(0.016f, GameConstants.BASE_SCROLL_SPEED)
            assertTrue("height=$height duck did not change body height", player.hitbox.top > standingTop)
            assertTrue(
                "height=$height dangerous Duck body must end above crouched body",
                duck.hitbox.bottom < player.hitbox.top
            )
            assertEquals(
                "height=$height a true crouch should clear the body and its mercy halo",
                CollisionResult.NONE,
                duck.onCollision(player, state)
            )
            duck.updatePlayerInteraction(player, state)
            assertTrue("height=$height low answer lane did not accept the real crouch",
                booleanField(duck, "stayedLow"))
        }
    }

    @Test
    fun `real crouch clears the whole scrolling Duck while standing is actually threatened`() {
        val dt = 0.016f
        for (height in listOf(720, 760, 1_080, 1_320, 1_440)) {
            for (speed in listOf(GameConstants.BASE_SCROLL_SPEED, GameConstants.MAX_SCROLL_SPEED)) {
                val standing = Player(1_920, height, spriteManager)
                val crouching = Player(1_920, height, spriteManager)
                crouching.onDuckPressed()
                val startX = standing.x + 180f
                val standingDuck = Duck(
                    context, startX, standing.groundY, spriteManager.duckFlying.copy()
                )
                val crouchDuck = Duck(
                    context, startX, crouching.groundY, spriteManager.duckFlying.copy()
                )
                val state = GameStateManager(context)
                var standingHit = false
                var horizontallyEntered = false
                var completed = false

                repeat(100) {
                    standing.update(dt, speed)
                    crouching.update(dt, speed)
                    standingDuck.update(dt, speed)
                    crouchDuck.update(dt, speed)
                    if (standingDuck.isActive &&
                        standingDuck.hitbox.left < standing.hitbox.right &&
                        standingDuck.hitbox.right > standing.hitbox.left
                    ) {
                        horizontallyEntered = true
                        if (standingDuck.onCollision(standing, state) == CollisionResult.HIT) {
                            standingHit = true
                        }
                    }
                    if (crouchDuck.isActive) {
                        assertTrue(
                            "height=$height speed=$speed: crouching still touches Duck",
                            crouchDuck.onCollision(crouching, state) != CollisionResult.HIT
                        )
                    }
                    if (horizontallyEntered &&
                        crouchDuck.hitbox.right < crouching.hitbox.left
                    ) completed = true
                }
                assertTrue("height=$height speed=$speed Duck never entered", horizontallyEntered)
                assertTrue("height=$height speed=$speed standing had no hazard", standingHit)
                assertTrue("height=$height speed=$speed ducked traverse did not complete", completed)
            }
        }
    }

    private fun rectField(duck: Duck, name: String): RectF {
        val field = Duck::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(duck) as RectF)
    }

    private fun booleanField(duck: Duck, name: String): Boolean {
        val field = Duck::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(duck)
    }
}
