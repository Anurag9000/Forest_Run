package com.anurag9000.forestrun.systems

import java.util.concurrent.FutureTask
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostNamespacePendingWriteRegistryTest {
    private val primary = GhostPersistenceNamespace(
        prefsName = "forest_run_prefs",
        ghostFilename = "ghost_run.bin"
    )
    private val compatibility = GhostPersistenceNamespace(
        prefsName = "forest_run_prefs_compat_v91",
        ghostFilename = "ghost_run_compat_v91.bin"
    )

    @Test
    fun `pending work blocks only its own namespace`() {
        val registry = GhostNamespacePendingWriteRegistry()
        val primaryTask = FutureTask<Unit> { Unit }

        registry.track(primary, primaryTask)

        assertTrue(registry.isActive(primary))
        assertFalse(registry.isActive(compatibility))
    }

    @Test
    fun `completed and cancelled tasks stop blocking admission`() {
        val registry = GhostNamespacePendingWriteRegistry()
        val completed = FutureTask<Unit> { Unit }
        completed.run()
        registry.track(primary, completed)

        assertFalse(registry.isActive(primary))
        assertFalse(registry.isActive(primary))

        val cancelled = FutureTask<Unit> { Unit }
        registry.track(primary, cancelled)
        assertTrue(cancelled.cancel(false))

        assertFalse(registry.isActive(primary))
    }

    @Test
    fun `latest same namespace task remains authoritative`() {
        val registry = GhostNamespacePendingWriteRegistry()
        val older = FutureTask<Unit> { Unit }
        val newer = FutureTask<Unit> { Unit }

        registry.track(primary, older)
        registry.track(primary, newer)
        older.run()

        assertTrue(registry.isActive(primary))

        newer.run()

        assertFalse(registry.isActive(primary))
    }

    @Test
    fun `clear removes every namespace activity marker`() {
        val registry = GhostNamespacePendingWriteRegistry()
        registry.track(primary, FutureTask<Unit> { Unit })
        registry.track(compatibility, FutureTask<Unit> { Unit })

        registry.clear()

        assertFalse(registry.isActive(primary))
        assertFalse(registry.isActive(compatibility))
    }
}
