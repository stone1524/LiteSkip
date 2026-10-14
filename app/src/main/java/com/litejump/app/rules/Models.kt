package com.litejump.app.rules

import kotlinx.serialization.Serializable

/** 规则类型: 开屏跳过 / 自动同意隐私弹窗 / 关闭应用内弹窗 */
@Serializable
enum class RuleType { SPLASH, CONSENT, POPUP }

/** 关键词匹配方式: 包含 / 完全相等 */
@Serializable
enum class MatchMode { CONTAINS, EQUALS }

/**
 * 一条自动点击规则。
 * SPLASH 类型允许 packageName 为空(全局规则); CONSENT / POPUP 必须指定包名, 防止误点。
 */
@Serializable
data class Rule(
    val id: String,
    val type: RuleType,
    val keywords: List<String>,
    val matchMode: MatchMode = MatchMode.CONTAINS,
    val packageName: String? = null,
    val label: String? = null,
)

/** 内置规则文件的顶层结构 (assets/rules.json) */
@Serializable
data class RuleFile(
    val version: Int = 1,
    val global: List<Rule> = emptyList(),
    val apps: List<Rule> = emptyList(),
)
