package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class KairoExtendedColors(
    val userBubble: Color,
    val codeBackground: Color,
    val codeBorder: Color,
    val subtleDivider: Color,
    val success: Color,
    val warning: Color
)

val LocalKairoColors = staticCompositionLocalOf {
    KairoExtendedColors(
        userBubble = WarmSurfaceVariant,
        codeBackground = WarmCodeBackground,
        codeBorder = WarmCodeBorder,
        subtleDivider = WarmOutlineVariant,
        success = EmeraldSuccess,
        warning = AmberWarning
    )
}

private val KairoLightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = TerracottaDarker,
    secondary = WarmTextPrimary,
    onSecondary = Color.White,
    secondaryContainer = WarmSurfaceContainer,
    onSecondaryContainer = WarmTextPrimary,
    tertiary = EmeraldSuccess,
    background = WarmCreamBackground,
    onBackground = WarmTextPrimary,
    surface = WarmSurface,
    onSurface = WarmTextPrimary,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmTextSecondary,
    surfaceContainerLowest = WarmSurface,
    surfaceContainerLow = WarmCreamBackground,
    surfaceContainer = WarmSurfaceContainer,
    surfaceContainerHigh = WarmSurfaceVariant,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
    error = CrimsonError,
    onError = Color.White
)

private val KairoDarkColorScheme = darkColorScheme(
    primary = CoralPrimary,
    onPrimary = Color(0xFF1A120F),
    primaryContainer = CoralContainer,
    onPrimaryContainer = Color(0xFFFFD8CB),
    secondary = ObsidianTextPrimary,
    onSecondary = ObsidianBackground,
    secondaryContainer = ObsidianSurfaceContainer,
    onSecondaryContainer = ObsidianTextPrimary,
    tertiary = EmeraldSuccess,
    background = ObsidianBackground,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianTextSecondary,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceVariant,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    error = Color(0xFFEF7474),
    onError = Color(0xFF2D0A0A)
)

val KairoShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

@Composable
fun KairoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) KairoDarkColorScheme else KairoLightColorScheme
    val extendedColors = if (darkTheme) {
        KairoExtendedColors(
            userBubble = ObsidianSurfaceVariant,
            codeBackground = ObsidianCodeBackground,
            codeBorder = ObsidianCodeBorder,
            subtleDivider = ObsidianOutlineVariant,
            success = Color(0xFF53B88D),
            warning = Color(0xFFE5A44E)
        )
    } else {
        KairoExtendedColors(
            userBubble = WarmSurfaceVariant,
            codeBackground = WarmCodeBackground,
            codeBorder = WarmCodeBorder,
            subtleDivider = WarmOutlineVariant,
            success = EmeraldSuccess,
            warning = AmberWarning
        )
    }

    CompositionLocalProvider(LocalKairoColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KairoTypography,
            shapes = KairoShapes,
            content = content
        )
    }
}
