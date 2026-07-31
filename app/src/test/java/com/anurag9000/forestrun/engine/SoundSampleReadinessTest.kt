package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundSampleReadinessTest {
    @Test
    fun `current successful callback marks only its sample ready`() {
        val registry = SoundSampleReadiness()
        val generation = registry.beginGeneration()

        val result = registry.complete(generation, sampleId = 4, status = 0)

        assertEquals(SoundSampleReadiness.CompletionResult.READY, result)
        assertTrue(registry.isReady(4))
        assertFalse(registry.isReady(5))
    }

    @Test
    fun `failed callback removes readiness`() {
        val registry = SoundSampleReadiness()
        val generation = registry.beginGeneration()
        registry.complete(generation, sampleId = 4, status = 0)

        val result = registry.complete(generation, sampleId = 4, status = -1)

        assertEquals(SoundSampleReadiness.CompletionResult.FAILED, result)
        assertFalse(registry.isReady(4))
    }

    @Test
    fun `callback from released generation cannot ready reused sample id`() {
        val registry = SoundSampleReadiness()
        val releasedGeneration = registry.beginGeneration()
        registry.invalidate()
        val replacementGeneration = registry.beginGeneration()

        val staleResult = registry.complete(releasedGeneration, sampleId = 1, status = 0)

        assertEquals(SoundSampleReadiness.CompletionResult.STALE, staleResult)
        assertFalse(registry.isReady(1))

        val activeResult = registry.complete(replacementGeneration, sampleId = 1, status = 0)
        assertEquals(SoundSampleReadiness.CompletionResult.READY, activeResult)
        assertTrue(registry.isReady(1))
    }

    @Test
    fun `starting a replacement generation clears prior readiness`() {
        val registry = SoundSampleReadiness()
        val first = registry.beginGeneration()
        registry.complete(first, sampleId = 8, status = 0)
        assertTrue(registry.isReady(8))

        val second = registry.beginGeneration()

        assertTrue(second != first)
        assertFalse(registry.isReady(8))
    }

    @Test
    fun `zero sample id is never ready`() {
        val registry = SoundSampleReadiness()
        val generation = registry.beginGeneration()

        assertEquals(
            SoundSampleReadiness.CompletionResult.FAILED,
            registry.complete(generation, sampleId = 0, status = 0)
        )
        assertFalse(registry.isReady(0))
    }
}
