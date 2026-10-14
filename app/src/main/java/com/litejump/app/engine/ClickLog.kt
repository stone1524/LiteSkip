package com.litejump.app.engine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.litejump.app.data.appDataStore
import com.litejump.app.rules.RuleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 自动点击日志: 最近 50 条, DataStore 持久化, 用于排查是哪条规则在什么时间点了什么 */
object ClickLog {

    @Serializable
    data class Entry(val time: Long, val packageName: String, val type: RuleType, val text: String)

    private val json = Json { ignoreUnknownKeys = true }
    private val KEY = stringPreferencesKey("click_log_json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val app = context.applicationContext
        scope.launch {
            val saved = app.appDataStore.data.first()[KEY]
            _entries.value = saved?.let { runCatching { json.decodeFromString<List<Entry>>(it) }.getOrNull() } ?: emptyList()
        }
    }

    fun record(context: Context, packageName: String, type: RuleType, text: String) {
        val entry = Entry(System.currentTimeMillis(), packageName, type, text)
        _entries.value = (listOf(entry) + _entries.value).take(50)
        val app = context.applicationContext
        scope.launch { app.appDataStore.edit { it[KEY] = json.encodeToString(_entries.value) } }
    }

    fun clear(context: Context) {
        _entries.value = emptyList()
        val app = context.applicationContext
        scope.launch { app.appDataStore.edit { it[KEY] = json.encodeToString(emptyList<Entry>()) } }
    }
}
