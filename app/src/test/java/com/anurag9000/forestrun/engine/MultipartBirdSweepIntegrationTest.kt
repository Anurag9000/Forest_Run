package com.anurag9000.forestrun.engine

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.birds.TitGroup
import com.anurag9000.forestrun.entities.birds.ChickadeeGroup
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MultipartBirdSweepIntegrationTest {
    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `Tit flock preserves each core and resolves true intervening hit`() {
        verifyRealFlockSweep(
            TitGroup(context, 650f, 885.6f, spriteManager.titSprite.copy(), count = 5)
        )
    }

    @Test
    fun `Chickadee flock preserves each core and resolves true intervening hit`() {
        val flock = ChickadeeGroup(
            context, 650f, 885.6f, spriteManager.chickadeeSprite.copy(),
            count = 3, random = Random(17)
        )
        // Lock a known, coherent flock for this geometry test; retain the
        // live per-bird translation and actual collision body dimensions.
        for (fieldName in listOf("altitudes", "targetAltitudes")) {
            floatArrayField(flock, fieldName).fill(400f)
        }
        floatArrayField(flock, "altitudeTimers").fill(10f)
        flock.update(0f, 0f)
        verifyRealFlockSweep(flock)
    }

    private fun verifyRealFlockSweep(flock: Entity) {
        flock.shouldRecordPersistence = false
        val state = GameStateManager(context) { false }
        state.update(10_000f)
        state.update(0.05f)
        assertEquals(GameConstants.MAX_SCROLL_SPEED, state.scrollSpeed, 0f)

        val manager = EntityManager(context, 1_920f, 1_080f, spriteManager)
        manager.activeEntities += flock
        val beforeBirds = birdRects(flock, "birdRects")
        val first = beforeBirds.first()
        val player = Player(1920, 1080, spriteManager)
        player.update(0.05f, state.scrollSpeed)

        // Initial frame: x is clear of the entire flock. Final frame: the
        // Player has fallen below the birds. True x/y overlap occurs during
        // the scroll interval despite two endpoint misses.
        val left = first.left - 54f
        val before = RectF(left, first.bottom - 40f, first.left - 4f, first.bottom)
        val after = RectF(left, first.bottom + 25f, first.left - 4f, first.bottom + 65f)
        player.previousHitbox.set(before)
        player.hitbox.set(after)
        manager.update(0.05f, state, player, runMode = RunMode.DEBUG_SCENARIO)

        val previous = birdRects(flock, "previousBirdRects")
        val current = birdRects(flock, "birdRects")
        assertEquals(beforeBirds.toList(), previous.toList())
        assertTrue(previous.all { !RectF.intersects(before, it) })
        assertTrue(current.all { !RectF.intersects(after, it) })
        assertTrue(player.hasMotionSample)
        assertTrue(flock.hasMotionSample)

        val frame = requireNotNull(manager.checkCollisions(player, state))
        assertSame(flock, frame.entity)
        assertEquals(CollisionResult.HIT, frame.result)
        assertEquals(EncounterOutcome.HIT, flock.encounterOutcome)
        assertEquals(0, state.mercyHearts)
        assertEquals(0, state.cleanPassesThisRun)
        assertEquals(null, manager.checkCollisions(player, state))
    }

    private fun birdRects(entity: Entity, fieldName: String): Array<RectF> {
        val field = entity.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val source = field.get(entity) as Array<RectF>
        return Array(source.size) { i -> RectF(source[i]) }
    }

    private fun floatArrayField(entity: Entity, fieldName: String): FloatArray {
        val field = entity.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(entity) as FloatArray
    }
}
