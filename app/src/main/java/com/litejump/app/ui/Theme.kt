package com.litejump.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandGreen = Color(0xFF16A34A)
private val BrandGreenDark = Color(0xFF4ADE80)

/** Android 12+ 跟随系统动态取色, 低版本与未启用时用品牌绿 */
@Composable
fun liteSkipColorScheme(darkTheme: Boolean = isSystemInDarkTheme()): ColorScheme {
    val context = LocalContext.current
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> darkColorScheme(primary = BrandGreenDark)
        else -> lightColorScheme(primary = BrandGreen)
    }
}
