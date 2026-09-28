package com.rashid.appcloner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rashid.appcloner.domain.AppTheme

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFAAA1FF), onPrimary = Color(0xFF17133D),
    secondary = Color(0xFF4DDBB0), background = Color(0xFF0A1225),
    surface = Color(0xFF182238), onSurface = Color(0xFFF5F6FC),
    onBackground = Color(0xFFF5F6FC), surfaceVariant = Color(0xFF202B42),
    onSurfaceVariant = Color(0xFFAEB8D0)
)
private val LightScheme = lightColorScheme(
    primary = Color(0xFF5144E9), onPrimary = Color.White,
    secondary = Color(0xFF008C70), background = Color(0xFFF3F4FA),
    surface = Color.White, onSurface = Color(0xFF151A2B),
    surfaceVariant = Color(0xFFE5E8F1), onSurfaceVariant = Color(0xFF535B70)
)

@Composable
fun AppClonerTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val dark = when (theme) {
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
