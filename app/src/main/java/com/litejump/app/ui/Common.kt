package com.litejump.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.litejump.app.R
import com.litejump.app.rules.RuleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class AppInfo(val label: String, val packageName: String)

/** 已安装应用列表: 几百个包的 PackageManager 查询必须离主线程 */
internal suspend fun loadInstalledApps(context: Context): List<AppInfo> =
    withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

@Composable
internal fun RuleType.displayName(): String = stringResource(
    when (this) {
        RuleType.SPLASH -> R.string.type_splash
        RuleType.CONSENT -> R.string.type_consent
        RuleType.POPUP -> R.string.type_popup
        RuleType.CONFIRM -> R.string.type_confirm
    }
)

internal fun formatSaved(seconds: Long, secUnit: String, minUnit: String): String =
    if (seconds < 60) "$seconds $secUnit" else "${seconds / 60} $minUnit ${seconds % 60} $secUnit"

internal val logTimeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

/** 「添加规则」弹窗的预填内容(来自取词结果) */
internal data class Prefill(val app: AppInfo?, val keywords: String, val viewId: String? = null)

internal val globalRuleLabel: String
    @Composable get() = stringResource(R.string.label_global_rule)
