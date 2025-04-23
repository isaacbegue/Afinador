package com.isaacbegue.afinador.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isaacbegue.afinador.model.FREE_SINGING_MODE_NAME // <- Import actualizado
import com.isaacbegue.afinador.ui.theme.AfinadorTheme
import com.isaacbegue.afinador.ui.theme.TunerGreen
import com.isaacbegue.afinador.ui.theme.TunerRed
import com.isaacbegue.afinador.ui.theme.TunerYellow
import java.util.Locale
import kotlin.math.abs

// Umbrales
private const val CENTS_CLOSE_THRESHOLD = 25.0f
private const val CENTS_IN_TUNE_THRESHOLD = 10.0f

@Composable
fun NoteIndicators(
    modifier: Modifier = Modifier,
    selectedTuningModeName: String,
    displayedNoteName: String?,
    displayedOctave: Int?,
    outsideRangeIndicator: String?,
    currentTargetNote: String?, // Texto a mostrar como objetivo (calculado en TunerScreen)
    centsOffset: Float,
    isNoteDetected: Boolean,
    isTuned: Boolean,
    showMicrotones: Boolean
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Indicador del Objetivo Actual (si aplica)
        // Ocultar en modo Libre / Canto
        if (selectedTuningModeName != FREE_SINGING_MODE_NAME && currentTargetNote != null) { // <- Constante actualizada
            Text(
                text = currentTargetNote, // Muestra la nota objetivo calculada (o nombre modo libre)
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else {
            // Dejar un espacio vacío en modo Libre / Canto para mantener la altura relativa
            Spacer(modifier = Modifier.height(28.dp)) // Altura aproximada del Text + padding
        }


        // 2. Indicador Principal (Nota Mostrada + Indicador +/- + Cents vs Objetivo/Detectado)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val noteText = if (isNoteDetected && displayedNoteName != null && displayedOctave != null) {
                "$displayedNoteName$displayedOctave${outsideRangeIndicator ?: ""}"
            } else {
                "--"
            }

            // El color sigue basándose en la desviación vs OBJETIVO (o vs detectado en Libre/Canto)
            val textColor = when {
                !isNoteDetected -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                isTuned -> TunerGreen
                abs(centsOffset) < CENTS_CLOSE_THRESHOLD -> TunerYellow
                else -> TunerRed
            }

            Text(
                text = noteText,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.End
            )

            // Cents
            // Se muestra si está activado y si se detectó *algo*
            if (showMicrotones && isNoteDetected) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = String.format(Locale.US, "%+.1f", centsOffset) + " ¢",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Normal,
                    color = textColor,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

// --- Previews (Actualizar uso de constante y texto) ---

@Preview(showBackground = false, name = "Detectando G#5 (Obj A4) - Fijo")
@Composable
private fun Preview_InRange_Sharp_Fixed() {
    AfinadorTheme { Surface(color = MaterialTheme.colorScheme.background) {
        NoteIndicators( Modifier.padding(16.dp),
            selectedTuningModeName = "Guitarra", // Modo fijo
            displayedNoteName = "G#", displayedOctave = 5, outsideRangeIndicator = null,
            currentTargetNote = "A4", centsOffset = +1100.0f,
            isNoteDetected = true, isTuned = false, showMicrotones = true)
    }}
}

@Preview(showBackground = false, name = "Detectando A4 (Obj A4) - Fijo, Afinada")
@Composable
private fun Preview_InTune_A4_Fixed() {
    AfinadorTheme { Surface(color = MaterialTheme.colorScheme.background) {
        NoteIndicators( Modifier.padding(16.dp),
            selectedTuningModeName = "Guitarra", // Modo fijo
            displayedNoteName = "A", displayedOctave = 4, outsideRangeIndicator = null,
            currentTargetNote = "A4", centsOffset = 1.8f,
            isNoteDetected = true, isTuned = true, showMicrotones = true)
    }}
}

@Preview(showBackground = false, name = "Modo Cromatico (Obj C4) - Det F#3") // Texto actualizado
@Composable
private fun Preview_ChromaticMode_DetectingFSharp3_TargetC4() { // Nombre actualizado
    AfinadorTheme { Surface(color = MaterialTheme.colorScheme.background) {
        NoteIndicators( Modifier.padding(16.dp),
            selectedTuningModeName = com.isaacbegue.afinador.model.CHROMATIC_MODE_NAME, // Constante actualizada
            displayedNoteName = "F#", displayedOctave = 3, outsideRangeIndicator = null,
            currentTargetNote = "C4", // Mostrar objetivo seleccionado
            centsOffset = -405.0f, // Ejemplo de offset vs C4
            isNoteDetected = true, isTuned = false, showMicrotones = true) // Mostrar cents y color vs C4
    }}
}

@Preview(showBackground = false, name = "Modo Libre/Canto - Det F#3") // Texto actualizado
@Composable
private fun Preview_FreeSingingMode_DetectingFSharp3() { // Nombre actualizado
    AfinadorTheme { Surface(color = MaterialTheme.colorScheme.background) {
        NoteIndicators( Modifier.padding(16.dp),
            selectedTuningModeName = FREE_SINGING_MODE_NAME, // Constante actualizada
            displayedNoteName = "F#", displayedOctave = 3, outsideRangeIndicator = null,
            currentTargetNote = "F#3", // Este valor viene de TunerScreen, pero no se mostrará
            centsOffset = -8.5f, // Offset vs detectado F#3
            isNoteDetected = true, isTuned = true, showMicrotones = true) // Color/cents vs detectado
    }}
}


@Preview(showBackground = false, name = "Sin Detección (Obj C4)")
@Composable
private fun Preview_NoDetection_TargetC4() {
    AfinadorTheme { Surface(color = MaterialTheme.colorScheme.background) {
        NoteIndicators( Modifier.padding(16.dp),
            selectedTuningModeName = "Guitarra", // Modo fijo
            displayedNoteName = null, displayedOctave = null, outsideRangeIndicator = null,
            currentTargetNote = "C4", centsOffset = 0.0f,
            isNoteDetected = false, isTuned = false, showMicrotones = true)
    }}
}