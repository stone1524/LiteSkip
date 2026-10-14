package com.litejump.app.engine

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.litejump.app.R
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
 *
 * 防误点设计:
 * - 全局/包级开屏规则只在窗口切换后 [SPLASH_WINDOW_MS] 内生效, 设置页里的"跳过此步"不会被打;
 * - 点中开屏规则后进入 [CONFIRM_HUNT_MS] 的追猎期, 期间"坚持跳过/放弃奖励"等二次确认会被补点。
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
        private const val SPLASH_WINDOW_MS = 5000L
        private const val CONFIRM_HUNT_MS = 3500L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableMapOf<String, Runnable>()
    private val lastClick = mutableMapOf<String, Long>()
    private val windowShownAt = mutableMapOf<String, Long>()
    private val activityClass = mutableMapOf<String, String>()
    private var huntUntil = 0L
    private var huntPkg: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        toastPublic(getString(R.string.toast_service_on))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // 忽略自己弹的 Toast, 防止自触发

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                windowShownAt[pkg] = SystemClock.elapsedRealtime()
                event.className?.let { activityClass[pkg] = it.toString() }
                handler.post { scan() }
            }

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
        val snapshot = RuleRepository.snapshot()
        if (!snapshot.masterEnabled) return
        val now = SystemClock.elapsedRealtime()
        val hunting = now < huntUntil

        // 活动窗口 + 所有应用窗口 (对话框型弹窗在独立窗口里)
        val roots = linkedMapOf<String, android.view.accessibility.AccessibilityNodeInfo>()
        rootInActiveWindow?.let { r -> r.packageName?.let { roots[it.toString()] = r } }
        try {
            windows.orEmpty()
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .forEach { w ->
                    w.root?.let { r ->
                        r.packageName?.let { p -> roots.putIfAbsent(p.toString(), r) }
                    }
                }
        } catch (_: Exception) {
            // 个别 ROM 在窗口列表上会抛异常, 退化为只扫活动窗口
        }
        if (roots.isEmpty()) return

        scope.launch {
            val hits = mutableListOf<Pair<String, Matcher.Target>>()
            for ((pkg, root) in roots) {
                val rules = snapshot.rulesFor(pkg).filter { rule ->
                    when (rule.type) {
                        // 开屏规则只在窗口出现后的短暂窗口期内生效
                        RuleType.SPLASH -> now - (windowShownAt[pkg] ?: 0L) < SPLASH_WINDOW_MS
                        // 二次确认只在追猎期内生效
                        RuleType.CONFIRM -> hunting && huntPkg == pkg
                        else -> true
                    }
                }.filter { rule ->
                    val act = activityClass[pkg]
                    rule.activityName == null || (act != null && rule.activityName == act)
                }
                if (rules.isEmpty()) continue
                Matcher.find(root, rules).forEach { hits += pkg to it }
            }
            if (hits.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                for ((pkg, target) in hits) {
                    val clickAt = SystemClock.elapsedRealtime()
                    val key = "${target.rule.id}|${target.matchedText}|${target.bounds}"
                    if (clickAt - (lastClick[key] ?: 0L) < CLICK_COOLDOWN_MS) continue
                    lastClick[key] = clickAt
                    if (!Matcher.click(this@SkipService, target)) continue

                    toastPublic(feedback(target.rule.type))
                    ClickLog.record(applicationContext, pkg, target.rule.type, target.matchedText)
                    Stats.record(applicationContext, target.rule.type)
                    if (target.rule.type == RuleType.SPLASH) {
                        // 点掉开屏后, 短时间内盯着二次确认弹窗
                        huntUntil = SystemClock.elapsedRealtime() + CONFIRM_HUNT_MS
                        huntPkg = pkg
                    }
                }
                // 冷却记录只留 60 秒, 防止 map 无限增长
                val cutoff = SystemClock.elapsedRealtime() - 60_000
                lastClick.entries.removeAll { it.value < cutoff }
            }
        }
    }

    /** 取词: 抓当前窗口的可点击控件文字, 结果放 CaptureHub, UI 回前台后消费 */
    fun captureNow() {
        val root = rootInActiveWindow
        if (root == null) {
            toastPublic(getString(R.string.toast_capture_no_window))
            return
        }
        val pkg = root.packageName?.toString()
        if (pkg == null || pkg == packageName) {
            toastPublic(getString(R.string.toast_capture_self))
            return
        }
        val items = Matcher.collectClickable(root)
        if (items.isEmpty()) {
            toastPublic(getString(R.string.toast_capture_empty))
            return
        }
        CaptureHub.pending = CaptureHub.Result(pkg, items.map { CaptureHub.Item(it.text, it.viewId) })
        toastPublic(getString(R.string.toast_capture_done, items.size))
    }

    private fun feedback(type: RuleType): String = getString(
        when (type) {
            RuleType.SPLASH -> R.string.toast_skipped_splash
            RuleType.CONSENT -> R.string.toast_auto_consent
            RuleType.POPUP -> R.string.toast_closed_popup
            RuleType.CONFIRM -> R.string.toast_auto_confirm
        }
    )

    fun toastPublic(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

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
