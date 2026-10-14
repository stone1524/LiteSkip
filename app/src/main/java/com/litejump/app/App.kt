package com.litejump.app

import android.app.Application
import com.litejump.app.engine.ClickLog
import com.litejump.app.engine.Stats
import com.litejump.app.rules.RuleRepository

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RuleRepository.init(this)
        ClickLog.init(this)
        Stats.init(this)
    }
}
