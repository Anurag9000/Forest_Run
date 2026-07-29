package com.anurag9000.forestrun.systems

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ParticleEmitterCacheTest {

    @After
    fun tearDown() {
        ParticleManager.resetBurstEmitterCacheForTests()
    }

    @Test
    fun `burst preset reuses one emitter while refreshing its origin`() {
        ParticleManager.resetBurstEmitterCacheForTests()

        ParticleManager.emit(FxPreset.JUMP_DUST, 100f, 200f)
        val first = ParticleManager.cachedBurstEmitterForTest(FxPreset.JUMP_DUST)
        assertNotNull(first)
        assertEquals(1, ParticleManager.burstEmitterBuildCountForTest)
        assertEquals(100f, first!!.x, 0f)
        assertEquals(200f, first.y, 0f)

        ParticleManager.emit(FxPreset.JUMP_DUST, 420f, 360f)
        val second = ParticleManager.cachedBurstEmitterForTest(FxPreset.JUMP_DUST)

        assertSame(first, second)
        assertEquals(1, ParticleManager.burstEmitterBuildCountForTest)
        assertEquals(420f, second!!.x, 0f)
        assertEquals(360f, second.y, 0f)
    }

    @Test
    fun `different burst presets receive independent cached emitters`() {
        ParticleManager.resetBurstEmitterCacheForTests()

        ParticleManager.emit(FxPreset.JUMP_DUST, 10f, 20f)
        ParticleManager.emit(FxPreset.LAND_THUD, 30f, 40f)

        val jump = ParticleManager.cachedBurstEmitterForTest(FxPreset.JUMP_DUST)
        val land = ParticleManager.cachedBurstEmitterForTest(FxPreset.LAND_THUD)
        assertNotNull(jump)
        assertNotNull(land)
        assertEquals(2, ParticleManager.burstEmitterBuildCountForTest)
        check(jump !== land)
    }

    @Test
    fun `continuous presets are never retained in the burst cache`() {
        ParticleManager.resetBurstEmitterCacheForTests()

        ParticleManager.emit(FxPreset.PETAL_DRIFT, 15f, 25f)

        assertNull(ParticleManager.cachedBurstEmitterForTest(FxPreset.PETAL_DRIFT))
        assertEquals(0, ParticleManager.burstEmitterBuildCountForTest)
    }
}
