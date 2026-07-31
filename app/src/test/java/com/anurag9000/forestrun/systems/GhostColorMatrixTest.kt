package com.anurag9000.forestrun.systems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostColorMatrixTest {

    @Test
    fun `ghost matrix combines desaturation with the authored cool tint`() {
        val matrix = ghostColorMatrixValues()

        assertEquals(20, matrix.size)
        assertTrue(matrix[1] > 0f)
        assertTrue(matrix[2] > 0f)
        assertTrue(matrix[5] > 0f)
        assertTrue(matrix[7] > 0f)
        assertTrue(matrix[10] > 0f)
        assertTrue(matrix[11] > 0f)

        assertEquals(0.8f, matrix[0] + matrix[1] + matrix[2], 0.0001f)
        assertEquals(0.8f, matrix[5] + matrix[6] + matrix[7], 0.0001f)
        assertEquals(1.1f, matrix[10] + matrix[11] + matrix[12], 0.0001f)

        assertEquals(20f, matrix[4], 0f)
        assertEquals(30f, matrix[9], 0f)
        assertEquals(60f, matrix[14], 0f)
        assertEquals(0f, matrix[15], 0f)
        assertEquals(0f, matrix[16], 0f)
        assertEquals(0f, matrix[17], 0f)
        assertEquals(1f, matrix[18], 0f)
        assertEquals(0f, matrix[19], 0f)
    }

    @Test
    fun `neutral grey remains neutral before offsets and receives a blue push`() {
        val matrix = ghostColorMatrixValues()
        val input = floatArrayOf(100f, 100f, 100f, 255f)

        val red = transformChannel(matrix, row = 0, input)
        val green = transformChannel(matrix, row = 1, input)
        val blue = transformChannel(matrix, row = 2, input)
        val alpha = transformChannel(matrix, row = 3, input)

        assertEquals(100f, red, 0.001f)
        assertEquals(110f, green, 0.001f)
        assertEquals(170f, blue, 0.001f)
        assertEquals(255f, alpha, 0.001f)
        assertTrue(blue > green)
        assertTrue(green > red)
    }

    private fun transformChannel(matrix: FloatArray, row: Int, input: FloatArray): Float {
        val offset = row * 5
        return matrix[offset] * input[0] +
            matrix[offset + 1] * input[1] +
            matrix[offset + 2] * input[2] +
            matrix[offset + 3] * input[3] +
            matrix[offset + 4]
    }
}
