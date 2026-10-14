package com.litejump.app.rules

/** 导入合并的纯逻辑, 与存储无关, 便于单元测试 */
object RuleMerger {

    /**
     * 把导入的规则合并进已有列表: 丢弃无效规则(无关键词且无控件ID),
     * 跳过与已有规则内容重复的项(防重复导入产生冗余), ID 冲突时按位置改名。
     * 返回"需要追加"的部分, 不改动 existing。
     */
    fun merge(existing: List<Rule>, incoming: List<Rule>, takenIds: Set<String>): List<Rule> {
        val valid = incoming.filter { it.keywords.isNotEmpty() || it.viewId != null }
        if (valid.isEmpty()) return emptyList()
        val usedIds = takenIds.toMutableSet()
        val usedSignatures = existing.mapTo(mutableSetOf()) { it.contentSignature() }
        return valid.mapIndexedNotNull { index, rule ->
            val signature = rule.contentSignature()
            when {
                signature in usedSignatures -> null
                rule.id !in usedIds -> rule.also { usedIds += it.id; usedSignatures += signature }
                else -> rule.copy(id = "imp_${index}_${rule.id}").also { usedIds += it.id; usedSignatures += signature }
            }
        }
    }

    /** 内容签名(与 ID/label 无关), 用于识别重复导入 */
    private fun Rule.contentSignature(): String =
        listOf(type.name, packageName.orEmpty(), viewId.orEmpty(), matchMode.name, keywords.joinToString("|"))
            .joinToString("§")
}
