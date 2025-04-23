package com.isaacbegue.afinador.ui.theme

import androidx.compose.ui.graphics.Color

// Nueva Paleta Oscura Basada en el Logo
val DarkBackground = Color(0xFF212121) // Gris oscuro (similar al icono/contorno del logo)
val DarkSurface = Color(0xFF303030)    // Gris ligeramente más claro para elevación
val DarkPrimary = Color(0xFFA8C945)    // Verde lima del fondo del logo
val DarkPrimaryVariant = Color(0xFF8BA83B) // Verde lima ligeramente más oscuro
val DarkSecondary = Color(0xFFA8C945)    // Reutilizamos el primario para simplicidad
val DarkOnBackground = Color(0xFFE0E0E0) // Texto/iconos claros sobre fondo oscuro
val DarkOnSurface = Color(0xFFE0E0E0)  // Texto/iconos claros sobre superficie oscura
val DarkOnPrimary = Color(0xFF212121)    // Texto/iconos oscuros sobre fondo primario (verde)
val DarkError = Color(0xFFCF6679)      // Color para errores (mantenido)
val DarkOnError = Color(0xFF000000)    // Texto/iconos sobre errores (mantenido)


// Colores funcionales para el afinador (mantenidos)
val TunerGreen = Color(0xFF4CAF50)     // Verde para indicar afinación correcta
val TunerYellow = Color(0xFFFFEB3B)    // Amarillo para indicar cercanía
val TunerRed = Color(0xFFF44336)       // Rojo para indicar desafinación