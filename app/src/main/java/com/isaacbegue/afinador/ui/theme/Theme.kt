package com.isaacbegue.afinador.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme // Necesitamos importarlo aunque no lo usemos por defecto
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Define nuestro esquema de colores oscuros personalizado
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkPrimaryVariant, // Puedes ajustar esto si necesitas un color terciario
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = DarkOnBackground, // O un color específico si el primario es muy claro
    onSecondary = DarkBackground, // O un color específico
    onTertiary = DarkOnBackground,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    error = DarkError,
    onError = DarkOnError
    // Puedes definir más colores si es necesario (surfaceVariant, etc.)
)

// Opcional: Define un esquema claro si quieres soportarlo, usando colores claros
private val LightColorScheme = lightColorScheme(
    primary = DarkPrimary, // Puedes definir colores claros aquí
    secondary = DarkSecondary,
    tertiary = DarkPrimaryVariant,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

@Composable
fun AfinadorTheme(
    darkTheme: Boolean = true, // Forzar tema oscuro por defecto
    // Dynamic color is available on Android 12+
    // Dynamic color actualmente no es recomendado para nuestra app, ya que queremos
    // colores específicos para el afinador. Lo dejamos en false.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Usar nuestro esquema oscuro personalizado si darkTheme es true
        darkTheme -> DarkColorScheme
        // Usar el esquema claro si darkTheme es false (aunque lo forzamos a true por defecto)
        else -> LightColorScheme // Necesitas definir LightColorScheme arriba si quieres soportarlo
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb() // Color de la barra de estado
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Usa la tipografía definida en Type.kt
        content = content
    )
}