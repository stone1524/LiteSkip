package com.litejump.app.rules

/** 导入合并的纯逻辑, 与存储无关, 便于单元测试 */
object RuleMerger {

    /** 导入规则条数上限: 防止恶意文件塞入海量规则拖慢扫描 */
    const val MAX_RULES = 500

    /** 单条规则关键词数上限 */
    const val MAX_KEYWORDS_PER_RULE = 20

    /** 单个关键词长度上限 */
    const val MAX_KEYWORD_LENGTH = 40

    /** 控件ID长度上限 */
    const val MAX_VIEWID_LENGTH = 200

    /**
     * 输入加固: 去空白、去重、截断超长字段、丢弃无效规则(无关键词且无控件ID)、限制总量。
     * 所有外来规则(导入文件)必须先过这里。
     */
    fun sanitize(incoming: List<Rule>): List<Rule> = incoming.asSequence()
        .map { r ->
            r.copy(
                keywords = r.keywords.asSequence()
                    .map { it.trim().take(MAX_KEYWORD_LENGTH) }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(MAX_KEYWORDS_PER_RULE)
                    .toList(),
                viewId = r.viewId?.trim()?.take(MAX_VIEWID_LENGTH)?.takeIf { it.isNotEmpty() },
            )
        }
        .filter { it.keywords.isNotEmpty() || it.viewId != null }
        .take(MAX_RULES)
        .toList()

    /**
     * 把导入的规则合并进已有列表: 先 sanitize 加固, 再跳过与已有规则内容重复的项,
     * ID 冲突时按位置改名。返回"需要追加"的部分, 不改动 existing。
     */
    fun merge(existing: List<Rule>, incoming: List<Rule>, takenIds: Set<String>): List<Rule> {
        val valid = sanitize(incoming)
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
