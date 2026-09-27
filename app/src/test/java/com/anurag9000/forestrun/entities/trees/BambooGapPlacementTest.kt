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
    fun `every seeded five stalk tunnel allows the whole sampled player passage`() {
        val dt = 0.016f
        val widestBody = Player.BASE_WIDTH * 1.25f - 2f * Player.HITBOX_INSET
        for (height in listOf(720f, 760f, 1_080f, 1_320f, 1_440f)) {
            val ground = height * 0.82f
            val flight = sampledShortHeldJump(ground)
            for (seed in 0 until 96) {
                val bamboo = Bamboo(
                    context, 680f, height, ground, sprites.bambooSprite.copy(), Random(seed)
                )
                val top = rectAt(bamboo, "topHitboxes", 0).bottom
                val bottom = rectAt(bamboo, "bottomHitboxes", 0).top
                val fitSeconds = longestVerticalWindow(flight, top, bottom).second * dt
                for (speed in listOf(GameConstants.BASE_SCROLL_SPEED, GameConstants.MAX_SCROLL_SPEED)) {
                    val exposureSeconds = (bamboo.hitbox.width() + widestBody) / speed
                    assertTrue(
                        "height=$height seed=$seed speed=$speed " +
                            "fit=$fitSeconds exposure=$exposureSeconds",
                        fitSeconds >= exposureSeconds + 2f * dt
                    )
                }
            }
        }
    }

    @Test
    fun `actual scrolling stalks permit an uninterrupted real player crossing`() {
        val dt = 0.016f
        val speed = GameConstants.BASE_SCROLL_SPEED
        for (height in listOf(720f, 1_080f, 1_440f)) {
            val ground = height * 0.82f
            val flight = sampledShortHeldJump(ground)
            for (seed in listOf(0, 7, 31, 95)) {
                val probe = Bamboo(
                    context, 680f, height, ground, sprites.bambooSprite.copy(), Random(seed)
                )
                val top = rectAt(probe, "topHitboxes", 0).bottom
                val bottom = rectAt(probe, "bottomHitboxes", 0).top
                val (startFrame, fitFrames) = longestVerticalWindow(flight, top, bottom)
                assertTrue(fitFrames * dt > (probe.hitbox.width() + 70f) / speed)

                val player = Player(1920, 1080, sprites, groundYOverride = ground)
                val startX = player.hitbox.right + 14f + speed * (startFrame + 1) * dt
                val bamboo = Bamboo(
                    context, startX, height, ground, sprites.bambooSprite.copy(), Random(seed)
                )
                val state = com.anurag9000.forestrun.engine.GameStateManager(context)
                player.onJumpPressed()
                var entered = false
                var completed = false
                repeat(140) { frame ->
                    if (frame == 7) player.onJumpReleased(0.10f)
                    player.update(dt, speed)
                    bamboo.update(dt, speed)
                    if (
                        bamboo.hitbox.left < player.hitbox.right &&
                        bamboo.hitbox.right > player.hitbox.left
                    ) entered = true
                    assertFalse(
                        "height=$height seed=$seed frame=$frame unexpectedly HIT",
                        bamboo.onCollision(player, state) ==
                            com.anurag9000.forestrun.entities.CollisionResult.HIT
                    )
                    if (entered && bamboo.hitbox.right < player.hitbox.left) completed = true
                }
                assertTrue("height=$height seed=$seed did not cross all stalks", completed)
            }
        }
    }

    private fun sampledShortHeldJump(ground: Float): List<RectF> {
        val player = Player(1920, 1080, sprites, groundYOverride = ground)
        player.onJumpPressed()
        return List(130) { frame ->
            if (frame == 7) player.onJumpReleased(0.10f)
            player.update(0.016f, GameConstants.BASE_SCROLL_SPEED)
            RectF(player.hitbox)
        }
    }

    private fun longestVerticalWindow(
        flight: List<RectF>,
        top: Float,
        bottom: Float
    ): Pair<Int, Int> {
        var currentStart = 0
        var currentLength = 0
        var longestStart = 0
        var longestLength = 0
        flight.forEachIndexed { frame, body ->
            if (body.top >= top && body.bottom <= bottom) {
                if (currentLength == 0) currentStart = frame
                currentLength++
                if (currentLength > longestLength) {
                    longestStart = currentStart
                    longestLength = currentLength
                }
            } else {
                currentLength = 0
            }
        }
        return longestStart to longestLength
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
