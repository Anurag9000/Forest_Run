package com.anurag9000.forestrun.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory

/** Fails release startup when a required packaged asset is absent, empty, or unsafe to decode. */
object RuntimeAssetValidator {
    private const val MAX_SPRITE_WIDTH_PX = 16_384
    private const val MAX_SPRITE_HEIGHT_PX = 4_096
    private const val MAX_SPRITE_PIXELS = 4L * 1_024L * 1_024L

    private val requiredSpritePaths = listOf(
        AssetPaths.Char.RUN,
        AssetPaths.Char.JUMP,
        AssetPaths.Char.DUCK,
        AssetPaths.Char.HIT,
        AssetPaths.Char.DEATH,
        AssetPaths.Plants.CACTUS,
        AssetPaths.Plants.LILY_OF_VALLEY,
        AssetPaths.Plants.HYACINTH,
        AssetPaths.Plants.EUCALYPTUS,
        AssetPaths.Plants.VANILLA_ORCHID,
        AssetPaths.Trees.WEEPING_WILLOW,
        AssetPaths.Trees.JACARANDA,
        AssetPaths.Trees.BAMBOO,
        AssetPaths.Trees.CHERRY_BLOSSOM,
        AssetPaths.Birds.DUCK,
        AssetPaths.Birds.DUCK_FLYING,
        AssetPaths.Birds.TIT,
        AssetPaths.Birds.TIT_FLYING,
        AssetPaths.Birds.CHICKADEE,
        AssetPaths.Birds.CHICKADEE_FLYING,
        AssetPaths.Birds.OWL,
        AssetPaths.Birds.OWL_FLYING,
        AssetPaths.Birds.EAGLE,
        AssetPaths.Birds.EAGLE_FLYING,
        AssetPaths.Animals.CAT,
        AssetPaths.Animals.WOLF,
        AssetPaths.Animals.FOX,
        AssetPaths.Animals.HEDGEHOG,
        AssetPaths.Animals.DOG
    )

    private val requiredAssetPaths = listOf(AssetPaths.PIXEL_FONT) + requiredSpritePaths

    /**
     * Bloom-ready/convert/fade cues are intentionally optional because
     * SfxManager has authored fallback sounds for those accents.
     */
    private val requiredRawResources = listOf(
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

    fun validateRelease(context: Context) {
        val appContext = context.applicationContext
        val isDebuggable =
            (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) return

        val missingAssets = requiredAssetPaths.filterNot { path ->
            runCatching {
                appContext.assets.open(path).use { stream -> stream.read() >= 0 }
            }.getOrDefault(false)
        }
        val missingAssetSet = missingAssets.toHashSet()

        // Decode bounds only. A malicious or accidentally gigantic sheet must
        // fail before SpriteManager allocates the bitmap, mutable bird copy,
        // pixel array, and flood-fill queue together.
        val unsafeSprites = requiredSpritePaths.filter { path ->
            path !in missingAssetSet && !hasSafeSpriteBounds(appContext, path)
        }

        val resources = appContext.resources
        val missingRaw = requiredRawResources.filter { name ->
            val resourceId = resources.getIdentifier(name, "raw", appContext.packageName)
            resourceId == 0 || !runCatching {
                resources.openRawResource(resourceId).use { stream -> stream.read() >= 0 }
            }.getOrDefault(false)
        }

        check(missingAssets.isEmpty() && unsafeSprites.isEmpty() && missingRaw.isEmpty()) {
            buildString {
                append("Forest Run release assets are incomplete or unsafe.")
                if (missingAssets.isNotEmpty()) {
                    append(" Missing or empty assets: ")
                    append(missingAssets.joinToString())
                    append('.')
                }
                if (unsafeSprites.isNotEmpty()) {
                    append(" Undecodable or oversized sprites: ")
                    append(unsafeSprites.joinToString())
                    append('.')
                }
                if (missingRaw.isNotEmpty()) {
                    append(" Missing or empty raw resources: ")
                    append(missingRaw.joinToString())
                    append('.')
                }
            }
        }
    }

    private fun hasSafeSpriteBounds(context: Context, path: String): Boolean =
        runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val width = options.outWidth
            val height = options.outHeight
            val pixels = width.toLong() * height.toLong()
            width > 0 &&
                height > 0 &&
                width <= MAX_SPRITE_WIDTH_PX &&
                height <= MAX_SPRITE_HEIGHT_PX &&
                pixels in 1L..MAX_SPRITE_PIXELS
        }.getOrDefault(false)
}
