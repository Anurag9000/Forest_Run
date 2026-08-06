package com.anurag9000.forestrun.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PackedArgbTest {
    @Test
    fun `rgb packs opaque channels exactly`() {
        assertEquals(0xFF000000.toInt(), PackedArgb.rgb(0, 0, 0))
        assertEquals(0xFFFFFFFF.toInt(), PackedArgb.rgb(255, 255, 255))
        assertEquals(0xFF90D2FF.toInt(), PackedArgb.rgb(144, 210, 255))
        assertEquals(0xFF50A050.toInt(), PackedArgb.rgb(80, 160, 80))
    }

    @Test
    fun `argb preserves alpha and channel positions`() {
        assertEquals(0x7F123456, PackedArgb.argb(127, 18, 52, 86))
        assertEquals(0x00112233, PackedArgb.argb(0, 17, 34, 51))
    }

    @Test
    fun `channels outside byte range are rejected`() {
        listOf(
            intArrayOf(-1, 0, 0, 0),
            intArrayOf(256, 0, 0, 0),
            intArrayOf(255, -1, 0, 0),
            intArrayOf(255, 0, 256, 0),
            intArrayOf(255, 0, 0, -1)
        ).forEach { channels ->
            assertThrows(IllegalArgumentException::class.java) {
                PackedArgb.argb(
                    alpha = channels[0],
                    red = channels[1],
                    green = channels[2],
                    blue = channels[3]
                )
            }
        }
    }

    @Test
    fun `biome catalogue loads without Android framework color calls`() {
        assertEquals(5, Biome.entries.size)
        assertEquals(0xFF90D2FF.toInt(), Biome.MEADOW.skyTopColour)
        assertEquals(0xFF0A0A28.toInt(), Biome.NIGHT_FOREST.skyTopColour)
    }
}
