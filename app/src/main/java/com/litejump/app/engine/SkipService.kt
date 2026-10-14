package com.litejump.app.engine

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.litejump.app.rules.RuleRepository
import com.litejump.app.rules.RuleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 核心服务: 监听全局窗口变化, 按规则自动点击"跳过/同意/关闭"。
 * 扫描在后台线程做, 点击回到主线程(无障碍操作要求)。
 */
class SkipService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: SkipService? = null
            private set

        /** UI 用这个判断服务是否在运行 */
        val connected: Boolean get() = instance != null

        private const val SCAN_DEBOUNCE_MS = 150L
        private const val CLICK_COOLDOWN_MS = 4000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableMapOf<String, Runnable>()
    private val lastClick = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        toast("轻跳服务已开启")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // 忽略自己弹的 Toast, 防止自触发

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handler.post { scan() }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> scheduleScan(pkg, SCAN_DEBOUNCE_MS)
        }
    }

    /** 内容变化事件会连发很多条, 合并成一次扫描 */
    private fun scheduleScan(pkg: String, delayMs: Long) {
        pending.remove(pkg)?.let(handler::removeCallbacks)
        val runnable = Runnable {
            pending.remove(pkg)
            scan()
        }
        pending[pkg] = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun scan() {
        val root = rootInActiveWindow ?: return
        val windowPkg = root.packageName?.toString() ?: return
        val rules = RuleRepository.snapshot().rulesFor(windowPkg)
        if (rules.isEmpty()) return

        scope.launch {
            val targets = Matcher.find(root, rules)
            if (targets.isEmpty()) return@launch
            withContext(Dispatchers.Main) {
                for (target in targets) {
                    val key = "${target.rule.id}|${target.matchedText}|${target.bounds}"
                    val now = SystemClock.elapsedRealtime()
                    if (now - (lastClick[key] ?: 0L) < CLICK_COOLDOWN_MS) continue
                    lastClick[key] = now
                    if (Matcher.click(this@SkipService, target)) toast(feedback(target))
                }
                // 冷却记录只留 60 秒, 防止 map 无限增长
                val cutoff = SystemClock.elapsedRealtime() - 60_000
                lastClick.entries.removeAll { it.value < cutoff }
            }
        }
    }

    private fun feedback(target: Matcher.Target): String = when (target.rule.type) {
        RuleType.SPLASH -> "已跳过开屏广告"
        RuleType.CONSENT -> "已自动同意隐私弹窗"
        RuleType.POPUP -> "已关闭弹窗"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }
}
