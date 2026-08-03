package com.anurag9000.forestrun.systems

import java.security.MessageDigest

/** Canonical identities for one validated ghost frame sequence. */
internal data class GhostRunIdentityValue(
    val fingerprint: Long,
    val sha256Hex: String
)

/**
 * Computes and verifies ghost identity over exactly the fields persisted by
 * SaveManager's version-2 ghost frame codec.
 *
 * SHA-256 provides collision-resistant artifact identity. The historical FNV-1a
 * fingerprint remains available only for reading version-1 receipt/manifest
 * sidecars.
 */
internal object GhostRunIdentity {
    const val SHA256_BYTE_COUNT = 32
    const val SHA256_HEX_LENGTH = SHA256_BYTE_COUNT * 2

    fun calculate(frames: List<GhostFrame>): GhostRunIdentityValue {
        val digest = MessageDigest.getInstance("SHA-256")
        var fingerprint = FNV_OFFSET_BASIS

        digest.updateInt(frames.size)
        fingerprint = mixInt(fingerprint, frames.size)
        frames.forEach { frame ->
            val values = intArrayOf(
                frame.t.toRawBits(),
                frame.x.toRawBits(),
                frame.y.toRawBits(),
                frame.stateOrdinal,
                frame.scaleX.toRawBits(),
                frame.scaleY.toRawBits()
            )
            values.forEach { value ->
                digest.updateInt(value)
                fingerprint = mixInt(fingerprint, value)
            }
        }

        return GhostRunIdentityValue(
            fingerprint = fingerprint,
            sha256Hex = encodeHex(digest.digest())
        )
    }

    fun matches(
        frames: List<GhostFrame>,
        frameCount: Int,
        fingerprint: Long,
        sha256Hex: String?
    ): Boolean {
        if (frames.size != frameCount || !GhostRunValidator.isValid(frames)) return false
        if (sha256Hex == null) {
            return GhostRunFingerprint.calculate(frames) == fingerprint
        }
        if (!isCanonicalSha256(sha256Hex)) return false
        val identity = calculate(frames)
        return identity.fingerprint == fingerprint && identity.sha256Hex == sha256Hex
    }

    fun isCanonicalSha256(value: String): Boolean =
        value.length == SHA256_HEX_LENGTH &&
            value.all { character ->
                character in '0'..'9' || character in 'a'..'f'
            }

    fun decodeSha256(value: String): ByteArray? {
        if (!isCanonicalSha256(value)) return null
        return ByteArray(SHA256_BYTE_COUNT) { index ->
            val offset = index * 2
            value.substring(offset, offset + 2).toInt(16).toByte()
        }
    }

    fun encodeHex(bytes: ByteArray): String {
        val result = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            result.append(HEX[value ushr 4])
            result.append(HEX[value and 0x0f])
        }
        return result.toString()
    }

    private fun MessageDigest.updateInt(value: Int) {
        update((value ushr 24).toByte())
        update((value ushr 16).toByte())
        update((value ushr 8).toByte())
        update(value.toByte())
    }

    private fun mixInt(initial: Long, value: Int): Long {
        var hash = initial
        repeat(Int.SIZE_BYTES) { index ->
            val byte = (value ushr (index * Byte.SIZE_BITS)) and 0xff
            hash = (hash xor byte.toLong()) * FNV_PRIME
        }
        return hash
    }

    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L
    private const val HEX = "0123456789abcdef"
}

/** Historical 64-bit identity retained strictly for version-1 sidecar reads. */
internal object GhostRunFingerprint {
    fun calculate(frames: List<GhostFrame>): Long {
        var hash = FNV_OFFSET_BASIS
        hash = mixInt(hash, frames.size)
        frames.forEach { frame ->
            hash = mixInt(hash, frame.t.toRawBits())
            hash = mixInt(hash, frame.x.toRawBits())
            hash = mixInt(hash, frame.y.toRawBits())
            hash = mixInt(hash, frame.stateOrdinal)
            hash = mixInt(hash, frame.scaleX.toRawBits())
            hash = mixInt(hash, frame.scaleY.toRawBits())
        }
        return hash
    }

    private fun mixInt(initial: Long, value: Int): Long {
        var hash = initial
        repeat(Int.SIZE_BYTES) { index ->
            val byte = (value ushr (index * Byte.SIZE_BITS)) and 0xff
            hash = (hash xor byte.toLong()) * FNV_PRIME
        }
        return hash
    }

    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L
}
