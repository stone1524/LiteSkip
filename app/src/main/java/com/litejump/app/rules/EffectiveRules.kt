package com.litejump.app.rules

/**
 * 一次扫描的生效规则快照: 由 RuleRepository 组装, 引擎只消费不回查。
 * 独立成类型使引擎与仓库实现解耦, 也让生效逻辑可以直接单测。
 */
data class EffectiveRules(
    val masterEnabled: Boolean,
    val builtinRules: RuleFile,
    val builtinDisabled: Set<String>,
    val splashBlacklist: Set<String>,
    val customRules: List<Rule>,
    val toastEnabled: Boolean = true,
) {
    /** 给定包名当前生效的全部规则 */
    fun rulesFor(packageName: String): List<Rule> {
        if (!masterEnabled) return emptyList()
        val all = (builtinRules.global + builtinRules.apps).filter { it.id !in builtinDisabled } +
            customRules
        return all.filter { rule ->
            when (rule.type) {
                // SPLASH 与 CONFIRM 走同一套排除名单; 生效时机(窗口期/追猎期)由服务控制
                RuleType.SPLASH, RuleType.CONFIRM ->
                    if (rule.packageName == null) packageName !in splashBlacklist
                    else rule.packageName == packageName

                RuleType.CONSENT, RuleType.POPUP -> rule.packageName == packageName
            }
        }
    }
}
