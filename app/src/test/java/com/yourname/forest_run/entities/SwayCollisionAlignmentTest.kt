package com.yourname.forest_run.entities

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.engine.SpriteManager
import com.yourname.forest_run.entities.flora.Cactus
import com.yourname.forest_run.entities.flora.VanillaOrchid
import com.yourname.forest_run.entities.trees.CherryBlossom
import com.yourname.forest_run.entities.trees.WeepingWillow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SwayCollisionAlignmentTest {
    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `cactus visual sway does not translate its collision box`() {
        val cactus = Cactus(context, 640f, 885.6f, spriteManager.cactusSprite.copy())
        val before = RectF(cactus.hitbox)

        cactus.update(deltaTime = 0.37f, scrollSpeed = 0f)

        assertRectEquals(before, cactus.hitbox)
        assertNotEquals(0f, floatField(cactus, "currentSway"), 0.0001f)
    }

    @Test
    fun `orchid segment collisions stay rooted while its segments rotate`() {
        val orchid = VanillaOrchid(context, 640f, 885.6f, spriteManager.orchidSprite.copy())
        val bottomBefore = rectField(orchid, "bottomHitbox")
        val topBefore = rectField(orchid, "topHitbox")

        orchid.update(deltaTime = 0.37f, scrollSpeed = 0f)

        assertRectEquals(bottomBefore, rectField(orchid, "bottomHitbox"))
        assertRectEquals(topBefore, rectField(orchid, "topHitbox"))
        assertNotEquals(0f, floatField(orchid, "currentSway"), 0.0001f)
    }

    @Test
    fun `willow curtain collision stays rooted while strands sway`() {
        val willow = WeepingWillow(
            context,
            640f,
            1080f,
            885.6f,
            spriteManager.willowSprite.copy()
        )
        val before = rectField(willow, "curtainHitbox")

        willow.update(deltaTime = 0.37f, scrollSpeed = 0f)

        assertRectEquals(before, rectField(willow, "curtainHitbox"))
        assertNotEquals(0f, floatField(willow, "currentSway"), 0.0001f)
    }

    @Test
    fun `cherry branch collision stays rooted while canopy sways`() {
        val cherry = CherryBlossom(
            context,
            640f,
            1080f,
            885.6f,
            spriteManager.cherryBlossomSprite.copy()
        )
        val before = rectField(cherry, "branchHitbox")

        cherry.update(deltaTime = 0.37f, scrollSpeed = 0f)

        assertRectEquals(before, rectField(cherry, "branchHitbox"))
        assertNotEquals(0f, floatField(cherry, "currentSway"), 0.0001f)
    }

    private fun assertRectEquals(expected: RectF, actual: RectF) {
        assertEquals(expected.left, actual.left, 0.0001f)
        assertEquals(expected.top, actual.top, 0.0001f)
        assertEquals(expected.right, actual.right, 0.0001f)
        assertEquals(expected.bottom, actual.bottom, 0.0001f)
    }

    private fun rectField(instance: Any, name: String): RectF {
        val field = instance.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return RectF(field.get(instance) as RectF)
    }

    private fun floatField(instance: Any, name: String): Float {
        val field = instance.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.getFloat(instance)
    }
}
