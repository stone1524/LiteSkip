package com.litejump.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.litejump.app.engine.SkipService
import com.litejump.app.rules.MatchMode
import com.litejump.app.rules.Rule
import com.litejump.app.rules.RuleRepository
import com.litejump.app.rules.RuleType
import kotlinx.coroutines.delay

data class AppInfo(val label: String, val packageName: String)

private fun loadInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun RuleType.displayName() = when (this) {
    RuleType.SPLASH -> "跳开屏"
    RuleType.CONSENT -> "同意隐私"
    RuleType.POPUP -> "关弹窗"
}

@Composable
fun AppUi() {
    val context = LocalContext.current
    val master by RuleRepository.masterEnabled.collectAsState()
    val builtin by RuleRepository.builtinRules.collectAsState()
    val builtinOff by RuleRepository.builtinDisabled.collectAsState()
    val blacklist by RuleRepository.splashBlacklist.collectAsState()
    val custom by RuleRepository.customRules.collectAsState()

    var serviceOn by remember { mutableStateOf(SkipService.connected) }
    var showAddRule by remember { mutableStateOf(false) }
    var showBlacklist by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(Unit) { apps = loadInstalledApps(context) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3000)
            message = null
        }
    }

    // 从系统设置页返回时刷新服务运行状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) serviceOn = SkipService.connected
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)!!.use {
                    it.write(RuleRepository.exportJson().toByteArray())
                }
            }.isSuccess
            message = if (ok) "规则已导出" else "导出失败"
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }.getOrNull()
            val n = text?.let { runCatching { RuleRepository.importJson(it) }.getOrNull() }
            message = when {
                n == null -> "导入失败: 文件格式不认识"
                n == 0 -> "文件里没有可用规则"
                else -> "已导入 $n 条规则"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("轻跳", style = MaterialTheme.typography.headlineMedium)
        Text(
            "自动跳过开屏广告与弹窗 · 无网络权限",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        ServiceCard(serviceOn, master, onMaster = RuleRepository::setMaster) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        message?.let {
            Card {
                Text(it, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.primary)
            }
        }

        Card {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                Text("内置规则", style = MaterialTheme.typography.titleMedium)
                (builtin.global + builtin.apps).forEach { rule ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.label ?: rule.id, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${rule.type.displayName()} · ${rule.packageName ?: "全局"} · ${rule.keywords.joinToString("/")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Switch(
                            checked = rule.id !in builtinOff,
                            onCheckedChange = { RuleRepository.setBuiltinEnabled(rule.id, it) },
                        )
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("开屏跳过排除名单", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (blacklist.isEmpty()) "全局跳过对所有 App 生效"
                            else "已排除 ${blacklist.size} 个 App",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    OutlinedButton(onClick = { showBlacklist = true }) { Text("管理") }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("我的规则", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { showAddRule = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.padding(2.dp))
                        Text("添加")
                    }
                }
                if (custom.isEmpty()) {
                    Text(
                        "还没有自定义规则。可以给指定 App 添加\"同意/关闭\"按钮规则。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                custom.forEach { rule ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.label ?: rule.packageName ?: rule.id, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${rule.type.displayName()} · ${rule.packageName ?: "全局"} · ${rule.keywords.joinToString("/")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        IconButton(onClick = { RuleRepository.removeCustom(rule.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                Text("规则备份", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("litejump-rules.json") }) { Text("导出") }
                    OutlinedButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }) { Text("导入") }
                }
                Text(
                    "规则只保存在本机, 导入导出走系统文件选择器, 不需要存储权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        Text(
            "本应用仅供学习交流, 请遵守所在地区法律法规。" +
                "应用无网络权限, 不上传任何数据; 无障碍内容仅在本机内存中瞬时处理。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }

    if (showAddRule) {
        AddRuleDialog(apps = apps, onDismiss = { showAddRule = false })
    }
    if (showBlacklist) {
        AppPickerDialog(
            title = "排除的 App",
            hint = "勾选后这些 App 不做全局开屏跳过",
            apps = apps,
            selected = blacklist,
            onToggle = { RuleRepository.setSplashBlocked(it.packageName, it.packageName !in blacklist) },
            onDismiss = { showBlacklist = false },
        )
    }
}

@Composable
private fun ServiceCard(
    serviceOn: Boolean,
    master: Boolean,
    onMaster: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("总开关", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (master) "自动跳过已启用" else "已暂停",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Switch(checked = master, onCheckedChange = onMaster, enabled = serviceOn)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (serviceOn) "● 无障碍服务运行中" else "○ 无障碍服务未开启",
                    Modifier.weight(1f),
                    color = if (serviceOn) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                )
                if (!serviceOn) Button(onClick = onOpenSettings) { Text("去开启") }
            }
        }
    }
}

@Composable
private fun AddRuleDialog(apps: List<AppInfo>, onDismiss: () -> Unit) {
    var app by remember { mutableStateOf<AppInfo?>(null) }
    var type by remember { mutableStateOf(RuleType.SPLASH) }
    var keywords by remember { mutableStateOf("") }
    var equalsMode by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    val parsedKeywords = keywords.split(',', '，', '、', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val needsApp = type != RuleType.SPLASH
    val valid = parsedKeywords.isNotEmpty() && (!needsApp || app != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { picking = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        app?.label
                            ?: if (needsApp) "选择应用(必选)" else "限定应用(可选, 不选=全局)"
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == RuleType.SPLASH,
                        onClick = { type = RuleType.SPLASH },
                        label = { Text(RuleType.SPLASH.displayName()) },
                    )
                    FilterChip(
                        selected = type == RuleType.CONSENT,
                        onClick = { type = RuleType.CONSENT },
                        label = { Text(RuleType.CONSENT.displayName()) },
                    )
                    FilterChip(
                        selected = type == RuleType.POPUP,
                        onClick = { type = RuleType.POPUP },
                        label = { Text(RuleType.POPUP.displayName()) },
                    )
                }
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("按钮文字(逗号分隔)") },
                    supportingText = { Text("例: 同意, 同意并继续") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !equalsMode,
                        onClick = { equalsMode = false },
                        label = { Text("包含匹配") },
                    )
                    FilterChip(
                        selected = equalsMode,
                        onClick = { equalsMode = true },
                        label = { Text("完全相等") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    RuleRepository.addCustom(
                        Rule(
                            id = "u_${System.currentTimeMillis()}",
                            type = type,
                            keywords = parsedKeywords,
                            matchMode = if (equalsMode) MatchMode.EQUALS else MatchMode.CONTAINS,
                            packageName = app?.packageName,
                            label = app?.label ?: "全局规则",
                        )
                    )
                    onDismiss()
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (picking) {
        AppPickerDialog(
            title = "选择应用",
            hint = null,
            apps = apps,
            selected = app?.packageName?.let { setOf(it) } ?: emptySet(),
            onToggle = {
                app = it
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun AppPickerDialog(
    title: String,
    hint: String?,
    apps: List<AppInfo>,
    selected: Set<String>,
    onToggle: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索") },
                    singleLine = true,
                )
                LazyColumn(Modifier.height(360.dp)) {
                    val filtered = apps.filter {
                        query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
                    }
                    items(filtered, key = { it.packageName }) { info ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(info) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(info.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    info.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Checkbox(checked = info.packageName in selected, onCheckedChange = null)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
