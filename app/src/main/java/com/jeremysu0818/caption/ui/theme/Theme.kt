package com.jeremysu0818.caption.ui.theme

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
    primary = CaptionPrimaryDark,
    onPrimary = CaptionOnPrimaryDark,
    primaryContainer = CaptionPrimaryContainerDark,
    onPrimaryContainer = CaptionOnPrimaryContainerDark,
    secondary = CaptionSecondaryDark,
    onSecondary = CaptionOnSecondaryDark,
    secondaryContainer = CaptionSecondaryContainerDark,
    onSecondaryContainer = CaptionOnSecondaryContainerDark,
    tertiary = CaptionTertiaryDark,
    onTertiary = CaptionOnTertiaryDark,
    tertiaryContainer = CaptionTertiaryContainerDark,
    onTertiaryContainer = CaptionOnTertiaryContainerDark,
    background = CaptionBackgroundDark,
    onBackground = CaptionOnBackgroundDark,
    surface = CaptionBackgroundDark,
    onSurface = CaptionOnBackgroundDark,
    surfaceVariant = CaptionSurfaceVariantDark,
    onSurfaceVariant = CaptionOnSurfaceVariantDark,
    outline = CaptionOutlineDark,
    outlineVariant = CaptionOutlineVariantDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFDFE3E2),
    inverseOnSurface = Color(0xFF2D3130),
    inversePrimary = CaptionPrimaryLight,
    surfaceTint = CaptionPrimaryDark,
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
    primary = CaptionPrimaryLight,
    onPrimary = CaptionOnPrimaryLight,
    primaryContainer = CaptionPrimaryContainerLight,
    onPrimaryContainer = CaptionOnPrimaryContainerLight,
    secondary = CaptionSecondaryLight,
    onSecondary = CaptionOnSecondaryLight,
    secondaryContainer = CaptionSecondaryContainerLight,
    onSecondaryContainer = CaptionOnSecondaryContainerLight,
    tertiary = CaptionTertiaryLight,
    onTertiary = CaptionOnTertiaryLight,
    tertiaryContainer = CaptionTertiaryContainerLight,
    onTertiaryContainer = CaptionOnTertiaryContainerLight,
    background = CaptionBackgroundLight,
    onBackground = CaptionOnBackgroundLight,
    surface = CaptionBackgroundLight,
    onSurface = CaptionOnBackgroundLight,
    surfaceVariant = CaptionSurfaceVariantLight,
    onSurfaceVariant = CaptionOnSurfaceVariantLight,
    outline = CaptionOutlineLight,
    outlineVariant = CaptionOutlineVariantLight,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2D3130),
    inverseOnSurface = Color(0xFFEFF1F0),
    inversePrimary = CaptionPrimaryDark,
    surfaceTint = CaptionPrimaryLight,
    scrim = Color.Black,
    surfaceDim = Color(0xFFD7DBDA),
    surfaceBright = CaptionBackgroundLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F4),
    surfaceContainer = Color(0xFFEBEFEE),
    surfaceContainerHigh = Color(0xFFE5E9E8),
    surfaceContainerHighest = Color(0xFFDFE3E2),
)

private val CaptionShapes = Shapes(
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
fun CaptionTheme(
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
        shapes = CaptionShapes,
        content = content,
    )
}
