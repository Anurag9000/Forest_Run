package com.yourname.forest_run.engine

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuntimeAssetContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private data class SpriteContract(
        val path: String,
        val frameWidth: Int,
        val frameHeight: Int,
        val frames: Int
    )

    @Test
    fun `all required sprite sheets exist and match exact frame geometry`() {
        val contracts = listOf(
            SpriteContract(AssetPaths.Char.RUN, 72, 100, 48),
            SpriteContract(AssetPaths.Char.JUMP, 72, 100, 48),
            SpriteContract(AssetPaths.Char.DUCK, 72, 100, 48),
            SpriteContract(AssetPaths.Char.HIT, 72, 100, 12),
            SpriteContract(AssetPaths.Char.DEATH, 72, 100, 12),
            SpriteContract(AssetPaths.Plants.CACTUS, 64, 64, 4),
            SpriteContract(AssetPaths.Plants.LILY_OF_VALLEY, 64, 64, 4),
            SpriteContract(AssetPaths.Plants.HYACINTH, 64, 64, 4),
            SpriteContract(AssetPaths.Plants.EUCALYPTUS, 64, 64, 4),
            SpriteContract(AssetPaths.Plants.VANILLA_ORCHID, 64, 64, 4),
            SpriteContract(AssetPaths.Trees.WEEPING_WILLOW, 64, 128, 4),
            SpriteContract(AssetPaths.Trees.JACARANDA, 64, 128, 4),
            SpriteContract(AssetPaths.Trees.BAMBOO, 64, 128, 4),
            SpriteContract(AssetPaths.Trees.CHERRY_BLOSSOM, 64, 128, 4),
            SpriteContract(AssetPaths.Birds.DUCK, 64, 64, 4),
            SpriteContract(AssetPaths.Birds.DUCK_FLYING, 64, 64, 4),
            SpriteContract(AssetPaths.Birds.TIT, 64, 64, 4),
            SpriteContract(AssetPaths.Birds.TIT_FLYING, 64, 64, 4),
            SpriteContract(AssetPaths.Birds.CHICKADEE, 64, 64, 4),
            SpriteContract(AssetPaths.Birds.CHICKADEE_FLYING, 64, 64, 4),
            SpriteContract(AssetPaths.Birds.OWL, 64, 64, 4),
            SpriteContract(AssetPaths.Birds.OWL_FLYING, 64, 64, 4),
            SpriteContract(AssetPaths.Birds.EAGLE, 64, 64, 4),
            SpriteContract(AssetPaths.Birds.EAGLE_FLYING, 64, 64, 4),
            SpriteContract(AssetPaths.Animals.CAT, 64, 64, 4),
            SpriteContract(AssetPaths.Animals.WOLF, 64, 64, 8),
            SpriteContract(AssetPaths.Animals.FOX, 64, 64, 4),
            SpriteContract(AssetPaths.Animals.HEDGEHOG, 64, 64, 4),
            SpriteContract(AssetPaths.Animals.DOG, 64, 64, 4)
        )

        for (contract in contracts) {
            val decoded = context.assets.open(contract.path).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            val bitmap = requireNotNull(decoded) {
                "Missing or undecodable ${contract.path}"
            }
            assertEquals(
                "Unexpected width for ${contract.path}",
                contract.frameWidth * contract.frames,
                bitmap.width
            )
            assertEquals(
                "Unexpected height for ${contract.path}",
                contract.frameHeight,
                bitmap.height
            )
            bitmap.recycle()
        }
    }

    @Test
    fun `pixel font is packaged and nonempty`() {
        val firstByte = context.assets.open(AssetPaths.PIXEL_FONT).use { it.read() }
        assertNotEquals("Pixel font is empty", -1, firstByte)
    }

    @Test
    fun `all required music and sound resources are packaged`() {
        val rawNames = listOf(
            "sfx_jump",
            "sfx_land",
            "sfx_seed_ping",
            "sfx_bark",
            "sfx_screech",
            "sfx_howl",
            "sfx_bloom",
            "sfx_bloom_ready",
            "sfx_bloom_convert",
            "sfx_bloom_fade",
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
