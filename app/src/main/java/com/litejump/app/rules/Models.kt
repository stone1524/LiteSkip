package com.litejump.app.rules

import kotlinx.serialization.Serializable

/** 规则类型: 开屏跳过 / 自动同意隐私弹窗 / 关闭应用内弹窗 / 跳过后的二次确认 */
@Serializable
enum class RuleType { SPLASH, CONSENT, POPUP, CONFIRM }

/** 关键词匹配方式: 包含 / 完全相等 */
@Serializable
enum class MatchMode { CONTAINS, EQUALS }

/**
 * 一条自动点击规则。
 * SPLASH / CONFIRM 允许 packageName 为空(全局); CONSENT / POPUP 必须指定包名, 防止误点。
 * viewId / activityName 是可选的进阶约束, 指定后必须同时满足才触发。
 */
@Serializable
data class Rule(
    val id: String,
    val type: RuleType,
    val keywords: List<String>,
    val matchMode: MatchMode = MatchMode.CONTAINS,
    val packageName: String? = null,
    val label: String? = null,
    val viewId: String? = null,
    val activityName: String? = null,
)

/** 内置规则文件的顶层结构 (assets/rules.json) */
@Serializable
data class RuleFile(
    val version: Int = 1,
    val global: List<Rule> = emptyList(),
    val apps: List<Rule> = emptyList(),
)
