package com.yourname.forest_run.engine

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import com.yourname.forest_run.BuildConfig
import com.yourname.forest_run.utils.BitmapHelper
import kotlin.math.abs

/** Loads, validates, sanitizes, and caches every runtime sprite sheet. */
class SpriteManager(private val context: Context) {
    companion object {
        private const val TAG = "SpriteManager"
        private const val PLAYER_RUN_FRAMES = 48
        private const val PLAYER_JUMP_STRIP_FRAMES = 48
        private const val PLAYER_DUCK_FRAMES = 48
        private const val PLAYER_HIT_FRAMES = 12
        private const val PLAYER_DEATH_FRAMES = 12
    }

    private val assets: AssetManager = context.assets

    val playerRun: SpriteSheet
    val playerJumpStart: SpriteSheet
    val playerJumping: SpriteSheet
    val playerApex: SpriteSheet
    val playerFalling: SpriteSheet
    val playerLanding: SpriteSheet
    val playerStandUp: SpriteSheet
    val playerDuck: SpriteSheet
    val playerHit: SpriteSheet
    val playerDeath: SpriteSheet

    val cactusSprite: SpriteSheet
    val lilySprite: SpriteSheet
    val hyacinthSprite: SpriteSheet
    val eucalyptusSprite: SpriteSheet
    val orchidSprite: SpriteSheet

    val willowSprite: SpriteSheet
    val jacarandaSprite: SpriteSheet
    val bambooSprite: SpriteSheet
    val cherryBlossomSprite: SpriteSheet

    val duckSprite: SpriteSheet
    val duckFlying: SpriteSheet
    val titSprite: SpriteSheet
    val titFlying: SpriteSheet
    val chickadeeSprite: SpriteSheet
    val chickadeeFlying: SpriteSheet
    val owlSprite: SpriteSheet
    val owlFlying: SpriteSheet
    val eagleSprite: SpriteSheet
    val eagleFlying: SpriteSheet

    val catSprite: SpriteSheet
    val wolfSprite: SpriteSheet
    val foxSprite: SpriteSheet
    val hedgehogSprite: SpriteSheet
    val dogSprite: SpriteSheet

