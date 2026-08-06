package com.anurag9000.forestrun.engine

internal enum class AccessibilityActionDisposition {
    PERFORMED,
    SEMANTICS_UNAVAILABLE,
    NODE_NOT_FOUND,
    NODE_DISABLED,
    ACTION_NOT_AVAILABLE,
    HANDLER_REJECTED
}

internal data class AccessibilityActionResult(
    val disposition: AccessibilityActionDisposition,
    val nodeId: Int,
    val action: AccessibilitySemanticAction
) {
    val performed: Boolean
        get() = disposition == AccessibilityActionDisposition.PERFORMED
}

internal fun interface AccessibilitySemanticActionHandler {
    fun perform(nodeId: Int, action: AccessibilitySemanticAction): Boolean
}

/**
 * Validates virtual-node actions against the same immutable semantic tree that
 * is exposed to accessibility services.
 *
 * A stale node ID, disabled control, unsupported action, malformed snapshot, or
 * failing live handler is rejected without invoking unrelated gameplay input.
 */
internal class GameAccessibilityActionRouter(
    private val snapshotProvider: () -> AccessibilitySemanticSnapshot,
    private val handler: AccessibilitySemanticActionHandler
) {
    fun currentNodes(): List<AccessibilitySemanticNode> =
        GameAccessibilitySemantics.build(snapshotProvider())

    fun perform(
        nodeId: Int,
        action: AccessibilitySemanticAction
    ): AccessibilityActionResult {
        val nodes = try {
            currentNodes()
        } catch (_: RuntimeException) {
            return result(
                AccessibilityActionDisposition.SEMANTICS_UNAVAILABLE,
                nodeId,
                action
            )
        }
        val node = nodes.firstOrNull { it.id == nodeId }
            ?: return result(
                AccessibilityActionDisposition.NODE_NOT_FOUND,
                nodeId,
                action
            )
        if (!node.enabled) {
            return result(
                AccessibilityActionDisposition.NODE_DISABLED,
                nodeId,
                action
            )
        }
        if (action !in node.actions) {
            return result(
                AccessibilityActionDisposition.ACTION_NOT_AVAILABLE,
                nodeId,
                action
            )
        }

        val accepted = try {
            handler.perform(nodeId, action)
        } catch (_: RuntimeException) {
            false
        }
        return result(
            if (accepted) {
                AccessibilityActionDisposition.PERFORMED
            } else {
                AccessibilityActionDisposition.HANDLER_REJECTED
            },
            nodeId,
            action
        )
    }

    private fun result(
        disposition: AccessibilityActionDisposition,
        nodeId: Int,
        action: AccessibilitySemanticAction
    ): AccessibilityActionResult = AccessibilityActionResult(
        disposition = disposition,
        nodeId = nodeId,
        action = action
    )
}
