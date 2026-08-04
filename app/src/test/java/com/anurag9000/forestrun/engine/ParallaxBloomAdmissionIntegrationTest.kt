package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ParallaxBloomAdmissionIntegrationTest {

    @Test
    fun `nonfinite activation and afterglow fail closed`() {
        val background = ParallaxBackground(screenWidth = 320, screenHeight = 180)

        background.setBloomState(
            isActive = true,
            activationLevel = Float.NaN,
            afterglowLevel = Float.POSITIVE_INFINITY
        )

        assertEquals(1f, background.privateFloat("bloomTarget"), 0f)
        assertEquals(0f, background.privateFloat("bloomActivationLevel"), 0f)
        assertEquals(0f, background.privateFloat("bloomAfterglowLevel"), 0f)

        background.setBloomState(
            isActive = true,
            activationLevel = Float.NEGATIVE_INFINITY,
            afterglowLevel = Float.NaN
        )

        assertEquals(0f, background.privateFloat("bloomActivationLevel"), 0f)
        assertEquals(0f, background.privateFloat("bloomAfterglowLevel"), 0f)
    }

    @Test
    fun `finite activation and afterglow preserve clamping`() {
        val background = ParallaxBackground(screenWidth = 320, screenHeight = 180)

        background.setBloomState(
            isActive = false,
            activationLevel = -0.25f,
            afterglowLevel = 1.75f
        )

        assertEquals(0f, background.privateFloat("bloomTarget"), 0f)
        assertEquals(0f, background.privateFloat("bloomActivationLevel"), 0f)
        assertEquals(1f, background.privateFloat("bloomAfterglowLevel"), 0f)

        background.setBloomState(
            isActive = true,
            activationLevel = 0.35f,
            afterglowLevel = 0.65f
        )

        assertEquals(0.35f, background.privateFloat("bloomActivationLevel"), 0f)
        assertEquals(0.65f, background.privateFloat("bloomAfterglowLevel"), 0f)
    }

    private fun ParallaxBackground.privateFloat(fieldName: String): Float {
        val field = javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.getFloat(this)
    }
}
