package com.anurag9000.forestrun.engine

import android.util.AtomicFile
import java.io.BufferedOutputStream
import java.io.File

/** Atomic persistence boundary for already validated deterministic trace evidence. */
internal object DeterministicScenarioTraceEvidenceStore {
    private const val FILE_PREFIX = "scenario-trace-"
    private const val FILE_SUFFIX = ".json"

    fun fileNameFor(evidence: DeterministicScenarioTraceEvidence): String =
        FILE_PREFIX + evidence.scenario.name.lowercase() + FILE_SUFFIX

    fun write(directory: File, evidence: DeterministicScenarioTraceEvidence): File? {
        if (!DeterministicScenarioTraceEvidenceCodec.isCanonical(evidence)) return null
        val payload = evidence.payloadJson.toByteArray(Charsets.UTF_8)
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory) return null

        val destination = File(directory, fileNameFor(evidence))
        val atomicFile = AtomicFile(destination)
        val activeStream = try {
            atomicFile.startWrite()
        } catch (_: Exception) {
            return null
        }
        return try {
            val output = BufferedOutputStream(activeStream)
            output.write(payload)
            output.flush()
            atomicFile.finishWrite(activeStream)
            destination
        } catch (_: Exception) {
            runCatching { atomicFile.failWrite(activeStream) }
            null
        }
    }
}
