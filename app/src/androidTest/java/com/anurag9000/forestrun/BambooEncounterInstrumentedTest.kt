package com.anurag9000.forestrun

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.trees.Bamboo
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BambooEncounterInstrumentedTest {
    @Test
    fun bambooRendersAndKeepsItsPassageAndStalkCollisionGeometry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val spriteManager = SpriteManager(context)
        val bamboo = Bamboo(
            context = context,
            startX = 560f,
            screenHeight = 1080f,
            groundY = 885.6f,
            sprite = spriteManager.bambooSprite.copy(),
            random = Random(73)
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)

        try {
            val gaps = rectArrayField(bamboo, "gapRects")
            assertEquals(4, gaps.size)
            assertTrue(gaps[1].width() > gaps[0].width())
            assertTrue(gaps[1].width() > gaps[2].width())

            player.hitbox.set(
                gaps[1].left + 4f,
                gaps[1].top + 8f,
                gaps[1].right - 4f,
                gaps[1].bottom - 8f
            )
            assertEquals(CollisionResult.NONE, bamboo.onCollision(player, gameState))

            val stalks = rectArrayField(bamboo, "topHitboxes")
            player.hitbox.set(
                stalks[1].left + 2f,
                stalks[1].bottom - 10f,
                stalks[1].right - 2f,
                stalks[1].bottom - 2f
            )
            assertEquals(CollisionResult.HIT, bamboo.onCollision(player, gameState))

            bamboo.update(deltaTime = 1f / 60f, scrollSpeed = 120f)
            bamboo.draw(Canvas(bitmap))
            assertTrue(bamboo.hitbox.right > bamboo.hitbox.left)
        } finally {
            bitmap.recycle()
        }
    }

    private fun rectArrayField(bamboo: Bamboo, name: String): Array<RectF> {
        val field = Bamboo::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val value = field.get(bamboo) as Array<RectF>
        return Array(value.size) { index -> RectF(value[index]) }
    }
}
