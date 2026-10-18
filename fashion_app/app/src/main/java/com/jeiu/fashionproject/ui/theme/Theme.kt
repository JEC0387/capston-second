package com.jeiu.fashionproject.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Camel,
    onPrimary = White,
    primaryContainer = CamelLight,
    onPrimaryContainer = BrownDark,
    secondary = Brown,
    onSecondary = White,
    secondaryContainer = CreamDark,
    onSecondaryContainer = BrownDark,
    background = Cream,
    onBackground = CharcoalBlack,
    surface = White,
    onSurface = CharcoalBlack,
    surfaceVariant = CreamDark,
    onSurfaceVariant = MediumGray,
    outline = LightGray
)

@Composable
fun FashionProjectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
