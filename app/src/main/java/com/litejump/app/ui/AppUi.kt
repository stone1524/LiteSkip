package com.litejump.app.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.litejump.app.rules.RuleRepository
import kotlinx.coroutines.delay

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
    val toastOn by RuleRepository.toastEnabled.collectAsState()

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

    // 回到前台: 刷新服务状态, 取消进行中的取词倒计时, 消费取词结果
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceOn = SkipService.connected
                CaptureScheduler.cancel()
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
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

        ServiceCard(
            serviceOn,
            master,
            toastOn,
            onMaster = RuleRepository::setMaster,
            onToastToggle = RuleRepository::setToastEnabled,
        ) {
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
                        androidx.compose.material3.Switch(
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
