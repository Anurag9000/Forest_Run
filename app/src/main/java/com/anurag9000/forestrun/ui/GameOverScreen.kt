package com.anurag9000.forestrun.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.anurag9000.forestrun.engine.AssetPaths
import com.anurag9000.forestrun.engine.CinematicOverlayRenderer
import com.anurag9000.forestrun.engine.CinematicScene
import com.anurag9000.forestrun.engine.GardenSanctuaryPlanner
import com.anurag9000.forestrun.engine.GardenSanctuaryState
import com.anurag9000.forestrun.engine.PacifistPresentation
import com.anurag9000.forestrun.engine.PostRunReflectionEntry
import com.anurag9000.forestrun.engine.PostRunReflectionPlanner
import com.anurag9000.forestrun.engine.RestSceneCopy
import com.anurag9000.forestrun.engine.RunSummary
import com.anurag9000.forestrun.engine.SanctuaryLightingScene
import com.anurag9000.forestrun.engine.SessionArcComposer
import com.anurag9000.forestrun.engine.CinematicPolishProfile
import com.anurag9000.forestrun.engine.resolveCinematicPolishProfile
import com.anurag9000.forestrun.engine.buildSanctuaryLightingIdentity
import kotlin.math.sin

/**
 * Full-screen post-run rest overlay.
 *
 * Displayed during [RunState.GAME_OVER].
 * Tap-anywhere input is handled in GameView's onTouchListener;
 * this class only handles drawing.
 *
 * Layout (top → bottom, centred):
 *  1. Semi-transparent dark scrim.
 *  2. Pixel-art bordered panel (rounded rect).
 *  3. "REST" header.
 *  4. Reflective quote / killer memory.
 *  5. Score and distance.
 *  6. Seeds carried into the garden.
 *  7. Mercy hearts earned this run.
 *  8. "tap anywhere to return to garden" — subtle pulsing prompt.
 */
