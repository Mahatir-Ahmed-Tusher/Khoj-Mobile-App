package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SlateDarkPrimary,
    secondary = SlateDarkSecondary,
    tertiary = SlateDarkTertiary,
    background = SlateDarkBackground,
    surface = SlateDarkSurface,
    onPrimary = OnSlateDarkPrimary,
    onSecondary = OnSlateDarkSecondary,
    onBackground = OnSlateDarkBackground,
    onSurface = OnSlateDarkSurface
)

private val LightColorScheme = lightColorScheme(
    primary = SlatePrimary,
    secondary = SlateSecondary,
    tertiary = SlateTertiary,
    background = SlateBackground,
    surface = SlateSurface,
    onPrimary = OnSlatePrimary,
    onSecondary = OnSlateSecondary,
    onBackground = OnSlateBackground,
    onSurface = OnSlateSurface
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isBn: Boolean = false,
    bengaliFont: String = "tiro",
    // Dynamic color support on Android 12+ (turn off by default to preserve Khoj branding colors)
    dynamicColor: Boolean = false,
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = getTypography(isBn, bengaliFont),
        content = content
    )
}

tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
