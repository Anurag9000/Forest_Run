package com.yourname.forest_run.engine

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.yourname.forest_run.utils.BitmapHelper
import kotlin.math.abs

/**
 * Loads, creates, and caches all SpriteSheets in the game.
 *
 * Sprite assets live in:  app/src/main/assets/sprites/<category>/<name>.png
 *
 * Player sheets live in:  sprites/char/
 * Flora sheets live in:   sprites/plants/
 * Tree  sheets live in:   sprites/trees/
 * Birds sheets live in:   sprites/birds/
 * Animal sheets live in:  sprites/animals/
 */
class SpriteManager(private val context: Context) {

    companion object {
        private const val PLAYER_RUN_FRAMES = 48
        private const val PLAYER_JUMP_STRIP_FRAMES = 48
        private const val PLAYER_DUCK_FRAMES = 48
        private const val PLAYER_HIT_FRAMES = 12
        private const val PLAYER_DEATH_FRAMES = 12
    }

    private val assets: AssetManager = context.assets

    // -----------------------------------------------------------------------
    // Player Animations
    // -----------------------------------------------------------------------
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

    // -----------------------------------------------------------------------
    // Flora / Plant Sprites  (4-frame idle animations)
    // -----------------------------------------------------------------------
    val cactusSprite: SpriteSheet
    val lilySprite: SpriteSheet
    val hyacinthSprite: SpriteSheet
    val eucalyptusSprite: SpriteSheet
    val orchidSprite: SpriteSheet

    // -----------------------------------------------------------------------
    // Tree Sprites  (4-frame idle animations)
    // -----------------------------------------------------------------------
    val willowSprite: SpriteSheet
    val jacarandaSprite: SpriteSheet
    val bambooSprite: SpriteSheet
    val cherryBlossomSprite: SpriteSheet

    // -----------------------------------------------------------------------
    // Bird Sprites  (4-frame idle AND 4-frame flying loops)
    // -----------------------------------------------------------------------
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

    // -----------------------------------------------------------------------
    // Animal Sprites  (4-frame idle / walk loops)
    // -----------------------------------------------------------------------
    val catSprite: SpriteSheet
    val wolfSprite: SpriteSheet
    val foxSprite: SpriteSheet
    val hedgehogSprite: SpriteSheet
    val dogSprite: SpriteSheet

    init {
        // ---- Player --------------------------------------------------------
        // Player character strips are loaded as packed horizontal sheets.
        val frameW = 72
        val frameH = 100

        val runBmp = loadOrFallback(AssetPaths.Char.RUN,
            Color.rgb(70, 160, 255), frameW, frameH, PLAYER_RUN_FRAMES)
        playerRun       = SpriteSheet(runBmp, frameCount = PLAYER_RUN_FRAMES, framesPerSec = 24f, isLooping = true)

        val jumpBmp = loadOrFallback(AssetPaths.Char.JUMP,
            Color.rgb(255, 220, 60), frameW, frameH, PLAYER_JUMP_STRIP_FRAMES)
        // Slice the jump strip logically:
        playerJumpStart = SpriteSheet(jumpBmp, frameCount = 2,  framesPerSec = 20f, isLooping = false, totalFramesInBitmap = PLAYER_JUMP_STRIP_FRAMES)
        playerJumping   = SpriteSheet(jumpBmp, frameCount = 12, framesPerSec = 15f, isLooping = true,  startFrame = 2, totalFramesInBitmap = PLAYER_JUMP_STRIP_FRAMES)
        playerApex      = SpriteSheet(jumpBmp, frameCount = 4,  framesPerSec = 8f,  isLooping = true,  startFrame = 14, totalFramesInBitmap = PLAYER_JUMP_STRIP_FRAMES)
        playerFalling   = SpriteSheet(jumpBmp, frameCount = 6,  framesPerSec = 12f, isLooping = true,  startFrame = 18, totalFramesInBitmap = PLAYER_JUMP_STRIP_FRAMES)
        playerLanding   = SpriteSheet(jumpBmp, frameCount = 4,  framesPerSec = 25f, isLooping = false, startFrame = 24, totalFramesInBitmap = PLAYER_JUMP_STRIP_FRAMES)
        playerStandUp   = SpriteSheet(jumpBmp, frameCount = 18, framesPerSec = 12f, isLooping = false, totalFramesInBitmap = PLAYER_JUMP_STRIP_FRAMES)

        val duckBmp = loadOrFallback(AssetPaths.Char.DUCK,
            Color.rgb(80, 220, 180), frameW, frameH, PLAYER_DUCK_FRAMES)
        playerDuck      = SpriteSheet(duckBmp, frameCount = PLAYER_DUCK_FRAMES, framesPerSec = 12f, isLooping = true)

        val hitBmp = loadOrFallback(AssetPaths.Char.HIT,
            Color.rgb(220, 100, 100), frameW, frameH, PLAYER_HIT_FRAMES)
        playerHit       = SpriteSheet(hitBmp, frameCount = PLAYER_HIT_FRAMES, framesPerSec = 15f, isLooping = false)

        val deathBmp = loadOrFallback(AssetPaths.Char.DEATH,
            Color.rgb(100, 100, 100), frameW, frameH, PLAYER_DEATH_FRAMES)
        playerDeath     = SpriteSheet(deathBmp, frameCount = PLAYER_DEATH_FRAMES, framesPerSec = 12f, isLooping = false)

        // ---- Flora ---------------------------------------------------------
        cactusSprite    = loadEntity(AssetPaths.Plants.CACTUS, Color.rgb(30,140,50), 4)
        lilySprite      = loadEntity(AssetPaths.Plants.LILY_OF_VALLEY, Color.WHITE, 4)
        hyacinthSprite  = loadEntity(AssetPaths.Plants.HYACINTH, Color.rgb(180,100,220), 4)
        eucalyptusSprite= loadEntity(AssetPaths.Plants.EUCALYPTUS, Color.rgb(80,160,120), 4)
        orchidSprite    = loadEntity(AssetPaths.Plants.VANILLA_ORCHID, Color.rgb(255,250,200), 4)

        // ---- Trees --------------------------------------------------------- (64×128 frames for tall silhouettes)
        willowSprite = loadTreeEntity(AssetPaths.Trees.WEEPING_WILLOW, Color.rgb(30,100,50))
        jacarandaSprite = loadTreeEntity(AssetPaths.Trees.JACARANDA, Color.rgb(150,80,200))
        bambooSprite = loadTreeEntity(AssetPaths.Trees.BAMBOO, Color.rgb(60,200,60))
        cherryBlossomSprite = loadTreeEntity(AssetPaths.Trees.CHERRY_BLOSSOM, Color.rgb(255,180,200))

        // ---- Birds ---------------------------------------------------------
        duckSprite      = loadEntity(AssetPaths.Birds.DUCK, Color.rgb(200,200,50), 4)
        duckFlying      = loadEntity(AssetPaths.Birds.DUCK_FLYING, Color.rgb(200,200,50), 4)
        
        titSprite       = loadEntity(AssetPaths.Birds.TIT, Color.rgb(100,180,220), 4)
        titFlying       = loadEntity(AssetPaths.Birds.TIT_FLYING, Color.rgb(100,180,220), 4)
        
        chickadeeSprite = loadEntity(AssetPaths.Birds.CHICKADEE, Color.rgb(180,140,100), 4)
        chickadeeFlying = loadEntity(AssetPaths.Birds.CHICKADEE_FLYING, Color.rgb(180,140,100), 4)
        
        owlSprite       = loadEntity(AssetPaths.Birds.OWL, Color.rgb(100,80,60), 4)
        owlFlying       = loadEntity(AssetPaths.Birds.OWL_FLYING, Color.rgb(100,80,60), 4)
        
        eagleSprite     = loadEntity(AssetPaths.Birds.EAGLE, Color.rgb(160,120,60), 4)
        eagleFlying     = loadEntity(AssetPaths.Birds.EAGLE_FLYING, Color.rgb(160,120,60), 4)

        // ---- Animals -------------------------------------------------------
        catSprite      = loadEntity(AssetPaths.Animals.CAT, Color.rgb(220,190,160), 4)
        wolfSprite     = loadEntity(AssetPaths.Animals.WOLF, Color.rgb(100,100,120), 8)
        foxSprite      = loadEntity(AssetPaths.Animals.FOX, Color.rgb(220,120,60), 4)
        hedgehogSprite = loadEntity(AssetPaths.Animals.HEDGEHOG, Color.rgb(120,100,80), 4)
        dogSprite      = loadEntity(AssetPaths.Animals.DOG, Color.rgb(200,170,130), 4)
    }

