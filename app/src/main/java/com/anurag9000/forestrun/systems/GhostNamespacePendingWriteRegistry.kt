package com.anurag9000.forestrun.systems

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Tracks the latest queued ghost write for each immutable persistence namespace.
 *
 * Same-namespace work is serial, so waiting for that namespace's latest future
 * also waits for all earlier work in its queue. Different namespaces may run in
 * parallel and are therefore awaited independently under one shared deadline.
 */
internal class GhostNamespacePendingWriteRegistry {
    private val latestByNamespace =
        ConcurrentHashMap<GhostPersistenceNamespace, Future<*>>()

    fun track(namespace: GhostPersistenceNamespace, task: Future<*>) {
        latestByNamespace[namespace] = task
    }

    fun isActive(namespace: GhostPersistenceNamespace): Boolean {
        val task = latestByNamespace[namespace] ?: return false
        if (!task.isDone) return true
        latestByNamespace.remove(namespace, task)
        return false
    }

    fun awaitAll(timeoutMs: Long): Boolean {
        if (timeoutMs < 0L) return false
        val budgetNs = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        val startedAtNs = System.nanoTime()

        while (true) {
            val active = activeTasks()
            if (active.isEmpty()) return true

            for (task in active) {
                val elapsedNs = (System.nanoTime() - startedAtNs).coerceAtLeast(0L)
                val remainingNs = budgetNs - elapsedNs
                if (remainingNs <= 0L) return false
                try {
                    task.get(remainingNs, TimeUnit.NANOSECONDS)
                } catch (_: TimeoutException) {
                    return false
                } catch (_: Exception) {
                    return false
                }
            }
        }
    }

    fun clear() {
        latestByNamespace.clear()
    }

    private fun activeTasks(): List<Future<*>> {
        val active = ArrayList<Future<*>>(latestByNamespace.size)
        latestByNamespace.forEach { (namespace, task) ->
            if (task.isDone) {
                latestByNamespace.remove(namespace, task)
            } else {
                active += task
            }
        }
        return active
    }
}
