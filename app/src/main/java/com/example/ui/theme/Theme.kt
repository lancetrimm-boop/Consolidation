package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = DiscoveryViolet,
    onPrimary = Color.White,
    primaryContainer = AuraPurpleContainer,
    onPrimaryContainer = DiscoveryViolet,
    
    secondary = DiscoveryMagenta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDE8FF),
    onSecondaryContainer = DiscoveryMagenta,
    
    tertiary = DiscoveryHotPink,
    onTertiary = Color.White,

    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F1117),
    
    surface = Color(0xFFF9FAFB),
    onSurface = Color(0xFF0F1117),
    
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF6B7280),
    
    outline = Color(0xFF6B7280),
    outlineVariant = Color(0xFFE5E7EB)
)

private val DarkColorScheme = darkColorScheme(
    primary = DiscoveryViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E1065),
    onPrimaryContainer = Color(0xFFDDD6FE),
    
    secondary = DiscoveryMagenta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF701A75),
    onSecondaryContainer = Color(0xFFFBCFE8),
    
    tertiary = DiscoveryHotPink,
    onTertiary = Color.White,

    background = Color(0xFF0F1117),
    onBackground = Color(0xFFF9FAFB),
    
    surface = Color(0xFF1F2937),
    onSurface = Color(0xFFF9FAFB),
    
    surfaceVariant = Color(0xFF374151),
    onSurfaceVariant = Color(0xFFD1D5DB),
    
    outline = Color(0xFF6B7280),
    outlineVariant = Color(0xFF374151)
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            val activity = context as? Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity
            activity?.window?.let { window ->
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
