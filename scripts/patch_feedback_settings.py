#!/usr/bin/env python3
"""Add persisted reduced-motion, audio, and haptic controls with tested menu UI."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def write(path: str, content: str) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def main() -> None:
    write(
        "app/src/main/java/com/anurag9000/forestrun/engine/FeedbackSettings.kt",
        '''package com.anurag9000.forestrun.engine

import android.content.Context
import kotlin.math.roundToInt

/** Persisted comfort and feedback preferences, independent of game progression. */
data class FeedbackPreferences(
    val reducedMotion: Boolean = false,
    val audioEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true
)

object FeedbackSettings {
    internal const val PREFS_NAME = "forest_run_feedback_settings"
    private const val KEY_REDUCED_MOTION = "reduced_motion"
    private const val KEY_AUDIO_ENABLED = "audio_enabled"
    private const val KEY_HAPTICS_ENABLED = "haptics_enabled"

    @Volatile
    var reducedMotion: Boolean = false
        private set

    @Volatile
    var audioEnabled: Boolean = true
        private set

    @Volatile
    var hapticsEnabled: Boolean = true
        private set

    @Synchronized
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        reducedMotion = prefs.getBoolean(KEY_REDUCED_MOTION, false)
        audioEnabled = prefs.getBoolean(KEY_AUDIO_ENABLED, true)
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        if (reducedMotion) CameraSystem.reset()
        if (!hapticsEnabled) HapticManager.cancel()
        LeitmotifManager.setAudioEnabled(audioEnabled)
    }

    fun snapshot(): FeedbackPreferences = FeedbackPreferences(
        reducedMotion = reducedMotion,
        audioEnabled = audioEnabled,
        hapticsEnabled = hapticsEnabled
    )

    @Synchronized
    fun setReducedMotion(context: Context, enabled: Boolean) {
        reducedMotion = enabled
        persist(context, KEY_REDUCED_MOTION, enabled)
        if (enabled) CameraSystem.reset()
    }

    @Synchronized
    fun setAudioEnabled(context: Context, enabled: Boolean) {
        audioEnabled = enabled
        persist(context, KEY_AUDIO_ENABLED, enabled)
        LeitmotifManager.setAudioEnabled(enabled)
    }

    @Synchronized
    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        hapticsEnabled = enabled
        persist(context, KEY_HAPTICS_ENABLED, enabled)
        if (!enabled) HapticManager.cancel()
    }

    private fun persist(context: Context, key: String, value: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    internal fun resetMemoryForTests() {
        reducedMotion = false
        audioEnabled = true
        hapticsEnabled = true
        CameraSystem.reset()
        LeitmotifManager.setAudioEnabled(true)
    }
}

internal fun adjustedParticleCount(baseCount: Int, reducedMotion: Boolean): Int {
    if (baseCount <= 0) return 0
    if (!reducedMotion) return baseCount
    return (baseCount * 0.35f).roundToInt().coerceIn(1, baseCount)
}

internal fun cinematicShimmerPulse(
    elapsedSeconds: Float,
    shimmerStrength: Float,
    reducedMotion: Boolean
): Float {
    if (reducedMotion) return 0.55f
    return 0.55f + 0.45f * kotlin.math.sin(elapsedSeconds * (1.2f + shimmerStrength))
}
'''
    )

    write(
        "app/src/main/java/com/anurag9000/forestrun/ui/FeedbackSettingsPanel.kt",
        '''package com.anurag9000.forestrun.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.anurag9000.forestrun.engine.AssetPaths
import com.anurag9000.forestrun.engine.FeedbackSettings

internal enum class FeedbackToggle { REDUCED_MOTION, AUDIO, HAPTICS }

internal data class FeedbackSettingsLayout(
    val reducedMotion: RectF,
    val audio: RectF,
    val haptics: RectF
) {
    val all: List<RectF> = listOf(reducedMotion, audio, haptics)
}

internal object FeedbackSettingsPanelLayout {
    fun build(width: Float, height: Float): FeedbackSettingsLayout {
        require(width > 0f && height > 0f)
        val right = width - (width * 0.02f).coerceAtLeast(18f)
        val chipWidth = (width * 0.18f).coerceIn(210f, 340f)
        val chipHeight = (height * 0.055f).coerceIn(38f, 54f)
        val gap = (height * 0.012f).coerceIn(7f, 13f)
        val top = (height * 0.61f).coerceAtMost(height - chipHeight * 3f - gap * 2f - 18f)
        fun rect(index: Int): RectF {
            val y = top + index * (chipHeight + gap)
            return RectF(right - chipWidth, y, right, y + chipHeight)
        }
        return FeedbackSettingsLayout(rect(0), rect(1), rect(2))
    }

    fun hitTest(layout: FeedbackSettingsLayout, x: Float, y: Float): FeedbackToggle? = when {
        layout.reducedMotion.contains(x, y) -> FeedbackToggle.REDUCED_MOTION
        layout.audio.contains(x, y) -> FeedbackToggle.AUDIO
        layout.haptics.contains(x, y) -> FeedbackToggle.HAPTICS
        else -> null
    }
}

internal class FeedbackSettingsPanel(
    private val context: Context,
    screenWidth: Int,
    screenHeight: Int
) {
    private val layout = FeedbackSettingsPanelLayout.build(screenWidth.toFloat(), screenHeight.toFloat())
    private val pixelFont: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, AssetPaths.PIXEL_FONT)
    }.getOrDefault(Typeface.MONOSPACE)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 18, 28, 24)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 214, 232, 198)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(238, 244, 224)
        textSize = 13f
        typeface = pixelFont
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 238, 232, 184)
        textSize = 11f
        typeface = pixelFont
        textAlign = Paint.Align.RIGHT
    }

    fun onTap(x: Float, y: Float): Boolean {
        when (FeedbackSettingsPanelLayout.hitTest(layout, x, y)) {
            FeedbackToggle.REDUCED_MOTION -> FeedbackSettings.setReducedMotion(
                context,
                !FeedbackSettings.reducedMotion
            )
            FeedbackToggle.AUDIO -> FeedbackSettings.setAudioEnabled(
                context,
                !FeedbackSettings.audioEnabled
            )
            FeedbackToggle.HAPTICS -> FeedbackSettings.setHapticsEnabled(
                context,
                !FeedbackSettings.hapticsEnabled
            )
            null -> return false
        }
        return true
    }

    fun draw(canvas: Canvas) {
        canvas.drawText("COMFORT", layout.audio.right, layout.reducedMotion.top - 9f, titlePaint)
        drawChip(canvas, layout.reducedMotion, if (FeedbackSettings.reducedMotion) "MOTION: LOW" else "MOTION: FULL")
        drawChip(canvas, layout.audio, if (FeedbackSettings.audioEnabled) "AUDIO: ON" else "AUDIO: OFF")
        drawChip(canvas, layout.haptics, if (FeedbackSettings.hapticsEnabled) "HAPTICS: ON" else "HAPTICS: OFF")
    }

    private fun drawChip(canvas: Canvas, rect: RectF, label: String) {
        canvas.drawRoundRect(rect, 12f, 12f, fillPaint)
        canvas.drawRoundRect(rect, 12f, 12f, borderPaint)
        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, textPaint)
    }
}
'''
    )

    write(
        "app/src/test/java/com/anurag9000/forestrun/engine/FeedbackSettingsTest.kt",
        '''package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedbackSettingsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(FeedbackSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        FeedbackSettings.resetMemoryForTests()
        FeedbackSettings.init(context)
    }

    @After
    fun tearDown() {
        FeedbackSettings.resetMemoryForTests()
        context.getSharedPreferences(FeedbackSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `defaults preserve full feedback`() {
        assertEquals(FeedbackPreferences(), FeedbackSettings.snapshot())
    }

    @Test
    fun `all preferences persist across memory reload`() {
        FeedbackSettings.setReducedMotion(context, true)
        FeedbackSettings.setAudioEnabled(context, false)
        FeedbackSettings.setHapticsEnabled(context, false)

        FeedbackSettings.resetMemoryForTests()
        FeedbackSettings.init(context)

        assertEquals(
            FeedbackPreferences(reducedMotion = true, audioEnabled = false, hapticsEnabled = false),
            FeedbackSettings.snapshot()
        )
    }

    @Test
    fun `reduced motion prevents camera trauma`() {
        FeedbackSettings.setReducedMotion(context, true)
        CameraSystem.addTrauma(1f)
        assertEquals(0f, CameraSystem.traumaForTest, 0f)
        CameraSystem.update(1f / 60f)
        assertEquals(0f, CameraSystem.offsetX, 0f)
        assertEquals(0f, CameraSystem.offsetY, 0f)
    }

    @Test
    fun `reduced motion lowers particles but never erases a positive cue`() {
        assertEquals(28, adjustedParticleCount(28, reducedMotion = false))
        assertEquals(10, adjustedParticleCount(28, reducedMotion = true))
        assertEquals(1, adjustedParticleCount(1, reducedMotion = true))
        assertEquals(0, adjustedParticleCount(0, reducedMotion = true))
    }

    @Test
    fun `reduced motion freezes cinematic shimmer`() {
        val stillA = cinematicShimmerPulse(0f, 0.5f, reducedMotion = true)
        val stillB = cinematicShimmerPulse(10f, 0.5f, reducedMotion = true)
        assertEquals(stillA, stillB, 0f)
        assertFalse(cinematicShimmerPulse(0f, 0.5f, false) == cinematicShimmerPulse(1f, 0.5f, false))
        assertTrue(stillA in 0f..1f)
    }
}
'''
    )

    write(
        "app/src/test/java/com/anurag9000/forestrun/ui/FeedbackSettingsPanelLayoutTest.kt",
        '''package com.anurag9000.forestrun.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackSettingsPanelLayoutTest {
    @Test
    fun `settings chips remain inside and non-overlapping across supported landscapes`() {
        for ((width, height) in listOf(1280f to 720f, 1920f to 1080f, 2560f to 1440f, 2400f to 1080f)) {
            val layout = FeedbackSettingsPanelLayout.build(width, height)
            for (rect in layout.all) {
                assertTrue(rect.left >= 0f)
                assertTrue(rect.top >= 0f)
                assertTrue(rect.right <= width)
                assertTrue(rect.bottom <= height)
            }
            for (left in layout.all.indices) {
                for (right in left + 1 until layout.all.size) {
                    assertFalse(android.graphics.RectF.intersects(layout.all[left], layout.all[right]))
                }
            }
        }
    }

    @Test
    fun `each chip maps to exactly one setting`() {
        val layout = FeedbackSettingsPanelLayout.build(1920f, 1080f)
        assertEquals(
            FeedbackToggle.REDUCED_MOTION,
            FeedbackSettingsPanelLayout.hitTest(layout, layout.reducedMotion.centerX(), layout.reducedMotion.centerY())
        )
        assertEquals(
            FeedbackToggle.AUDIO,
            FeedbackSettingsPanelLayout.hitTest(layout, layout.audio.centerX(), layout.audio.centerY())
        )
        assertEquals(
            FeedbackToggle.HAPTICS,
            FeedbackSettingsPanelLayout.hitTest(layout, layout.haptics.centerX(), layout.haptics.centerY())
        )
        assertEquals(null, FeedbackSettingsPanelLayout.hitTest(layout, 10f, 10f))
    }
}
'''
    )

    main_activity = Path("app/src/main/java/com/anurag9000/forestrun/MainActivity.kt")
    replace_once(
        main_activity,
        '''        RuntimeAssetValidator.validateRelease(this)
        gameView = GameView(this)
''',
        '''        FeedbackSettings.init(this)
        RuntimeAssetValidator.validateRelease(this)
        gameView = GameView(this)
''',
        "initialize feedback preferences before runtime managers",
    )
    replace_once(
        main_activity,
        '''import com.anurag9000.forestrun.engine.GameView
''',
        '''import com.anurag9000.forestrun.engine.FeedbackSettings
import com.anurag9000.forestrun.engine.GameView
''',
        "FeedbackSettings import",
    )

    haptic = Path("app/src/main/java/com/anurag9000/forestrun/engine/HapticManager.kt")
    replace_once(
        haptic,
        '''    fun bloomSurge() {
        val vib = vibrator ?: return
''',
        '''    fun bloomSurge() {
        if (!FeedbackSettings.hapticsEnabled) return
        val vib = vibrator ?: return
''',
        "Bloom haptic preference gate",
    )
    replace_once(
        haptic,
        '''    fun doubleTap() {
        val vib = vibrator ?: return
''',
        '''    fun doubleTap() {
        if (!FeedbackSettings.hapticsEnabled) return
        val vib = vibrator ?: return
''',
        "double-tap haptic preference gate",
    )
    replace_once(
        haptic,
        '''    private fun pulse(durationMs: Long) {
        val vib = vibrator ?: return
''',
        '''    private fun pulse(durationMs: Long) {
        if (!FeedbackSettings.hapticsEnabled) return
        val vib = vibrator ?: return
''',
        "pulse haptic preference gate",
    )

    sfx = Path("app/src/main/java/com/anurag9000/forestrun/engine/SfxManager.kt")
    replace_once(
        sfx,
        '''    private fun play(id: Int, volume: Float = 1f, rate: Float = RATE_1X) {
        if (id == 0 || id !in readySamples) return
''',
        '''    private fun play(id: Int, volume: Float = 1f, rate: Float = RATE_1X) {
        if (!FeedbackSettings.audioEnabled || id == 0 || id !in readySamples) return
''',
        "SFX audio preference gate",
    )

    camera = Path("app/src/main/java/com/anurag9000/forestrun/engine/CameraSystem.kt")
    replace_once(
        camera,
        '''    fun addTrauma(amount: Float) {
        trauma = (trauma + amount).coerceIn(0f, 1f)
''',
        '''    fun addTrauma(amount: Float) {
        if (FeedbackSettings.reducedMotion) return
        trauma = (trauma + amount).coerceIn(0f, 1f)
''',
        "camera reduced-motion gate",
    )
    replace_once(
        camera,
        '''    /** Reset to zero shake — call on run start/reset. */
    fun reset() {
''',
        '''    internal val traumaForTest: Float
        get() = trauma

    /** Reset to zero shake — call on run start/reset. */
    fun reset() {
''',
        "camera trauma test visibility",
    )

    cinematic = Path("app/src/main/java/com/anurag9000/forestrun/engine/CinematicPolish.kt")
    replace_once(
        cinematic,
        '''        val shimmerPulse = 0.55f + 0.45f * sin(elapsedSeconds * (1.2f + profile.shimmerStrength))
''',
        '''        val shimmerPulse = cinematicShimmerPulse(
            elapsedSeconds = elapsedSeconds,
            shimmerStrength = profile.shimmerStrength,
            reducedMotion = FeedbackSettings.reducedMotion
        )
''',
        "cinematic reduced-motion shimmer",
    )
    replace_once(
        cinematic,
        '''import kotlin.math.sin
''',
        '',
        "remove inline shimmer sine import",
    )

    particles = Path("app/src/main/java/com/anurag9000/forestrun/systems/ParticleManager.kt")
    replace_once(
        particles,
        '''import android.graphics.RectF
''',
        '''import android.graphics.RectF
import com.anurag9000.forestrun.engine.FeedbackSettings
import com.anurag9000.forestrun.engine.adjustedParticleCount
''',
        "particle preference imports",
    )
    replace_once(
        particles,
        '''    fun emit(emitter: ParticleEmitter) {
        repeat(emitter.count) {
''',
        '''    fun emit(emitter: ParticleEmitter) {
        repeat(adjustedParticleCount(emitter.count, FeedbackSettings.reducedMotion)) {
''',
        "reduced particle density",
    )

    leitmotif = Path("app/src/main/java/com/anurag9000/forestrun/engine/LeitmotifManager.kt")
    replace_once(
        leitmotif,
        '''    private var currentState: MusicState = MusicState.MENU
''',
        '''    private var audioEnabled = true
    private var currentState: MusicState = MusicState.MENU
''',
        "music preference state",
    )
    replace_once(
        leitmotif,
        '''    fun init(context: Context) {
        synchronized(audioLock) {
            ctx = context.applicationContext
        }
        // currentState starts as MENU, but no MediaPlayer exists yet. The
        // transition method therefore checks both state and player presence.
        transitionTo(MusicState.MENU)
    }
''',
        '''    fun init(context: Context) {
        val enabled = FeedbackSettings.audioEnabled
        synchronized(audioLock) {
            ctx = context.applicationContext
            audioEnabled = enabled
        }
        // currentState starts as MENU, but no MediaPlayer exists yet. The
        // transition method therefore checks both state and player presence.
        if (enabled) transitionTo(MusicState.MENU)
    }

    fun setAudioEnabled(enabled: Boolean) {
        var resumeState: MusicState? = null
        synchronized(audioLock) {
            if (audioEnabled == enabled) return
            audioEnabled = enabled
            if (!enabled) {
                stopFadeLocked()
                releasePlayer(activePlayer)
                releasePlayer(fadingPlayer)
                activePlayer = null
                fadingPlayer = null
            } else if (ctx != null) {
                resumeState = currentState
            }
        }
        resumeState?.let(::transitionTo)
    }
''',
        "music preference API",
    )
    replace_once(
        leitmotif,
        '''        synchronized(audioLock) {
            if (newState == currentState && activePlayer != null) return

            val appContext = ctx ?: return
''',
        '''        synchronized(audioLock) {
            if (!audioEnabled) {
                previousState = currentState
                currentState = newState
                if (newState == MusicState.BLOOM) bloomMusicSignature = defaultBloomMusicSignature
                return
            }
            if (newState == currentState && activePlayer != null) return

            val appContext = ctx ?: return
''',
        "music transition preference gate",
    )
    replace_once(
        leitmotif,
        '''    fun resume() {
        synchronized(audioLock) {
            runCatching { activePlayer?.start() }
''',
        '''    fun resume() {
        synchronized(audioLock) {
            if (!audioEnabled) return
            runCatching { activePlayer?.start() }
''',
        "music resume preference gate",
    )

    menu = Path("app/src/main/java/com/anurag9000/forestrun/ui/MainMenuScreen.kt")
    replace_once(
        menu,
        '''    private val cinematicOverlay = CinematicOverlayRenderer()
''',
        '''    private val cinematicOverlay = CinematicOverlayRenderer()
    private val feedbackSettingsPanel = FeedbackSettingsPanel(context, screenW, screenH)
''',
        "menu feedback panel field",
    )
    replace_once(
        menu,
        '''    fun onTap(tapX: Float = 0f, tapY: Float = 0f) {
        // Garden button: bottom-left strip in IDLE phase
''',
        '''    fun onTap(tapX: Float = 0f, tapY: Float = 0f) {
        if (phase == Phase.IDLE && feedbackSettingsPanel.onTap(tapX, tapY)) return
        // Garden button: bottom-left strip in IDLE phase
''',
        "menu settings touch routing",
    )
    replace_once(
        menu,
        '''        drawHomecomingConsequences(canvas, cw, ch, groundY)

        // Prompt
''',
        '''        drawHomecomingConsequences(canvas, cw, ch, groundY)
        if (phase == Phase.IDLE) feedbackSettingsPanel.draw(canvas)

        // Prompt
''',
        "menu settings rendering",
    )


if __name__ == "__main__":
    main()
