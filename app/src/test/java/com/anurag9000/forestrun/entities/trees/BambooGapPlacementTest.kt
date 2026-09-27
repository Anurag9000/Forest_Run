package com.anurag9000.forestrun.entities.trees

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameConstants
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class BambooGapPlacementTest {
    private lateinit var context: Context
    private lateinit var sprites: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sprites = SpriteManager(context)
    }

    @Test
    fun `old high opening was beyond the physical full jump envelope`() {
        val ground = 885.6f
        val gapHeight = Player.BASE_HEIGHT * 1.5f
        val oldMinimumCentre = gapHeight
        val flight = sampledFullJump(ground, GameConstants.BASE_SCROLL_SPEED)
        assertFalse(
            flight.any { body ->
                body.top >= oldMinimumCentre - gapHeight / 2f &&
                    body.bottom <= oldMinimumCentre + gapHeight / 2f
            }
        )
        assertTrue(BambooGapPlacement.reachableCentreRange(ground, gapHeight).start > oldMinimumCentre)
    }

    @Test
    fun `every seeded production opening admits a physical full jump collision body`() {
        val ground = 885.6f
        val flight = sampledFullJump(ground, GameConstants.MAX_SCROLL_SPEED)
        val range = BambooGapPlacement.reachableCentreRange(
            ground, Player.BASE_HEIGHT * 1.5f
        )
        assertTrue(range.start < range.endInclusive)
        repeat(96) { seed ->
            val bamboo = Bamboo(
                context = context,
                startX = 680f,
                screenHeight = 1080f,
                groundY = ground,
                sprite = sprites.bambooSprite.copy(),
                random = Random(seed)
            )
            val top = rectAt(bamboo, "topHitboxes", 0)
            val bottom = rectAt(bamboo, "bottomHitboxes", 0)
            val centre = (top.bottom + bottom.top) / 2f
            assertTrue("seed=$seed below bound", centre >= range.start)
            assertTrue("seed=$seed above bound", centre <= range.endInclusive)
            assertTrue(
                "seed=$seed opening cannot fit the real Player body",
                flight.any { body -> body.top >= top.bottom && body.bottom <= bottom.top }
            )
        }
    }

    @Test
    fun `both boundary openings admit a sampled real jump across supported speeds`() {
        val ground = 885.6f
        val gapHeight = Player.BASE_HEIGHT * 1.5f
        val range = BambooGapPlacement.reachableCentreRange(ground, gapHeight)
        for (speed in listOf(GameConstants.BASE_SCROLL_SPEED, GameConstants.MAX_SCROLL_SPEED)) {
            val flight = sampledFullJump(ground, speed)
            for (centre in listOf(range.start, (range.start + range.endInclusive) / 2f, range.endInclusive)) {
                assertTrue(
                    "speed=$speed centre=$centre",
                    flight.any { body ->
                        body.top >= centre - gapHeight / 2f &&
                            body.bottom <= centre + gapHeight / 2f
                    }
                )
            }
        }
    }

    @Test
    fun `invalid or too short geometry fails instead of emitting an impossible gap`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f).forEach { ground ->
            assertRejected { BambooGapPlacement.reachableCentreRange(ground, 150f) }
        }
        assertRejected { BambooGapPlacement.reachableCentreRange(220f, 150f) }
        assertRejected { BambooGapPlacement.reachableCentreRange(885.6f, 0f) }
        assertRejected { BambooGapPlacement.reachableCentreRange(885.6f, Float.NaN) }
    }

    private fun sampledFullJump(ground: Float, speed: Float): List<RectF> {
        val player = Player(1920, 1080, sprites, groundYOverride = ground)
        player.onJumpPressed()
        return List(100) {
            player.update(0.016f, speed)
            RectF(player.hitbox)
        }
    }

    private fun rectAt(bamboo: Bamboo, fieldName: String, index: Int): RectF {
        val field = Bamboo::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return RectF((field.get(bamboo) as Array<*>)[index] as RectF)
    }

    private fun assertRejected(action: () -> Unit) {
        var rejected = false
        try {
            action()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
