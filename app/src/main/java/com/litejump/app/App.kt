package com.litejump.app

import android.app.Application
import com.litejump.app.rules.RuleRepository

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RuleRepository.init(this)
    }
}
