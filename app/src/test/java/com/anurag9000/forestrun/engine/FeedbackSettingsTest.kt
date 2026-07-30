package com.anurag9000.forestrun.engine

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
    fun `wrong typed stored preferences fall back safely and are repaired`() {
        val prefs = context.getSharedPreferences(FeedbackSettings.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("reduced_motion", "broken")
            .putInt("audio_enabled", 7)
            .putFloat("haptics_enabled", 1f)
            .commit()

        FeedbackSettings.init(context)

        assertEquals(FeedbackPreferences(), FeedbackSettings.snapshot())
        assertEquals(false, prefs.getBoolean("reduced_motion", true))
        assertEquals(true, prefs.getBoolean("audio_enabled", false))
        assertEquals(true, prefs.getBoolean("haptics_enabled", false))
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
        assertTrue(adjustedParticleCount(Int.MAX_VALUE, reducedMotion = true) in 1..Int.MAX_VALUE)
    }

    @Test
    fun `reduced motion freezes cinematic shimmer`() {
        val stillA = cinematicShimmerPulse(0f, 0.5f, reducedMotion = true)
        val stillB = cinematicShimmerPulse(10f, 0.5f, reducedMotion = true)
        assertEquals(stillA, stillB, 0f)
        assertFalse(cinematicShimmerPulse(0f, 0.5f, false) == cinematicShimmerPulse(1f, 0.5f, false))
        assertTrue(stillA in 0f..1f)
    }

    @Test
    fun `cinematic shimmer remains finite for invalid and extreme inputs`() {
        val baseline = cinematicShimmerPulse(0f, 0f, reducedMotion = false)
        listOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        ).forEach { invalid ->
            assertEquals(baseline, cinematicShimmerPulse(invalid, invalid, false), 0f)
        }

        val extreme = cinematicShimmerPulse(Float.MAX_VALUE, Float.MAX_VALUE, false)
        assertTrue(extreme.isFinite())
        assertTrue(extreme in 0f..1f)
    }

    @Test
    fun `negative shimmer strength is normalized without changing elapsed timing`() {
        assertEquals(
            cinematicShimmerPulse(2f, 0f, false),
            cinematicShimmerPulse(2f, -10f, false),
            0f
        )
    }
}
