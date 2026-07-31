package com.anurag9000.forestrun.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.systems.ParticleManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GardenScreenBoundaryTest {
    private lateinit var context: Context
    private lateinit var screen: GardenScreen

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ParticleManager.resetOneShotEmitterCacheForTests()
        screen = GardenScreen(
            context = context,
            spriteManager = SpriteManager(context),
            screenW = 1_920,
            screenH = 1_080
        )
        screen.load()
    }

    @After
    fun tearDown() {
        ParticleManager.resetOneShotEmitterCacheForTests()
        SaveManager.usePrimaryPreferences()
    }

    @Test
    fun `invalid frame deltas are complete no ops`() {
        setPrivateField(screen, "elapsed", 0.25f)
        setPrivateField(screen, "unlockAnim", 0.4f)
        setPrivateField(screen, "unlockIdx", 2)
        setPrivateField(screen, "wardrobeMessageTimer", 2f)

        screen.update(Float.NaN)
        screen.update(Float.POSITIVE_INFINITY)
        screen.update(Float.NEGATIVE_INFINITY)
        screen.update(-1f)
        screen.update(0f)

        assertEquals(0.25f, getPrivateField<Float>(screen, "elapsed"), 0f)
        assertEquals(0.4f, getPrivateField<Float>(screen, "unlockAnim"), 0f)
        assertEquals(2, getPrivateField<Int>(screen, "unlockIdx"))
        assertEquals(2f, getPrivateField<Float>(screen, "wardrobeMessageTimer"), 0f)
    }

    @Test
    fun `valid frame caps catch up and repairs poisoned animation state`() {
        setPrivateField(screen, "elapsed", Float.NaN)
        setPrivateField(screen, "unlockAnim", Float.NaN)
        setPrivateField(screen, "unlockIdx", 4)
        setPrivateField(screen, "wardrobeMessageTimer", Float.POSITIVE_INFINITY)

        screen.update(10f)

        assertEquals(0.05f, getPrivateField<Float>(screen, "elapsed"), 0.0001f)
        assertEquals(-1f, getPrivateField<Float>(screen, "unlockAnim"), 0f)
        assertEquals(-1, getPrivateField<Int>(screen, "unlockIdx"))
        assertEquals(0f, getPrivateField<Float>(screen, "wardrobeMessageTimer"), 0f)
    }

    @Test
    fun `non finite taps cannot invoke callbacks or mutate persisted state`() {
        var backCalls = 0
        var runCalls = 0
        screen.onBack = { backCalls++ }
        screen.onRun = { runCalls++ }
        SaveManager.saveLifetimeSeeds(context, 50)
        SaveManager.saveGardenProgress(context, 1)
        screen.load()

        listOf(
            Float.NaN to 0f,
            0f to Float.NaN,
            Float.POSITIVE_INFINITY to 1_080f,
            960f to Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY to Float.NEGATIVE_INFINITY
        ).forEach { (x, y) ->
            assertFalse(screen.onTap(x, y))
        }

        assertEquals(0, backCalls)
        assertEquals(0, runCalls)
        assertEquals(1, SaveManager.loadGardenProgress(context))
        assertEquals(50, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `screen adopts atomic purchase result as its only local state`() {
        SaveManager.saveLifetimeSeeds(context, 50)
        SaveManager.saveGardenProgress(context, 1)
        screen.load()
        val layout = GardenLayoutPlanner.build(
            width = 1_920f,
            height = 1_080f,
            plantCount = 9,
            costumeCount = CostumeStyle.entries.size
        )
        val nextCard = layout.plantCards[1]

        assertTrue(screen.onTap(nextCard.centerX, nextCard.centerY))

        assertEquals(2, SaveManager.loadGardenProgress(context))
        assertEquals(30, SaveManager.loadLifetimeSeeds(context))
        assertEquals(2, getPrivateField<Int>(screen, "unlockedCount"))
        assertEquals(30, getPrivateField<Int>(screen, "lifeSeeds"))
        assertEquals(1, getPrivateField<Int>(screen, "unlockIdx"))
        assertEquals(0f, getPrivateField<Float>(screen, "unlockAnim"), 0f)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateField(target: Any, name: String): T {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target) as T
    }

    private fun setPrivateField(target: Any, name: String, value: Any) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }
}
