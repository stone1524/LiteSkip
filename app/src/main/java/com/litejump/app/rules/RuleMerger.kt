package com.litejump.app.rules

/** 导入合并的纯逻辑, 与存储无关, 便于单元测试 */
object RuleMerger {

    /**
     * 把导入的规则合并进已有列表: 丢弃无效规则(无关键词且无控件ID), 与已有 ID 冲突时改名。
     * 返回"需要追加"的部分, 不改动 existing。
     */
    fun merge(existing: List<Rule>, incoming: List<Rule>, takenIds: Set<String>): List<Rule> {
        val valid = incoming.filter { it.keywords.isNotEmpty() || it.viewId != null }
        if (valid.isEmpty()) return emptyList()
        val usedIds = takenIds.toMutableSet()
        // 用位置索引重命名: 两条内容相同的规则也能得到不同的新 ID
        return valid.mapIndexed { index, rule ->
            when {
                rule.id !in usedIds -> rule.also { usedIds += it.id }
                else -> rule.copy(id = "imp_${index}_${rule.id}").also { usedIds += it.id }
            }
        }
    }
}
