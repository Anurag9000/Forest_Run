package com.anurag9000.forestrun.engine

/**
 * Identity-based ownership for asynchronous retry loops.
 *
 * Starting a new request invalidates every previously issued token. Identity
 * avoids numeric wraparound and keeps cancellation allocation-free after the
 * one token created for each external request.
 */
internal class LatestRequestGate {
    internal class Token internal constructor()

    @Volatile
    private var current: Token? = null

    fun begin(): Token = Token().also { current = it }

    fun isCurrent(token: Token): Boolean = current === token

    fun cancel() {
        current = null
    }
}
