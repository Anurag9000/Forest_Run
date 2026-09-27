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
