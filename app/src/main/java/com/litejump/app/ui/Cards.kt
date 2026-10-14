package com.litejump.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.litejump.app.R
import com.litejump.app.engine.ClickLog
import com.litejump.app.engine.Stats
import com.litejump.app.rules.Rule
import java.util.Date

@Composable
internal fun RuleSubtitle(rule: Rule) {
    val extra = buildList {
        rule.viewId?.let { add(it) }
        rule.activityName?.let { add(it.substringAfterLast('.')) }
    }
    Text(
        buildString {
            append(rule.type.displayName())
            append(" · ")
            append(rule.packageName ?: globalRuleLabel)
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
internal fun ServiceCard(
    serviceOn: Boolean,
    master: Boolean,
    toastOn: Boolean,
    onMaster: (Boolean) -> Unit,
    onToastToggle: (Boolean) -> Unit,
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
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.toast_toggle), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.toast_toggle_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Switch(checked = toastOn, onCheckedChange = onToastToggle, enabled = serviceOn)
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
internal fun StatsCard(stats: Stats.Snapshot, onClear: () -> Unit) {
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
internal fun ClickLogCard(entries: List<ClickLog.Entry>, apps: List<AppInfo>, onClear: () -> Unit) {
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
                val typeLabel = entry.type.displayName()
                Text(
                    buildString {
                        append(logTimeFormat.format(Date(entry.time)))
                        append(" · ")
                        append(labelMap[entry.packageName] ?: entry.packageName)
                        append(" · ")
                        append(typeLabel)
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
