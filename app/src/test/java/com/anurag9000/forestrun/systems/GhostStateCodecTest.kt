package com.anurag9000.forestrun.systems

import com.anurag9000.forestrun.entities.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostStateCodecTest {
    @Test
    fun `every player state has one stable round trip code`() {
        val codes = PlayerState.entries.map { state -> GhostStateCodec.encode(state) }

        assertEquals(codes.size, codes.toSet().size)
        PlayerState.entries.forEach { state ->
            assertEquals(
                state.ordinal,
                GhostStateCodec.decodeToOrdinal(GhostStateCodec.encode(state))
            )
            assertEquals(GhostStateCodec.encode(state), GhostStateCodec.encodeOrdinal(state.ordinal))
        }
    }

    @Test
    fun `legacy Bloom code remains between ducking and stumble`() {
        assertEquals(6, GhostStateCodec.encode(PlayerState.DUCKING))
        assertEquals(7, GhostStateCodec.encode(PlayerState.BLOOM))
        assertEquals(8, GhostStateCodec.encode(PlayerState.STUMBLE))
        assertEquals(9, GhostStateCodec.encode(PlayerState.REST))
    }

    @Test
    fun `unknown state codes and ordinals are rejected`() {
        assertNull(GhostStateCodec.decodeToOrdinal(-1))
        assertNull(GhostStateCodec.decodeToOrdinal(Int.MAX_VALUE))
        assertNull(GhostStateCodec.encodeOrdinal(-1))
        assertNull(GhostStateCodec.encodeOrdinal(PlayerState.entries.size))
    }

    @Test
    fun `stable codes remain compact for binary persistence`() {
        assertTrue(PlayerState.entries.all { GhostStateCodec.encode(it) in 0..255 })
    }
}
