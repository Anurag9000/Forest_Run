package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class BloomPresentationAdmissionTest {

    @Test
    fun `nonfinite levels fail closed`() {
        assertEquals(0f, BloomPresentationAdmission.level(Float.NaN), 0f)
        assertEquals(0f, BloomPresentationAdmission.level(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, BloomPresentationAdmission.level(Float.NEGATIVE_INFINITY), 0f)
    }

    @Test
    fun `finite levels retain unit interval clamping`() {
        assertEquals(0f, BloomPresentationAdmission.level(-0.25f), 0f)
        assertEquals(0f, BloomPresentationAdmission.level(0f), 0f)
        assertEquals(0.35f, BloomPresentationAdmission.level(0.35f), 0f)
        assertEquals(1f, BloomPresentationAdmission.level(1f), 0f)
        assertEquals(1f, BloomPresentationAdmission.level(1.75f), 0f)
    }
}
