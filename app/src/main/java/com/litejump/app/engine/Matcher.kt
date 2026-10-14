package com.litejump.app.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.litejump.app.rules.Rule
import com.litejump.app.rules.TextMatcher

/**
 * 在无障碍节点树里找规则命中的按钮, 以及为「屏幕取词」收集可点击控件。
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

    data class PickItem(val text: String, val viewId: String?)

    fun find(root: AccessibilityNodeInfo, rules: List<Rule>): List<Target> {
        if (rules.isEmpty()) return emptyList()
        val targets = mutableListOf<Target>()
        var visited = 0

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++
            for (rule in rules) {
                if (!idMatches(node, rule)) continue
                val textHit = nodeTexts(node).firstOrNull { TextMatcher.hits(rule, it) }
                // 有 keywords 按文本命中; keywords 为空时 viewId 命中即可触发
                val matchedText = when {
                    textHit != null -> textHit
                    rule.keywords.isEmpty() -> rule.viewId ?: continue
                    else -> continue
                }
                if (isActionable(node)) {
                    targets += Target(node, rule, matchedText, Rect().also(node::getBoundsInScreen))
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

    /** 取词: 收集当前界面上可见、可点击控件的文字与控件ID */
    fun collectClickable(root: AccessibilityNodeInfo, cap: Int = 40): List<PickItem> {
        val out = mutableListOf<PickItem>()
        val seen = mutableSetOf<String>()
        var visited = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty() && visited < MAX_NODES && out.size < cap) {
            val (node, depth) = queue.removeFirst()
            visited++
            if (node.isVisibleToUser && hasClickable(node, maxHops = 2)) {
                val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                if (text != null && seen.add(text)) {
                    out += PickItem(text, node.viewIdResourceName)
                }
            }
            if (depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it to depth + 1) }
                }
            }
        }
        return out
    }

    private fun nodeTexts(node: AccessibilityNodeInfo): List<String> = buildList {
        node.text?.let { add(it.toString()) }
        node.contentDescription?.let { add(it.toString()) }
    }

    /** 控件ID约束: 支持全称 com.pkg:id/xxx 或短名 xxx */
    private fun idMatches(node: AccessibilityNodeInfo, rule: Rule): Boolean {
        val want = rule.viewId ?: return true
        val actual = node.viewIdResourceName ?: return false
        return actual == want || actual.endsWith(":id/$want")
    }

    /** 可见、有实际大小, 且自身或两层以内父节点可点击 —— 三者都满足才允许自动点击 */
    private fun isActionable(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        val bounds = Rect().also(node::getBoundsInScreen)
        if (bounds.isEmpty || bounds.width() <= 0 || bounds.height() <= 0) return false
        return hasClickable(node, maxHops = 2)
    }

    private fun hasClickable(start: AccessibilityNodeInfo, maxHops: Int): Boolean {
        var cur: AccessibilityNodeInfo? = start
        var hops = 0
        while (cur != null && hops <= maxHops) {
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
