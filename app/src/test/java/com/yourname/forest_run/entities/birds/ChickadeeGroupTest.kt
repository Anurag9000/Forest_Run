package com.yourname.forest_run.entities.birds

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.engine.GameStateManager
import com.yourname.forest_run.engine.SpriteManager
import com.yourname.forest_run.entities.CollisionResult
import com.yourname.forest_run.entities.Player
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
        val chickadees = ChickadeeGroup(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            sprite = spriteManager.chickadeeSprite.copy(),
            count = 3
        )
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
