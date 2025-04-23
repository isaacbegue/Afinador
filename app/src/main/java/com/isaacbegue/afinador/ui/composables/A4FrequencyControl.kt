package com.isaacbegue.afinador.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isaacbegue.afinador.ui.theme.AfinadorTheme
import java.util.Locale

private const val MIN_A4_FREQ = 430.0f
private const val MAX_A4_FREQ = 450.0f
private const val FREQ_STEP = 0.1f

@Composable
fun A4FrequencyControl(
    modifier: Modifier = Modifier,
    initialFrequency: Float = 440.0f,
    onFrequencyChange: (Float) -> Unit = {}
) {
    var currentFrequency by remember { mutableFloatStateOf(initialFrequency.coerceIn(MIN_A4_FREQ, MAX_A4_FREQ)) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    val newFreq = (currentFrequency - FREQ_STEP).coerceIn(MIN_A4_FREQ, MAX_A4_FREQ)
                    currentFrequency = newFreq
                    onFrequencyChange(newFreq)
                },
                enabled = currentFrequency > MIN_A4_FREQ, // Habilitado de nuevo
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("-", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = String.format(Locale.US, "A4 = %.1f Hz", currentFrequency),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = {
                    val newFreq = (currentFrequency + FREQ_STEP).coerceIn(MIN_A4_FREQ, MAX_A4_FREQ)
                    currentFrequency = newFreq
                    onFrequencyChange(newFreq)
                },
                enabled = currentFrequency < MAX_A4_FREQ, // Habilitado de nuevo
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("+", fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = currentFrequency,
            onValueChange = { newValue ->
                val steppedValue = (newValue / FREQ_STEP).toInt() * FREQ_STEP
                val clampedValue = steppedValue.coerceIn(MIN_A4_FREQ, MAX_A4_FREQ)
                currentFrequency = clampedValue
            },
            valueRange = MIN_A4_FREQ..MAX_A4_FREQ,
            modifier = Modifier.fillMaxWidth(0.8f),
            onValueChangeFinished = {
                onFrequencyChange(currentFrequency)
            },
            enabled = true, // Habilitado de nuevo
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
            )
        )
    }
}

// --- Previsualización ---

@Preview(showBackground = true, widthDp = 300)
@Composable
fun A4FrequencyControlPreview() {
    AfinadorTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            A4FrequencyControl(modifier = Modifier.padding(16.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
fun A4FrequencyControlAtMinPreview() {
    AfinadorTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            A4FrequencyControl(
                modifier = Modifier.padding(16.dp),
                initialFrequency = MIN_A4_FREQ
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 300)
@Composable
fun A4FrequencyControlAtMaxPreview() {
    AfinadorTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            A4FrequencyControl(
                modifier = Modifier.padding(16.dp),
                initialFrequency = MAX_A4_FREQ
            )
        }
    }
}