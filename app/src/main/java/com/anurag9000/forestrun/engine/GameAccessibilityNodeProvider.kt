package com.anurag9000.forestrun.engine

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import kotlin.math.ceil
import kotlin.math.floor

internal fun interface AccessibilityNodeBoundsResolver {
    fun resolve(nodeId: Int): Rect
}

/**
 * Android accessibility bridge for the custom Canvas UI.
 *
 * The provider never invents actions: it rebuilds the current semantic tree for
 * every query and delegates activation through [GameAccessibilityActionRouter].
 * This makes stale virtual IDs fail closed after screen/state transitions.
 * Mutation owners are responsible for publishing semantic-tree changes after a
 * successful write or transition; the provider emits only interaction/focus
 * events itself. Framework accessibility events are emitted only while Android's
 * accessibility service is enabled; direct semantic routing remains safe and
 * deterministic when tests or other callers query the provider while it is off.
 */
internal class GameAccessibilityNodeProvider(
    private val hostView: View,
    private val router: GameAccessibilityActionRouter,
    private val boundsResolver: AccessibilityNodeBoundsResolver
) : AccessibilityNodeProvider() {
    private var accessibilityFocusedNodeId: Int = View.NO_ID
    private val accessibilityManager: AccessibilityManager? =
        hostView.context.getSystemService(AccessibilityManager::class.java)

    override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? =
        if (virtualViewId == HOST_VIEW_ID) {
            createHostNode()
        } else {
            createVirtualNode(virtualViewId)
        }

    override fun findAccessibilityNodeInfosByText(
        searched: String,
        virtualViewId: Int
    ): MutableList<AccessibilityNodeInfo> {
        val needle = searched.trim().lowercase()
        if (needle.isEmpty()) return mutableListOf()
        val nodes = currentNodesOrEmpty()
        return nodes.asSequence()
            .filter { node ->
                node.label.lowercase().contains(needle) ||
                    node.stateDescription?.lowercase()?.contains(needle) == true
            }
            .mapNotNull { createVirtualNode(it.id) }
            .toMutableList()
    }

    override fun findFocus(focus: Int): AccessibilityNodeInfo? {
        if (focus != AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) return null
        val focused = accessibilityFocusedNodeId
        return if (focused == View.NO_ID) null else createVirtualNode(focused)
    }

    override fun performAction(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?
    ): Boolean {
        if (virtualViewId == HOST_VIEW_ID) return false

        return when (action) {
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> requestAccessibilityFocus(virtualViewId)
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> clearAccessibilityFocus(virtualViewId)
            AccessibilityNodeInfo.ACTION_CLICK -> performSemanticClick(virtualViewId)
            else -> false
        }
    }

    fun notifySemanticTreeChanged() {
        if (!accessibilityEventsEnabled()) return
        hostView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    fun notifyNodeChanged(nodeId: Int) {
        if (!accessibilityEventsEnabled()) return
        if (createVirtualNode(nodeId) == null) return
        sendEvent(nodeId, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    private fun createHostNode(): AccessibilityNodeInfo {
        val info = AccessibilityNodeInfo.obtain(hostView)
        info.className = hostView.javaClass.name
        info.packageName = hostView.context.packageName
        info.isFocusable = true
        info.isVisibleToUser = hostView.visibility == View.VISIBLE
        currentNodesOrEmpty().forEach { node ->
            info.addChild(hostView, node.id)
        }
        return info
    }

    private fun createVirtualNode(nodeId: Int): AccessibilityNodeInfo? {
        val node = currentNodesOrEmpty().firstOrNull { it.id == nodeId } ?: return null
        val info = AccessibilityNodeInfo.obtain()
        info.setSource(hostView, node.id)
        info.setParent(hostView)
        info.packageName = hostView.context.packageName
        info.className = if (node.actions.isEmpty()) {
            View::class.java.name
        } else {
            "android.widget.Button"
        }
        info.contentDescription = buildDescription(node)
        info.isEnabled = node.enabled
        info.isVisibleToUser = hostView.visibility == View.VISIBLE
        info.isFocusable = true
        info.isClickable = node.enabled && node.actions.isNotEmpty()
        info.isAccessibilityFocused = accessibilityFocusedNodeId == node.id

        if (node.liveRegion) {
            info.liveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        if (isBooleanToggle(node.id)) {
            info.isCheckable = true
            info.isChecked = node.stateDescription == "On"
        }

        val parentBounds = safeBounds(node.id)
        info.setBoundsInParent(parentBounds)
        val screenBounds = Rect(parentBounds)
        val location = IntArray(2)
        hostView.getLocationOnScreen(location)
        screenBounds.offset(location[0], location[1])
        info.setBoundsInScreen(screenBounds)

        if (info.isClickable) {
            info.addAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        if (info.isAccessibilityFocused) {
            info.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
        } else {
            info.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        }
        return info
    }

    private fun performSemanticClick(nodeId: Int): Boolean {
        val node = currentNodesOrEmpty().firstOrNull { it.id == nodeId } ?: return false
        if (!node.enabled) return false
        val semanticAction = node.actions.singleOrNull() ?: return false
        val result = router.perform(nodeId, semanticAction)
        if (!result.performed) return false
        sendEvent(nodeId, AccessibilityEvent.TYPE_VIEW_CLICKED)
        return true
    }

    private fun requestAccessibilityFocus(nodeId: Int): Boolean {
        if (createVirtualNode(nodeId) == null) return false
        if (accessibilityFocusedNodeId == nodeId) return false
        val previous = accessibilityFocusedNodeId
        accessibilityFocusedNodeId = nodeId
        if (previous != View.NO_ID) {
            sendEvent(previous, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
        }
        hostView.invalidate()
        sendEvent(nodeId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
        return true
    }

    private fun clearAccessibilityFocus(nodeId: Int): Boolean {
        if (accessibilityFocusedNodeId != nodeId) return false
        accessibilityFocusedNodeId = View.NO_ID
        hostView.invalidate()
        sendEvent(nodeId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
        return true
    }

    private fun sendEvent(nodeId: Int, eventType: Int) {
        if (!accessibilityEventsEnabled()) return
        val node = currentNodesOrEmpty().firstOrNull { it.id == nodeId } ?: return
        val event = AccessibilityEvent.obtain(eventType)
        event.packageName = hostView.context.packageName
        event.className = hostView.javaClass.name
        event.contentDescription = buildDescription(node)
        event.isEnabled = node.enabled
        event.setSource(hostView, nodeId)
        val parent = hostView.parent
        if (parent is ViewGroup) {
            parent.requestSendAccessibilityEvent(hostView, event)
        } else {
            hostView.sendAccessibilityEventUnchecked(event)
        }
    }

    private fun accessibilityEventsEnabled(): Boolean = accessibilityManager?.isEnabled == true

    private fun currentNodesOrEmpty(): List<AccessibilitySemanticNode> = try {
        router.currentNodes()
    } catch (_: RuntimeException) {
        emptyList()
    }

    private fun safeBounds(nodeId: Int): Rect {
        val raw = try {
            boundsResolver.resolve(nodeId)
        } catch (_: RuntimeException) {
            Rect(0, 0, hostView.width.coerceAtLeast(1), hostView.height.coerceAtLeast(1))
        }
        val maxWidth = hostView.width.coerceAtLeast(1)
        val maxHeight = hostView.height.coerceAtLeast(1)
        val left = raw.left.coerceIn(0, maxWidth - 1)
        val top = raw.top.coerceIn(0, maxHeight - 1)
        val right = raw.right.coerceIn(left + 1, maxWidth)
        val bottom = raw.bottom.coerceIn(top + 1, maxHeight)
        return Rect(left, top, right, bottom)
    }

    private fun buildDescription(node: AccessibilitySemanticNode): String =
        listOfNotNull(node.label, node.stateDescription?.takeIf { it.isNotBlank() })
            .joinToString(", ")

    private fun isBooleanToggle(nodeId: Int): Boolean = nodeId == AccessibilityNodeIds.SETTINGS_REDUCED_MOTION ||
        nodeId == AccessibilityNodeIds.SETTINGS_AUDIO ||
        nodeId == AccessibilityNodeIds.SETTINGS_HAPTICS
}

internal fun AccessibilityNodeBounds.toRect(): Rect = Rect(
    floor(left.toDouble()).toInt(),
    floor(top.toDouble()).toInt(),
    ceil(right.toDouble()).toInt(),
    ceil(bottom.toDouble()).toInt()
)
