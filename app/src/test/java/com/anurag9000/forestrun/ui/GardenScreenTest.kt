package com.anurag9000.forestrun.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.CostumeManager
import com.anurag9000.forestrun.engine.ReturnMomentState
import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.systems.FxPreset
import com.anurag9000.forestrun.systems.ParticleManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GardenScreenTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        spriteManager = SpriteManager(context)
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ParticleManager.resetOneShotEmitterCacheForTests()
    }

    @After
    fun tearDown() {
        ParticleManager.resetOneShotEmitterCacheForTests()
        SaveManager.usePrimaryPreferences()
    }



    @Test
    fun `screen initialization cannot earn Garden story pages before Garden entry`() {
        assertTrue(SaveManager.loadUnlockedMemoryPages(context).isEmpty())
        val screen = GardenScreen(context, spriteManager, 960, 540)

        screen.load()

        assertTrue(SaveManager.loadUnlockedMemoryPages(context).isEmpty())

        screen.refresh()

        val earned = SaveManager.loadUnlockedMemoryPages(context)
        assertTrue(earned.isNotEmpty())
        val field = screen.javaClass.getDeclaredField("memoryPageCount")
        field.isAccessible = true
        assertEquals(earned.size, field.getInt(screen))
    }

    @Test
    fun `Garden return moment is consumed only after its first rendered frame`() {
        val before = ReturnMomentState(
            lastActiveAtMs = 0L,
            lastGardenGreetingDay = -1L,
            roughRunStreak = 0
        )
        SaveManager.saveReturnMomentState(context, before)
        val screen = GardenScreen(context, spriteManager, 960, 540)

        screen.load()
        screen.refresh()

        // Entry and refresh prepare presentation but do not consume it.
        assertEquals(before, SaveManager.loadReturnMomentState(context))
        val preparedField = screen.javaClass.getDeclaredField("returnMomentPreparedAtMs")
        preparedField.isAccessible = true
        val preparedAtMs = preparedField.getLong(screen)
        assertTrue(preparedAtMs > 0L)

        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        screen.draw(Canvas(bitmap))

        val afterFirstDraw = SaveManager.loadReturnMomentState(context)
        assertTrue(afterFirstDraw.lastGardenGreetingDay >= 0L)
        assertEquals(preparedAtMs, afterFirstDraw.lastActiveAtMs)

        // Further frames do not create a second consumption transition.
        screen.draw(Canvas(bitmap))
        assertEquals(afterFirstDraw, SaveManager.loadReturnMomentState(context))
        bitmap.recycle()
    }

    @Test
    fun `unlocking next plant spends seeds and defers particles to update`() {
        SaveManager.saveLifetimeSeeds(context, 50)
        SaveManager.saveGardenProgress(context, 1)
        val screen = GardenScreen(context, spriteManager, 1_920, 1_080)
        screen.load()
        val layout = GardenLayoutPlanner.build(
            width = 1_920f,
            height = 1_080f,
            plantCount = 9,
            costumeCount = CostumeStyle.entries.size
        )
        val nextPlantCard = layout.plantCards[1]
        val tapX = (nextPlantCard.left + nextPlantCard.right) / 2f
        val tapY = (nextPlantCard.top + nextPlantCard.bottom) / 2f

        assertTrue(screen.onTap(tapX, tapY))
        assertEquals(2, SaveManager.loadGardenProgress(context))
        assertEquals(30, SaveManager.loadLifetimeSeeds(context))
        assertNull(ParticleManager.cachedOneShotEmitterForTest(FxPreset.SEED_COLLECT))

        screen.update(1f / 60f)

        assertNotNull(ParticleManager.cachedOneShotEmitterForTest(FxPreset.SEED_COLLECT))
    }

    @Test
    fun `tapping unlocked costume equips it from the wardrobe`() {
        repeat(3) { SaveManager.incrementSparedCount(context, EntityType.CAT) }
        CostumeManager.refreshUnlocks(context)

        val screen = GardenScreen(context, spriteManager, 1_920, 1_080)
        screen.load()
        val layout = GardenLayoutPlanner.build(
            width = 1_920f,
            height = 1_080f,
            plantCount = 9,
            costumeCount = CostumeStyle.entries.size
        )
        val flowerCrownCard = layout.wardrobeCards[CostumeStyle.FLOWER_CROWN.ordinal]
        val tapX = (flowerCrownCard.left + flowerCrownCard.right) / 2f
        val tapY = (flowerCrownCard.top + flowerCrownCard.bottom) / 2f

        assertTrue(screen.onTap(tapX, tapY))
        assertEquals(CostumeStyle.FLOWER_CROWN, SaveManager.loadActiveCostume(context))
    }
}