    /**
     * Attempt to decode [assetPath] from assets. If the file is missing or
     * corrupt, generate a coloured placeholder strip so the game never crashes.
     */
    private fun loadOrFallback(
        assetPath: String,
        fallbackColour: Int,
        frameW: Int, frameH: Int, frames: Int
    ): android.graphics.Bitmap {
        return try {
            assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)
                    ?.let { sanitizeBitmap(it, assetPath) }
                    ?: BitmapHelper.buildPlaceholderStrip(frameW, frameH, frames, fallbackColour)
            }
        } catch (e: Exception) {
            BitmapHelper.buildPlaceholderStrip(frameW, frameH, frames, fallbackColour)
        }
    }

    /**
     * Some checked-in bird sheets still contain opaque source-sheet backgrounds.
     * Strip edge-connected light-grey backgrounds so gameplay renders the sprite only.
     */
    private fun sanitizeBitmap(bitmap: Bitmap, assetPath: String): Bitmap {
        if (!assetPath.startsWith("sprites/birds/")) return bitmap
        if (bitmap.config == Bitmap.Config.HARDWARE) return bitmap.copy(Bitmap.Config.ARGB_8888, false)

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

            fun enqueue(x: Int, y: Int) {
                if (x !in frameLeft until frameRight || y !in 0 until height) return
                val index = y * width + x
                if (!withinTolerance(pixels[index])) return
                pixels[index] = Color.TRANSPARENT
                queue[tail++] = index
            }

            for (x in frameLeft until frameRight) {
                enqueue(x, 0)
                enqueue(x, height - 1)
            }
            for (y in 0 until height) {
                enqueue(frameLeft, y)
                enqueue(frameRight - 1, y)
            }

            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                enqueue(x - 1, y)
                enqueue(x + 1, y)
                enqueue(x, y - 1)
                enqueue(x, y + 1)
            }
        }

        repeat(frameCount, ::clearFrameBackground)
        working.setPixels(pixels, 0, width, 0, 0, width, height)
        return working
    }

    /** Loads a 4-frame entity sprite (64×64 frames) with fallback. */
    private fun loadEntity(assetPath: String, fallbackColour: Int, frames: Int = 4): SpriteSheet {
        val bmp = loadOrFallback(assetPath, fallbackColour, 64, 64, frames)
        return SpriteSheet(bmp, frameCount = frames, framesPerSec = 8f, isLooping = true)
    }

    /** Loads a 4-frame TREE entity sprite (64×128 frames — taller ratio) with fallback. */
    private fun loadTreeEntity(assetPath: String, fallbackColour: Int, frames: Int = 4): SpriteSheet {
        val bmp = loadOrFallback(assetPath, fallbackColour, 64, 128, frames)
        return SpriteSheet(bmp, frameCount = frames, framesPerSec = 6f, isLooping = true)
    }
}
