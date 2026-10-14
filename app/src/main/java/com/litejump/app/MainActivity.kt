package com.litejump.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.litejump.app.rules.RuleRepository
import com.litejump.app.ui.AppUi
import com.litejump.app.ui.OnboardingScreen
import com.litejump.app.ui.liteSkipColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = liteSkipColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val onboarded by RuleRepository.onboarded.collectAsState()
                    if (onboarded) AppUi() else OnboardingScreen()
                }
            }
        }
    }
}
