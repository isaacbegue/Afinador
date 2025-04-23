package com.isaacbegue.afinador.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme // Aunque forcemos oscuro, lo mantenemos por si acaso
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Define nuestro esquema de colores oscuros personalizado usando los nuevos colores
private val AppDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkPrimaryVariant, // Opcional, usamos la variante oscura del primario
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = DarkOnPrimary, // Texto oscuro sobre primario (verde)
    onSecondary = DarkOnPrimary, // Texto oscuro sobre secundario (verde)
    onTertiary = DarkOnBackground, // Texto claro sobre terciario (verde oscuro)
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    error = DarkError,
    onError = DarkOnError
    // Puedes definir más colores si es necesario (surfaceVariant, outline, etc.)
    // Por ejemplo, un color para bordes sutiles:
    // outline = DarkOnSurface.copy(alpha = 0.12f)
)

// Ya no definimos LightColorScheme explícitamente si sólo queremos modo oscuro

@Composable
fun AfinadorTheme(
    darkTheme: Boolean = true, // Forzar tema oscuro por defecto
    // El color dinámico (Android 12+) generalmente no se usa cuando queremos
    // una paleta específica de marca como la nuestra. Lo dejamos en false.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            // Incluso con color dinámico, respetamos la preferencia darkTheme
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Siempre usamos nuestro esquema oscuro personalizado
        else -> AppDarkColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Usamos el nuevo color de fondo para la barra de estado
            window.statusBarColor = colorScheme.background.toArgb()
            // Aseguramos que los iconos/texto de la barra de estado sean claros (porque el fondo es oscuro)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Usa la tipografía definida en Type.kt
        content = content
    )
}