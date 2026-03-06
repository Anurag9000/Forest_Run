package com.yourname.forest_run.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.yourname.forest_run.engine.GameConstants
import com.yourname.forest_run.engine.SaveManager
import com.yourname.forest_run.systems.FxPreset
import com.yourname.forest_run.systems.ParticleManager
import kotlin.math.sin

/**
 * Garden Meta-Loop Screen — Phase 23.
 *
 * Shows all 9 unlockable plants in a scrollable garden row. The player spends
 * lifetime seeds (accumulated across runs) to unlock new plants.
 *
 * Layout:
 *   - Top bar: 🌱 seed count + "GARDEN" title
 *   - Main area: horizontal row of 9 plant cards
 *       · Unlocked: full colour, name shown
 *       · Next unlockable: greyed 30%, cost shown, tap to unlock
 *       · Locked (beyond next): dark silhouette + "???"
 *   - Bottom: "back" hint
 *
 * After unlock: bloom burst particle effect fires at the card centre.
 * SaveManager persists which plants are unlocked + remaining lifetime seeds.
 */
class GardenScreen(
    private val context: Context,
    private val screenW: Int,
    private val screenH: Int
) {
    // ── Plant catalogue ───────────────────────────────────────────────────

    data class GardenPlant(
        val name: String,
        val seedCost: Int,
        val colour: Int,       // primary display colour
        val emoji: String      // unicode plant char rendered large
    )

    private val catalogue = listOf(
        GardenPlant("Lily",       15, Color.rgb(220, 240, 180), "🌸"),
        GardenPlant("Cactus",     20, Color.rgb(100, 200,  80), "🌵"),
        GardenPlant("Hyacinth",   25, Color.rgb(180, 100, 220), "💜"),
        GardenPlant("Eucalyptus", 30, Color.rgb( 80, 180, 120), "🍃"),
        GardenPlant("Orchid",     40, Color.rgb(255, 200, 220), "🌺"),
        GardenPlant("Willow",     50, Color.rgb( 60, 160,  60), "🌿"),
        GardenPlant("Jacaranda",  60, Color.rgb(160, 100, 240), "🌲"),
        GardenPlant("Bamboo",     75, Color.rgb(120, 200,  80), "🎋"),
        GardenPlant("Cherry",    100, Color.rgb(255, 150, 180), "🌸")
    )

    // ── State ─────────────────────────────────────────────────────────────

    /** How many plants are currently unlocked (left-to-right). */
    private var unlockedCount: Int = 1   // first plant always unlocked

    /** Lifetime seeds available to spend. */
    private var lifeSeeds: Int = 0

    /** Callback — called when player taps the back button area. */
    var onBack: (() -> Unit)? = null

    // Unlock animation
    private var unlockAnim: Float = -1f   // -1 = none; 0..1 = progress
    private var unlockIdx:  Int   = -1

    private var elapsed = 0f

    // ── Font ─────────────────────────────────────────────────────────────
    private val pixelFont: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, "fonts/PressStart2P-Regular.ttf")
    }.getOrDefault(Typeface.MONOSPACE)

    // ── Layout ────────────────────────────────────────────────────────────

    private val CARD_W     = screenW / 10.5f
    private val CARD_H     = screenH * 0.55f
    private val CARD_GAP   = CARD_W * 0.12f
    private val ROW_START_X = (screenW - (catalogue.size * (CARD_W + CARD_GAP) - CARD_GAP)) / 2f
    private val ROW_Y       = screenH * 0.20f
    private val cardRect    = RectF()

    // ── Paints ────────────────────────────────────────────────────────────

    private val skyPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, screenH.toFloat(),
            intArrayOf(Color.rgb(60, 140, 230), Color.rgb(180, 230, 160)),
            null, Shader.TileMode.CLAMP
        )
    }
    private val groundPaint = Paint().apply { color = Color.rgb(80, 160, 70) }

    private val cardUnlockPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cardNextPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 200, 220, 200); style = Paint.Style.FILL
    }
    private val cardLockedPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 40, 40, 40); style = Paint.Style.FILL
    }
    private val cardBorderPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(160, 100, 200, 100)
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = 48f; textAlign = Paint.Align.CENTER
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 14f; typeface = pixelFont; textAlign = Paint.Align.CENTER
    }
    private val costPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 220, 60); textSize = 14f; typeface = pixelFont; textAlign = Paint.Align.CENTER
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 240, 100); textSize = 28f; typeface = pixelFont; textAlign = Paint.Align.CENTER
    }
    private val seedCountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(160, 255, 120); textSize = 22f; typeface = pixelFont; textAlign = Paint.Align.LEFT
    }
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 220, 220, 220); textSize = 16f; typeface = pixelFont; textAlign = Paint.Align.CENTER
    }

    // ── API ───────────────────────────────────────────────────────────────

    /** Load persisted garden state from SaveManager. */
    fun load() {
        unlockedCount = SaveManager.loadGardenProgress(context).coerceAtLeast(1)
        lifeSeeds     = SaveManager.loadLifetimeSeeds(context)
    }

    /** Called after a run to refresh the seed count. */
    fun refresh() {
        lifeSeeds = SaveManager.loadLifetimeSeeds(context)
    }

    fun update(deltaTime: Float) {
        elapsed += deltaTime
        if (unlockAnim >= 0f) {
            unlockAnim = (unlockAnim + deltaTime * 1.5f).coerceAtMost(1f)
            if (unlockAnim >= 1f) unlockAnim = -1f
        }
    }

    /**
     * Handle a tap at screen position ([tapX], [tapY]).
     * Returns true if the tap was consumed.
     */
    fun onTap(tapX: Float, tapY: Float): Boolean {
        // Back area — bottom strip
        if (tapY > screenH * 0.88f) { onBack?.invoke(); return true }

        // Card taps
        for (i in catalogue.indices) {
            val cx = ROW_START_X + i * (CARD_W + CARD_GAP) + CARD_W / 2f
            val cy = ROW_Y + CARD_H / 2f
            if (tapX in cx - CARD_W / 2f..cx + CARD_W / 2f &&
                tapY in cy - CARD_H / 2f..cy + CARD_H / 2f) {
                if (i == unlockedCount && lifeSeeds >= catalogue[i].seedCost) {
                    // Unlock!
                    lifeSeeds     -= catalogue[i].seedCost
                    unlockedCount++
                    unlockIdx  = i
                    unlockAnim = 0f
                    // Bloom burst (using SEED_COLLECT preset for a nice golden unlock pop)
                    ParticleManager.emit(FxPreset.SEED_COLLECT, cx, cy)
                    // Persist
                    SaveManager.saveGardenProgress(context, unlockedCount)
                    SaveManager.saveLifetimeSeeds(context, lifeSeeds)
                }
                return true
            }
        }
        return false
    }

    fun draw(canvas: Canvas) {
        val cw = screenW.toFloat()
        val ch = screenH.toFloat()
        val groundY = ch * 0.82f

        canvas.drawRect(0f, 0f, cw, ch, skyPaint)
        canvas.drawRect(0f, groundY, cw, ch, groundPaint)

        // Title
        canvas.drawText("GARDEN", cw / 2f, ch * 0.10f, titlePaint)

        // Seed count
        canvas.drawText("🌱 $lifeSeeds", 28f, ch * 0.10f, seedCountPaint)

        // Cards
        for (i in catalogue.indices) {
            val left = ROW_START_X + i * (CARD_W + CARD_GAP)
            val top  = ROW_Y
            cardRect.set(left, top, left + CARD_W, top + CARD_H)

            val isUnlocked = i < unlockedCount
            val isNext     = i == unlockedCount
            val isAnimatingThis = unlockIdx == i && unlockAnim in 0f..1f

            // Card background
            when {
                isUnlocked -> {
                    val col = catalogue[i].colour
                    cardUnlockPaint.color = Color.argb(
                        200,
                        Color.red(col), Color.green(col), Color.blue(col)
                    )
                    // Bounce pop effect during unlock animation
                    if (isAnimatingThis) {
                        val scale = 1f + 0.25f * sin(unlockAnim * Math.PI.toFloat())
                        canvas.save()
                        canvas.scale(scale, scale, cardRect.centerX(), cardRect.centerY())
                    }
                    canvas.drawRoundRect(cardRect, 12f, 12f, cardUnlockPaint)
                }
                isNext -> {
                    canvas.drawRoundRect(cardRect, 12f, 12f, cardNextPaint)
                }
                else -> {
                    canvas.drawRoundRect(cardRect, 12f, 12f, cardLockedPaint)
                }
            }
            canvas.drawRoundRect(cardRect, 12f, 12f, cardBorderPaint)

            // Emoji
            val emojiFade = if (isUnlocked) 255 else if (isNext) 160 else 60
            emojiPaint.alpha = emojiFade
            canvas.drawText(catalogue[i].emoji, cardRect.centerX(), cardRect.centerY() - 10f, emojiPaint)

            // Name / cost
            when {
                isUnlocked -> {
                    namePaint.alpha = 220
                    canvas.drawText(catalogue[i].name, cardRect.centerX(), cardRect.bottom - 22f, namePaint)
                }
                isNext -> {
                    val pulse = (0.6f + 0.4f * sin(elapsed * 2.5f)) * 255
                    costPaint.alpha = pulse.toInt()
                    canvas.drawText("🌱${catalogue[i].seedCost}", cardRect.centerX(), cardRect.bottom - 36f, costPaint)
                    namePaint.alpha = 180
                    canvas.drawText(catalogue[i].name, cardRect.centerX(), cardRect.bottom - 18f, namePaint)
                }
                else -> {
                    namePaint.alpha = 60
                    canvas.drawText("???", cardRect.centerX(), cardRect.bottom - 18f, namePaint)
                }
            }

            if (isAnimatingThis) canvas.restore()
        }

        // Back hint
        val backAlpha = (0.5f + 0.5f * sin(elapsed * 2f)) * 200
        backPaint.alpha = backAlpha.toInt()
        canvas.drawText("tap anywhere to go back", cw / 2f, ch * 0.93f, backPaint)

        // Particle layer
        ParticleManager.draw(canvas)
    }
}
