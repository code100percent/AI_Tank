package com.example.new_tank_ai.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val CyberDarkColorScheme = darkColorScheme(
    primary = CyberElectricBlue,
    onPrimary = CyberBlack,
    primaryContainer = CyberBlueGlow,
    onPrimaryContainer = CyberWhite,
    secondary = CyberCyan,
    onSecondary = CyberBlack,
    tertiary = CyberSteelLight,
    background = CyberBlack,
    onBackground = CyberWhite,
    surface = CyberSurface,
    onSurface = CyberWhite,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = CyberTextMuted,
    error = CyberRed,
    onError = CyberWhite
)

@Composable
fun New_tank_aiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep dynamic color optional, default to our custom dark cyber theme for the cyber truck aesthetic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> CyberDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
