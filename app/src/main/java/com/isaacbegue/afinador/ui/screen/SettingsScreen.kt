package com.isaacbegue.afinador.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isaacbegue.afinador.model.CHROMATIC_MODE_NAME // <- Import actualizado
import com.isaacbegue.afinador.model.Tunings
import com.isaacbegue.afinador.ui.composables.A4FrequencyControl
import com.isaacbegue.afinador.ui.theme.AfinadorTheme
// TunerUiState y TunerViewModel no son necesarios en las importaciones si sólo se usa en el Preview
// import com.isaacbegue.afinador.viewmodel.TunerUiState
import com.isaacbegue.afinador.viewmodel.TunerViewModel


@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: TunerViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentDefaultTuning by viewModel.defaultTuningName.collectAsStateWithLifecycle()

    val tuningOptions = remember {
        listOf(CHROMATIC_MODE_NAME) + Tunings.ALL_TUNINGS.map { it.name } // <- Constante actualizada
    }
    var tuningDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Barra superior
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver"
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))


        // *** Orden Cambiado: Dropdown Primero ***
        Text(
            text = "Al Iniciar la App",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { tuningDropdownExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(currentDefaultTuning) // Muestra la preferencia guardada
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Seleccionar afinación predeterminada"
                )
            }

            DropdownMenu(
                expanded = tuningDropdownExpanded,
                onDismissRequest = { tuningDropdownExpanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                tuningOptions.forEach { optionName ->
                    DropdownMenuItem(
                        text = { Text(optionName) },
                        onClick = {
                            viewModel.saveDefaultTuning(optionName) // Guarda la preferencia
                            tuningDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))

        // *** Orden Cambiado: Sección A4 Después ***
        Text(
            text = "Referencia de Afinación",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        A4FrequencyControl(
            initialFrequency = uiState.a4Frequency,
            onFrequencyChange = viewModel::setA4Frequency
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))


        // Sección Mostrar Cents (al final)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Mostrar Cents", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = uiState.showMicrotones,
                onCheckedChange = { viewModel.toggleMicrotoneDisplay() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    // Para el Preview, necesitamos un ViewModel falso o usar viewModel()
    // Si usamos viewModel(), puede fallar si no hay un Application context (en previews simples)
    // Es mejor crear una instancia directa si es posible o mockearla.
    // Aquí asumimos que viewModel() podría funcionar en el entorno de preview actual
    // o que se proporciona un context adecuado.
    val previewViewModel: TunerViewModel = viewModel() // Puede requerir configuración adicional del preview

    AfinadorTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SettingsScreen(
                modifier = Modifier.padding(16.dp),
                viewModel = previewViewModel,
                onNavigateBack = {}
            )
        }
    }
}