package com.anurag9000.forestrun.engine

import android.util.AtomicFile
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Atomic persistence boundary for already validated deterministic trace evidence. */
internal object DeterministicScenarioTraceEvidenceStore {
    private const val FILE_PREFIX = "scenario-trace-"
    private const val FILE_SUFFIX = ".json"

    fun fileNameFor(evidence: DeterministicScenarioTraceEvidence): String =
        FILE_PREFIX + evidence.scenario.name.lowercase() + FILE_SUFFIX

    fun write(directory: File, evidence: DeterministicScenarioTraceEvidence): File? {
        val payload = evidence.payloadJson.toByteArray(Charsets.UTF_8)
        if (payload.isEmpty() || sha256(payload) != evidence.payloadSha256) return null
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory) return null

        val destination = File(directory, fileNameFor(evidence))
        val atomicFile = AtomicFile(destination)
        var pendingStream: FileOutputStream? = null
        return try {
            val activeStream = atomicFile.startWrite()
            pendingStream = activeStream
            val output = BufferedOutputStream(activeStream)
            output.write(payload)
            output.flush()
            atomicFile.finishWrite(activeStream)
            pendingStream = null
            destination
        } catch (_: Exception) {
            pendingStream?.let(atomicFile::failWrite)
            null
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
