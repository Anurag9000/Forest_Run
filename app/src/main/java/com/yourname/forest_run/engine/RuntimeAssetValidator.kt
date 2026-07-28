package com.yourname.forest_run.engine

import android.content.Context
import android.content.pm.ApplicationInfo

/** Fails release startup when a required packaged asset is absent or empty. */
object RuntimeAssetValidator {
    private val requiredAssetPaths = listOf(
        AssetPaths.PIXEL_FONT,
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

        val resources = appContext.resources
        val missingRaw = requiredRawResources.filter { name ->
            resources.getIdentifier(name, "raw", appContext.packageName) == 0
        }

        check(missingAssets.isEmpty() && missingRaw.isEmpty()) {
            buildString {
                append("Forest Run release assets are incomplete.")
                if (missingAssets.isNotEmpty()) {
                    append(" Missing assets: ")
                    append(missingAssets.joinToString())
                    append('.')
                }
                if (missingRaw.isNotEmpty()) {
                    append(" Missing raw resources: ")
                    append(missingRaw.joinToString())
                    append('.')
                }
            }
        }
    }
}