    init {
        val playerFrameWidth = 72
        val playerFrameHeight = 100

        val runBitmap = loadValidated(
            AssetPaths.Char.RUN,
            Color.rgb(70, 160, 255),
            playerFrameWidth,
            playerFrameHeight,
            PLAYER_RUN_FRAMES
        )
        playerRun = SpriteSheet(
            runBitmap,
            frameCount = PLAYER_RUN_FRAMES,
            framesPerSec = 24f,
            isLooping = true
        )

        val jumpBitmap = loadValidated(
            AssetPaths.Char.JUMP,
            Color.rgb(255, 220, 60),
            playerFrameWidth,
            playerFrameHeight,
            PLAYER_JUMP_STRIP_FRAMES
        )
        playerJumpStart = SpriteSheet(
            jumpBitmap,
            frameCount = 2,
            framesPerSec = 20f,
            isLooping = false,
            totalFramesInBitmap = PLAYER_JUMP_STRIP_FRAMES
        )
        playerJumping = SpriteSheet(
            jumpBitmap,
            frameCount = 12,
            framesPerSec = 15f,
            isLooping = true,
            startFrame = 2,
            totalFramesInBitmap = PLAYER_JUMP_STRIP_FRAMES
        )
        playerApex = SpriteSheet(
            jumpBitmap,
            frameCount = 4,
            framesPerSec = 8f,
            isLooping = true,
            startFrame = 14,
            totalFramesInBitmap = PLAYER_JUMP_STRIP_FRAMES
        )
        playerFalling = SpriteSheet(
            jumpBitmap,
            frameCount = 6,
            framesPerSec = 12f,
            isLooping = true,
            startFrame = 18,
            totalFramesInBitmap = PLAYER_JUMP_STRIP_FRAMES
        )
        playerLanding = SpriteSheet(
            jumpBitmap,
            frameCount = 4,
            framesPerSec = 25f,
            isLooping = false,
            startFrame = 24,
            totalFramesInBitmap = PLAYER_JUMP_STRIP_FRAMES
        )
        playerStandUp = SpriteSheet(
            jumpBitmap,
            frameCount = 18,
            framesPerSec = 12f,
            isLooping = false,
            totalFramesInBitmap = PLAYER_JUMP_STRIP_FRAMES
        )

        val duckBitmap = loadValidated(
            AssetPaths.Char.DUCK,
            Color.rgb(80, 220, 180),
            playerFrameWidth,
            playerFrameHeight,
            PLAYER_DUCK_FRAMES
        )
        playerDuck = SpriteSheet(
            duckBitmap,
            frameCount = PLAYER_DUCK_FRAMES,
            framesPerSec = 12f,
            isLooping = true
        )

        val hitBitmap = loadValidated(
            AssetPaths.Char.HIT,
            Color.rgb(220, 100, 100),
            playerFrameWidth,
            playerFrameHeight,
            PLAYER_HIT_FRAMES
        )
        playerHit = SpriteSheet(
            hitBitmap,
            frameCount = PLAYER_HIT_FRAMES,
            framesPerSec = 15f,
            isLooping = false
        )

        val deathBitmap = loadValidated(
            AssetPaths.Char.DEATH,
            Color.rgb(100, 100, 100),
            playerFrameWidth,
            playerFrameHeight,
            PLAYER_DEATH_FRAMES
        )
        playerDeath = SpriteSheet(
            deathBitmap,
            frameCount = PLAYER_DEATH_FRAMES,
            framesPerSec = 12f,
            isLooping = false
        )

        cactusSprite = loadEntity(AssetPaths.Plants.CACTUS, Color.rgb(30, 140, 50), 4)
        lilySprite = loadEntity(AssetPaths.Plants.LILY_OF_VALLEY, Color.WHITE, 4)
        hyacinthSprite = loadEntity(AssetPaths.Plants.HYACINTH, Color.rgb(180, 100, 220), 4)
        eucalyptusSprite = loadEntity(AssetPaths.Plants.EUCALYPTUS, Color.rgb(80, 160, 120), 4)
        orchidSprite = loadEntity(AssetPaths.Plants.VANILLA_ORCHID, Color.rgb(255, 250, 200), 4)

        willowSprite = loadTreeEntity(AssetPaths.Trees.WEEPING_WILLOW, Color.rgb(30, 100, 50))
        jacarandaSprite = loadTreeEntity(AssetPaths.Trees.JACARANDA, Color.rgb(150, 80, 200))
        bambooSprite = loadTreeEntity(AssetPaths.Trees.BAMBOO, Color.rgb(60, 200, 60))
        cherryBlossomSprite = loadTreeEntity(AssetPaths.Trees.CHERRY_BLOSSOM, Color.rgb(255, 180, 200))

        duckSprite = loadEntity(AssetPaths.Birds.DUCK, Color.rgb(200, 200, 50), 4)
        duckFlying = loadEntity(AssetPaths.Birds.DUCK_FLYING, Color.rgb(200, 200, 50), 4)
        titSprite = loadEntity(AssetPaths.Birds.TIT, Color.rgb(100, 180, 220), 4)
        titFlying = loadEntity(AssetPaths.Birds.TIT_FLYING, Color.rgb(100, 180, 220), 4)
        chickadeeSprite = loadEntity(AssetPaths.Birds.CHICKADEE, Color.rgb(180, 140, 100), 4)
        chickadeeFlying = loadEntity(AssetPaths.Birds.CHICKADEE_FLYING, Color.rgb(180, 140, 100), 4)
        owlSprite = loadEntity(AssetPaths.Birds.OWL, Color.rgb(100, 80, 60), 4)
        owlFlying = loadEntity(AssetPaths.Birds.OWL_FLYING, Color.rgb(100, 80, 60), 4)
        eagleSprite = loadEntity(AssetPaths.Birds.EAGLE, Color.rgb(160, 120, 60), 4)
        eagleFlying = loadEntity(AssetPaths.Birds.EAGLE_FLYING, Color.rgb(160, 120, 60), 4)

        catSprite = loadEntity(AssetPaths.Animals.CAT, Color.rgb(220, 190, 160), 4)
        wolfSprite = loadEntity(AssetPaths.Animals.WOLF, Color.rgb(100, 100, 120), 8)
        foxSprite = loadEntity(AssetPaths.Animals.FOX, Color.rgb(220, 120, 60), 4)
        hedgehogSprite = loadEntity(AssetPaths.Animals.HEDGEHOG, Color.rgb(120, 100, 80), 4)
        dogSprite = loadEntity(AssetPaths.Animals.DOG, Color.rgb(200, 170, 130), 4)
    }

