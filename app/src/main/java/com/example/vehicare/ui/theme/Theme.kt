package com.example.vehicare.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Light theme only, by design (Section 3: "implement light theme as primary; dark theme is
 * optional"). Keeping a single scheme guarantees consistent contrast for the safety colours, which
 * carry clinical meaning in this app.
 */
private val LightColorScheme = lightColorScheme(
    primary = Navy800,
    onPrimary = White,
    primaryContainer = Navy100,
    onPrimaryContainer = Navy900,
    secondary = Slate700,
    onSecondary = White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Navy900,
    tertiary = Teal600,
    onTertiary = White,
    tertiaryContainer = Teal100,
    onTertiaryContainer = Color(0xFF04322D),
    background = White,
    onBackground = Charcoal900,
    surface = White,
    onSurface = Charcoal900,
    surfaceVariant = Slate50,
    onSurfaceVariant = Slate500,
    outline = Slate200,
    outlineVariant = Slate100,
    error = CriticalRed,
    onError = White,
    errorContainer = CriticalRedContainer,
    onErrorContainer = Color(0xFF410E0B)
)

private val VehiCareShapes = Shapes(
    small = RoundedCornerShape(Dimens.CornerSm),
    medium = RoundedCornerShape(Dimens.CornerMd),
    large = RoundedCornerShape(Dimens.CornerLg),
    extraLarge = RoundedCornerShape(Dimens.CornerXl)
)

@Composable
fun VehiCareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = VehiCareShapes,
        content = content
    )
}
