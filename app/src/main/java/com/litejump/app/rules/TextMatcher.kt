package com.litejump.app.rules

/** 纯文本匹配逻辑, 与 Android 框架无关, 便于单元测试 */
object TextMatcher {
    fun hits(rule: Rule, text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || rule.keywords.isEmpty()) return false
        return when (rule.matchMode) {
            MatchMode.CONTAINS -> rule.keywords.any { t.contains(it, ignoreCase = true) }
            MatchMode.EQUALS -> rule.keywords.any { t.equals(it, ignoreCase = true) }
        }
    }
}
