package com.anurag9000.forestrun.systems

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticleTest {

    @Test
    fun `invalid lifetime is immediately terminal with bounded appearance`() {
        val particle = Particle(
            lifetime = Float.NaN,
            elapsed = 0f,
            startSize = Float.NaN,
            endSize = -10f,
            isActive = true
        )

        assertTrue(particle.isDead)
        assertEquals(1f, particle.progress, 0f)
        assertEquals(0f, particle.currentSize, 0f)
        assertEquals(Color.TRANSPARENT, particle.currentColor)
    }

    @Test
    fun `invalid update delta is a no op`() {
        val particle = Particle(
            x = 10f,
            y = 20f,
            velX = 30f,
            velY = 40f,
            lifetime = 10f,
            isActive = true
        )

        particle.update(Float.NaN)
        particle.update(Float.POSITIVE_INFINITY)
        particle.update(-1f)

        assertEquals(10f, particle.x, 0f)
        assertEquals(20f, particle.y, 0f)
        assertEquals(0f, particle.elapsed, 0f)
        assertTrue(particle.isActive)
    }

    @Test
    fun `non finite kinematics deactivate particle before drawing`() {
        val particle = Particle(
            x = Float.NaN,
            y = 20f,
            lifetime = 10f,
            isActive = true
        )

        particle.update(0.1f)

        assertFalse(particle.isActive)
    }

    @Test
    fun `extreme finite physics saturates without becoming non finite`() {
        val particle = Particle(
            x = Float.MAX_VALUE,
            y = -Float.MAX_VALUE,
            velX = Float.MAX_VALUE,
            velY = -Float.MAX_VALUE,
            gravity = Float.MAX_VALUE,
            drag = 1f,
            lifetime = 100f,
            spinRate = Float.MAX_VALUE,
            rotation = 359f,
            isActive = true
        )

        particle.update(1f)

        assertTrue(particle.x.isFinite())
        assertTrue(particle.y.isFinite())
        assertTrue(particle.velX.isFinite())
        assertTrue(particle.velY.isFinite())
        assertTrue(particle.rotation.isFinite())
        assertTrue(particle.rotation in -360f..360f)
    }

    @Test
    fun `particle stops integrating once lifetime is reached`() {
        val particle = Particle(
            x = 10f,
            y = 20f,
            velX = 1_000f,
            lifetime = 0.5f,
            isActive = true
        )

        particle.update(1f)

        assertEquals(0.5f, particle.elapsed, 0f)
        assertEquals(10f, particle.x, 0f)
        assertTrue(particle.isDead)
    }

    @Test
    fun `reset restores every pooled field`() {
        val particle = Particle(
            x = 10f,
            y = 20f,
            velX = 30f,
            velY = 40f,
            gravity = 50f,
            drag = 0.1f,
            lifetime = 5f,
            elapsed = 2f,
            startColor = Color.RED,
            endColor = Color.BLUE,
            startSize = 30f,
            endSize = 20f,
            isCircle = false,
            rotation = 180f,
            spinRate = 90f,
            isActive = true
        )

        particle.reset()

        assertEquals(0f, particle.x, 0f)
        assertEquals(0f, particle.y, 0f)
        assertEquals(0f, particle.velX, 0f)
        assertEquals(0f, particle.velY, 0f)
        assertEquals(0f, particle.gravity, 0f)
        assertEquals(0.92f, particle.drag, 0f)
        assertEquals(1f, particle.lifetime, 0f)
        assertEquals(0f, particle.elapsed, 0f)
        assertEquals(Color.WHITE, particle.startColor)
        assertEquals(Color.TRANSPARENT, particle.endColor)
        assertEquals(8f, particle.startSize, 0f)
        assertEquals(0f, particle.endSize, 0f)
        assertTrue(particle.isCircle)
        assertEquals(0f, particle.rotation, 0f)
        assertEquals(0f, particle.spinRate, 0f)
        assertFalse(particle.isActive)
    }
}
