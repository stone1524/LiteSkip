package com.litejump.app.rules

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.litejump.app.data.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 规则仓库: 内置规则(assets/rules.json) + 用户自定义规则(DataStore) 在此合并。
 * 全部数据只存本机; 应用无网络权限, 没有任何上传通道。
 */
object RuleRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var appContext: Context

    private val KEY_MASTER = booleanPreferencesKey("master_enabled")
    private val KEY_BUILTIN_OFF = stringSetPreferencesKey("builtin_disabled")
    private val KEY_SPLASH_BLACKLIST = stringSetPreferencesKey("splash_blacklist")
    private val KEY_CUSTOM = stringPreferencesKey("custom_rules_json")
    private val KEY_ONBOARDED = booleanPreferencesKey("onboarding_done")
    private val KEY_TOAST_ENABLED = booleanPreferencesKey("toast_enabled")

    private val _masterEnabled = MutableStateFlow(true)
    private val _builtinDisabled = MutableStateFlow<Set<String>>(emptySet())
    private val _splashBlacklist = MutableStateFlow<Set<String>>(emptySet())
    private val _customRules = MutableStateFlow<List<Rule>>(emptyList())
    private val _builtinRules = MutableStateFlow(RuleFile())
    private val _onboarded = MutableStateFlow(false)
    private val _toastEnabled = MutableStateFlow(true)

    val masterEnabled: StateFlow<Boolean> = _masterEnabled
    val builtinDisabled: StateFlow<Set<String>> = _builtinDisabled
    val splashBlacklist: StateFlow<Set<String>> = _splashBlacklist
    val customRules: StateFlow<List<Rule>> = _customRules
    val builtinRules: StateFlow<RuleFile> = _builtinRules
    val onboarded: StateFlow<Boolean> = _onboarded
    val toastEnabled: StateFlow<Boolean> = _toastEnabled

    fun init(context: Context) {
        appContext = context.applicationContext
        _builtinRules.value = loadBuiltin()
        scope.launch {
            appContext.appDataStore.data.collect { p ->
                _masterEnabled.value = p[KEY_MASTER] ?: true
                _builtinDisabled.value = p[KEY_BUILTIN_OFF] ?: emptySet()
                _splashBlacklist.value = p[KEY_SPLASH_BLACKLIST] ?: emptySet()
                _customRules.value = p[KEY_CUSTOM]?.let(::decodeRules) ?: emptyList()
                _onboarded.value = p[KEY_ONBOARDED] ?: false
                _toastEnabled.value = p[KEY_TOAST_ENABLED] ?: true
            }
        }
    }

    private fun decodeRules(text: String): List<Rule>? =
        runCatching { json.decodeFromString<List<Rule>>(text) }.getOrNull()

    private fun loadBuiltin(): RuleFile = runCatching {
        appContext.assets.open("rules.json").bufferedReader().use { reader ->
            json.decodeFromString<RuleFile>(reader.readText())
        }
    }.getOrElse { RuleFile() }

    /** 服务端每次扫描用的即时快照, 规则计算不做任何 IO */
    fun snapshot(): Snapshot = Snapshot(
        masterEnabled = _masterEnabled.value,
        builtinRules = _builtinRules.value,
        builtinDisabled = _builtinDisabled.value,
        splashBlacklist = _splashBlacklist.value,
        customRules = _customRules.value,
        toastEnabled = _toastEnabled.value,
    )

    data class Snapshot(
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

    fun setMaster(enabled: Boolean) = scope.launch {
        appContext.appDataStore.edit { it[KEY_MASTER] = enabled }
    }

    fun setOnboarded(done: Boolean) = scope.launch {
        appContext.appDataStore.edit { it[KEY_ONBOARDED] = done }
    }

    fun setToastEnabled(enabled: Boolean) = scope.launch {
        appContext.appDataStore.edit { it[KEY_TOAST_ENABLED] = enabled }
    }

    fun setBuiltinEnabled(id: String, enabled: Boolean) = scope.launch {
        appContext.appDataStore.edit { p ->
            val cur = p[KEY_BUILTIN_OFF] ?: emptySet()
            p[KEY_BUILTIN_OFF] = if (enabled) cur - id else cur + id
        }
    }

    /** 全局开屏跳过对单个 App 的排除开关 */
    fun setSplashBlocked(pkg: String, blocked: Boolean) = scope.launch {
        appContext.appDataStore.edit { p ->
            val cur = p[KEY_SPLASH_BLACKLIST] ?: emptySet()
            p[KEY_SPLASH_BLACKLIST] = if (blocked) cur + pkg else cur - pkg
        }
    }

    fun addCustom(rule: Rule) = scope.launch {
        appContext.appDataStore.edit { p ->
            val cur = p[KEY_CUSTOM]?.let(::decodeRules) ?: emptyList()
            p[KEY_CUSTOM] = json.encodeToString(cur.filterNot { it.id == rule.id } + rule)
        }
    }

    fun removeCustom(id: String) = scope.launch {
        appContext.appDataStore.edit { p ->
            val cur = p[KEY_CUSTOM]?.let(::decodeRules) ?: emptyList()
            p[KEY_CUSTOM] = json.encodeToString(cur.filterNot { it.id == id })
        }
    }

    /** 导出只含"我的规则", 内置规则随 APK 分发 */
    fun exportJson(): String = json.encodeToString(_customRules.value)

    /**
     * 导入规则, 返回导入条数。兼容两种格式:
     * 1. Rule 数组 (本应用导出的格式)
     * 2. 完整 RuleFile (取其 global + apps)
     */
    fun importJson(text: String): Int {
        val incoming: List<Rule> = decodeRules(text) ?: runCatching {
            val file = json.decodeFromString<RuleFile>(text)
            file.global + file.apps
        }.getOrNull() ?: return 0

        val existing = _customRules.value
        val takenIds = existing.mapTo(mutableSetOf()) { it.id } +
            _builtinRules.value.global.mapTo(mutableSetOf()) { it.id } +
            _builtinRules.value.apps.mapTo(mutableSetOf()) { it.id }
        val merged = RuleMerger.merge(existing, incoming, takenIds)
        if (merged.isEmpty()) return 0
        scope.launch {
            appContext.appDataStore.edit { p ->
                p[KEY_CUSTOM] = json.encodeToString(existing + merged)
            }
        }
        return merged.size
    }
}
