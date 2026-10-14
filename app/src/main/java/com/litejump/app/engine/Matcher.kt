package com.litejump.app.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.litejump.app.rules.MatchMode
import com.litejump.app.rules.Rule

/**
 * 在无障碍节点树里找规则命中的按钮。
 * 全部遍历有深度与节点数上限, 防止在超复杂界面上拖慢系统。
 */
object Matcher {

    private const val MAX_DEPTH = 30
    private const val MAX_NODES = 4000

    data class Target(
        val node: AccessibilityNodeInfo,
        val rule: Rule,
        val matchedText: String,
        val bounds: Rect,
    )

    fun find(root: AccessibilityNodeInfo, rules: List<Rule>): List<Target> {
        if (rules.isEmpty()) return emptyList()
        val targets = mutableListOf<Target>()
        var visited = 0

        fun matchedText(node: AccessibilityNodeInfo, rule: Rule): String? {
            val texts = buildList {
                node.text?.let { add(it.toString()) }
                node.contentDescription?.let { add(it.toString()) }
            }
            for (raw in texts) {
                val t = raw.trim()
                if (t.isEmpty()) continue
                val hit = when (rule.matchMode) {
                    MatchMode.CONTAINS -> rule.keywords.any { t.contains(it, ignoreCase = true) }
                    MatchMode.EQUALS -> rule.keywords.any { t.equals(it, ignoreCase = true) }
                }
                if (hit) return t
            }
            return null
        }

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++
            for (rule in rules) {
                val text = matchedText(node, rule) ?: continue
                if (isActionable(node)) {
                    targets += Target(node, rule, text, Rect().also(node::getBoundsInScreen))
                    break // 同一节点只按第一条命中的规则处理
                }
            }
            if (depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it to depth + 1) }
                }
            }
        }
        return targets
    }

    /** 可见、有实际大小, 且自身或两层以内父节点可点击 —— 三者都满足才允许自动点击 */
    private fun isActionable(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        val bounds = Rect().also(node::getBoundsInScreen)
        if (bounds.isEmpty || bounds.width() <= 0 || bounds.height() <= 0) return false
        var cur: AccessibilityNodeInfo? = node
        var hops = 0
        while (cur != null && hops <= 2) {
            if (cur.isClickable) return true
            cur = cur.parent
            hops++
        }
        return false
    }

    /** 优先模拟标准点击; 按钮没绑定点击事件时用坐标手势兜底 */
    fun click(service: AccessibilityService, target: Target): Boolean {
        if (target.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val b = target.bounds
        if (b.isEmpty) return false
        val path = Path().apply { moveTo(b.exactCenterX(), b.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 30))
            .build()
        return service.dispatchGesture(gesture, null, null)
    }
}
