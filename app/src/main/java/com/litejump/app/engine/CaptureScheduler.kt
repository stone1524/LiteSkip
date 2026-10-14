package com.litejump.app.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 取词倒计时调度: 点「开始取词」后 App 退到后台, 用户在倒计时内切到目标界面,
 * 到点由服务抓取当前窗口的可点击控件。
 */
object CaptureScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    const val DEFAULT_SECONDS = 12

    /** @return false 表示无障碍服务未连接, 无法取词 */
    fun start(service: SkipService?, seconds: Int = DEFAULT_SECONDS): Boolean {
        if (service == null) return false
        job?.cancel()
        job = scope.launch {
            for (left in seconds downTo 1) {
                if (left <= 3) {
                    withContext(Dispatchers.Main) {
                        service.toastPublic(service.getString(com.litejump.app.R.string.toast_capture_countdown, left))
                    }
                }
                delay(1000)
            }
            withContext(Dispatchers.Main) { service.captureNow() }
        }
        return true
    }
}
