package com.anurag9000.forestrun.systems

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

/**
 * Tracks only the latest queued ghost write for each immutable persistence namespace.
 *
 * The manager still uses one serial executor, but recovery admission must care only
 * about work targeting the same namespace. Completed or cancelled tasks are removed
 * lazily during admission checks so stale entries cannot keep recovery blocked.
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

    fun clear() {
        latestByNamespace.clear()
    }
}
