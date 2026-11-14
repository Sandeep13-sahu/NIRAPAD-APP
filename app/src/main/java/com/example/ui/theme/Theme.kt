package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NirapadLightColorScheme = lightColorScheme(
    primary = NirapadPrimaryTeal,
    onPrimary = Color.White,
    primaryContainer = NirapadPrimaryTealContainer,
    onPrimaryContainer = NirapadNavyDark,
    secondary = NirapadNavyDark,
    onSecondary = Color.White,
    secondaryContainer = NirapadSurfaceVariant,
    onSecondaryContainer = NirapadNavyDark,
    tertiary = NirapadPositiveGreen,
    onTertiary = Color.White,
    tertiaryContainer = NirapadPositiveContainer,
    onTertiaryContainer = NirapadNavyDark,
    error = NirapadEmergencyRed,
    onError = Color.White,
    errorContainer = NirapadEmergencyContainer,
    onErrorContainer = NirapadEmergencyRed,
    background = NirapadCanvas,
    onBackground = NirapadNavyDark,
    surface = NirapadSurface,
    onSurface = NirapadNavyDark,
    surfaceVariant = NirapadSurfaceVariant,
    onSurfaceVariant = NirapadTextSecondary,
    outline = NirapadBorderSubtle,
    outlineVariant = NirapadBorderSubtle
)

private val NirapadDarkColorScheme = darkColorScheme(
    primary = NirapadDarkTeal,
    onPrimary = NirapadNavyDark,
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFFA8EFF7),
    secondary = Color(0xFF9FB3C8),
    onSecondary = NirapadNavyDark,
    secondaryContainer = NirapadDarkSurfaceVariant,
    onSecondaryContainer = Color(0xFFD9E2EC),
    tertiary = Color(0xFF56C596),
    onTertiary = Color(0xFF032B1A),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5C0000),
    errorContainer = Color(0xFF6B1414),
    onErrorContainer = Color(0xFFFFDAD6),
    background = NirapadDarkCanvas,
    onBackground = Color(0xFFF0F4F8),
    surface = NirapadDarkSurface,
    onSurface = Color(0xFFF0F4F8),
    surfaceVariant = NirapadDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFBCCCDC),
    outline = Color(0xFF2B4661)
)

val NirapadShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun NirapadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NirapadDarkColorScheme else NirapadLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NirapadTypography,
        shapes = NirapadShapes,
        content = content
    )
}
