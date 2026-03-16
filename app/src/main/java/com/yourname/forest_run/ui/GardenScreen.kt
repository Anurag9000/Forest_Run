package com.yourname.forest_run.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.yourname.forest_run.engine.AssetPaths
import com.yourname.forest_run.engine.CostumeManager
import com.yourname.forest_run.engine.GardenSanctuaryPlanner
import com.yourname.forest_run.engine.GardenSanctuaryState
import com.yourname.forest_run.engine.GameConstants
import com.yourname.forest_run.engine.PersistentMemoryManager
import com.yourname.forest_run.engine.RelationshipArcSystem
import com.yourname.forest_run.engine.RunSummary
import com.yourname.forest_run.engine.SaveManager
import com.yourname.forest_run.engine.StoryFragmentSystem
import com.yourname.forest_run.engine.SpriteManager
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import com.yourname.forest_run.engine.Biome
import com.yourname.forest_run.engine.ForestMoodState
import com.yourname.forest_run.engine.ForestMoodSystem
import com.yourname.forest_run.engine.ReturnMoment
import com.yourname.forest_run.engine.ReturnMomentsSystem
import com.yourname.forest_run.entities.CostumeStyle
import com.yourname.forest_run.entities.EntityType
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
    private val spriteManager: SpriteManager,
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
    private val catalogueSprites: List<SpriteSheet> = listOf(
        spriteManager.lilySprite.copy(),
        spriteManager.cactusSprite.copy(),
        spriteManager.hyacinthSprite.copy(),
        spriteManager.eucalyptusSprite.copy(),
        spriteManager.orchidSprite.copy(),
        spriteManager.willowSprite.copy(),
        spriteManager.jacarandaSprite.copy(),
        spriteManager.bambooSprite.copy(),
        spriteManager.cherryBlossomSprite.copy()
    )

    // ── State ─────────────────────────────────────────────────────────────

    /** How many plants are currently unlocked (left-to-right). */
    private var unlockedCount: Int = 1   // first plant always unlocked

    /** Lifetime seeds available to spend. */
    private var lifeSeeds: Int = 0

    /** Callback — called when player taps the back button area. */
    var onBack: (() -> Unit)? = null
    var onRun: (() -> Unit)? = null

    // Unlock animation
    private var unlockAnim: Float = -1f   // -1 = none; 0..1 = progress
    private var unlockIdx:  Int   = -1

    private var elapsed = 0f
    private var bestDistance = 0f
    private var lastKillerLabel = "None"
    private var sparedTotal = 0
    private var friendshipTotal = 0
    private var unlockedCostumes: List<CostumeStyle> = listOf(CostumeStyle.NONE)
    private var activeCostume: CostumeStyle = CostumeStyle.NONE
    private var wardrobeMessage = ""
    private var wardrobeMessageTimer = 0f
    private var lastRunSummary: RunSummary? = null
    private var forestMoodState: ForestMoodState = ForestMoodState()
    private var returnMoment: ReturnMoment? = null
    private var returnVisitorSprite: SpriteSheet? = null
    private var strongestBondLabel = "None"
    private var milestoneRewardLabel = "None"
    private var memoryPageCount = 0
    private var gardenReflectionLine = ""
    private var creatureThoughtLine = ""
    private var weatherThoughtLine = ""
    private var sanctuaryState = GardenSanctuaryState()

    // ── Font ─────────────────────────────────────────────────────────────
    private val pixelFont: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, AssetPaths.PIXEL_FONT)
    }.getOrDefault(Typeface.MONOSPACE)

    // ── Layout ────────────────────────────────────────────────────────────

    private val CARD_W     = screenW / 10.5f
    private val CARD_H     = screenH * 0.55f
    private val CARD_GAP   = CARD_W * 0.12f
    private val ROW_START_X = (screenW - (catalogue.size * (CARD_W + CARD_GAP) - CARD_GAP)) / 2f
    private val ROW_Y       = screenH * 0.20f
    private val cardRect    = RectF()
    private val spriteRect  = RectF()
    private val runButtonRect = RectF()
    private val statsRect = RectF()
    private val wardrobeRect = RectF()
    private val lastRunRect = RectF()
    private val wardrobeCardRects = MutableList(CostumeStyle.entries.size) { RectF() }

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
    private val moodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(232, 246, 212); textSize = 14f; typeface = pixelFont; textAlign = Paint.Align.CENTER
    }
    private val returnTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 233, 180); textSize = 13f; typeface = pixelFont; textAlign = Paint.Align.CENTER
    }
    private val returnLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 240, 245, 236); textSize = 12f; typeface = pixelFont; textAlign = Paint.Align.CENTER
    }
    private val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 228, 240, 228)
        textSize = 12f
        typeface = pixelFont
        textAlign = Paint.Align.LEFT
    }
    private val thoughtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 255, 240, 210)
        textSize = 12f
        typeface = pixelFont
        textAlign = Paint.Align.LEFT
    }
    private val sanctuaryLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 240, 248, 228)
        textSize = 12f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val canopyShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val traceChipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val traceChipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 245, 246, 232)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val traceChipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(32, 44, 28)
        textSize = 11f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val ambiencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val seedCountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(160, 255, 120); textSize = 22f; typeface = pixelFont; textAlign = Paint.Align.LEFT
    }
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 220, 220, 220); textSize = 16f; typeface = pixelFont; textAlign = Paint.Align.CENTER
    }
    private val statsPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(145, 20, 40, 30)
        style = Paint.Style.FILL
    }
    private val statsBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 150, 220, 160)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val statsLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 220, 240, 220)
        textSize = 14f
        typeface = pixelFont
        textAlign = Paint.Align.LEFT
    }
    private val statsValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f
        typeface = pixelFont
        textAlign = Paint.Align.LEFT
    }
    private val runButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 245, 226, 130)
        style = Paint.Style.FILL
    }
    private val runButtonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 120, 40)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val runButtonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 55, 20)
        textSize = 20f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val wardrobePanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(165, 28, 48, 38)
        style = Paint.Style.FILL
    }
    private val wardrobeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 180, 225, 175)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val wardrobeCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val wardrobeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val wardrobeHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 232, 250, 222)
        textSize = 12f
        typeface = pixelFont
        textAlign = Paint.Align.LEFT
    }

    // ── API ───────────────────────────────────────────────────────────────

    /** Load persisted garden state from SaveManager. */
    fun load() {
        unlockedCount = SaveManager.loadGardenProgress(context).coerceAtLeast(1)
        lifeSeeds     = SaveManager.loadLifetimeSeeds(context)
        syncWardrobe()
        refreshStats()
    }

    /** Called after a run to refresh the seed count. */
    fun refresh() {
        lifeSeeds = SaveManager.loadLifetimeSeeds(context)
        syncWardrobe()
        refreshStats()
    }

    fun update(deltaTime: Float) {
        elapsed += deltaTime
        catalogueSprites.forEach { it.update(deltaTime) }
        returnVisitorSprite?.update(deltaTime)
        if (unlockAnim >= 0f) {
            unlockAnim = (unlockAnim + deltaTime * 1.5f).coerceAtMost(1f)
            if (unlockAnim >= 1f) unlockAnim = -1f
        }
        if (wardrobeMessageTimer > 0f) {
            wardrobeMessageTimer = (wardrobeMessageTimer - deltaTime).coerceAtLeast(0f)
        }
    }

    /**
     * Handle a tap at screen position ([tapX], [tapY]).
     * Returns true if the tap was consumed.
     */
    fun onTap(tapX: Float, tapY: Float): Boolean {
        syncInteractiveLayout(screenW.toFloat(), screenH.toFloat())

        if (runButtonRect.contains(tapX, tapY)) {
            onRun?.invoke()
            return true
        }

        wardrobeCardRects.firstOrNull { it.contains(tapX, tapY) }?.let { tappedRect ->
            val index = wardrobeCardRects.indexOf(tappedRect)
            val style = CostumeStyle.entries[index]
            if (style == CostumeStyle.NONE || style in unlockedCostumes) {
                if (CostumeManager.equip(context, style)) {
                    activeCostume = style
                    wardrobeMessage = "${style.displayName} equipped"
                    wardrobeMessageTimer = 2.5f
                }
            } else {
                wardrobeMessage = style.unlockLabel
                wardrobeMessageTimer = 2.5f
            }
            return true
        }

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

        applyMoodPalette(ch)
        canvas.drawRect(0f, 0f, cw, ch, skyPaint)
        canvas.drawRect(0f, groundY, cw, ch, groundPaint)
        drawSanctuaryAtmosphere(canvas, cw, ch, groundY)

        // Title
        titlePaint.color = forestMoodState.currentMood.accentColor
        canvas.drawText("GARDEN", cw / 2f, ch * 0.10f, titlePaint)
        canvas.drawText(forestMoodState.currentMood.gardenLine, cw / 2f, ch * 0.13f, moodPaint)
        returnMoment?.let { moment ->
            canvas.drawText(moment.title, cw / 2f, ch * 0.16f, returnTitlePaint)
            canvas.drawText(moment.line, cw / 2f, ch * 0.185f, returnLinePaint)
            drawReturnVisitor(canvas, cw, ch)
        }
        if (sanctuaryState.sanctuaryLine.isNotBlank()) {
            canvas.drawText(sanctuaryState.sanctuaryLine, cw / 2f, ch * 0.212f, sanctuaryLinePaint)
        }
        drawSanctuaryTraces(canvas, cw, ch)

        // Seed count
        canvas.drawText("🌱 $lifeSeeds", 28f, ch * 0.10f, seedCountPaint)

        syncInteractiveLayout(cw, ch)
        drawStatsPanel(canvas, cw, ch)
        drawRunButton(canvas, cw, ch)
        drawLastRunPanel(canvas)
        drawWardrobe(canvas, cw, ch)

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

            if (isUnlocked || isNext) {
                val spriteTop = cardRect.top + CARD_H * 0.10f
                val spriteBottom = cardRect.top + CARD_H * 0.66f
                val spriteHeight = spriteBottom - spriteTop
                val spriteWidth = SpriteSizing.widthForHeight(
                    catalogueSprites[i],
                    spriteHeight,
                    minWidth = spriteHeight * if (i >= 5) 0.45f else 0.35f
                )
                spriteRect.set(
                    cardRect.centerX() - spriteWidth / 2f,
                    spriteTop,
                    cardRect.centerX() + spriteWidth / 2f,
                    spriteBottom
                )
                catalogueSprites[i].draw(canvas, spriteRect)
            } else {
                val emojiFade = 60
                emojiPaint.alpha = emojiFade
                canvas.drawText(catalogue[i].emoji, cardRect.centerX(), cardRect.centerY() - 10f, emojiPaint)
            }

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

    private fun drawStatsPanel(canvas: Canvas, cw: Float, ch: Float) {
        statsRect.set(cw * 0.05f, ch * 0.13f, cw * 0.35f, ch * 0.48f)
        canvas.drawRoundRect(statsRect, 18f, 18f, statsPanelPaint)
        canvas.drawRoundRect(statsRect, 18f, 18f, statsBorderPaint)

        var y = statsRect.top + 30f
        canvas.drawText("Best Run", statsRect.left + 18f, y, statsLabelPaint)
        canvas.drawText(formatDistance(bestDistance), statsRect.left + 18f, y + 18f, statsValuePaint)
        y += 44f
        canvas.drawText("Last Killer", statsRect.left + 18f, y, statsLabelPaint)
        canvas.drawText(lastKillerLabel, statsRect.left + 18f, y + 18f, statsValuePaint)
        y += 44f
        canvas.drawText("Spared", statsRect.left + 18f, y, statsLabelPaint)
        canvas.drawText(sparedTotal.toString(), statsRect.left + 18f, y + 18f, statsValuePaint)
        y += 44f
        canvas.drawText("Friend Biomes", statsRect.left + 18f, y, statsLabelPaint)
        canvas.drawText(friendshipTotal.toString(), statsRect.left + 18f, y + 18f, statsValuePaint)
        y += 44f
        canvas.drawText("Strongest Bond", statsRect.left + 18f, y, statsLabelPaint)
        canvas.drawText(strongestBondLabel, statsRect.left + 18f, y + 18f, statsValuePaint)
        y += 44f
        canvas.drawText("Bond Reward", statsRect.left + 18f, y, statsLabelPaint)
        canvas.drawText(milestoneRewardLabel, statsRect.left + 18f, y + 18f, statsValuePaint)
        y += 44f
        canvas.drawText("Memory Pages", statsRect.left + 18f, y, statsLabelPaint)
        canvas.drawText(memoryPageCount.toString(), statsRect.left + 18f, y + 18f, statsValuePaint)
    }

    private fun drawRunButton(canvas: Canvas, @Suppress("UNUSED_PARAMETER") cw: Float, @Suppress("UNUSED_PARAMETER") ch: Float) {
        val pulse = 0.9f + 0.1f * sin(elapsed * 2.8f)
        runButtonPaint.alpha = (225f * pulse).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(runButtonRect, 20f, 20f, runButtonPaint)
        canvas.drawRoundRect(runButtonRect, 20f, 20f, runButtonBorderPaint)
        val labelY = runButtonRect.centerY() - (runButtonTextPaint.descent() + runButtonTextPaint.ascent()) / 2f
        canvas.drawText("RUN", runButtonRect.centerX(), labelY, runButtonTextPaint)
    }

    private fun drawWardrobe(canvas: Canvas, @Suppress("UNUSED_PARAMETER") cw: Float, @Suppress("UNUSED_PARAMETER") ch: Float) {
        canvas.drawRoundRect(wardrobeRect, 18f, 18f, wardrobePanelPaint)
        canvas.drawRoundRect(wardrobeRect, 18f, 18f, wardrobeBorderPaint)
        canvas.drawText("Wardrobe", wardrobeRect.left + 20f, wardrobeRect.top + 26f, wardrobeHintPaint)

        CostumeStyle.entries.forEachIndexed { index, style ->
            val rect = wardrobeCardRects[index]
            val unlocked = style == CostumeStyle.NONE || style in unlockedCostumes
            val equipped = style == activeCostume
            wardrobeCardPaint.color = when {
                equipped -> Color.argb(235, 248, 232, 136)
                unlocked -> Color.argb(210, 106, 164, 112)
                else -> Color.argb(120, 58, 68, 64)
            }
            canvas.drawRoundRect(rect, 16f, 16f, wardrobeCardPaint)
            canvas.drawRoundRect(rect, 16f, 16f, cardBorderPaint)
            drawCostumeIcon(canvas, rect, style, unlocked)
            wardrobeTextPaint.alpha = if (unlocked) 235 else 130
            canvas.drawText(style.displayName, rect.centerX(), rect.bottom - 16f, wardrobeTextPaint)
        }

        if (wardrobeMessageTimer > 0f && wardrobeMessage.isNotBlank()) {
            wardrobeHintPaint.alpha = ((wardrobeMessageTimer / 2.5f) * 255).toInt().coerceIn(120, 255)
            canvas.drawText(wardrobeMessage, wardrobeRect.left + 20f, wardrobeRect.bottom - 12f, wardrobeHintPaint)
            wardrobeHintPaint.alpha = 210
        }
    }

    private fun drawLastRunPanel(canvas: Canvas) {
        val summary = lastRunSummary ?: return
        canvas.drawRoundRect(lastRunRect, 18f, 18f, statsPanelPaint)
        canvas.drawRoundRect(lastRunRect, 18f, 18f, statsBorderPaint)

        var y = lastRunRect.top + 28f
        canvas.drawText("Last Run", lastRunRect.left + 18f, y, wardrobeHintPaint)
        y += 26f
        canvas.drawText(
            "${formatDistance(summary.distanceM)}  •  ${summary.seedsCollected} seeds",
            lastRunRect.left + 18f,
            y,
            statsValuePaint
        )
        y += 24f

        val detailBits = buildList {
            if (summary.sparedCount > 0) add("${summary.sparedCount} spared")
            if (summary.bloomConversions > 0) add("${summary.bloomConversions} Bloom")
            if (summary.kindnessChain > 0) add("chain ${summary.kindnessChain}")
        }
        if (detailBits.isNotEmpty()) {
            canvas.drawText(detailBits.joinToString("  •  "), lastRunRect.left + 18f, y, statsLabelPaint)
            y += 22f
        }

        canvas.drawText("Tone: ${summary.forestMood.displayName}", lastRunRect.left + 18f, y, statsLabelPaint)
        y += 22f
        val killerText = summary.lastKiller?.let { formatEntityName(it) } ?: "None"
        canvas.drawText("Last killer: $killerText", lastRunRect.left + 18f, y, statsLabelPaint)
        y += 22f
        canvas.drawText(summary.restQuote.take(92), lastRunRect.left + 18f, y, wardrobeHintPaint)
        if (gardenReflectionLine.isNotBlank()) {
            y += 24f
            canvas.drawText(gardenReflectionLine.take(88), lastRunRect.left + 18f, y, reflectionPaint)
        }
        if (weatherThoughtLine.isNotBlank()) {
            y += 20f
            canvas.drawText(weatherThoughtLine.take(90), lastRunRect.left + 18f, y, reflectionPaint)
        }
        if (creatureThoughtLine.isNotBlank()) {
            y += 20f
            canvas.drawText(creatureThoughtLine.take(90), lastRunRect.left + 18f, y, thoughtPaint)
        }
        if (sanctuaryState.carryHomeLine.isNotBlank()) {
            y += 22f
            canvas.drawText(sanctuaryState.carryHomeLine.take(94), lastRunRect.left + 18f, y, thoughtPaint)
        }
    }

    private fun drawCostumeIcon(canvas: Canvas, rect: RectF, style: CostumeStyle, unlocked: Boolean) {
        val iconCenterY = rect.top + rect.height() * 0.42f
        val iconCenterX = rect.centerX()
        val alpha = if (unlocked) 255 else 110
        val accent = when (style) {
            CostumeStyle.NONE -> Color.rgb(220, 220, 220)
            CostumeStyle.FLOWER_CROWN -> Color.rgb(255, 214, 228)
            CostumeStyle.VINE_SCARF -> Color.rgb(126, 210, 120)
            CostumeStyle.MOON_CAPE -> Color.rgb(139, 150, 232)
            CostumeStyle.BLOOM_RIBBON -> Color.rgb(255, 195, 100)
        }
        wardrobeCardPaint.color = accent
        wardrobeCardPaint.alpha = alpha
        when (style) {
            CostumeStyle.NONE -> canvas.drawCircle(iconCenterX, iconCenterY, rect.width() * 0.12f, wardrobeCardPaint)
            CostumeStyle.FLOWER_CROWN -> {
                repeat(3) { index ->
                    canvas.drawCircle(
                        iconCenterX + (index - 1) * rect.width() * 0.09f,
                        iconCenterY,
                        rect.width() * 0.07f,
                        wardrobeCardPaint
                    )
                }
            }
            CostumeStyle.VINE_SCARF -> {
                canvas.drawLine(iconCenterX - rect.width() * 0.10f, iconCenterY - 6f, iconCenterX + rect.width() * 0.10f, iconCenterY + 2f, wardrobeCardPaint)
                canvas.drawLine(iconCenterX + rect.width() * 0.04f, iconCenterY, iconCenterX + rect.width() * 0.12f, iconCenterY + rect.height() * 0.12f, wardrobeCardPaint)
            }
            CostumeStyle.MOON_CAPE -> {
                canvas.drawRect(
                    iconCenterX - rect.width() * 0.12f,
                    iconCenterY - rect.height() * 0.10f,
                    iconCenterX + rect.width() * 0.12f,
                    iconCenterY + rect.height() * 0.12f,
                    wardrobeCardPaint
                )
            }
            CostumeStyle.BLOOM_RIBBON -> {
                canvas.drawCircle(iconCenterX, iconCenterY - 8f, rect.width() * 0.06f, wardrobeCardPaint)
                canvas.drawLine(iconCenterX, iconCenterY - 4f, iconCenterX - rect.width() * 0.05f, iconCenterY + rect.height() * 0.12f, wardrobeCardPaint)
                canvas.drawLine(iconCenterX, iconCenterY - 4f, iconCenterX + rect.width() * 0.05f, iconCenterY + rect.height() * 0.12f, wardrobeCardPaint)
            }
        }
        wardrobeCardPaint.alpha = 255
    }

    private fun refreshStats() {
        bestDistance = SaveManager.loadBestDistance(context)
        lastKillerLabel = PersistentMemoryManager.getLastKiller(context)?.let { formatEntityName(it) } ?: "None"
        sparedTotal =
            PersistentMemoryManager.getSparedCount(context, EntityType.CAT) +
            PersistentMemoryManager.getSparedCount(context, EntityType.FOX) +
            PersistentMemoryManager.getSparedCount(context, EntityType.WOLF)
        friendshipTotal = Biome.entries.sumOf { PersistentMemoryManager.getBiomeFriendship(context, it) }
        lastRunSummary = SaveManager.loadLastRunSummary(context)
        forestMoodState = ForestMoodSystem.currentState(context)
        returnMoment = ReturnMomentsSystem.resolveGardenMoment(context, lastRunSummary)
        val strongestBond = RelationshipArcSystem.strongestRelationship(context)
        returnVisitorSprite = (returnMoment?.visitor ?: strongestBond?.first)?.let(::spriteForVisitor)
        strongestBondLabel = strongestBond?.let { "${formatEntityName(it.first)} ${it.second.displayName}" } ?: "None"
        milestoneRewardLabel = RelationshipArcSystem.featuredMilestoneReward(context)?.label ?: "None"
        memoryPageCount = StoryFragmentSystem.memoryPageCount(context)
        gardenReflectionLine = StoryFragmentSystem.gardenReflection(context, lastRunSummary).orEmpty()
        weatherThoughtLine = StoryFragmentSystem.weatherThought(context, lastRunSummary)
        creatureThoughtLine = StoryFragmentSystem.creatureThought(context, strongestBond?.first).orEmpty()
        sanctuaryState = GardenSanctuaryPlanner.build(context, lastRunSummary)
    }

    private fun syncWardrobe() {
        val newUnlocks = CostumeManager.refreshUnlocks(context)
        unlockedCostumes = CostumeManager.availableCostumes(context)
        activeCostume = CostumeManager.activeCostume(context)
        if (newUnlocks.isNotEmpty()) {
            wardrobeMessage = if (newUnlocks.size == 1) {
                newUnlocks.first().unlockLabel
            } else {
                newUnlocks.joinToString(" + ") { it.displayName }
            }
            wardrobeMessageTimer = 3f
        }
    }

    private fun syncInteractiveLayout(cw: Float, ch: Float) {
        runButtonRect.set(cw * 0.70f, ch * 0.14f, cw * 0.93f, ch * 0.26f)
        lastRunRect.set(cw * 0.62f, ch * 0.30f, cw * 0.95f, ch * 0.66f)
        wardrobeRect.set(cw * 0.05f, ch * 0.73f, cw * 0.95f, ch * 0.87f)
        val cardGap = wardrobeRect.width() * 0.015f
        val cardWidth = (wardrobeRect.width() - cardGap * 5f) / 5f
        val top = wardrobeRect.top + 34f
        val bottom = wardrobeRect.bottom - 18f
        CostumeStyle.entries.forEachIndexed { index, _ ->
            val left = wardrobeRect.left + 18f + index * (cardWidth + cardGap)
            val right = left + cardWidth
            wardrobeCardRects[index].set(left, top, right, bottom)
        }
    }

    private fun formatDistance(distanceMetres: Float): String =
        if (distanceMetres < 1_000f) "${distanceMetres.toInt()} m" else String.format("%.2f km", distanceMetres / 1_000f)

    private fun formatEntityName(type: EntityType): String =
        type.name.lowercase().split("_").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    private fun applyMoodPalette(ch: Float) {
        val mood = forestMoodState.currentMood
        skyPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            ch,
            intArrayOf(mood.skyTopColor, mood.skyBottomColor),
            null,
            Shader.TileMode.CLAMP
        )
        groundPaint.color = mood.groundColor
    }

    private fun drawReturnVisitor(canvas: Canvas, cw: Float, ch: Float) {
        val sprite = returnVisitorSprite ?: return
        val height = ch * 0.11f
        val width = SpriteSizing.widthForHeight(sprite, height, minWidth = height * 0.7f)
        val top = ch * 0.08f
        val left = cw * 0.76f
        spriteRect.set(left, top, left + width, top + height)
        sprite.draw(canvas, spriteRect)
    }

    private fun drawSanctuaryAtmosphere(canvas: Canvas, cw: Float, ch: Float, groundY: Float) {
        canopyShadePaint.color = Color.argb(sanctuaryState.canopyShadeAlpha, 24, 44, 38)
        canvas.drawRect(0f, 0f, cw, ch * 0.34f, canopyShadePaint)

        repeat(sanctuaryState.fireflyCount) { index ->
            val drift = sin(elapsed * (1.2f + index * 0.08f) + index) * 14f
            val x = cw * (0.10f + ((index * 0.13f) % 0.76f)) + drift
            val y = ch * (0.16f + (index % 4) * 0.05f) + sin(elapsed * 1.6f + index * 0.7f) * 10f
            ambiencePaint.color = Color.argb(150, 252, 246, 180)
            canvas.drawCircle(x, y, 4f + (index % 3), ambiencePaint)
            ambiencePaint.color = Color.argb(72, 252, 246, 180)
            canvas.drawCircle(x, y, 9f + (index % 3) * 2f, ambiencePaint)
        }

        repeat(sanctuaryState.petalCount) { index ->
            val drift = (elapsed * (18f + index * 1.6f) + index * 46f) % (cw + 80f)
            val x = cw - drift
            val y = ch * (0.18f + (index % 5) * 0.07f) + sin(elapsed * 1.4f + index) * 10f
            ambiencePaint.color = Color.argb(96, 255, 220, 236)
            canvas.drawOval(x, y, x + 12f, y + 7f, ambiencePaint)
        }

        repeat(sanctuaryState.bloomPatchCount) { index ->
            val x = cw * (0.18f + index * 0.19f)
            val y = groundY + ch * 0.06f + sin(elapsed * 0.8f + index) * 5f
            ambiencePaint.color = Color.argb(64, 232, 255, 204)
            canvas.drawCircle(x, y, 18f, ambiencePaint)
            ambiencePaint.color = Color.argb(112, 255, 240, 186)
            canvas.drawCircle(x - 8f, y + 4f, 7f, ambiencePaint)
            canvas.drawCircle(x + 10f, y - 2f, 6f, ambiencePaint)
        }
    }

    private fun drawSanctuaryTraces(canvas: Canvas, cw: Float, ch: Float) {
        if (sanctuaryState.traces.isEmpty()) return
        val chipWidth = cw * 0.16f
        val chipHeight = ch * 0.045f
        val gap = cw * 0.012f
        val totalWidth = sanctuaryState.traces.size * chipWidth + (sanctuaryState.traces.size - 1) * gap
        var left = (cw - totalWidth) / 2f
        val top = ch * 0.225f

        sanctuaryState.traces.forEach { trace ->
            val rect = RectF(left, top, left + chipWidth, top + chipHeight)
            traceChipPaint.color = trace.color
            traceChipPaint.alpha = 205
            canvas.drawRoundRect(rect, 16f, 16f, traceChipPaint)
            canvas.drawRoundRect(rect, 16f, 16f, traceChipBorderPaint)
            val labelY = rect.centerY() - (traceChipTextPaint.descent() + traceChipTextPaint.ascent()) / 2f
            canvas.drawText(trace.label, rect.centerX(), labelY, traceChipTextPaint)
            left += chipWidth + gap
        }
    }

    private fun spriteForVisitor(type: EntityType): SpriteSheet = when (type) {
        EntityType.CAT -> spriteManager.catSprite.copy()
        EntityType.FOX -> spriteManager.foxSprite.copy()
        EntityType.DOG -> spriteManager.dogSprite.copy()
        EntityType.OWL -> spriteManager.owlSprite.copy()
        EntityType.EAGLE -> spriteManager.eagleSprite.copy()
        EntityType.WOLF -> spriteManager.wolfSprite.copy()
        else -> spriteManager.catSprite.copy()
    }
}
