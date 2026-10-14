package com.litejump.app.engine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.litejump.app.data.appDataStore
import com.litejump.app.rules.RuleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 跳过统计: 累计/今日次数 + 按类型估算省下的手动操作秒数 */
object Stats {

    data class Snapshot(val total: Int, val today: Int, val savedSeconds: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val KEY_TOTAL = intPreferencesKey("stat_total")
    private val KEY_TODAY = intPreferencesKey("stat_today")
    private val KEY_DAY = stringPreferencesKey("stat_day")
    private val KEY_SAVED = intPreferencesKey("stat_saved_seconds")

    private val _stats = MutableStateFlow(Snapshot(0, 0, 0))
    val stats: StateFlow<Snapshot> = _stats

    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val app = context.applicationContext
        scope.launch {
            app.appDataStore.data.collect { p ->
                val total = p[KEY_TOTAL] ?: 0
                val today = p[KEY_TODAY] ?: 0
                val day = p[KEY_DAY] ?: ""
                val saved = p[KEY_SAVED] ?: 0
                // 跨天显示归零, 下次 record 时才真正写回
                val isToday = day == todayString()
                _stats.value = Snapshot(total, if (isToday) today else 0, saved.toLong())
            }
        }
    }

    fun record(context: Context, type: RuleType) {
        val app = context.applicationContext
        scope.launch {
            app.appDataStore.edit { p ->
                val today = todayString()
                val isToday = p[KEY_DAY] == today
                val newTotal = (p[KEY_TOTAL] ?: 0) + 1
                val newToday = (if (isToday) p[KEY_TODAY] ?: 0 else 0) + 1
                val newSaved = (p[KEY_SAVED] ?: 0) + estimateSeconds(type)
                p[KEY_TOTAL] = newTotal
                p[KEY_TODAY] = newToday
                p[KEY_DAY] = today
                p[KEY_SAVED] = newSaved
            }
        }
    }

    fun reset(context: Context) {
        val app = context.applicationContext
        scope.launch {
            app.appDataStore.edit { p ->
                p[KEY_TOTAL] = 0
                p[KEY_TODAY] = 0
                p[KEY_SAVED] = 0
                p[KEY_DAY] = todayString()
            }
        }
    }

    private fun estimateSeconds(type: RuleType): Int = when (type) {
        RuleType.SPLASH, RuleType.CONFIRM -> 4
        RuleType.POPUP -> 3
        RuleType.CONSENT -> 2
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
