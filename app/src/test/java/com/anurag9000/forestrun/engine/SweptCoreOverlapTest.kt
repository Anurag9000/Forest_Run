package com.anurag9000.forestrun.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.animals.Hedgehog
import com.anurag9000.forestrun.entities.flora.LilyOfValley
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.FlavorTextManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SweptCoreOverlapTest {
    private lateinit var context: Context
    private lateinit var sprites: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
        sprites = SpriteManager(context)
    }

    @After
    fun tearDown() {
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
    }

    @Test
    fun `horizontal tunnel intersects without either endpoint touching`() {
        val p = RectF(100f, 100f, 140f, 140f)
        val before = RectF(150f, 110f, 170f, 130f)
        val after = RectF(50f, 110f, 70f, 130f)
        assertFalse(RectF.intersects(p, before))
        assertFalse(RectF.intersects(p, after))
        assertTrue(SweptCoreOverlap.intersects(p, p, before, after))
    }

    @Test
    fun `diagonal bounding union never invents contact at different times`() {
        val p = RectF(100f, 100f, 140f, 140f)
        val before = RectF(150f, 340f, 170f, 360f)
        val after = RectF(50f, 110f, 70f, 130f)
        assertFalse(RectF.intersects(p, before))
        assertFalse(RectF.intersects(p, after))
        assertFalse(SweptCoreOverlap.intersects(p, p, before, after))
    }

    @Test
    fun `boundary touch is not an overlap and malformed geometry fails closed`() {
        val p = RectF(100f, 100f, 140f, 140f)
        val before = RectF(150f, 110f, 170f, 130f)
        val touching = RectF(140f, 110f, 160f, 130f)
        assertFalse(SweptCoreOverlap.intersects(p, p, before, touching))
        assertFalse(SweptCoreOverlap.intersects(
            RectF(Float.NaN, 100f, 140f, 140f), p, before, touching
        ))
        assertFalse(SweptCoreOverlap.intersects(
            p, p, RectF(150f, 100f, 150f, 140f), touching
        ))
    }

    @Test
    fun `moving player and moving core share one physical time interval`() {
        val beforeP = RectF(100f, 100f, 140f, 140f)
        val afterP = RectF(140f, 100f, 180f, 140f)
        val beforeC = RectF(200f, 110f, 220f, 130f)
        val afterC = RectF(80f, 110f, 100f, 130f)
        assertFalse(RectF.intersects(beforeP, beforeC))
        assertFalse(RectF.intersects(afterP, afterC))
        assertTrue(SweptCoreOverlap.intersects(beforeP, afterP, beforeC, afterC))
    }

    @Test
    fun `actual Lily minimum-width core cannot tunnel through a grounded player`() {
        val player = Player(1920, 1080, sprites)
        val flower = LilyOfValley(context, 700f, player.groundY, thinValidSprite())
        assertSweptContact(player, flower, CollisionResult.HIT, 1f, 100f)
    }

    @Test
    fun `fast Hedgehog core cannot turn a crossed body into provisional mercy`() {
        val player = Player(1920, 1080, sprites)
        val hedgehog = Hedgehog(context, 700f, player.groundY, thinValidSprite())
        assertSweptContact(player, hedgehog, CollisionResult.STUMBLE, 6f, 115f)
    }

    private fun thinValidSprite(): SpriteSheet = SpriteSheet(
        Bitmap.createBitmap(8, 10, Bitmap.Config.ARGB_8888),
        frameCount = 4,
        framesPerSec = 8f
    )

    private fun assertSweptContact(
        player: Player,
        entity: Entity,
        expected: CollisionResult,
        startingGapPx: Float,
        displacementPx: Float
    ) {
        val state = GameStateManager(context) { false }
        state.update(5_000f)
        state.update(5_000f)
        assertEquals(GameConstants.MAX_SCROLL_SPEED, state.scrollSpeed, 0f)

        // Stage a genuine entity with the specified minimum legal sprite width.
        val offset = player.hitbox.right + startingGapPx - entity.hitbox.left
        entity.x += offset
        entity.hitbox.offset(offset, 0f)
        val initial = RectF(entity.hitbox)
        assertFalse(RectF.intersects(player.hitbox, initial))
        assertTrue(
            "fixture must cross the entire core within this admitted frame",
            player.hitbox.width() + entity.hitbox.width() + startingGapPx < displacementPx
        )

        val manager = EntityManager(context, 1920f, 1080f, sprites)
        entity.shouldRecordPersistence = false
        manager.activeEntities.add(entity)
        player.update(0.05f, state.scrollSpeed)
        manager.update(0.05f, state, player, runMode = RunMode.DEBUG_SCENARIO)
        assertTrue(player.hasMotionSample)
        assertTrue(entity.hasMotionSample)
        assertFalse(RectF.intersects(player.hitbox, entity.hitbox))
        // The endpoint may sit in the mercy halo, but it cannot report
        // the physical body contact that occurred between samples.
        assertTrue(
            "endpoint-only query missed the stronger physical body contact",
            entity.onCollision(player, state) != expected
        )

        val frame = requireNotNull(manager.checkCollisions(player, state))
        assertSame(entity, frame.entity)
        assertEquals(expected, frame.result)
        assertEquals(
            if (expected == CollisionResult.HIT) EncounterOutcome.HIT else EncounterOutcome.STUMBLE,
            entity.encounterOutcome
        )
        assertEquals(0, state.mercyHearts)
        assertEquals(0, state.cleanPassesThisRun)
        assertEquals(null, manager.checkCollisions(player, state))
    }
}
