package com.anurag9000.forestrun.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiniteCoordinateAdmissionTest {

    @Test
    fun `accepts every finite coordinate shape`() {
        val finite = floatArrayOf(
            -Float.MAX_VALUE,
            -1f,
            -0f,
            0f,
            1f,
            Float.MIN_VALUE,
            Float.MAX_VALUE
        )

        for (x in finite) {
            for (y in finite) {
                assertTrue("expected finite pair x=$x y=$y", FiniteCoordinateAdmission.accepts(x, y))
            }
        }
    }

    @Test
    fun `rejects nonfinite value on either axis`() {
        val nonfinite = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        val finite = floatArrayOf(-1f, 0f, 1f)

        for (value in nonfinite) {
            for (other in finite) {
                assertFalse(FiniteCoordinateAdmission.accepts(value, other))
                assertFalse(FiniteCoordinateAdmission.accepts(other, value))
            }
        }
    }

    @Test
    fun `rejects every nonfinite pair`() {
        val nonfinite = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)

        for (x in nonfinite) {
            for (y in nonfinite) {
                assertFalse(FiniteCoordinateAdmission.accepts(x, y))
            }
        }
    }
}
