package com.anurag9000.forestrun.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraSystemTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FeedbackSettings.resetMemoryForTests()
        CameraSystem.reset()
    }

    @Test
    fun `invalid and nonpositive trauma are ignored`() {
        CameraSystem.addTrauma(Float.NaN)
        CameraSystem.addTrauma(Float.POSITIVE_INFINITY)
        CameraSystem.addTrauma(-1f)
        CameraSystem.addTrauma(0f)

        assertEquals(0f, CameraSystem.traumaForTest, 0f)
    }

    @Test
    fun `trauma compounds only within bounded range`() {
        CameraSystem.addTrauma(0.8f)
        CameraSystem.addTrauma(0.8f)

        assertEquals(1f, CameraSystem.traumaForTest, 0f)
        CameraSystem.update(0.1f)
        assertTrue(CameraSystem.traumaForTest in 0f..1f)
        assertTrue(CameraSystem.offsetX.isFinite())
        assertTrue(CameraSystem.offsetY.isFinite())
    }

    @Test
    fun `invalid update delta leaves finite state unchanged`() {
        CameraSystem.addTrauma(0.8f)
        val trauma = CameraSystem.traumaForTest

        CameraSystem.update(Float.NaN)
        CameraSystem.update(Float.POSITIVE_INFINITY)
        CameraSystem.update(-1f)

        assertEquals(trauma, CameraSystem.traumaForTest, 0f)
        assertEquals(0f, CameraSystem.offsetX, 0f)
        assertEquals(0f, CameraSystem.offsetY, 0f)
    }

    @Test
    fun `enabling reduced motion clears active shake immediately`() {
        CameraSystem.addTrauma(0.9f)
        CameraSystem.update(0.05f)

        FeedbackSettings.setReducedMotion(context, true)
        CameraSystem.update(0.05f)

        assertEquals(0f, CameraSystem.traumaForTest, 0f)
        assertEquals(0f, CameraSystem.offsetX, 0f)
        assertEquals(0f, CameraSystem.offsetY, 0f)
    }

    @Test
    fun `applyTo restores Canvas even when drawing throws`() {
        setFloatField("offsetX", 5f)
        setFloatField("offsetY", 7f)
        val canvas = Canvas(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
        val saveCount = canvas.saveCount

        runCatching {
            CameraSystem.applyTo(canvas) {
                throw IllegalStateException("draw failure")
            }
        }

        assertEquals(saveCount, canvas.saveCount)
    }

    @Test
    fun `reset clears all visible and latent shake state`() {
        CameraSystem.addTrauma(1f)
        CameraSystem.update(0.05f)

        CameraSystem.reset()

        assertEquals(0f, CameraSystem.traumaForTest, 0f)
        assertEquals(0f, CameraSystem.offsetX, 0f)
        assertEquals(0f, CameraSystem.offsetY, 0f)
    }

    private fun setFloatField(name: String, value: Float) {
        val field = CameraSystem::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setFloat(CameraSystem, value)
    }
}