    /**
     * Debug builds may substitute an unmistakable generated strip so content
     * iteration can continue. Release builds fail fast: shipping a placeholder
     * is a release defect, not graceful degradation.
     */
    private fun loadValidated(
        assetPath: String,
        fallbackColour: Int,
        frameWidth: Int,
        frameHeight: Int,
        frames: Int
    ): Bitmap {
        return try {
            val decoded = assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)
                    ?: throw IllegalArgumentException("BitmapFactory returned null")
            }
            validateDimensions(decoded, assetPath, frameWidth, frameHeight, frames)
            sanitizeBitmap(decoded, assetPath)
        } catch (error: Exception) {
            if (!BuildConfig.DEBUG) {
                throw IllegalStateException(
                    "Required release sprite is missing, corrupt, or malformed: $assetPath",
                    error
                )
            }
            Log.e(
                TAG,
                "Using DEBUG placeholder for $assetPath: ${error.message}",
                error
            )
            BitmapHelper.buildPlaceholderStrip(
                frameWidth,
                frameHeight,
                frames,
                fallbackColour
            )
        }
    }

    private fun validateDimensions(
        bitmap: Bitmap,
        assetPath: String,
        frameWidth: Int,
        frameHeight: Int,
        frames: Int
    ) {
        require(frameWidth > 0 && frameHeight > 0 && frames > 0) {
            "Invalid expected sprite geometry for $assetPath"
        }
        val expectedWidth = frameWidth * frames
        require(bitmap.width == expectedWidth && bitmap.height == frameHeight) {
            "$assetPath is ${bitmap.width}x${bitmap.height}; expected " +
                "${expectedWidth}x$frameHeight ($frames frames of ${frameWidth}x$frameHeight)"
        }
    }

    /** Remove edge-connected opaque source-sheet backgrounds from bird strips. */
    private fun sanitizeBitmap(bitmap: Bitmap, assetPath: String): Bitmap {
        if (!assetPath.startsWith("sprites/birds/")) return bitmap
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            bitmap.config == Bitmap.Config.HARDWARE
        ) {
            return sanitizeBitmap(bitmap.copy(Bitmap.Config.ARGB_8888, false), assetPath)
        }

        val working = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = working.width
        val height = working.height
        val pixels = IntArray(width * height)
        working.getPixels(pixels, 0, width, 0, 0, width, height)
        val frameCount = 4
        if (width % frameCount != 0) return working
        val frameWidth = width / frameCount

        fun clearFrameBackground(frameIndex: Int) {
            val frameLeft = frameIndex * frameWidth
            val frameRight = frameLeft + frameWidth
            val seedColor = pixels[frameLeft]
            if (Color.alpha(seedColor) < 250) return

            fun withinTolerance(color: Int): Boolean {
                if (Color.alpha(color) < 250) return false
                return abs(Color.red(color) - Color.red(seedColor)) <= 140 &&
                    abs(Color.green(color) - Color.green(seedColor)) <= 140 &&
                    abs(Color.blue(color) - Color.blue(seedColor)) <= 140
            }

            val queue = IntArray(frameWidth * height)
            var head = 0
            var tail = 0

            fun enqueue(px: Int, py: Int) {
                if (px !in frameLeft until frameRight || py !in 0 until height) return
                val index = py * width + px
                if (!withinTolerance(pixels[index])) return
                pixels[index] = Color.TRANSPARENT
                queue[tail++] = index
            }

            for (px in frameLeft until frameRight) {
                enqueue(px, 0)
                enqueue(px, height - 1)
            }
            for (py in 0 until height) {
                enqueue(frameLeft, py)
                enqueue(frameRight - 1, py)
            }

            while (head < tail) {
                val index = queue[head++]
                val px = index % width
                val py = index / width
                enqueue(px - 1, py)
                enqueue(px + 1, py)
                enqueue(px, py - 1)
                enqueue(px, py + 1)
            }
        }

        repeat(frameCount, ::clearFrameBackground)
        working.setPixels(pixels, 0, width, 0, 0, width, height)
        return working
    }

    private fun loadEntity(
        assetPath: String,
        fallbackColour: Int,
        frames: Int = 4
    ): SpriteSheet {
        val bitmap = loadValidated(assetPath, fallbackColour, 64, 64, frames)
        return SpriteSheet(
            bitmap,
            frameCount = frames,
            framesPerSec = 8f,
            isLooping = true
        )
    }

    private fun loadTreeEntity(
        assetPath: String,
        fallbackColour: Int,
        frames: Int = 4
    ): SpriteSheet {
        val bitmap = loadValidated(assetPath, fallbackColour, 64, 128, frames)
        return SpriteSheet(
            bitmap,
            frameCount = frames,
            framesPerSec = 6f,
            isLooping = true
        )
    }
}
