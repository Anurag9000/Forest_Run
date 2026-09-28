package com.anurag9000.forestrun.entities.flora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.GameConstants
import com.anurag9000.forestrun.engine.EntityManager
import com.anurag9000.forestrun.engine.RunMode
import com.anurag9000.forestrun.engine.ReadabilityProfile
import com.anurag9000.forestrun.engine.SpriteSheet
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VanillaOrchidTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `orchid keeps the true thread open between low and high hazards`() {
        val orchid = VanillaOrchid(
            context = context,
            startX = 560f,
            groundY = 885.6f,
            sprite = spriteManager.orchidSprite.copy()
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        val bottomHitbox = rectField(orchid, "bottomHitbox")
        val threadRect = rectField(orchid, "threadRect")

        val threadLeft = threadRect.left + 2f
        val threadRight = threadRect.right - 2f
        val threadTop = threadRect.top + 2f
        val threadBottom = threadRect.bottom - 2f

        player.hitbox.set(threadLeft, threadTop, threadRight, threadBottom)
        assertEquals(CollisionResult.NONE, orchid.onCollision(player, gameState))

        player.hitbox.set(threadLeft, bottomHitbox.top + 2f, threadRight, bottomHitbox.bottom - 2f)
        assertEquals(CollisionResult.HIT, orchid.onCollision(player, gameState))

        player.hitbox.set(threadLeft, bottomHitbox.top - 6f, threadRight, bottomHitbox.top - 1f)
        assertEquals(CollisionResult.MERCY_MISS, orchid.onCollision(player, gameState))
    }

    @Test
    fun `falling Player and moving Orchid upper band contact between sampled endpoints`() {
        val orchid = VanillaOrchid(
            context = context,
            startX = 650f,
            groundY = 885.6f,
            sprite = spriteManager.orchidSprite.copy()
        )
        orchid.shouldRecordPersistence = false
        val initialUpper = rectField(orchid, "topHitbox")
        val player = Player(1920, 1080, spriteManager)
        val state = GameStateManager(context) { false }
        val manager = EntityManager(context, 1920f, 1080f, spriteManager)
        manager.activeEntities += orchid

        state.update(10_000f)
        state.update(0.05f)
        assertEquals(GameConstants.MAX_SCROLL_SPEED, state.scrollSpeed, 0f)
        player.update(0.05f, state.scrollSpeed)
        val bodyLeft = initialUpper.left - 60f
        val before = RectF(
            bodyLeft, initialUpper.bottom - 42f,
            initialUpper.left - 4f, initialUpper.bottom - 2f
        )
        val after = RectF(
            bodyLeft, initialUpper.bottom + 2f,
            initialUpper.left - 4f, initialUpper.bottom + 42f
        )
        player.previousHitbox.set(before)
        player.hitbox.set(after)

        manager.update(0.05f, state, player, runMode = RunMode.DEBUG_SCENARIO)
        assertEquals(initialUpper, rectField(orchid, "previousTopHitbox"))
        assertTrue(!RectF.intersects(before, initialUpper))
        assertTrue(!RectF.intersects(after, rectField(orchid, "topHitbox")))
        assertTrue(!RectF.intersects(after, rectField(orchid, "bottomHitbox")))

        val frame = requireNotNull(manager.checkCollisions(player, state))
        assertEquals(CollisionResult.HIT, frame.result)
        assertEquals(EncounterOutcome.HIT, orchid.encounterOutcome)
        assertEquals(0, state.mercyHearts)
        assertEquals(0, state.cleanPassesThisRun)
    }

    @Test
    fun `minimum width orchid thread fits the real jumping player in each density bucket`() {
        // Force SpriteSizing onto ReadabilityProfile's minimum width, instead
        // of relying on the current artwork's potentially larger aspect ratio.
        val narrowStrip = Bitmap.createBitmap(4 * 16, 128, Bitmap.Config.ARGB_8888)
        val narrowSprite = SpriteSheet(narrowStrip, frameCount = 4, framesPerSec = 8f)
        for (screenHeight in intArrayOf(720, 1080, 1440)) {
            val groundY = screenHeight * 0.82f
            val orchid = VanillaOrchid(
                context = context,
                startX = 560f,
                groundY = groundY,
                sprite = narrowSprite.copy()
            )
            val player = Player(1920, screenHeight, spriteManager, groundYOverride = groundY)
            val state = GameStateManager(context)
            val thread = rectField(orchid, "threadRect")
            val readable = ReadabilityProfile.entityForGround(EntityType.VANILLA_ORCHID, groundY)
            assertEquals(readable.minWidthPx, orchid.hitbox.width(), 0.01f)
            assertTrue("height=$screenHeight thread missing", !thread.isEmpty)

            // The largest Player jumping/landing collision dimensions must
            // physically fit the whole marked lane, not a toy RectF.
            val widestBody = Player.BASE_WIDTH * 1.30f - 2f * Player.HITBOX_INSET
            val tallestJumpBody = Player.BASE_HEIGHT * 1.20f - 2f * Player.HITBOX_INSET
            assertTrue("height=$screenHeight horizontal clearance", thread.width() > widestBody)
            assertTrue("height=$screenHeight vertical clearance", thread.height() > tallestJumpBody)

            // Use the actual Player physics and per-state hitbox through a
            // real full jump at 60Hz; no mutation of Player.hitbox is used.
            player.x = thread.centerX() - Player.BASE_WIDTH * 0.5f
            player.onJumpPressed()
            var occupiedSafeThread = false
            repeat(90) {
                player.update(1f / 60f, 600f)
                if (thread.contains(
                        player.hitbox.left,
                        player.hitbox.top,
                        player.hitbox.right,
                        player.hitbox.bottom
                    ) && orchid.onCollision(player, state) == CollisionResult.NONE
                ) {
                    occupiedSafeThread = true
                }
            }
            assertTrue("height=$screenHeight actual jump never entered safe thread", occupiedSafeThread)
        }
    }

    private fun rectField(orchid: VanillaOrchid, name: String): RectF {
        val field = VanillaOrchid::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(orchid) as RectF)
    }
}
