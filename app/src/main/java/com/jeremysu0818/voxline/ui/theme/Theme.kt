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
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFDFE3E2),
    inverseOnSurface = Color(0xFF2D3130),
    inversePrimary = VoxlinePrimaryLight,
    surfaceTint = VoxlinePrimaryDark,
    scrim = Color.Black,
    surfaceDim = Color(0xFF101414),
    surfaceBright = Color(0xFF363A39),
    surfaceContainerLowest = Color(0xFF0B0F0F),
    surfaceContainerLow = Color(0xFF181C1C),
    surfaceContainer = Color(0xFF1C2020),
    surfaceContainerHigh = Color(0xFF262B2A),
    surfaceContainerHighest = Color(0xFF313534),
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
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2D3130),
    inverseOnSurface = Color(0xFFEFF1F0),
    inversePrimary = VoxlinePrimaryDark,
    surfaceTint = VoxlinePrimaryLight,
    scrim = Color.Black,
    surfaceDim = Color(0xFFD7DBDA),
    surfaceBright = VoxlineBackgroundLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F4),
    surfaceContainer = Color(0xFFEBEFEE),
    surfaceContainerHigh = Color(0xFFE5E9E8),
    surfaceContainerHighest = Color(0xFFDFE3E2),
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
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
