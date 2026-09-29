package com.anurag9000.forestrun.entities.flora

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.engine.EntityManager
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HyacinthTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `hyacinth distinguishes core hit soft brush stumble and outer mercy band`() {
        val hyacinth = Hyacinth(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.hyacinthSprite.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        val hitRect = RectF(hyacinth.hitbox)
        val brushRect = RectF(hyacinth.encounterBounds)
        player.hitbox.set(hitRect)
        assertEquals(CollisionResult.HIT, hyacinth.onCollision(player, gameState))

        // Visible purple fringe is physically contacted but the hard core is not.
        player.hitbox.set(
            hitRect.left,
            hitRect.top - 10f,
            hitRect.right,
            hitRect.top - 1f
        )
        assertEquals(CollisionResult.STUMBLE, hyacinth.onCollision(player, gameState))

        // Beyond the drawn brush is a separate mercy band, not a stumble.
        player.hitbox.set(
            hitRect.left,
            brushRect.top - 9f,
            hitRect.right,
            brushRect.top - 1f
        )
        assertEquals(CollisionResult.MERCY_MISS, hyacinth.onCollision(player, gameState))

        player.hitbox.set(
            hitRect.left,
            brushRect.top - 30f,
            hitRect.right,
            brushRect.top - 20f
        )
        assertEquals(CollisionResult.NONE, hyacinth.onCollision(player, gameState))
    }

    @Test
    fun `soft brush detects midframe contact when both endpoint samples miss`() {
        val hyacinth = Hyacinth(
            context = context,
            startX = 620f,
            groundY = 885.6f,
            sprite = spriteManager.hyacinthSprite.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val state = GameStateManager(context)
        val manager = EntityManager(context, 1_920f, 1_080f, spriteManager)
        manager.activeEntities += hyacinth

        val coreBefore = RectF(hyacinth.hitbox)
        val brushBefore = RectF(hyacinth.encounterBounds)
        val playerLeft = brushBefore.left - 30f
        val playerTop = brushBefore.top + 3f
        val playerBottom = coreBefore.top - 3f
        assertTrue(playerBottom > playerTop)
        val playerRect = RectF(
            playerLeft,
            playerTop,
            playerLeft + 10f,
            playerBottom
        )

        // Let Player own a real previous/current sample, then keep the test
        // rectangle stationary in the visible brush-only vertical band.
        player.hitbox.set(playerRect)
        player.update(0.001f, state.scrollSpeed)
        assertTrue(player.hasMotionSample)
        assertEquals(playerRect, player.previousHitbox)
        player.hitbox.set(playerRect)

        assertFalse(RectF.intersects(player.hitbox, coreBefore))
        assertFalse(RectF.intersects(player.hitbox, brushBefore))

        // Hyacinth moves 100 px left in one admitted recovery frame. Its brush
        // begins to the right of the Player and ends fully to the left, so the
        // only physical contact occurs between endpoint samples.
        manager.update(
            deltaTime = 0.05f,
            gameState = state,
            player = player,
            runMode = com.anurag9000.forestrun.engine.RunMode.DEBUG_SCENARIO
        )
        assertFalse(RectF.intersects(player.hitbox, hyacinth.hitbox))
        assertFalse(RectF.intersects(player.hitbox, hyacinth.encounterBounds))

        val frame = requireNotNull(manager.checkCollisions(player, state))
        assertEquals(CollisionResult.STUMBLE, frame.result)
        assertTrue(frame.entity === hyacinth)
        assertEquals(EncounterOutcome.STUMBLE, hyacinth.encounterOutcome)
        assertEquals(0, state.cleanPassesThisRun)
        assertEquals(0, state.mercyHearts)
    }

    @Test
    fun `brush contact resolves as a stumble and cannot earn a later clean pass`() {
        val hyacinth = Hyacinth(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.hyacinthSprite.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)
        val manager = EntityManager(context, 1_920f, 1_080f, spriteManager)
        manager.activeEntities += hyacinth

        val core = RectF(hyacinth.hitbox)
        player.hitbox.set(core.left, core.top - 10f, core.right, core.top - 1f)
        val frame = requireNotNull(manager.checkCollisions(player, gameState))
        assertEquals(CollisionResult.STUMBLE, frame.result)
        assertEquals(EncounterOutcome.STUMBLE, hyacinth.encounterOutcome)
        assertEquals(0, gameState.mercyHearts)

        player.hitbox.set(1_000f, 0f, 1_050f, 20f)
        assertEquals(null, manager.checkCollisions(player, gameState))
        assertEquals(EncounterOutcome.STUMBLE, hyacinth.encounterOutcome)
        assertEquals(0, gameState.cleanPassesThisRun)
    }

    @Test
    fun `pass credit waits for the complete brush rather than the narrow core`() {
        val hyacinth = Hyacinth(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.hyacinthSprite.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)
        val manager = EntityManager(context, 1_920f, 1_080f, spriteManager)
        manager.activeEntities += hyacinth

        val coreRight = hyacinth.hitbox.right
        val brushRight = hyacinth.encounterBounds.right
        val between = (coreRight + brushRight) / 2f
        player.hitbox.set(between, 0f, between + 20f, 20f)
        assertEquals(null, manager.checkCollisions(player, gameState))
        assertEquals(EncounterOutcome.PENDING, hyacinth.encounterOutcome)

        player.hitbox.set(brushRight + 1f, 0f, brushRight + 21f, 20f)
        assertEquals(null, manager.checkCollisions(player, gameState))
        assertEquals(EncounterOutcome.CLEAN_PASS, hyacinth.encounterOutcome)
        assertEquals(1, gameState.cleanPassesThisRun)
    }
}
