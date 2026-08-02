package com.jeremysu0818.voxline.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import com.jeremysu0818.voxline.data.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = VoxlinePrimaryDark,
    onPrimary = VoxlineOnPrimaryDark,
    primaryContainer = VoxlinePrimaryContainerDark,
    onPrimaryContainer = VoxlineOnPrimaryContainerDark,
    secondary = VoxlineSecondaryDark,
    onSecondary = VoxlineOnSecondaryDark,
    secondaryContainer = VoxlineSecondaryContainerDark,
    onSecondaryContainer = VoxlineOnSecondaryContainerDark,
    tertiary = VoxlineTertiaryDark,
    onTertiary = VoxlineOnTertiaryDark,
    tertiaryContainer = VoxlineTertiaryContainerDark,
    onTertiaryContainer = VoxlineOnTertiaryContainerDark,
    background = VoxlineBackgroundDark,
    onBackground = VoxlineOnBackgroundDark,
    surface = VoxlineBackgroundDark,
    onSurface = VoxlineOnBackgroundDark,
    surfaceVariant = VoxlineSurfaceVariantDark,
    onSurfaceVariant = VoxlineOnSurfaceVariantDark,
    outline = VoxlineOutlineDark,
    outlineVariant = VoxlineOutlineVariantDark,
    error = VoxlineErrorDark,
    onError = VoxlineOnErrorDark,
    errorContainer = VoxlineErrorContainerDark,
    onErrorContainer = VoxlineOnErrorContainerDark,
    inverseSurface = Color(0xFFDDE4E1),
    inverseOnSurface = Color(0xFF2B3230),
    inversePrimary = VoxlinePrimaryLight,
    surfaceTint = VoxlinePrimaryDark,
    scrim = Color.Black,
    surfaceDim = Color(0xFF0E1513),
    surfaceBright = Color(0xFF343A39),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF161D1C),
    surfaceContainer = Color(0xFF1A2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303635),
)

private val LightColorScheme = lightColorScheme(
    primary = VoxlinePrimaryLight,
    onPrimary = VoxlineOnPrimaryLight,
    primaryContainer = VoxlinePrimaryContainerLight,
    onPrimaryContainer = VoxlineOnPrimaryContainerLight,
    secondary = VoxlineSecondaryLight,
    onSecondary = VoxlineOnSecondaryLight,
    secondaryContainer = VoxlineSecondaryContainerLight,
    onSecondaryContainer = VoxlineOnSecondaryContainerLight,
    tertiary = VoxlineTertiaryLight,
    onTertiary = VoxlineOnTertiaryLight,
    tertiaryContainer = VoxlineTertiaryContainerLight,
    onTertiaryContainer = VoxlineOnTertiaryContainerLight,
    background = VoxlineBackgroundLight,
    onBackground = VoxlineOnBackgroundLight,
    surface = VoxlineBackgroundLight,
    onSurface = VoxlineOnBackgroundLight,
    surfaceVariant = VoxlineSurfaceVariantLight,
    onSurfaceVariant = VoxlineOnSurfaceVariantLight,
    outline = VoxlineOutlineLight,
    outlineVariant = VoxlineOutlineVariantLight,
    error = VoxlineErrorLight,
    onError = VoxlineOnErrorLight,
    errorContainer = VoxlineErrorContainerLight,
    onErrorContainer = VoxlineOnErrorContainerLight,
    inverseSurface = Color(0xFF2B3230),
    inverseOnSurface = Color(0xFFECF2EF),
    inversePrimary = VoxlinePrimaryDark,
    surfaceTint = VoxlinePrimaryLight,
    scrim = Color.Black,
    surfaceDim = Color(0xFFD5DBD9),
    surfaceBright = VoxlineBackgroundLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = Color(0xFFE9EFED),
    surfaceContainerHigh = Color(0xFFE3EAE7),
    surfaceContainerHighest = Color(0xFFDDE4E1),
)

private val VoxlineShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
    largeIncreased = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(36.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VoxlineTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val (isDark, useDynamic) = when (themeMode) {
        ThemeMode.SYSTEM -> {
            val isAtLeastS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            Pair(darkTheme, isAtLeastS)
        }
        ThemeMode.LIGHT -> Pair(false, false)
        ThemeMode.DARK -> Pair(true, false)
    }

    val colorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        shapes = VoxlineShapes,
        content = content,
    )
}
