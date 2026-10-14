package com.litejump.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.litejump.app.R
import com.litejump.app.engine.SkipService
import com.litejump.app.rules.RuleRepository
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4

/**
 * 分页新手引导: 欢迎 → 开无障碍(实时检测) → 保活(内嵌四步) → 试一试。
 * 右上角「跳过」随时可退出; 之后可从首页「使用指南」重新打开。
 */
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    var serviceOn by remember { mutableStateOf(SkipService.connected) }
    var showKeepalive by remember { mutableStateOf(false) }

    // 从系统设置回来时刷新服务状态, 第二页自动打勾
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) serviceOn = SkipService.connected
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { RuleRepository.setOnboarded(true) }) {
                Text(stringResource(R.string.ob_skip))
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (page) {
                    0 -> TextPage(stringResource(R.string.ob_p1_title), stringResource(R.string.ob_p1_body))
                    1 -> AccessibilityPage(serviceOn) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }

                    2 -> KeepAlivePage { showKeepalive = true }
                    else -> TextPage(stringResource(R.string.ob_p4_title), stringResource(R.string.ob_p4_body))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(PAGE_COUNT) { index ->
                val active = index == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                )
            }
        }

        Button(
            onClick = {
                if (pagerState.currentPage < PAGE_COUNT - 1) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    RuleRepository.setOnboarded(true)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (pagerState.currentPage < PAGE_COUNT - 1) R.string.ob_next else R.string.ob_done
                )
            )
        }
    }

    if (showKeepalive) {
        AlertDialog(
            onDismissRequest = { showKeepalive = false },
            title = { Text(stringResource(R.string.ob_p3_title)) },
            text = { Text(stringResource(R.string.ob_keepalive_steps), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showKeepalive = false }) { Text(stringResource(R.string.done)) }
            },
        )
    }
}

@Composable
private fun TextPage(title: String, body: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun AccessibilityPage(serviceOn: Boolean, onOpenSettings: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.ob_p2_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.ob_p2_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            stringResource(if (serviceOn) R.string.service_on else R.string.service_off),
            style = MaterialTheme.typography.titleSmall,
            color = if (serviceOn) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
        )
        if (!serviceOn) {
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.open_accessibility)) }
        } else {
            Text("✓", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF16A34A))
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun KeepAlivePage(onOpenGuide: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.ob_p3_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.ob_p3_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline,
        )
        OutlinedButton(onClick = onOpenGuide) { Text(stringResource(R.string.ob_open_guide)) }
    }
}
