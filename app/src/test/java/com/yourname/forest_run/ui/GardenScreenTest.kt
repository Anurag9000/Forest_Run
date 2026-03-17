package com.yourname.forest_run.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.engine.CostumeManager
import com.yourname.forest_run.engine.SaveManager
import com.yourname.forest_run.engine.SpriteManager
import com.yourname.forest_run.entities.CostumeStyle
import com.yourname.forest_run.entities.EntityType
import org.junit.Assert.assertEquals
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
        spriteManager = SpriteManager(context)
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `unlocking next plant spends seeds and persists progress`() {
        SaveManager.saveLifetimeSeeds(context, 50)
        SaveManager.saveGardenProgress(context, 1)
        val screen = GardenScreen(context, spriteManager, 1_920, 1_080)
        screen.load()

        val cardWidth = 1_920 / 10.5f
        val cardGap = cardWidth * 0.12f
        val rowStartX = (1_920 - (9 * (cardWidth + cardGap) - cardGap)) / 2f
        val rowY = 1_080 * 0.20f
        val tapX = rowStartX + (cardWidth + cardGap) + cardWidth / 2f
        val tapY = rowY + (1_080 * 0.55f) / 2f

        assertTrue(screen.onTap(tapX, tapY))
        assertEquals(2, SaveManager.loadGardenProgress(context))
        assertEquals(30, SaveManager.loadLifetimeSeeds(context))
    }

    @Test
    fun `tapping unlocked costume equips it from the wardrobe`() {
        repeat(3) { SaveManager.incrementSparedCount(context, EntityType.CAT) }
        CostumeManager.refreshUnlocks(context)

        val screen = GardenScreen(context, spriteManager, 1_920, 1_080)
        screen.load()

        val wardrobeLeft = 1_920 * 0.05f
        val wardrobeTop = 1_080 * 0.68f
        val wardrobeWidth = 1_920 * 0.90f
        val wardrobeHeight = 1_080 * 0.21f
        val columns = 4
        val gapX = wardrobeWidth * 0.015f
        val innerLeft = wardrobeLeft + 18f
        val innerRight = wardrobeLeft + wardrobeWidth - 18f
        val innerTop = wardrobeTop + 34f
        val innerBottom = wardrobeTop + wardrobeHeight - 22f
        val cardWidth = (innerRight - innerLeft - gapX * (columns - 1)) / columns
        val rows = 2
        val gapY = wardrobeHeight * 0.06f
        val cardHeight = (innerBottom - innerTop - gapY * (rows - 1)) / rows
        val tapX = innerLeft + cardWidth + gapX + cardWidth / 2f
        val tapY = innerTop + cardHeight / 2f

        assertTrue(screen.onTap(tapX, tapY))
        assertEquals(CostumeStyle.FLOWER_CROWN, SaveManager.loadActiveCostume(context))
    }
}
