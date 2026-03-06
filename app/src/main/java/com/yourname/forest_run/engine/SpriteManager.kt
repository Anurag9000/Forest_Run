package com.yourname.forest_run.engine

import android.content.Context
import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.graphics.Color
import com.yourname.forest_run.utils.BitmapHelper

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

    private val assets: AssetManager = context.assets

    // -----------------------------------------------------------------------
    // Player Animations  (48-frame strips from real PNGs)
    // -----------------------------------------------------------------------
    val playerRun: SpriteSheet
    val playerJumpStart: SpriteSheet
    val playerJumping: SpriteSheet
    val playerApex: SpriteSheet
    val playerFalling: SpriteSheet
    val playerLanding: SpriteSheet
    val playerDuck: SpriteSheet

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
    // Bird Sprites  (4-frame fly loops)
    // -----------------------------------------------------------------------
    val duckSprite: SpriteSheet
    val titSprite: SpriteSheet
    val chickadeeSprite: SpriteSheet
    val owlSprite: SpriteSheet
    val eagleSprite: SpriteSheet

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
        // The 48-frame jump and duck sheets are split into logical segments
        // for each animation state.
        val frameW = 72
        val frameH = 100

        val runBmp = loadOrFallback("sprites/char/runner_girl_technical_48frame.png",
            Color.rgb(70, 160, 255), frameW, frameH, 48)
        playerRun       = SpriteSheet(runBmp, frameCount = 48, framesPerSec = 24f, isLooping = true)

        val jumpBmp = loadOrFallback("sprites/char/runner_girl_jump_48frame.png",
            Color.rgb(255, 220, 60), frameW, frameH, 48)
        // Slice the jump strip logically:
        playerJumpStart = SpriteSheet(jumpBmp, frameCount = 2,  framesPerSec = 20f, isLooping = false)
        playerJumping   = SpriteSheet(jumpBmp, frameCount = 12, framesPerSec = 15f, isLooping = true,  startFrame = 2)
        playerApex      = SpriteSheet(jumpBmp, frameCount = 4,  framesPerSec = 8f,  isLooping = true,  startFrame = 14)
        playerFalling   = SpriteSheet(jumpBmp, frameCount = 6,  framesPerSec = 12f, isLooping = true,  startFrame = 18)
        playerLanding   = SpriteSheet(jumpBmp, frameCount = 4,  framesPerSec = 25f, isLooping = false, startFrame = 24)

        val duckBmp = loadOrFallback("sprites/char/runner_girl_duck_48frame.png",
            Color.rgb(80, 220, 180), frameW, frameH, 48)
        playerDuck      = SpriteSheet(duckBmp, frameCount = 8, framesPerSec = 12f, isLooping = true)

        // ---- Flora ---------------------------------------------------------
        cactusSprite    = loadEntity("sprites/plants/cactus_4frames.png",       Color.rgb(30,140,50),   4)
        lilySprite      = loadEntity("sprites/plants/lily_of_valley_4frames.png",Color.WHITE,           4)
        hyacinthSprite  = loadEntity("sprites/plants/hyacinth_4frames.png",     Color.rgb(180,100,220), 4)
        eucalyptusSprite= loadEntity("sprites/plants/eucalyptus_4frames.png",   Color.rgb(80,160,120),  4)
        orchidSprite    = loadEntity("sprites/plants/vanilla_orchid_4frames.png",Color.rgb(255,250,200), 4)

        // ---- Trees --------------------------------------------------------- (64×128 frames for tall silhouettes)
        willowSprite        = loadTreeEntity("sprites/trees/weeping_willow_4frames.png", Color.rgb(30,100,50))
        jacarandaSprite     = loadTreeEntity("sprites/trees/jacaranda_4frames.png",      Color.rgb(150,80,200))
        bambooSprite        = loadTreeEntity("sprites/trees/bamboo_4frames.png",         Color.rgb(60,200,60))
        cherryBlossomSprite = loadTreeEntity("sprites/trees/cherry_blossom_4frames.png", Color.rgb(255,180,200))

        // ---- Birds ---------------------------------------------------------
        duckSprite      = loadEntity("sprites/birds/duck_4frames.png",      Color.rgb(200,200,50),  4)
        titSprite       = loadEntity("sprites/birds/tit_4frames.png",       Color.rgb(100,180,220), 4)
        chickadeeSprite = loadEntity("sprites/birds/chickadee_4frames.png", Color.rgb(180,140,100), 4)
        owlSprite       = loadEntity("sprites/birds/owl_4frames.png",       Color.rgb(100,80,60),   4)
        eagleSprite     = loadEntity("sprites/birds/eagle_4frames.png",     Color.rgb(160,120,60),  4)

        // ---- Animals -------------------------------------------------------
        catSprite      = loadEntity("sprites/animals/cat_4frames.png",     Color.rgb(220,190,160), 4)
        wolfSprite     = loadEntity("sprites/animals/wolf_4frames.png",    Color.rgb(100,100,120), 4)
        foxSprite      = loadEntity("sprites/animals/fox_4frames.png",     Color.rgb(220,120,60),  4)
        hedgehogSprite = loadEntity("sprites/animals/hedgehog_4frames.png",Color.rgb(120,100,80),  4)
        dogSprite      = loadEntity("sprites/animals/dog_4frames.png",     Color.rgb(200,170,130), 4)
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
                    ?: BitmapHelper.buildPlaceholderStrip(frameW, frameH, frames, fallbackColour)
            }
        } catch (e: Exception) {
            BitmapHelper.buildPlaceholderStrip(frameW, frameH, frames, fallbackColour)
        }
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
