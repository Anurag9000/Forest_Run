package com.anurag9000.forestrun.entities

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.EncounterVariant
import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.engine.SpriteManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntityFactoryTest {
    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        spriteManager = SpriteManager(context)
    }

    @After
    fun tearDown() {
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `every entity receives finite defaults from malformed factory geometry`() {
        EntityType.entries.forEach { type ->
            val entity = EntityFactory.create(
                context = context,
                type = type,
                startX = Float.NaN,
                screenWidth = Float.POSITIVE_INFINITY,
                screenHeight = -1f,
                spriteManager = spriteManager,
                variant = if (type == EntityType.DOG) {
                    EncounterVariant.DOG_BUDDY
                } else {
                    EncounterVariant.DEFAULT
                }
            )

            assertTrue("$type x", entity.x.isFinite())
            assertTrue("$type y", entity.y.isFinite())
            assertTrue("$type velocityX", entity.velocityX.isFinite())
            assertTrue("$type velocityY", entity.velocityY.isFinite())
            val bounds = entity.encounterBounds
            assertTrue("$type bounds.left", bounds.left.isFinite())
            assertTrue("$type bounds.top", bounds.top.isFinite())
            assertTrue("$type bounds.right", bounds.right.isFinite())
            assertTrue("$type bounds.bottom", bounds.bottom.isFinite())
        }
    }

    @Test
    fun `valid spawn origin remains unchanged`() {
        val entity = EntityFactory.create(
            context = context,
            type = EntityType.CACTUS,
            startX = 777f,
            screenWidth = 1_920f,
            screenHeight = 1_080f,
            spriteManager = spriteManager
        )

        assertEquals(777f, entity.x, 0f)
        assertTrue(entity.y.isFinite())
    }
}