class GameOverScreen(
    context: Context,
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    private val appContext = context.applicationContext
    // ── Fonts ─────────────────────────────────────────────────────────────
    private val pixelFont: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, AssetPaths.PIXEL_FONT)
    }.getOrDefault(Typeface.MONOSPACE)

    // ── Paints ────────────────────────────────────────────────────────────
    private val scrimPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(232, 16, 18, 26)
        style = Paint.Style.FILL
    }
    private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 120, 210, 150)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val ambiencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(216, 244, 236, 172)
        style = Paint.Style.FILL
    }
    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 242, 246, 230)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pixelFont
        textSize = 13f
        textAlign = Paint.Align.CENTER
        color = Color.rgb(44, 50, 24)
    }
    private val restChipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(216, 230, 245, 220)
        style = Paint.Style.FILL
    }
    private val restChipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(196, 242, 246, 230)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val restChipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pixelFont
        textSize = 12f
        textAlign = Paint.Align.CENTER
        color = Color.rgb(36, 44, 24)
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = pixelFont
        textSize  = 34f
        textAlign = Paint.Align.CENTER
        color     = Color.rgb(220, 255, 210)
    }
    private val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pixelFont
        textSize = 18f
        textAlign = Paint.Align.CENTER
        color = Color.argb(220, 220, 240, 220)
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pixelFont
        textSize = 16f
        textAlign = Paint.Align.CENTER
        color = Color.argb(220, 232, 246, 228)
    }
    private val scoreLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = pixelFont
        textSize  = 22f
        textAlign = Paint.Align.CENTER
        color     = Color.argb(200, 180, 180, 180)
    }
    private val scoreValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = pixelFont
        textSize  = 44f
        textAlign = Paint.Align.CENTER
        color     = Color.WHITE
    }
    private val distancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = pixelFont
        textSize  = 24f
        textAlign = Paint.Align.CENTER
        color     = Color.rgb(140, 220, 140)
    }
    private val moodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pixelFont
        textSize = 18f
        textAlign = Paint.Align.CENTER
        color = Color.argb(220, 212, 230, 255)
    }
    private val newBestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = pixelFont
        textSize  = 20f
        textAlign = Paint.Align.CENTER
        color     = Color.rgb(255, 220, 60)
    }
    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = 28f
        textAlign = Paint.Align.CENTER
        color     = Color.rgb(255, 100, 100)
    }
    private val seedsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pixelFont
        textSize = 20f
        textAlign = Paint.Align.CENTER
        color = Color.rgb(160, 255, 120)
    }
    private val promptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface  = pixelFont
        textSize  = 18f
        textAlign = Paint.Align.CENTER
        color     = Color.argb(200, 200, 180, 255)
    }
    private val carryHomePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pixelFont
        textSize = 15f
        textAlign = Paint.Align.CENTER
        color = Color.argb(220, 236, 244, 226)
    }
    private val sectionLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pixelFont
        textSize = 14f
        textAlign = Paint.Align.LEFT
        color = Color.argb(220, 220, 236, 196)
    }
    private val sectionBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pixelFont
        textSize = 16f
        textAlign = Paint.Align.LEFT
        color = Color.argb(228, 234, 244, 232)
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(176, 22, 28, 34)
        style = Paint.Style.FILL
    }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(136, 202, 222, 204)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val infoCardRect = RectF()

    // ── Pulse timer ───────────────────────────────────────────────────────
    private var pulseTimer = 0f

    // ── Panel rect (computed once) ────────────────────────────────────────
    private val panelW     = screenWidth  * 0.72f
    private val panelH     = screenHeight * 0.76f
    private val panelLeft  = (screenWidth  - panelW) / 2f
    private val panelTop   = (screenHeight - panelH) / 2f
    private val panelRect  = RectF(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH)
    private val cx         = screenWidth / 2f
    private val cinematicOverlay = CinematicOverlayRenderer()
    private val cinematicProfile = CinematicPolishProfile()
    private val restLighting = buildSanctuaryLightingIdentity(SanctuaryLightingScene.REST)
    private val restChipRect = RectF()
    private val arrivalBadgeRect = RectF()
    private val homeCharacterRect = RectF()

    private data class RestComposition(
        val summary: RunSummary,
        val sceneCopy: RestSceneCopy,
        val sanctuaryState: GardenSanctuaryState,
        val reflectionEntry: PostRunReflectionEntry?
    )

    private var cachedComposition: RestComposition? = null

    private fun compositionFor(summary: RunSummary): RestComposition {
        cachedComposition?.takeIf { it.summary == summary }?.let { return it }

        val sceneCopy = SessionArcComposer.restCopy(appContext, summary)
        val sanctuaryState = GardenSanctuaryPlanner.build(appContext, summary)
        return RestComposition(
            summary = summary,
            sceneCopy = sceneCopy,
            sanctuaryState = sanctuaryState,
            reflectionEntry = PostRunReflectionPlanner.restEntry(
                summary = summary,
                sanctuaryState = sanctuaryState,
                recoveryLine = sceneCopy.recoveryLine,
                carryHomeLine = sceneCopy.carryHomeLine
            )
        ).also { cachedComposition = it }
    }

    // ── Update ────────────────────────────────────────────────────────────

    fun update(deltaTime: Float) {
        pulseTimer += deltaTime
    }

    // ── Draw ──────────────────────────────────────────────────────────────

    fun draw(
        canvas:        Canvas,
        summary: RunSummary,
        isRecovering: Boolean = false,
        recoveryProgress: Float = 1f
    ) {
        val composition = compositionFor(summary)
        val sceneCopy = composition.sceneCopy
        val sanctuaryState = composition.sanctuaryState
        val reflectionEntry = composition.reflectionEntry
        val w = screenWidth.toFloat()
        val h = screenHeight.toFloat()
        val stageAlpha = if (isRecovering) (0.38f + recoveryProgress * 0.62f).coerceIn(0f, 1f) else 1f

        // 1. Scrim
        scrimPaint.alpha = (180f * stageAlpha).toInt().coerceIn(0, 255)
        panelPaint.alpha = (232f * stageAlpha).toInt().coerceIn(0, 255)
        panelBorderPaint.alpha = (255f * stageAlpha).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, w, h, scrimPaint)
        drawRecoveryAtmosphere(canvas, w, h, sanctuaryState, stageAlpha)
        val restEmphasis = (
            0.38f +
                sanctuaryState.lanternGlowCount * 0.05f +
                sanctuaryState.homecomingConsequences.size * 0.07f
            ).coerceIn(0f, 1f) * stageAlpha
        cinematicOverlay.draw(
            canvas = canvas,
            width = w,
            height = h,
            profile = resolveCinematicPolishProfile(
                target = cinematicProfile,
                scene = CinematicScene.REST,
                emphasis = restEmphasis
            ),
            elapsedSeconds = pulseTimer,
            glowColor = restLighting.groundGlowColor,
            centerYFraction = 0.50f
        )

        // 2. Panel
        canvas.drawRoundRect(panelRect, 24f, 24f, panelPaint)
        canvas.drawRoundRect(panelRect, 24f, 24f, panelBorderPaint)

        var ty = panelTop + 70f

        // 3. Rest chip + title
        restChipRect.set(cx - 58f, ty - 26f, cx + 58f, ty - 2f)
        restChipPaint.alpha = (216f * stageAlpha).toInt().coerceIn(0, 255)
        restChipBorderPaint.alpha = (196f * stageAlpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(restChipRect, 12f, 12f, restChipPaint)
        canvas.drawRoundRect(restChipRect, 12f, 12f, restChipBorderPaint)
        canvas.drawText(
            "REST",
            cx,
            restChipRect.centerY() -
                (restChipTextPaint.descent() + restChipTextPaint.ascent()) / 2f,
            restChipTextPaint
        )
        canvas.drawText(sceneCopy.recoveryTitle, cx, ty + 24f, titlePaint)
        ty += 56f

        drawWrappedCenteredText(canvas, sceneCopy.subtitle, cx, ty, panelW * 0.80f, subtitlePaint)
        ty += 42f
        drawWrappedCenteredText(canvas, sceneCopy.recoveryLine, cx, ty, panelW * 0.82f, carryHomePaint)
        ty += 42f

        if (sanctuaryState.arrivalBadge.isNotBlank()) {
            val badgeWidth = panelW * 0.34f
            arrivalBadgeRect.set(
                cx - badgeWidth / 2f,
                ty - 16f,
                cx + badgeWidth / 2f,
                ty + 10f
            )
            canvas.drawRoundRect(arrivalBadgeRect, 16f, 16f, badgePaint)
            canvas.drawRoundRect(arrivalBadgeRect, 16f, 16f, badgeBorderPaint)
            val labelY = arrivalBadgeRect.centerY() -
                (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
            canvas.drawText(sanctuaryState.arrivalBadge, cx, labelY, badgeTextPaint)
            ty += 28f
        }

        if (sanctuaryState.homeCharacterLabel.isNotBlank()) {
            val homeWidth = panelW * 0.36f
            homeCharacterRect.set(
                cx - homeWidth / 2f,
                ty - 14f,
                cx + homeWidth / 2f,
                ty + 10f
            )
            canvas.drawRoundRect(homeCharacterRect, 14f, 14f, restChipPaint)
            canvas.drawRoundRect(homeCharacterRect, 14f, 14f, restChipBorderPaint)
            val labelY = homeCharacterRect.centerY() -
                (restChipTextPaint.descent() + restChipTextPaint.ascent()) / 2f
            canvas.drawText(sanctuaryState.homeCharacterLabel, cx, labelY, restChipTextPaint)
            ty += 26f
            drawWrappedCenteredText(canvas, sanctuaryState.homeCharacterLine, cx, ty, panelW * 0.80f, carryHomePaint)
            ty += 34f
        }

        drawWrappedCenteredText(canvas, summary.restQuote, cx, ty, panelW * 0.82f, quotePaint)
        ty += 58f
        canvas.drawText(summary.forestMood.restLine, cx, ty, moodPaint)
        ty += 30f
        if (summary.pacifistRouteTier != com.anurag9000.forestrun.engine.PacifistRouteTier.NONE) {
            canvas.drawText("Route: ${summary.pacifistRouteTier.displayName}", cx, ty, moodPaint)
            ty += 28f
            drawWrappedCenteredText(
                canvas,
                PacifistPresentation.routeAfterglowLine(summary.pacifistRouteTier),
                cx,
                ty,
                panelW * 0.80f,
                carryHomePaint
            )
            ty += 34f
        }
        if (sanctuaryState.worldOpinionLabel.isNotBlank()) {
            canvas.drawText("Opinion: ${sanctuaryState.worldOpinionLabel}", cx, ty, moodPaint)
            ty += 28f
            drawWrappedCenteredText(
                canvas,
                sanctuaryState.worldOpinionLine,
                cx,
                ty,
                panelW * 0.80f,
                carryHomePaint
            )
            ty += 34f
        }
        if (sanctuaryState.routeWorldLabel.isNotBlank()) {
            canvas.drawText("World: ${sanctuaryState.routeWorldLabel}", cx, ty, moodPaint)
            ty += 28f
            drawWrappedCenteredText(
                canvas,
                sanctuaryState.routeWorldLine,
                cx,
                ty,
                panelW * 0.80f,
                carryHomePaint
            )
            ty += 34f
        }

        // 4. Score label + value
        canvas.drawText("SCORE", cx, ty, scoreLabelPaint)
        ty += 36f
        canvas.drawText(formatNumber(summary.score), cx, ty, scoreValuePaint)
        ty += 55f

        // 5. Distance
        canvas.drawText("${summary.distanceM.toInt()} m", cx, ty, distancePaint)
        ty += 40f

        // 6. New best badge (if applicable)
        if (summary.isNewHighScore) {
            val pulse = sin(pulseTimer * 4f) * 0.2f + 0.8f
            newBestPaint.alpha = (pulse * 255f).toInt()
            canvas.drawText("★ NEW BEST! ${formatNumber(summary.highScore)} ★", cx, ty, newBestPaint)
            ty += 38f
        }

        if (summary.seedsCollected > 0) {
            canvas.drawText("+${summary.seedsCollected} seeds carried home", cx, ty, seedsPaint)
            ty += 38f
        }

        if (summary.bloomConversions > 0) {
            canvas.drawText("${summary.bloomConversions} Bloom conversions", cx, ty, seedsPaint)
            ty += 32f
        }

        // 7. Mercy hearts row
        if (summary.mercyHearts > 0) {
            val hearts = "♥".repeat(summary.mercyHearts.coerceAtMost(10))
            canvas.drawText(hearts, cx, ty, heartPaint)
            ty += 40f
        }

        val summaryBits = buildList {
            if (summary.mercyMisses > 0) add("${summary.mercyMisses} close calls")
            if (summary.cleanPasses > 0) add("${summary.cleanPasses} clean")
            if (summary.sparedCount > 0) add("${summary.sparedCount} spared")
            if (summary.hitsTaken > 0) add("${summary.hitsTaken} hit")
        }
        if (summaryBits.isNotEmpty()) {
            canvas.drawText(summaryBits.joinToString("  •  "), cx, ty, scoreLabelPaint)
            ty += 34f
        }
        if (summary.kindnessChain > 0) {
            canvas.drawText("best kindness chain ${summary.kindnessChain}", cx, ty, distancePaint)
            ty += 34f
        }

        if (reflectionEntry != null) {
            drawInfoCard(
                canvas = canvas,
                left = panelLeft + panelW * 0.10f,
                top = ty - 8f,
                width = panelW * 0.80f,
                label = reflectionEntry.label.uppercase(),
                line = reflectionEntry.text,
                alphaScale = stageAlpha
            )
            ty += 72f
        }

        sanctuaryState.homecomingConsequences.take(2).forEach { consequence ->
            drawInfoCard(
                canvas = canvas,
                left = panelLeft + panelW * 0.10f,
                top = ty - 6f,
                width = panelW * 0.80f,
                label = consequence.label.uppercase(),
                line = consequence.line,
                alphaScale = stageAlpha
            )
            ty += 68f
        }

        val statSummary = buildList {
            if (summary.seedsCollected > 0) add("+${summary.seedsCollected} seeds")
            if (summary.cleanPasses > 0) add("${summary.cleanPasses} clean")
            if (summary.sparedCount > 0) add("${summary.sparedCount} spared")
            if (summary.bloomConversions > 0) add("${summary.bloomConversions} Bloom")
        }.joinToString("  •  ")
        if (statSummary.isNotBlank()) {
            drawInfoCard(
                canvas = canvas,
                left = panelLeft + panelW * 0.10f,
                top = ty - 6f,
                width = panelW * 0.80f,
                label = "RUN KEPT",
                line = statSummary,
                alphaScale = stageAlpha
            )
            ty += 68f
        }

        drawInfoCard(
            canvas = canvas,
            left = panelLeft + panelW * 0.10f,
            top = ty - 6f,
            width = panelW * 0.80f,
            label = sceneCopy.carryHomeLabel.uppercase(),
            line = sceneCopy.carryHomeLine,
            alphaScale = stageAlpha
        )
        ty += 74f

        // 8. Tap prompt — pulsing alpha
        val promptAlpha = if (isRecovering) {
            0
        } else {
            ((sin(pulseTimer * 2.5f) * 0.4f + 0.6f) * 200).toInt().coerceIn(0, 255)
        }
        promptPaint.alpha = promptAlpha
        canvas.drawText(sceneCopy.promptLine, cx, ty + 20f, promptPaint)
    }

    private fun drawRecoveryAtmosphere(
        canvas: Canvas,
        w: Float,
        h: Float,
        sanctuaryState: GardenSanctuaryState,
        alphaScale: Float
    ) {
        val lighting = buildSanctuaryLightingIdentity(SanctuaryLightingScene.REST)
        ambiencePaint.color = Color.argb(
            (sanctuaryState.canopyShadeAlpha.coerceAtMost(84) * alphaScale).toInt().coerceIn(0, 255),
            Color.red(lighting.canopyColor),
            Color.green(lighting.canopyColor),
            Color.blue(lighting.canopyColor)
        )
        canvas.drawRect(0f, 0f, w, h * 0.34f, ambiencePaint)

        repeat(sanctuaryState.mistBandCount.coerceAtMost(3)) { index ->
            ambiencePaint.color = Color.argb(
                ((24 + index * 10) * alphaScale).toInt().coerceIn(0, 255),
                Color.red(lighting.mistColor),
                Color.green(lighting.mistColor),
                Color.blue(lighting.mistColor)
            )
            val top = h * (0.26f + index * 0.06f)
            canvas.drawOval(-30f, top, w + 30f, top + h * 0.08f, ambiencePaint)
        }

        if (sanctuaryState.groundGlowAlpha > 0) {
            ambiencePaint.color = Color.argb(
                (sanctuaryState.groundGlowAlpha.coerceAtMost(96) * alphaScale).toInt().coerceIn(0, 255),
                Color.red(lighting.groundGlowColor),
                Color.green(lighting.groundGlowColor),
                Color.blue(lighting.groundGlowColor)
            )
            canvas.drawOval(w * 0.16f, h * 0.70f, w * 0.84f, h * 0.92f, ambiencePaint)
        }

        repeat(sanctuaryState.lanternGlowCount.coerceAtMost(4)) { index ->
            val x = w * (0.24f + index * 0.16f)
            val y = h * (0.20f + (index % 2) * 0.05f)
            ambiencePaint.color = Color.argb(
                (58f * alphaScale).toInt().coerceIn(0, 255),
                Color.red(lighting.lanternOuterColor),
                Color.green(lighting.lanternOuterColor),
                Color.blue(lighting.lanternOuterColor)
            )
            canvas.drawCircle(x, y, 14f, ambiencePaint)
            ambiencePaint.color = Color.argb(
                (118f * alphaScale).toInt().coerceIn(0, 255),
                Color.red(lighting.lanternInnerColor),
                Color.green(lighting.lanternInnerColor),
                Color.blue(lighting.lanternInnerColor)
            )
            canvas.drawCircle(x, y, 5f, ambiencePaint)
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private fun formatNumber(n: Int): String {
        if (n < 1000) return n.toString()
        val thousands = n / 1000
        val remainder = n % 1000
        return "${thousands},${remainder.toString().padStart(3, '0')}"
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
            y += paint.textSize + 8f
        }
    }

    private fun drawInfoCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        label: String,
        line: String,
        alphaScale: Float
    ) {
        val height = 58f
        infoCardRect.set(left, top, left + width, top + height)
        cardPaint.alpha = (176f * alphaScale).toInt().coerceIn(0, 255)
        cardBorderPaint.alpha = (136f * alphaScale).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(infoCardRect, 14f, 14f, cardPaint)
        canvas.drawRoundRect(infoCardRect, 14f, 14f, cardBorderPaint)
        sectionLabelPaint.alpha = (220f * alphaScale).toInt().coerceIn(0, 255)
        sectionBodyPaint.alpha = (228f * alphaScale).toInt().coerceIn(0, 255)
        canvas.drawText(label, left + 14f, top + 17f, sectionLabelPaint)
        drawWrappedLeftText(
            canvas = canvas,
            text = line,
            left = left + 14f,
            baselineY = top + 35f,
            maxWidth = width - 28f,
            paint = sectionBodyPaint
        )
    }

    private fun drawWrappedLeftText(
        canvas: Canvas,
        text: String,
        left: Float,
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
            canvas.drawText(line, left, y, paint)
            y += paint.textSize + 6f
        }
    }
}
