package com.litejump.app.ui

import android.app.Activity
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.litejump.app.R
import com.litejump.app.engine.CaptureHub
import com.litejump.app.engine.CaptureScheduler
import com.litejump.app.engine.ClickLog
import com.litejump.app.engine.SkipService
import com.litejump.app.engine.Stats
import com.litejump.app.rules.MatchMode
import com.litejump.app.rules.Rule
import com.litejump.app.rules.RuleRepository
import com.litejump.app.rules.RuleType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppInfo(val label: String, val packageName: String)

private fun loadInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

@Composable
private fun RuleType.displayName(): String = stringResource(
    when (this) {
        RuleType.SPLASH -> R.string.type_splash
        RuleType.CONSENT -> R.string.type_consent
        RuleType.POPUP -> R.string.type_popup
        RuleType.CONFIRM -> R.string.type_confirm
    }
)

private fun formatSaved(seconds: Long, secUnit: String, minUnit: String): String =
    if (seconds < 60) "$seconds $secUnit" else "${seconds / 60} $minUnit ${seconds % 60} $secUnit"

private val logTimeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

/** 「添加规则」弹窗的预填内容(来自取词结果) */
private data class Prefill(val app: AppInfo?, val keywords: String)

@Composable
fun AppUi() {
    val context = LocalContext.current
    val master by RuleRepository.masterEnabled.collectAsState()
    val builtin by RuleRepository.builtinRules.collectAsState()
    val builtinOff by RuleRepository.builtinDisabled.collectAsState()
    val blacklist by RuleRepository.splashBlacklist.collectAsState()
    val custom by RuleRepository.customRules.collectAsState()
    val logEntries by ClickLog.entries.collectAsState()
    val stats by Stats.stats.collectAsState()

    var serviceOn by remember { mutableStateOf(SkipService.connected) }
    var showAddRule by remember { mutableStateOf(false) }
    var addRulePrefill by remember { mutableStateOf<Prefill?>(null) }
    var showBlacklist by remember { mutableStateOf(false) }
    var showCaptureIntro by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf<CaptureHub.Result?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(Unit) { apps = loadInstalledApps(context) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3000)
            message = null
        }
    }

    // 回到前台: 刷新服务状态, 消费取词结果
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceOn = SkipService.connected
                captured = CaptureHub.consume()
            }
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
            message = context.getString(if (ok) R.string.msg_exported else R.string.msg_export_failed)
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
                n == null -> context.getString(R.string.msg_import_failed)
                n == 0 -> context.getString(R.string.msg_import_none)
                else -> context.getString(R.string.msg_imported, n)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { RuleRepository.setOnboarded(false) }) {
                Text(stringResource(R.string.usage_guide))
            }
        }
        Text(
            stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        ServiceCard(serviceOn, master, onMaster = RuleRepository::setMaster) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        message?.let {
            Card {
                Text(
                    it,
                    Modifier.fillMaxWidth().padding(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        StatsCard(stats, onClear = { Stats.reset(context) })

        Card {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.builtin_rules), style = MaterialTheme.typography.titleMedium)
                (builtin.global + builtin.apps).forEach { rule ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.label ?: rule.id, style = MaterialTheme.typography.bodyMedium)
                            RuleSubtitle(rule)
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
                        Text(stringResource(R.string.splash_blacklist), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (blacklist.isEmpty()) stringResource(R.string.blacklist_empty)
                            else stringResource(R.string.blacklist_count, blacklist.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    OutlinedButton(onClick = { showBlacklist = true }) { Text(stringResource(R.string.manage)) }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.my_rules), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showAddRule = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.padding(2.dp))
                        Text(stringResource(R.string.add))
                    }
                    OutlinedButton(onClick = { showCaptureIntro = true }) { Text(stringResource(R.string.capture)) }
                }
                if (custom.isEmpty()) {
                    Text(
                        stringResource(R.string.my_rules_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                custom.forEach { rule ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.label ?: rule.packageName ?: rule.id, style = MaterialTheme.typography.bodyMedium)
                            RuleSubtitle(rule)
                        }
                        IconButton(onClick = { RuleRepository.removeCustom(rule.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }

        ClickLogCard(logEntries, apps, onClear = { ClickLog.clear(context) })

        Card {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("litejump-rules.json") }) { Text(stringResource(R.string.export_rules)) }
                    OutlinedButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }) { Text(stringResource(R.string.import_rules)) }
                }
                Text(
                    stringResource(R.string.backup_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        Text(
            stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }

    if (showAddRule) {
        AddRuleDialog(apps = apps, prefill = addRulePrefill, onDismiss = { showAddRule = false })
    }
    if (showBlacklist) {
        AppPickerDialog(
            title = stringResource(R.string.blacklist_dialog_title),
            hint = stringResource(R.string.blacklist_dialog_hint),
            apps = apps,
            selected = blacklist,
            onToggle = { RuleRepository.setSplashBlocked(it.packageName, it.packageName !in blacklist) },
            onDismiss = { showBlacklist = false },
        )
    }
    if (showCaptureIntro) {
        AlertDialog(
            onDismissRequest = { showCaptureIntro = false },
            title = { Text(stringResource(R.string.capture_dlg_title)) },
            text = {
                if (!serviceOn) Text(stringResource(R.string.need_accessibility_first))
                else Text(stringResource(R.string.capture_dlg_steps))
            },
            confirmButton = {
                TextButton(
                    enabled = serviceOn,
                    onClick = {
                        showCaptureIntro = false
                        val started = CaptureScheduler.start(SkipService.instance)
                        if (started) (context as? Activity)?.moveTaskToBack(true)
                        else message = context.getString(R.string.need_accessibility_first)
                    },
                ) { Text(stringResource(R.string.capture_start)) }
            },
            dismissButton = { TextButton(onClick = { showCaptureIntro = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    captured?.let { result ->
        CaptureResultDialog(
            result = result,
            apps = apps,
            onUse = { prefill ->
                addRulePrefill = prefill
                showAddRule = true
                captured = null
            },
            onDismiss = { captured = null },
        )
    }
}

@Composable
private fun RuleSubtitle(rule: Rule) {
    val extra = buildList {
        rule.viewId?.let { add(it) }
        rule.activityName?.let { add(it.substringAfterLast('.')) }
    }
    Text(
        buildString {
            append(rule.type.displayName())
            append(" · ")
            append(rule.packageName ?: stringResource(R.string.label_global_rule))
            if (rule.keywords.isNotEmpty()) {
                append(" · ")
                append(rule.keywords.joinToString("/"))
            }
            if (extra.isNotEmpty()) {
                append(" · ")
                append(extra.joinToString("/"))
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
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
                    Text(stringResource(R.string.master_switch), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (master) R.string.master_on else R.string.master_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Switch(checked = master, onCheckedChange = onMaster, enabled = serviceOn)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (serviceOn) R.string.service_on else R.string.service_off),
                    Modifier.weight(1f),
                    color = if (serviceOn) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                )
                if (!serviceOn) Button(onClick = onOpenSettings) { Text(stringResource(R.string.open_accessibility)) }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: Stats.Snapshot, onClear: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.stats_total, stats.total))
                Text(stringResource(R.string.stats_today, stats.today))
            }
            val sec = stringResource(R.string.unit_seconds)
            val min = stringResource(R.string.unit_minutes)
            Text(
                stringResource(R.string.stats_saved, formatSaved(stats.savedSeconds, sec, min)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ClickLogCard(entries: List<ClickLog.Entry>, apps: List<AppInfo>, onClear: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.click_log_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
            }
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.click_log_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            val labelMap = remember(apps) { apps.associate { it.packageName to it.label } }
            entries.take(8).forEach { entry ->
                Text(
                    buildString {
                        append(logTimeFormat.format(Date(entry.time)))
                        append(" · ")
                        append(labelMap[entry.packageName] ?: entry.packageName)
                        append(" · ")
                        append(entry.text)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AddRuleDialog(apps: List<AppInfo>, prefill: Prefill?, onDismiss: () -> Unit) {
    var app by remember { mutableStateOf(prefill?.app) }
    var type by remember { mutableStateOf(RuleType.SPLASH) }
    var keywords by remember { mutableStateOf(prefill?.keywords ?: "") }
    var viewId by remember { mutableStateOf("") }
    var equalsMode by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    val parsedKeywords = keywords.split(',', '，', '、', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val trimmedViewId = viewId.trim().takeIf { it.isNotEmpty() }
    val needsApp = type == RuleType.CONSENT || type == RuleType.POPUP
    val valid = (parsedKeywords.isNotEmpty() || trimmedViewId != null) && (!needsApp || app != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_rule_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        app?.label
                            ?: if (needsApp) stringResource(R.string.pick_app_required) else stringResource(R.string.pick_app_optional)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    FilterChip(
                        selected = type == RuleType.CONFIRM,
                        onClick = { type = RuleType.CONFIRM },
                        label = { Text(RuleType.CONFIRM.displayName()) },
                    )
                }
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.keywords_label)) },
                    supportingText = { Text(stringResource(R.string.keywords_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = viewId,
                    onValueChange = { viewId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.viewid_label)) },
                    supportingText = { Text(stringResource(R.string.viewid_hint)) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !equalsMode,
                        onClick = { equalsMode = false },
                        label = { Text(stringResource(R.string.match_contains)) },
                    )
                    FilterChip(
                        selected = equalsMode,
                        onClick = { equalsMode = true },
                        label = { Text(stringResource(R.string.match_equals)) },
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
                            label = app?.label,
                            viewId = trimmedViewId,
                        )
                    )
                    onDismiss()
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (picking) {
        AppPickerDialog(
            title = stringResource(R.string.pick_app_required),
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
private fun CaptureResultDialog(
    result: CaptureHub.Result,
    apps: List<AppInfo>,
    onUse: (Prefill) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember { mutableStateOf(setOf<Int>()) }
    val app = apps.find { it.packageName == result.packageName } ?: AppInfo(result.packageName, result.packageName)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.capture_result_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.capture_result_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(app.label, style = MaterialTheme.typography.titleSmall)
                LazyColumn(Modifier.height(320.dp)) {
                    items(result.items.size) { index ->
                        val item = result.items[index]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected.value =
                                        if (index in selected.value) selected.value - index
                                        else selected.value + index
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                item.viewId?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                                }
                            }
                            Checkbox(checked = index in selected.value, onCheckedChange = null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.value.isNotEmpty(),
                onClick = {
                    val keywords = selected.value.sorted().mapNotNull { result.items.getOrNull(it)?.text }
                    onUse(Prefill(app, keywords.joinToString(", ")))
                },
            ) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
                    label = { Text(stringResource(R.string.search)) },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
