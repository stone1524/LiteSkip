package com.litejump.app.engine

/** 取词中枢: 服务抓取的结果放在这里, UI 回到前台时消费 */
object CaptureHub {

    data class Item(val text: String, val viewId: String?)

    data class Result(val packageName: String, val items: List<Item>)

    @Volatile
    var pending: Result? = null

    /** UI 消费后置空, 避免重复弹窗 */
    fun consume(): Result? = pending.also { pending = null }
}
