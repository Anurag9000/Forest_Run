package com.yourname.forest_run.engine

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuntimeAssetContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private data class SpriteContract(
        val path: String,
        val frames: Int
    )

    @Test
    fun `all required sprite sheets decode and divide into declared frames`() {
        val contracts = listOf(
            SpriteContract(AssetPaths.Char.RUN, 48),
            SpriteContract(AssetPaths.Char.JUMP, 48),
            SpriteContract(AssetPaths.Char.DUCK, 48),
            SpriteContract(AssetPaths.Char.HIT, 12),
            SpriteContract(AssetPaths.Char.DEATH, 12),
            SpriteContract(AssetPaths.Plants.CACTUS, 4),
            SpriteContract(AssetPaths.Plants.LILY_OF_VALLEY, 4),
            SpriteContract(AssetPaths.Plants.HYACINTH, 4),
            SpriteContract(AssetPaths.Plants.EUCALYPTUS, 4),
            SpriteContract(AssetPaths.Plants.VANILLA_ORCHID, 4),
            SpriteContract(AssetPaths.Trees.WEEPING_WILLOW, 4),
            SpriteContract(AssetPaths.Trees.JACARANDA, 4),
            SpriteContract(AssetPaths.Trees.BAMBOO, 4),
            SpriteContract(AssetPaths.Trees.CHERRY_BLOSSOM, 4),
            SpriteContract(AssetPaths.Birds.DUCK, 4),
            SpriteContract(AssetPaths.Birds.DUCK_FLYING, 4),
            SpriteContract(AssetPaths.Birds.TIT, 4),
            SpriteContract(AssetPaths.Birds.TIT_FLYING, 4),
            SpriteContract(AssetPaths.Birds.CHICKADEE, 4),
            SpriteContract(AssetPaths.Birds.CHICKADEE_FLYING, 4),
            SpriteContract(AssetPaths.Birds.OWL, 4),
            SpriteContract(AssetPaths.Birds.OWL_FLYING, 4),
            SpriteContract(AssetPaths.Birds.EAGLE, 4),
            SpriteContract(AssetPaths.Birds.EAGLE_FLYING, 4),
            SpriteContract(AssetPaths.Animals.CAT, 4),
            SpriteContract(AssetPaths.Animals.WOLF, 8),
            SpriteContract(AssetPaths.Animals.FOX, 4),
            SpriteContract(AssetPaths.Animals.HEDGEHOG, 4),
            SpriteContract(AssetPaths.Animals.DOG, 4)
        )

        for (contract in contracts) {
            val decoded = context.assets.open(contract.path).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            val bitmap = requireNotNull(decoded) {
                "Missing or undecodable ${contract.path}"
            }
            assertTrue("Empty bitmap ${contract.path}", bitmap.width > 0 && bitmap.height > 0)
            assertEquals(
                "Width is not divisible by ${contract.frames} frames for ${contract.path}",
                0,
                bitmap.width % contract.frames
            )
            val frameWidth = bitmap.width / contract.frames
            assertTrue("Frame is too narrow for ${contract.path}: $frameWidth", frameWidth >= 8)
            assertTrue("Frame is too short for ${contract.path}: ${bitmap.height}", bitmap.height >= 8)
            assertTrue("Sheet is too wide for ${contract.path}", bitmap.width <= 16_384)
            assertTrue("Sheet is too tall for ${contract.path}", bitmap.height <= 4_096)
            bitmap.recycle()
        }
    }

    @Test
    fun `pixel font is packaged and nonempty`() {
        val firstByte = context.assets.open(AssetPaths.PIXEL_FONT).use { it.read() }
        assertNotEquals("Pixel font is empty", -1, firstByte)
    }

    @Test
    fun `all mandatory music and sound resources are packaged`() {
        val rawNames = listOf(
            "sfx_jump",
            "sfx_land",
            "sfx_seed_ping",
            "sfx_bark",
            "sfx_screech",
            "sfx_howl",
            "sfx_bloom",
            "sfx_mercy_miss",
            "sfx_hit",
            "music_garden",
            "music_run_1",
            "music_run_2",
            "music_run_3",
            "music_bloom",
            "music_rest"
        )

        for (name in rawNames) {
            val resourceId = context.resources.getIdentifier(name, "raw", context.packageName)
            assertNotEquals("Missing res/raw/$name", 0, resourceId)
        }
    }
}
