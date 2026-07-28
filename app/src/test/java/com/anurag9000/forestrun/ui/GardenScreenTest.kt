package com.anurag9000.forestrun.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.CostumeManager
import com.anurag9000.forestrun.engine.SaveManager
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType
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
