package com.anurag9000.forestrun.entities.birds

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TitGroupTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `tit group tracks the trough guide as a separate rhythm reward lane`() {
        val titGroup = titGroup()
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        titGroup.update(deltaTime = 0f, scrollSpeed = 0f)

        val troughGuideRect = rectField(titGroup, "troughGuideRect")
        val birdRects = rectArrayField(titGroup, "birdRects")

        player.hitbox.set(
            troughGuideRect.left + 10f,
            troughGuideRect.top + 4f,
            troughGuideRect.left + 54f,
            troughGuideRect.bottom - 4f
        )
        titGroup.updatePlayerInteraction(player, gameState)
        assertEquals(CollisionResult.NONE, titGroup.onCollision(player, gameState))
        assertTrue(booleanField(titGroup, "keptBeat"))

        player.hitbox.set(
            birdRects[2].left + 4f,
            birdRects[2].top + 4f,
            birdRects[2].right - 4f,
            birdRects[2].bottom - 4f
        )
        assertEquals(CollisionResult.HIT, titGroup.onCollision(player, gameState))
    }

    @Test
    fun `full trough fits the real airborne Player and rewards only complete threading`() {
        val titGroup = titGroup()
        titGroup.update(0f, 0f)
        val guide = rectField(titGroup, "troughGuideRect")
        val player = Player(1920, 1080, spriteManager)
        val isolatedState = GameStateManager(context) { false }
        player.onJumpPressed()

        var observedRealFlightFit = false
        repeat(120) {
            player.update(0.01f)
            // Preserve the actual Player's vertical physics and hitbox size;
            // horizontally align its stationary screen position with the wave.
            player.hitbox.offsetTo(guide.left + 20f, player.hitbox.top)
            if (guide.contains(
                    player.hitbox.left, player.hitbox.top,
                    player.hitbox.right, player.hitbox.bottom
                )
            ) {
                observedRealFlightFit = true
                assertFalse(titGroup.onCollision(player, isolatedState) == CollisionResult.HIT)
            }
        }
        assertTrue("trough must admit at least one real jump-trajectory frame", observedRealFlightFit)

        // A grazing hitbox used to count as a successful rhythm read.
        player.hitbox.set(
            guide.left + 20f,
            guide.bottom - 1f,
            guide.left + 20f + player.hitbox.width(),
            guide.bottom - 1f + player.hitbox.height()
        )
        titGroup.updatePlayerInteraction(player, isolatedState)
        assertFalse(booleanField(titGroup, "keptBeat"))

        val bodyWidth = player.hitbox.width()
        val bodyHeight = player.hitbox.height()
        player.hitbox.offsetTo(
            guide.left + 20f,
            guide.centerY() - bodyHeight * 0.5f
        )
        assertTrue(guide.contains(
            player.hitbox.left, player.hitbox.top,
            player.hitbox.right, player.hitbox.bottom
        ))
        titGroup.updatePlayerInteraction(player, isolatedState)
        assertTrue(booleanField(titGroup, "keptBeat"))
        titGroup.performUniqueAction(player, isolatedState)
        assertEquals(1, isolatedState.seedsThisRun)
    }

    @Test
    fun `tit aggregate bounds equal the live flock after wave and scroll movement`() {
        val titGroup = titGroup()

        titGroup.update(deltaTime = 0.37f, scrollSpeed = 280f)

        assertAggregateMatchesBirds(titGroup.hitbox, rectArrayField(titGroup, "birdRects"))
    }


    @Test
    fun `near miss of first bird does not mask hit on later bird`() {
        val group = titGroup()
        val player = Player(1920, 1080, spriteManager)
        val state = GameStateManager(context)
        val field = TitGroup::class.java.getDeclaredField("birdRects")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val birds = field.get(group) as Array<RectF>
        player.hitbox.set(110f, 110f, 130f, 130f)
        birds[0].set(135f, 100f, 175f, 140f)
        birds[1].set(100f, 100f, 140f, 140f)

        assertEquals(CollisionResult.HIT, group.onCollision(player, state))
        birds[1].set(250f, 100f, 290f, 140f)
        for (index in 2 until birds.size) {
            birds[index].set(300f + index * 80f, 100f, 335f + index * 80f, 140f)
        }
        assertEquals(CollisionResult.MERCY_MISS, group.onCollision(player, state))
    }

    private fun titGroup() = TitGroup(
        context = context,
        startX = 520f,
        groundY = 885.6f,
        sprite = spriteManager.titSprite.copy(),
        count = 5
    )

    private fun assertAggregateMatchesBirds(aggregate: RectF, birds: Array<RectF>) {
        assertEquals(birds.minOf { it.left }, aggregate.left, 0.001f)
        assertEquals(birds.minOf { it.top }, aggregate.top, 0.001f)
        assertEquals(birds.maxOf { it.right }, aggregate.right, 0.001f)
        assertEquals(birds.maxOf { it.bottom }, aggregate.bottom, 0.001f)
    }

    private fun rectField(titGroup: TitGroup, name: String): RectF {
        val field = TitGroup::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(titGroup) as RectF)
    }

    private fun rectArrayField(titGroup: TitGroup, name: String): Array<RectF> {
        val field = TitGroup::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val value = field.get(titGroup) as Array<RectF>
        return Array(value.size) { index -> RectF(value[index]) }
    }

    private fun booleanField(titGroup: TitGroup, name: String): Boolean {
        val field = TitGroup::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(titGroup)
    }
}
