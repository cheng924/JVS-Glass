package com.example.jvsglass.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun JVSGlassTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme, // 替换为你的颜色方案
        typography = YourTypography,    // 替换为你的字体配置
        content = content
    )
}