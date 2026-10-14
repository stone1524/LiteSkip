package com.litejump.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.litejump.app.R
import com.litejump.app.rules.RuleRepository

/** 首次启动的三步引导; 之后可从首页「使用指南」重新打开 */
@Composable
fun OnboardingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(
            stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )

        Card {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.ob1_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.ob1_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Card {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.ob2_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.ob2_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Card {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.ob3_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.ob3_body), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Button(onClick = { RuleRepository.setOnboarded(true) }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.ob_done))
        }
    }
}
