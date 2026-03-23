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
import com.yourname.forest_run.engine.GardenSanctuaryPlanner
import com.yourname.forest_run.engine.GardenSanctuaryState
import com.yourname.forest_run.engine.SaveManager
import com.yourname.forest_run.engine.SessionArcComposer
import com.yourname.forest_run.engine.SpriteManager
import com.yourname.forest_run.engine.SpriteSizing
import com.yourname.forest_run.engine.SpriteSheet
import kotlin.math.sin

/**
 * Main-menu / Garden loading screen — Phase 22.
 *
 * Two phases:
 *   IDLE  — The character sits under a Weeping Willow. Ambient loop plays.
 *            A soft "Run!" prompt pulses at the bottom.
 *   TAP1  — Player character stands up (2 s animation drawn as a simple
 *            squash/stretch transition on the idle rect).
 *   TAP2  — Run starts (GameView transitions to PLAYING).
 *
 * The main forest colour palette used here matches the Spring Orchard biome.
 */
class MainMenuScreen(
    private val context: Context,
    private val spriteManager: SpriteManager,
    private val screenW: Int,
    private val screenH: Int
) {
    enum class Phase { IDLE, STANDING_UP, READY }

    var phase: Phase = Phase.IDLE
        private set

    /** Latched when the menu requests a run start; consumed by GameView. */
    private var startRunRequested: Boolean = false

    /** Returns true until [consumeStartRunRequest] is called. */
    val shouldStartRun: Boolean
        get() = startRunRequested

    /** Called when the user taps the Garden button in IDLE phase. */
    var onGardenTap: (() -> Unit)? = null

    // Timers
    private var standTimer = 0f
    private val STAND_DURATION = 1.8f

    // Pulse / ambient
    private var elapsedT = 0f
    private var sceneCopy = SessionArcComposer.menuCopy(context)
    private var sanctuaryState = GardenSanctuaryState()

    // ── Font ─────────────────────────────────────────────────────────────
    private val pixelFont: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, AssetPaths.PIXEL_FONT)
    }.getOrDefault(Typeface.MONOSPACE)

    // ── Paints ────────────────────────────────────────────────────────────

    // Sky gradient
    private val skyPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, screenH * 0.75f,
            intArrayOf(Color.rgb(120, 200, 255), Color.rgb(200, 235, 180)),
            null, Shader.TileMode.CLAMP
        )
    }

    // Ground
    private val groundPaint = Paint().apply { color = Color.rgb(90, 170, 80) }

    private val willowSprite: SpriteSheet = spriteManager.willowSprite.copy().apply {
        framesPerSec = 4f
    }
    private val birdSprite: SpriteSheet = spriteManager.chickadeeFlying.copy().apply {
        framesPerSec = 8f
    }
    private val idlePlayerSprite: SpriteSheet = spriteManager.playerDuck.copy().apply {
        framesPerSec = 0f
        setFrame(3)
    }
    private val standPlayerSprite: SpriteSheet = spriteManager.playerStandUp.copy()
    private val readyPlayerSprite: SpriteSheet = spriteManager.playerRun.copy().apply {
        framesPerSec = 10f
    }
    private val treeRect = RectF()
    private val charRect = RectF()
    private val birdRect = RectF()

    // Prompt text
    private val promptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textSize  = 22f
        typeface  = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color    = Color.rgb(255, 240, 100)
        textSize = 34f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val atmospherePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 235, 247, 230)
        textSize = 14f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val secondaryAtmospherePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(196, 220, 236, 214)
        textSize = 12f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val supportPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 220, 234, 214)
        textSize = 13f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val canopyShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ambiencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(212, 246, 239, 180)
        style = Paint.Style.FILL
    }
    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 246, 245, 228)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(52, 60, 26)
        textSize = 12f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val homeSignPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(192, 236, 246, 220)
        style = Paint.Style.FILL
    }
    private val homeSignBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(176, 244, 248, 238)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val homeSignTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(46, 58, 32)
        textSize = 11f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val launchCuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(116, 246, 238, 176)
        style = Paint.Style.FILL
    }
    private val launchCueBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(156, 252, 246, 214)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val launchCueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(52, 64, 34)
        textSize = 11f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }

    init {
        refreshCopy()
    }

    // ── API ───────────────────────────────────────────────────────────────

    /** Called on each tap from GameView.onTouchListener. */
    fun onTap(tapX: Float = 0f, tapY: Float = 0f) {
        // Garden button: bottom-left strip in IDLE phase
        if (phase == Phase.IDLE && tapX < screenW * 0.35f && tapY > screenH * 0.85f) {
            onGardenTap?.invoke()
            return
        }
        when (phase) {
            Phase.IDLE         -> { phase = Phase.STANDING_UP; standTimer = 0f }
            Phase.STANDING_UP  -> { /* wait for animation */ }
            Phase.READY        -> { startRunRequested = true }
        }
    }

    fun update(deltaTime: Float) {
        elapsedT += deltaTime
        willowSprite.update(deltaTime)
        birdSprite.update(deltaTime)

        if (phase == Phase.STANDING_UP) {
            standTimer += deltaTime
            standPlayerSprite.update(deltaTime)
            if (standTimer >= STAND_DURATION || standPlayerSprite.isFinished) {
                phase = Phase.READY
            }
        }
        if (phase == Phase.READY) readyPlayerSprite.update(deltaTime)
    }

    fun refreshCopy() {
        sceneCopy = SessionArcComposer.menuCopy(context)
        val summary = SaveManager.loadLastRunSummary(context.applicationContext)
        sanctuaryState = GardenSanctuaryPlanner.build(context, summary)
    }

    /** Consume a pending run-start request so it only fires once. */
    fun consumeStartRunRequest(): Boolean {
        if (!startRunRequested) return false
        startRunRequested = false
        return true
    }

    fun draw(canvas: Canvas) {
        val cw = screenW.toFloat()
        val ch = screenH.toFloat()
        val groundY = ch * 0.78f

        // Sky
        canvas.drawRect(0f, 0f, cw, ch, skyPaint)
        // Ground
        canvas.drawRect(0f, groundY, cw, ch, groundPaint)
        drawMenuSanctuaryAtmosphere(canvas, cw, ch, groundY)

        drawAmbientBird(canvas, cw, ch)
        drawWillow(canvas, cw * 0.35f, groundY)
        drawCharacter(canvas, cw * 0.62f, groundY)

        // Title
        canvas.drawText("FOREST RUN", cw / 2f, ch * 0.10f + titlePaint.textSize, titlePaint)
        drawWrappedCenteredText(canvas, sceneCopy.atmosphereLine, cw / 2f, ch * 0.17f, cw * 0.70f, atmospherePaint)
        drawWrappedCenteredText(canvas, sceneCopy.secondaryAtmosphereLine, cw / 2f, ch * 0.205f, cw * 0.68f, secondaryAtmospherePaint)
        drawArrivalBadge(canvas, cw, ch)
        drawHomeSign(canvas, cw, ch)
        drawLaunchCue(canvas, cw, ch, groundY)

        // Prompt
        val promptAlpha = when (phase) {
            Phase.IDLE         -> (0.6f + 0.4f * sin(elapsedT * 2.5f)) * 255
            Phase.STANDING_UP  -> 200f
            Phase.READY        -> (0.5f + 0.5f * sin(elapsedT * 4f)) * 255
        }
        promptPaint.alpha = promptAlpha.toInt().coerceIn(0, 255)
        val promptText = when (phase) {
            Phase.IDLE        -> sceneCopy.idlePrompt
            Phase.STANDING_UP -> sceneCopy.standingPrompt
            Phase.READY       -> sceneCopy.readyPrompt
        }
        val supportText = when (phase) {
            Phase.IDLE -> sceneCopy.idleSupportLine
            Phase.STANDING_UP -> sceneCopy.standingSupportLine
            Phase.READY -> sceneCopy.readySupportLine
        }
        canvas.drawText(promptText, cw / 2f, ch * 0.90f, promptPaint)
        drawWrappedCenteredText(canvas, supportText, cw / 2f, ch * 0.935f, cw * 0.72f, supportPaint)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun drawWillow(canvas: Canvas, cx: Float, groundY: Float) {
        val sway = sin(elapsedT * 0.7f) * 14f
        val treeH = screenH * 0.46f
        val treeW = SpriteSizing.widthForHeight(willowSprite, treeH, minWidth = treeH * 0.55f)
        treeRect.set(cx - treeW / 2f + sway, groundY - treeH, cx + treeW / 2f + sway, groundY)
        willowSprite.draw(canvas, treeRect)
    }

    private fun drawAmbientBird(canvas: Canvas, cw: Float, ch: Float) {
        val flap = sin(elapsedT * 1.8f)
        val birdX = cw * 0.24f + sin(elapsedT * 0.45f) * cw * 0.08f
        val birdY = ch * 0.18f + flap * ch * 0.02f
        val birdH = 88f
        val birdW = SpriteSizing.widthForHeight(birdSprite, birdH, minWidth = birdH * 0.7f)
        birdRect.set(birdX - birdW / 2f, birdY - birdH / 2f, birdX + birdW / 2f, birdY + birdH / 2f)
        birdSprite.draw(canvas, birdRect)
    }

    private fun drawMenuSanctuaryAtmosphere(canvas: Canvas, cw: Float, ch: Float, groundY: Float) {
        canopyShadePaint.color = Color.argb(sanctuaryState.canopyShadeAlpha.coerceAtMost(72), 26, 42, 34)
        canvas.drawRect(0f, 0f, cw, ch * 0.34f, canopyShadePaint)

        repeat(sanctuaryState.mistBandCount) { index ->
            ambiencePaint.color = Color.argb(32 + index * 10, 232, 246, 236)
            val top = ch * (0.19f + index * 0.055f)
            canvas.drawOval(-40f, top, cw + 40f, top + ch * 0.08f, ambiencePaint)
        }

        repeat(sanctuaryState.fireflyCount.coerceAtMost(6)) { index ->
            val drift = sin(elapsedT * (1.1f + index * 0.08f) + index * 0.6f) * 11f
            val x = cw * (0.14f + (index * 0.14f)) + drift
            val y = ch * (0.14f + (index % 3) * 0.05f)
            ambiencePaint.color = Color.argb(136, 252, 246, 182)
            canvas.drawCircle(x, y, 3.5f + (index % 2), ambiencePaint)
            ambiencePaint.color = Color.argb(64, 252, 246, 182)
            canvas.drawCircle(x, y, 8f + (index % 3), ambiencePaint)
        }

        repeat(sanctuaryState.lanternGlowCount.coerceAtMost(4)) { index ->
            val x = cw * (0.18f + index * 0.18f)
            val y = groundY - ch * (0.07f + (index % 2) * 0.03f)
            ambiencePaint.color = Color.argb(70, 255, 235, 168)
            canvas.drawCircle(x, y, 16f, ambiencePaint)
            ambiencePaint.color = Color.argb(128, 255, 240, 188)
            canvas.drawCircle(x, y, 6f, ambiencePaint)
        }

        repeat(sanctuaryState.petalCount.coerceAtMost(8)) { index ->
            val driftX = ((elapsedT * (10f + index * 0.6f)) + index * 0.9f) % 1f
            val x = cw * (0.10f + driftX * 0.80f) + sin(elapsedT * (0.9f + index * 0.08f) + index) * 14f
            val y = ch * (0.18f + (index % 4) * 0.06f) + sin(elapsedT * (1.4f + index * 0.05f) + index * 0.7f) * 10f
            ambiencePaint.color = Color.argb(98, 255, 220 - index * 4, 232)
            canvas.drawOval(x - 6f, y - 3f, x + 6f, y + 3f, ambiencePaint)
        }

        if (sanctuaryState.groundGlowAlpha > 0) {
            ambiencePaint.color = Color.argb(sanctuaryState.groundGlowAlpha.coerceAtMost(120), 240, 246, 184)
            canvas.drawOval(
                cw * 0.24f,
                groundY - ch * 0.05f,
                cw * 0.76f,
                groundY + ch * 0.08f,
                ambiencePaint
            )
        }

        repeat(sanctuaryState.bloomPatchCount.coerceAtMost(4)) { index ->
            val x = cw * (0.28f + index * 0.13f)
            val y = groundY + ch * 0.008f
            ambiencePaint.color = Color.argb(74, 250, 236, 166)
            canvas.drawOval(x - 26f, y - 10f, x + 26f, y + 12f, ambiencePaint)
            ambiencePaint.color = Color.argb(126, 255, 242, 196)
            canvas.drawCircle(x, y - 2f, 5f, ambiencePaint)
            canvas.drawCircle(x - 9f, y + 3f, 4f, ambiencePaint)
            canvas.drawCircle(x + 10f, y + 2f, 4f, ambiencePaint)
        }

        if (phase == Phase.STANDING_UP || phase == Phase.READY) {
            val phaseT = when (phase) {
                Phase.STANDING_UP -> (standTimer / STAND_DURATION).coerceIn(0f, 1f)
                Phase.READY -> 1f
                Phase.IDLE -> 0f
            }
            val laneTop = groundY - ch * 0.02f
            val laneBottom = groundY + ch * 0.02f
            val laneLeft = cw * 0.58f
            val laneRight = cw * (0.58f + 0.22f * phaseT)
            launchCuePaint.color = Color.argb((70 + phaseT * 54).toInt(), 244, 238, 172)
            canvas.drawRoundRect(RectF(laneLeft, laneTop, laneRight, laneBottom), 20f, 20f, launchCuePaint)
            launchCueBorderPaint.color = Color.argb((96 + phaseT * 64).toInt(), 252, 244, 208)
            canvas.drawRoundRect(RectF(laneLeft, laneTop, laneRight, laneBottom), 20f, 20f, launchCueBorderPaint)

            repeat(4) { index ->
                val footstepT = ((elapsedT * (0.7f + index * 0.12f)) + index * 0.24f) % 1f
                val x = cw * (0.60f + footstepT * 0.19f)
                val y = groundY + if (index % 2 == 0) -5f else 5f
                launchCuePaint.color = Color.argb((64 + phaseT * 72).toInt(), 252, 246, 188)
                canvas.drawOval(x - 8f, y - 3f, x + 8f, y + 3f, launchCuePaint)
            }
        }
    }

    private fun drawArrivalBadge(canvas: Canvas, cw: Float, ch: Float) {
        if (sanctuaryState.arrivalBadge.isBlank()) return
        val width = cw * 0.22f
        val height = ch * 0.05f
        val rect = RectF(cw / 2f - width / 2f, ch * 0.205f, cw / 2f + width / 2f, ch * 0.205f + height)
        canvas.drawRoundRect(rect, 18f, 18f, badgePaint)
        canvas.drawRoundRect(rect, 18f, 18f, badgeBorderPaint)
        val labelY = rect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
        canvas.drawText(sanctuaryState.arrivalBadge, rect.centerX(), labelY, badgeTextPaint)
    }

    private fun drawHomeSign(canvas: Canvas, cw: Float, ch: Float) {
        if (sceneCopy.homeSignLabel.isBlank()) return
        val width = cw * 0.20f
        val height = ch * 0.045f
        val rect = RectF(cw * 0.09f, ch * 0.11f, cw * 0.09f + width, ch * 0.11f + height)
        canvas.drawRoundRect(rect, 16f, 16f, homeSignPaint)
        canvas.drawRoundRect(rect, 16f, 16f, homeSignBorderPaint)
        val labelY = rect.centerY() - (homeSignTextPaint.descent() + homeSignTextPaint.ascent()) / 2f
        canvas.drawText(sceneCopy.homeSignLabel.take(26), rect.centerX(), labelY, homeSignTextPaint)
    }

    private fun drawLaunchCue(canvas: Canvas, cw: Float, ch: Float, groundY: Float) {
        if (phase != Phase.STANDING_UP && phase != Phase.READY) return
        val rect = RectF(cw * 0.66f, groundY - ch * 0.105f, cw * 0.92f, groundY - ch * 0.055f)
        launchCuePaint.color = Color.argb(if (phase == Phase.READY) 172 else 138, 242, 238, 176)
        canvas.drawRoundRect(rect, 18f, 18f, launchCuePaint)
        launchCueBorderPaint.color = Color.argb(if (phase == Phase.READY) 184 else 152, 248, 246, 222)
        canvas.drawRoundRect(rect, 18f, 18f, launchCueBorderPaint)
        val line = if (phase == Phase.READY) sceneCopy.readyLaunchLine else sceneCopy.standingSupportLine
        val labelY = rect.top + 17f
        drawWrappedCenteredText(canvas, line, rect.centerX(), labelY, rect.width() - 18f, launchCueTextPaint)
    }

    private fun drawCharacter(canvas: Canvas, x: Float, groundY: Float) {
        val t     = (standTimer / STAND_DURATION).coerceIn(0f, 1f)
        val baseH = 200f
        val baseW = SpriteSizing.widthForHeight(readyPlayerSprite, baseH, minWidth = baseH * 0.65f)

        val scaleY = when (phase) {
            Phase.IDLE         -> 0.65f   // sitting
            Phase.STANDING_UP  -> 0.65f + t * 0.35f  // lerp to standing
            Phase.READY        -> 1.00f + 0.05f * sin(elapsedT * 4f)  // gentle bounce
        }
        val scaleX = when (phase) {
            Phase.STANDING_UP -> 1f + 0.15f * sin(t * Math.PI.toFloat()) // squash mid-rise
            else -> 1f
        }

        val h = baseH * scaleY
        val w = baseW * scaleX
        charRect.set(x - w / 2f, groundY - h, x + w / 2f, groundY)
        val sprite = when (phase) {
            Phase.IDLE -> idlePlayerSprite
            Phase.STANDING_UP -> standPlayerSprite
            Phase.READY -> readyPlayerSprite
        }
        sprite.draw(canvas, charRect)
    }

    private fun drawWrappedCenteredText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        baselineY: Float,
        maxWidth: Float,
        paint: Paint
    ) {
        val words = text.split(" ")
        if (words.isEmpty()) return

        val lines = mutableListOf<String>()
        val builder = StringBuilder()
        for (word in words) {
            val candidate = if (builder.isEmpty()) word else "${builder} $word"
            if (paint.measureText(candidate) <= maxWidth) {
                builder.clear()
                builder.append(candidate)
            } else {
                lines += builder.toString()
                builder.clear()
                builder.append(word)
            }
        }
        if (builder.isNotEmpty()) lines += builder.toString()

        var y = baselineY
        for (line in lines.take(2)) {
            canvas.drawText(line, centerX, y, paint)
            y += paint.textSize + 6f
        }
    }
}
