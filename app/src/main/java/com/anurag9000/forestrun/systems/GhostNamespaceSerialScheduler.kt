package com.anurag9000.forestrun.systems

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

/**
 * Schedules ghost persistence in FIFO order per immutable namespace.
 *
 * Different namespaces may use different backend threads concurrently, while
 * work targeting the same receipt/ghost/manifest/distance bundle is serialized.
 * Namespace queues are lightweight and retain no worker threads of their own.
 */
internal class GhostNamespaceSerialScheduler(
    private val backend: Executor
) {
    private val serialExecutors =
        ConcurrentHashMap<GhostPersistenceNamespace, SerialExecutor>()

    fun submit(
        namespace: GhostPersistenceNamespace,
        work: () -> Unit
    ): Future<*> {
        val task = FutureTask<Unit> {
            work()
            Unit
        }
        serialExecutors
            .computeIfAbsent(namespace) { SerialExecutor(backend) }
            .execute(task)
        return task
    }

    private class SerialExecutor(
        private val backend: Executor
    ) {
        private val queue = ArrayDeque<Runnable>()
        private var active: Runnable? = null

        fun execute(command: Runnable) {
            val wrapped = Runnable {
                try {
                    command.run()
                } finally {
                    scheduleNext()
                }
            }
            val first = synchronized(this) {
                queue.addLast(wrapped)
                if (active == null) {
                    queue.removeFirst().also { active = it }
                } else {
                    null
                }
            }
            first?.let(backend::execute)
        }

        private fun scheduleNext() {
            val next = synchronized(this) {
                queue.pollFirst().also { active = it }
            }
            next?.let(backend::execute)
        }
    }
}
