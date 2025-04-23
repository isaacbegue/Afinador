package com.isaacbegue.afinador.model

// Lista de notas en notación estándar (sostenidos)
val STANDARD_NOTES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
// Rango de octavas que soportaremos
val OCTAVE_RANGE = 0..8

// Estructura para representar una nota específica (ej. E4)
data class Pitch(val noteName: String, val octave: Int) {
    override fun toString(): String = "$noteName$octave"
}

// Estructura para representar una afinación de instrumento
data class InstrumentTuning(val name: String, val pitches: List<Pitch>)

// Modos especiales renombrados
const val CHROMATIC_MODE_NAME = "Cromático" // <- RENOMBRADO (antes FREE_TUNING_MODE_NAME)
const val FREE_SINGING_MODE_NAME = "Libre / Canto" // <- RENOMBRADO (antes SINGING_MODE_NAME)

// Afinaciones predefinidas
object Tunings {
    // --- GUITAR ---
    val GUITAR_STANDARD = InstrumentTuning(
        "Guitarra (Estándar)",
        listOf(Pitch("E", 2), Pitch("A", 2), Pitch("D", 3), Pitch("G", 3), Pitch("B", 3), Pitch("E", 4))
    )
    val GUITAR_DROP_D = InstrumentTuning(
        "Guitarra (Drop D)",
        listOf(Pitch("D", 2), Pitch("A", 2), Pitch("D", 3), Pitch("G", 3), Pitch("B", 3), Pitch("E", 4))
    )
    val GUITAR_DROP_C = InstrumentTuning(
        "Guitarra (Drop C)",
        listOf(Pitch("C", 2), Pitch("G", 2), Pitch("C", 3), Pitch("F", 3), Pitch("A", 3), Pitch("D", 4))
    )
    val GUITAR_OPEN_G = InstrumentTuning(
        "Guitarra (Open G)",
        listOf(Pitch("D", 2), Pitch("G", 2), Pitch("D", 3), Pitch("G", 3), Pitch("B", 3), Pitch("D", 4))
    )
    val GUITAR_DADGAD = InstrumentTuning(
        "Guitarra (DADGAD)",
        listOf(Pitch("D", 2), Pitch("A", 2), Pitch("D", 3), Pitch("G", 3), Pitch("A", 3), Pitch("D", 4))
    )

    // --- BASS ---
    val BASS_4_STANDARD = InstrumentTuning(
        "Bajo 4C (Estándar)",
        listOf(Pitch("E", 1), Pitch("A", 1), Pitch("D", 2), Pitch("G", 2))
    )
    val BASS_5_STANDARD = InstrumentTuning(
        "Bajo 5C (Estándar)",
        listOf(Pitch("B", 0), Pitch("E", 1), Pitch("A", 1), Pitch("D", 2), Pitch("G", 2))
    )
    val BASS_6_STANDARD = InstrumentTuning(
        "Bajo 6C (Estándar)",
        listOf(Pitch("B", 0), Pitch("E", 1), Pitch("A", 1), Pitch("D", 2), Pitch("G", 2), Pitch("C", 3))
    )

    // --- OTHER INSTRUMENTS ---
    val UKULELE_STANDARD = InstrumentTuning(
        "Ukelele (Estándar C)",
        listOf(Pitch("G", 4), Pitch("C", 4), Pitch("E", 4), Pitch("A", 4))
    )
    val VIOLIN_STANDARD = InstrumentTuning(
        "Violín (Estándar)",
        listOf(Pitch("G", 3), Pitch("D", 4), Pitch("A", 4), Pitch("E", 5))
    )

    // --- SPECIAL PROFILES ---
    val FREE_SINGING_PROFILE = InstrumentTuning( // <- RENOMBRADO (antes CANTO_PROFILE)
        FREE_SINGING_MODE_NAME, // Usa la nueva constante
        emptyList() // No tiene notas/cuerdas predefinidas
    )

    // Lista de todas las afinaciones disponibles (asegúrate que el perfil especial usa el nombre nuevo)
    val ALL_TUNINGS = listOf(
        GUITAR_STANDARD,
        GUITAR_DROP_D,
        GUITAR_DROP_C,
        GUITAR_OPEN_G,
        GUITAR_DADGAD,
        BASS_4_STANDARD,
        BASS_5_STANDARD,
        BASS_6_STANDARD,
        UKULELE_STANDARD,
        VIOLIN_STANDARD,
        FREE_SINGING_PROFILE // <- Actualizado para reflejar el cambio de nombre del objeto
    )
}