package com.anurag9000.forestrun.systems

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostRunIdentityTest {

    @Test
    fun `canonical SHA-256 matches independent persisted-byte golden vector`() {
        val identity = GhostRunIdentity.calculate(frames())

        assertEquals(-4_791_329_882_507_978_193L, identity.fingerprint)
        assertEquals(
            "1d3081358ab985faef01869defb5c7a2353f547c165d381b2d80a066c461087f",
            identity.sha256Hex
        )
        assertTrue(GhostRunIdentity.isCanonicalSha256(identity.sha256Hex))
    }

    @Test
    fun `strong identity covers frame count and every persisted frame field`() {
        val base = frames()
        val expected = GhostRunIdentity.calculate(base)
        val variants = listOf(
            base.toMutableList().apply { this[0] = this[0].copy(t = 0.01f) },
            base.toMutableList().apply { this[0] = this[0].copy(x = 101f) },
            base.toMutableList().apply { this[0] = this[0].copy(y = 201f) },
            base.toMutableList().apply { this[0] = this[0].copy(stateOrdinal = 1) },
            base.toMutableList().apply { this[0] = this[0].copy(scaleX = 0.9f) },
            base.toMutableList().apply { this[0] = this[0].copy(scaleY = 1.1f) },
            base + GhostFrame(0.08f, 108f, 192f, 0, 1f, 1f)
        )

        variants.forEach { variant ->
            val actual = GhostRunIdentity.calculate(variant)
            assertFalse(expected.sha256Hex == actual.sha256Hex)
        }
    }

    @Test
    fun `hex codec round trips all byte values`() {
        val bytes = ByteArray(GhostRunIdentity.SHA256_BYTE_COUNT) { index ->
            (index * 7 - 128).toByte()
        }

        val encoded = GhostRunIdentity.encodeHex(bytes)
        val decoded = GhostRunIdentity.decodeSha256(encoded)

        assertEquals(64, encoded.length)
        assertArrayEquals(bytes, decoded)
    }

    @Test
    fun `canonical digest rejects uppercase malformed and wrong length text`() {
        val valid = GhostRunIdentity.calculate(frames()).sha256Hex

        assertFalse(GhostRunIdentity.isCanonicalSha256(valid.uppercase()))
        assertFalse(GhostRunIdentity.isCanonicalSha256("g" + valid.drop(1)))
        assertFalse(GhostRunIdentity.isCanonicalSha256(valid.dropLast(1)))
        assertFalse(GhostRunIdentity.isCanonicalSha256(valid + "0"))
        assertEquals(null, GhostRunIdentity.decodeSha256(valid.uppercase()))
    }

    @Test
    fun `matching uses SHA-256 when present and legacy fingerprint only when absent`() {
        val frames = frames()
        val identity = GhostRunIdentity.calculate(frames)
        val tamperedDigest = (if (identity.sha256Hex.first() == '0') '1' else '0') +
            identity.sha256Hex.drop(1)

        assertTrue(
            GhostRunIdentity.matches(
                frames,
                frames.size,
                identity.fingerprint,
                identity.sha256Hex
            )
        )
        assertFalse(
            GhostRunIdentity.matches(
                frames,
                frames.size,
                identity.fingerprint,
                tamperedDigest
            )
        )
        assertTrue(
            GhostRunIdentity.matches(
                frames,
                frames.size,
                GhostRunFingerprint.calculate(frames),
                null
            )
        )
    }

    private fun frames(): List<GhostFrame> = listOf(
        GhostFrame(0f, 100f, 200f, 0, 1f, 1f),
        GhostFrame(0.04f, 104f, 196f, 1, 0.98f, 1.02f)
    )
}
