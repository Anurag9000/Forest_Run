package com.anurag9000.forestrun.systems

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostNamespaceSerialSchedulerTest {
    private val primary = GhostPersistenceNamespace(
        prefsName = "forest_run_prefs",
        ghostFilename = "ghost_run.bin"
    )
    private val compatibility = GhostPersistenceNamespace(
        prefsName = "forest_run_prefs_compat_v99",
        ghostFilename = "ghost_run_compat_v99.bin"
    )

    @Test
    fun `same namespace stays fifo and never overlaps`() {
        val backend = Executors.newFixedThreadPool(2)
        try {
            val scheduler = GhostNamespaceSerialScheduler(backend)
            val releaseFirst = CountDownLatch(1)
            val firstStarted = CountDownLatch(1)
            val secondStarted = CountDownLatch(1)
            val order = Collections.synchronizedList(mutableListOf<String>())
            val concurrent = AtomicInteger(0)
            val peak = AtomicInteger(0)

            val first = scheduler.submit(primary) {
                val active = concurrent.incrementAndGet()
                peak.accumulateAndGet(active, ::maxOf)
                order += "first-start"
                firstStarted.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
                order += "first-end"
                concurrent.decrementAndGet()
            }
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS))

            val second = scheduler.submit(primary) {
                val active = concurrent.incrementAndGet()
                peak.accumulateAndGet(active, ::maxOf)
                order += "second"
                secondStarted.countDown()
                concurrent.decrementAndGet()
            }

            assertFalse(secondStarted.await(100, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            first.get(1, TimeUnit.SECONDS)
            second.get(1, TimeUnit.SECONDS)

            assertEquals(listOf("first-start", "first-end", "second"), order)
            assertEquals(1, peak.get())
        } finally {
            backend.shutdownNow()
        }
    }

    @Test
    fun `different namespaces may overlap`() {
        val backend = Executors.newFixedThreadPool(2)
        try {
            val scheduler = GhostNamespaceSerialScheduler(backend)
            val bothStarted = CountDownLatch(2)
            val release = CountDownLatch(1)
            val concurrent = AtomicInteger(0)
            val peak = AtomicInteger(0)

            val primaryTask = scheduler.submit(primary) {
                val active = concurrent.incrementAndGet()
                peak.accumulateAndGet(active, ::maxOf)
                bothStarted.countDown()
                release.await(2, TimeUnit.SECONDS)
                concurrent.decrementAndGet()
            }
            val compatibilityTask = scheduler.submit(compatibility) {
                val active = concurrent.incrementAndGet()
                peak.accumulateAndGet(active, ::maxOf)
                bothStarted.countDown()
                release.await(2, TimeUnit.SECONDS)
                concurrent.decrementAndGet()
            }

            assertTrue(bothStarted.await(1, TimeUnit.SECONDS))
            assertEquals(2, peak.get())
            release.countDown()
            primaryTask.get(1, TimeUnit.SECONDS)
            compatibilityTask.get(1, TimeUnit.SECONDS)
        } finally {
            backend.shutdownNow()
        }
    }

    @Test
    fun `failure completes its future and does not strand later namespace work`() {
        val backend = Executors.newFixedThreadPool(2)
        try {
            val scheduler = GhostNamespaceSerialScheduler(backend)
            val laterRan = CountDownLatch(1)
            val failed = scheduler.submit(primary) {
                error("expected")
            }
            val later = scheduler.submit(primary) {
                laterRan.countDown()
            }

            val failure = runCatching { failed.get(1, TimeUnit.SECONDS) }.exceptionOrNull()
            assertTrue(failure is ExecutionException)
            later.get(1, TimeUnit.SECONDS)
            assertTrue(laterRan.await(100, TimeUnit.MILLISECONDS))
        } finally {
            backend.shutdownNow()
        }
    }
}
