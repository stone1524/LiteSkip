package com.litejump.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.litejump.app.R
import com.litejump.app.engine.CaptureHub
import com.litejump.app.rules.MatchMode
import com.litejump.app.rules.Rule
import com.litejump.app.rules.RuleRepository
import com.litejump.app.rules.RuleType

@Composable
internal fun AddRuleDialog(apps: List<AppInfo>, prefill: Prefill?, onDismiss: () -> Unit) {
    var app by remember { mutableStateOf(prefill?.app) }
    var type by remember { mutableStateOf(RuleType.SPLASH) }
    var keywords by remember { mutableStateOf(prefill?.keywords ?: "") }
    var viewId by remember { mutableStateOf(prefill?.viewId ?: "") }
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
internal fun CaptureResultDialog(
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
                    val chosen = selected.value.sorted().mapNotNull { result.items.getOrNull(it) }
                    // 只勾一项且带控件ID时, 顺手把 viewId 也预填上
                    val viewId = if (chosen.size == 1) chosen[0].viewId else null
                    onUse(Prefill(app, chosen.joinToString(", ") { it.text }, viewId))
                },
            ) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun AppPickerDialog(
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
