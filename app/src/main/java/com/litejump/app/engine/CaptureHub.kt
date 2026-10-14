package com.litejump.app.engine

/** 取词中枢: 服务抓取的结果放在这里, UI 回到前台时消费 */
object CaptureHub {

    /** 屏幕文字属于敏感内容: 超过此时长未被消费就自动丢弃 */
    private const val STALE_MS = 10 * 60_000L

    data class Item(val text: String, val viewId: String?)

    data class Result(val packageName: String, val items: List<Item>, val createdAt: Long = System.currentTimeMillis())

    @Volatile
    private var pendingResult: Result? = null

    /** UI 消费后置空; 过期结果直接丢弃, 避免屏幕内容在静态字段长期驻留 */
    fun consume(): Result? {
        val result = pendingResult ?: return null
        pendingResult = null
        return if (System.currentTimeMillis() - result.createdAt > STALE_MS) null else result
    }

    fun publish(result: Result) {
        pendingResult = result
    }
}
