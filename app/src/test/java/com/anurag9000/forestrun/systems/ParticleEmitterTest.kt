package com.anurag9000.forestrun.systems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticleEmitterTest {

    @Test
    fun `every authored FX preset builds a valid finite emitter`() {
        FxPreset.entries.forEach { preset ->
            val emitter = preset.build(100f, 200f)
            val particle = Particle()

            emitter.configure(particle)

            assertTrue(particle.isActive)
            assertTrue(particle.x.isFinite())
            assertTrue(particle.y.isFinite())
            assertTrue(particle.velX.isFinite())
            assertTrue(particle.velY.isFinite())
            assertTrue(particle.lifetime.isFinite() && particle.lifetime > 0f)
            assertTrue(particle.startSize.isFinite() && particle.startSize >= 0f)
            assertTrue(particle.endSize.isFinite() && particle.endSize >= 0f)
        }
    }

    @Test
    fun `continuous emission accumulates fractional intervals without a loop`() {
        val emitter = ParticleEmitter(
            x = 0f,
            y = 0f,
            isBurst = false,
            count = 4
        )

        assertEquals(0, emitter.updateContinuous(0.24f))
        assertEquals(1, emitter.updateContinuous(0.01f))
        assertEquals(2, emitter.updateContinuous(0.50f))
    }

    @Test
    fun `continuous catch up is capped and backlog is dropped`() {
        val emitter = ParticleEmitter(
            x = 0f,
            y = 0f,
            isBurst = false,
            count = ParticleEmitter.MAX_CONFIGURED_COUNT
        )

        assertEquals(
            ParticleEmitter.MAX_CONTINUOUS_SPAWN_PER_UPDATE,
            emitter.updateContinuous(Float.MAX_VALUE)
        )
        assertEquals(0, emitter.updateContinuous(0.0001f))
    }

    @Test
    fun `invalid deltas and stopped emitter produce no particles`() {
        val emitter = ParticleEmitter(x = 0f, y = 0f, isBurst = false, count = 8)

        assertEquals(0, emitter.updateContinuous(Float.NaN))
        assertEquals(0, emitter.updateContinuous(Float.POSITIVE_INFINITY))
        assertEquals(0, emitter.updateContinuous(-1f))
        emitter.stop()
        assertEquals(0, emitter.updateContinuous(10f))
        emitter.resume()
        assertEquals(8, emitter.updateContinuous(1f))
    }

    @Test
    fun `mutable origin ignores non finite replacement`() {
        val emitter = ParticleEmitter(x = 10f, y = 20f)

        emitter.x = Float.NaN
        emitter.y = Float.POSITIVE_INFINITY

        assertEquals(10f, emitter.x, 0f)
        assertEquals(20f, emitter.y, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero count is rejected`() {
        ParticleEmitter(x = 0f, y = 0f, count = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reversed lifetime is rejected`() {
        ParticleEmitter(x = 0f, y = 0f, lifetimeMin = 2f, lifetimeMax = 1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `amplifying drag is rejected`() {
        ParticleEmitter(x = 0f, y = 0f, drag = 1.1f)
    }
}
