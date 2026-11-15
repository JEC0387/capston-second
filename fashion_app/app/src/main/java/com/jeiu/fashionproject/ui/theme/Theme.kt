package com.jeiu.fashionproject.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

internal val FashionMuted = Color(0xFF62564C)
internal val FashionBorder = Color(0xFF9C8270)
internal val FashionCardShape = RoundedCornerShape(20.dp)
internal val FashionActionShape = RoundedCornerShape(14.dp)

/** Opt-in palette for the updated fitting, body and catalogue screens. */
@Composable
internal fun FashionFeatureTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = BrownDark, onPrimary = White,
            primaryContainer = CamelLight, onPrimaryContainer = BrownDark,
            secondary = BrownDark, onSecondary = White,
            secondaryContainer = CamelLight, onSecondaryContainer = CharcoalBlack,
            tertiary = BrownDark, onTertiary = White,
            tertiaryContainer = CreamDark, onTertiaryContainer = CharcoalBlack,
            background = Cream, onBackground = CharcoalBlack,
            surface = Cream, onSurface = CharcoalBlack,
            surfaceVariant = CreamDark, onSurfaceVariant = FashionMuted,
            surfaceTint = BrownDark, outline = FashionBorder, outlineVariant = LightGray,
            surfaceContainerLowest = White, surfaceContainerLow = Cream,
            surfaceContainer = CreamDark, surfaceContainerHigh = CreamDark,
            surfaceContainerHighest = CamelLight
        ),
        content = content
    )
}

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
