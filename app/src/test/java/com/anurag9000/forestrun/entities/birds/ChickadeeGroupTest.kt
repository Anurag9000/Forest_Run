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
class ChickadeeGroupTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `chickadee group exposes a readable flutter pocket around the lead bird`() {
        val chickadees = chickadees()
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        setFloatArray(chickadees, "altitudes", floatArrayOf(240f, 310f, 380f))
        setFloatArray(chickadees, "targetAltitudes", floatArrayOf(240f, 310f, 380f))
        chickadees.update(deltaTime = 0f, scrollSpeed = 0f)

        val pocket = rectField(chickadees, "flutterPocketRect")
        val birdRects = rectArrayField(chickadees, "birdRects")

        assertTrue(pocket.height() > 0f)
        assertTrue(kotlin.math.abs(pocket.centerX() - birdRects[1].centerX()) < 2f)

        player.hitbox.set(
            pocket.left + 4f,
            pocket.top + 4f,
            pocket.right - 4f,
            pocket.bottom - 4f
        )
        chickadees.updatePlayerInteraction(player, gameState)
        assertTrue(chickadees.onCollision(player, gameState) != CollisionResult.HIT)
        assertTrue(booleanField(chickadees, "readPocket"))
    }

    @Test
    fun `real airborne Player must fit the entire highlighted pocket to earn credit`() {
        val chickadees = chickadees()
        val altitudes = floatArrayOf(240f, 310f, 380f)
        setFloatArray(chickadees, "altitudes", altitudes)
        setFloatArray(chickadees, "targetAltitudes", altitudes)
        chickadees.update(0f, 0f)

        val airborne = Player(1920, 1080, spriteManager)
        airborne.onJumpPressed()
        airborne.update(0.05f)
        val pocket = rectField(chickadees, "flutterPocketRect")
        assertTrue(
            "cue must fit the full airborne collision height",
            pocket.height() >= airborne.hitbox.height()
        )
        assertTrue(
            "cue must fit the full airborne width",
            pocket.width() >= airborne.hitbox.width()
        )

        // The fallback is also truly traversable by the unmodified grounded
        // Player at its actual y; only horizontal scrolling aligns the lane.
        val player = Player(1920, 1080, spriteManager)
        val bodyWidth = player.hitbox.width()
        val bodyHeight = player.hitbox.height()
        assertTrue(pocket.contains(
            pocket.centerX() - bodyWidth * 0.5f,
            player.hitbox.top,
            pocket.centerX() + bodyWidth * 0.5f,
            player.hitbox.bottom
        ))

        val isolatedState = GameStateManager(context) { false }
        // One pixel of overlap with the cue is not a completed pocket read.
        player.hitbox.offsetTo(pocket.right - 1f, player.hitbox.top)
        chickadees.updatePlayerInteraction(player, isolatedState)
        assertFalse(booleanField(chickadees, "readPocket"))

        player.hitbox.offsetTo(
            pocket.centerX() - bodyWidth * 0.5f,
            player.hitbox.top
        )
        assertTrue(pocket.contains(
            player.hitbox.left, player.hitbox.top,
            player.hitbox.right, player.hitbox.bottom
        ))
        chickadees.updatePlayerInteraction(player, isolatedState)
        assertTrue(booleanField(chickadees, "readPocket"))
        chickadees.performUniqueAction(player, isolatedState)
        assertEquals(1, isolatedState.seedsThisRun)
    }

    @Test
    fun `grounded fallback disappears rather than advertising an occupied lane`() {
        val chickadees = chickadees()
        val lowBirds = floatArrayOf(770f, 780f, 790f)
        setFloatArray(chickadees, "altitudes", lowBirds)
        setFloatArray(chickadees, "targetAltitudes", lowBirds)
        chickadees.update(0f, 0f)

        val pocket = rectField(chickadees, "flutterPocketRect")
        assertTrue("no full-body gap must not display a fake safe cue", pocket.isEmpty)
        val player = Player(1920, 1080, spriteManager)
        val isolatedState = GameStateManager(context) { false }
        chickadees.updatePlayerInteraction(player, isolatedState)
        assertFalse(booleanField(chickadees, "readPocket"))
    }

    @Test
    fun `chickadee aggregate bounds equal independently moving flock`() {
        val chickadees = chickadees()
        setFloatArray(chickadees, "altitudes", floatArrayOf(210f, 410f, 285f))
        setFloatArray(chickadees, "targetAltitudes", floatArrayOf(210f, 410f, 285f))

        chickadees.update(deltaTime = 0.25f, scrollSpeed = 300f)

        val birds = rectArrayField(chickadees, "birdRects")
        assertEquals(birds.minOf { it.left }, chickadees.hitbox.left, 0.001f)
        assertEquals(birds.minOf { it.top }, chickadees.hitbox.top, 0.001f)
        assertEquals(birds.maxOf { it.right }, chickadees.hitbox.right, 0.001f)
        assertEquals(birds.maxOf { it.bottom }, chickadees.hitbox.bottom, 0.001f)
    }


    @Test
    fun `near miss of first chickadee does not mask direct hit on next bird`() {
        val group = chickadees()
        val player = Player(1920, 1080, spriteManager)
        val state = GameStateManager(context)
        val field = ChickadeeGroup::class.java.getDeclaredField("birdRects")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val birds = field.get(group) as Array<RectF>
        player.hitbox.set(110f, 110f, 130f, 130f)
        birds[0].set(135f, 100f, 175f, 140f)
        birds[1].set(100f, 100f, 140f, 140f)
        birds[2].set(310f, 100f, 350f, 140f)

        assertEquals(CollisionResult.HIT, group.onCollision(player, state))
        birds[1].set(250f, 100f, 290f, 140f)
        assertEquals(CollisionResult.MERCY_MISS, group.onCollision(player, state))
    }


    @Test
    fun `flutter pocket excludes every live bird during spread and crowding`() {
        val chickadees = chickadees()
        val arrangements = listOf(
            floatArrayOf(240f, 310f, 380f),
            floatArrayOf(200f, 360f, 510f),
            floatArrayOf(350f, 352f, 354f)
        )
        for (altitudes in arrangements) {
            with(chickadees) {
                setFloatArray(this, "altitudes", altitudes)
                setFloatArray(this, "targetAltitudes", altitudes)
                update(0f, 0f)
            }
            repeat(20) {
                val pocket = rectField(chickadees, "flutterPocketRect")
                val birds = rectArrayField(chickadees, "birdRects")
                assertTrue(
                    "Pocket must fit the full airborne Player, not just a point: $altitudes",
                    pocket.height() >= Player.BASE_HEIGHT
                )
                for (bird in birds) {
                    assertFalse("Pocket intersects a live bird: $altitudes", RectF.intersects(pocket, bird))
                }
                chickadees.update(0.05f, 0f)
            }
        }
    }

    private fun chickadees() = ChickadeeGroup(
        context = context,
        startX = 520f,
        groundY = 885.6f,
        sprite = spriteManager.chickadeeSprite.copy(),
        count = 3
    )

    private fun rectField(chickadees: ChickadeeGroup, name: String): RectF {
        val field = ChickadeeGroup::class.java.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(chickadees) as RectF)
    }

    private fun rectArrayField(chickadees: ChickadeeGroup, name: String): Array<RectF> {
        val field = ChickadeeGroup::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val value = field.get(chickadees) as Array<RectF>
        return Array(value.size) { index -> RectF(value[index]) }
    }

    private fun setFloatArray(chickadees: ChickadeeGroup, name: String, values: FloatArray) {
        val field = ChickadeeGroup::class.java.getDeclaredField(name)
        field.isAccessible = true
        val target = field.get(chickadees) as FloatArray
        for (index in values.indices) target[index] = values[index]
    }

    private fun booleanField(chickadees: ChickadeeGroup, name: String): Boolean {
        val field = ChickadeeGroup::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(chickadees)
    }
}
