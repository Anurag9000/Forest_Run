package com.anurag9000.forestrun.systems

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class ParticleEmitterCacheTest {

    @After
    fun tearDown() {
        ParticleManager.resetOneShotEmitterCacheForTests()
    }

    @Test
    fun `one shot preset reuses one emitter while refreshing its origin`() {
        ParticleManager.resetOneShotEmitterCacheForTests()

        ParticleManager.emit(FxPreset.JUMP_DUST, 100f, 200f)
        val first = ParticleManager.cachedOneShotEmitterForTest(FxPreset.JUMP_DUST)
        assertNotNull(first)
        assertEquals(1, ParticleManager.oneShotEmitterBuildCountForTest)
        assertEquals(100f, first!!.x, 0f)
        assertEquals(200f, first.y, 0f)

        ParticleManager.emit(FxPreset.JUMP_DUST, 420f, 360f)
        val second = ParticleManager.cachedOneShotEmitterForTest(FxPreset.JUMP_DUST)

        assertSame(first, second)
        assertEquals(1, ParticleManager.oneShotEmitterBuildCountForTest)
        assertEquals(420f, second!!.x, 0f)
        assertEquals(360f, second.y, 0f)
    }

    @Test
    fun `different one shot presets receive independent cached emitters`() {
        ParticleManager.resetOneShotEmitterCacheForTests()

        ParticleManager.emit(FxPreset.JUMP_DUST, 10f, 20f)
        ParticleManager.emit(FxPreset.LAND_THUD, 30f, 40f)

        val jump = ParticleManager.cachedOneShotEmitterForTest(FxPreset.JUMP_DUST)
        val land = ParticleManager.cachedOneShotEmitterForTest(FxPreset.LAND_THUD)
        assertNotNull(jump)
        assertNotNull(land)
        assertEquals(2, ParticleManager.oneShotEmitterBuildCountForTest)
        assertNotSame(jump, land)
    }

    @Test
    fun `continuous preset used one shot is cached without sharing owner timers`() {
        ParticleManager.resetOneShotEmitterCacheForTests()

        ParticleManager.emit(FxPreset.PETAL_DRIFT, 15f, 25f)
        val cached = ParticleManager.cachedOneShotEmitterForTest(FxPreset.PETAL_DRIFT)
        val ownerA = FxPreset.PETAL_DRIFT.build(30f, 40f)
        val ownerB = FxPreset.PETAL_DRIFT.build(50f, 60f)

        assertNotNull(cached)
        assertEquals(1, ParticleManager.oneShotEmitterBuildCountForTest)
        assertNotSame(cached, ownerA)
        assertNotSame(ownerA, ownerB)
    }
}
