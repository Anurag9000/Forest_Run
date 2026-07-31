package com.anurag9000.forestrun.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceResizePolicyTest {
    @Test
    fun `initialized dimension-bound systems require recreation after a real resize`() {
        assertTrue(
            SurfaceResizePolicy.requiresActivityRecreation(
                previousWidth = 960,
                previousHeight = 540,
                newWidth = 1200,
                newHeight = 540,
                dimensionBoundSystemsInitialized = true
            )
        )
    }

    @Test
    fun `same dimensions do not trigger a recreation loop`() {
        assertFalse(
            SurfaceResizePolicy.requiresActivityRecreation(
                previousWidth = 960,
                previousHeight = 540,
                newWidth = 960,
                newHeight = 540,
                dimensionBoundSystemsInitialized = true
            )
        )
    }

    @Test
    fun `startup and invalid transient dimensions are ignored`() {
        assertFalse(
            SurfaceResizePolicy.requiresActivityRecreation(
                previousWidth = 0,
                previousHeight = 0,
                newWidth = 960,
                newHeight = 540,
                dimensionBoundSystemsInitialized = true
            )
        )
        assertFalse(
            SurfaceResizePolicy.requiresActivityRecreation(
                previousWidth = 960,
                previousHeight = 540,
                newWidth = 0,
                newHeight = 0,
                dimensionBoundSystemsInitialized = true
            )
        )
        assertFalse(
            SurfaceResizePolicy.requiresActivityRecreation(
                previousWidth = 960,
                previousHeight = 540,
                newWidth = 1200,
                newHeight = 540,
                dimensionBoundSystemsInitialized = false
            )
        )
    }
}
