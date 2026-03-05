package com.yourname.forest_run.engine

import android.content.Context
import android.graphics.Color
import com.yourname.forest_run.utils.BitmapHelper

/**
 * Loads, creates, and caches all SpriteSheets in the game.
 * Ensures we don't hold multiple copies of the exact same Bitmap in memory.
 *
 * Phase 6: Generates placeholder strips programmatically.
 * Phase 24: Replaces generation with BitmapFactory.decodeResource() for actual assets.
 */
class SpriteManager(context: Context) {

    // -----------------------------------------------------------------------
    // Player Animations
    // -----------------------------------------------------------------------
    val playerRun: SpriteSheet
    val playerJumpStart: SpriteSheet
    val playerJumping: SpriteSheet
    val playerApex: SpriteSheet
    val playerFalling: SpriteSheet
    val playerLanding: SpriteSheet
    val playerDuck: SpriteSheet

    init {
        // Pixel dimensions match Player BASE_WIDTH and BASE_HEIGHT
        val frameW = 72
        val frameH = 100

        // RUNNING - 8 frames, 12 FPS, blue placeholder
        playerRun = SpriteSheet(
            BitmapHelper.buildPlaceholderStrip(frameW, frameH, 8, Color.rgb(70, 160, 255)),
            frameCount = 8, framesPerSec = 12f, isLooping = true
        )

        // JUMP_START - 2 frames, fast squash, stops on last frame. Yellow.
        playerJumpStart = SpriteSheet(
            BitmapHelper.buildPlaceholderStrip(frameW, frameH, 2, Color.rgb(255, 230, 50)),
            frameCount = 2, framesPerSec = 20f, isLooping = false
        )

        // JUMPING - 2 frame cycle. Bright yellow.
        playerJumping = SpriteSheet(
            BitmapHelper.buildPlaceholderStrip(frameW, frameH, 2, Color.rgb(255, 255, 120)),
            frameCount = 2, framesPerSec = 10f, isLooping = true
        )

        // APEX - 1 frame float. Lavender.
        playerApex = SpriteSheet(
            BitmapHelper.buildPlaceholderStrip(frameW, frameH, 1, Color.rgb(255, 200, 255)),
            frameCount = 1, framesPerSec = 0f, isLooping = true
        )

        // FALLING - 2 frame cycle. Orange.
        playerFalling = SpriteSheet(
            BitmapHelper.buildPlaceholderStrip(frameW, frameH, 2, Color.rgb(255, 160, 60)),
            frameCount = 2, framesPerSec = 10f, isLooping = true
        )

        // LANDING - 3 frame smash. Red-orange.
        playerLanding = SpriteSheet(
            BitmapHelper.buildPlaceholderStrip(frameW, frameH, 3, Color.rgb(255, 80, 80)),
            frameCount = 3, framesPerSec = 25f, isLooping = false
        )

        // DUCKING - 4 frame slide. Teal.
        playerDuck = SpriteSheet(
            BitmapHelper.buildPlaceholderStrip(frameW, frameH, 4, Color.rgb(80, 220, 180)),
            frameCount = 4, framesPerSec = 10f, isLooping = true
        )
    }
}
