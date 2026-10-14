package com.litejump.app.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** 全应用共用一个 DataStore 文件; 各模块的键定义在各自文件里 */
internal val Context.appDataStore by preferencesDataStore(name = "litejump")
