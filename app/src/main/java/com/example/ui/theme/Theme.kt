package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun buildLightSchemeForPalette(palette: AppThemePalette) = lightColorScheme(
    primary = palette.primaryColor,
    onPrimary = Color.White,
    primaryContainer = palette.primaryContainerColor,
    onPrimaryContainer = palette.onPrimaryContainerColor,
    secondary = palette.secondaryColor,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = MoneyEmerald,
    onTertiary = Color.White,
    tertiaryContainer = MoneyEmeraldLight,
    onTertiaryContainer = MoneyEmeraldDark,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = SurfaceBorderLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Mevcut aydınlık ve temiz tasarım korunur
    dynamicColor: Boolean = false,
    palette: AppThemePalette = AppThemePalette.CLASSIC_BLUE,
    content: @Composable () -> Unit,
) {
    val scheme = buildLightSchemeForPalette(palette)
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
