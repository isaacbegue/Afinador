package com.isaacbegue.afinador.ui.composables

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isaacbegue.afinador.model.CHROMATIC_MODE_NAME // <- Import actualizado
import com.isaacbegue.afinador.model.FREE_SINGING_MODE_NAME // <- Import actualizado
import com.isaacbegue.afinador.model.InstrumentTuning
import com.isaacbegue.afinador.model.OCTAVE_RANGE
import com.isaacbegue.afinador.model.Pitch
import com.isaacbegue.afinador.model.STANDARD_NOTES
import com.isaacbegue.afinador.model.Tunings
import com.isaacbegue.afinador.ui.theme.AfinadorTheme
import androidx.compose.material3.MenuAnchorType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetNoteSelector(
    modifier: Modifier = Modifier,
    selectedTuningModeName: String,
    currentTargetPitch: Pitch?,
    onTuningModeSelected: (String) -> Unit,
    onTargetPitchSelected: (Pitch?) -> Unit
) {
    var mainDropdownExpanded by remember { mutableStateOf(false) }

    val currentInstrumentTuning: InstrumentTuning? = remember(selectedTuningModeName) {
        Tunings.ALL_TUNINGS.find { it.name == selectedTuningModeName }
    }
    val currentStringIndex: Int = remember(currentInstrumentTuning, currentTargetPitch) {
        currentInstrumentTuning?.pitches?.indexOf(currentTargetPitch) ?: 0
    }
    val currentFreeNoteIndex = remember(selectedTuningModeName, currentTargetPitch) {
        if (selectedTuningModeName == CHROMATIC_MODE_NAME) { // <- Constante actualizada
            STANDARD_NOTES.indexOf(currentTargetPitch?.noteName ?: "A")
        } else 0
    }
    val currentFreeOctave = remember(selectedTuningModeName, currentTargetPitch) {
        if (selectedTuningModeName == CHROMATIC_MODE_NAME) { // <- Constante actualizada
            currentTargetPitch?.octave ?: 4
        } else 0
    }
    val isInstrumentMode = currentInstrumentTuning != null &&
            selectedTuningModeName != CHROMATIC_MODE_NAME && // <- Constante actualizada
            selectedTuningModeName != FREE_SINGING_MODE_NAME // <- Constante actualizada

    Column(modifier = modifier.fillMaxWidth()) {
        // --- Main Mode Selector ---
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { mainDropdownExpanded = true }
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedTuningModeName, // Muestra el nombre del modo tal como está guardado
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(Icons.Default.KeyboardArrowDown, "Seleccionar modo")
            }
            DropdownMenu(
                expanded = mainDropdownExpanded,
                onDismissRequest = { mainDropdownExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(CHROMATIC_MODE_NAME) }, // <- Texto actualizado
                    onClick = {
                        onTuningModeSelected(CHROMATIC_MODE_NAME) // <- Constante actualizada
                        mainDropdownExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(FREE_SINGING_MODE_NAME) }, // <- Texto actualizado
                    onClick = {
                        onTuningModeSelected(FREE_SINGING_MODE_NAME) // <- Constante actualizada
                        mainDropdownExpanded = false
                    }
                )
                // Filtrar el perfil especial por su nuevo nombre de constante
                Tunings.ALL_TUNINGS.filter { it.name != FREE_SINGING_MODE_NAME }.forEach { tuning -> // <- Constante actualizada
                    DropdownMenuItem(
                        text = { Text(tuning.name) },
                        onClick = {
                            onTuningModeSelected(tuning.name)
                            mainDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Specific Mode Controls ---
        if (isInstrumentMode) {
            // We already checked currentInstrumentTuning is not null for isInstrumentMode
            InstrumentModeControls(
                tuning = currentInstrumentTuning!!,
                currentIndex = currentStringIndex,
                onIndexChange = { newIndex ->
                    val newPitch = currentInstrumentTuning.pitches.getOrNull(newIndex)
                    onTargetPitchSelected(newPitch)
                }
            )
        } else if (selectedTuningModeName == CHROMATIC_MODE_NAME) { // <- Constante actualizada
            FreeModeControls(
                currentNoteIndex = currentFreeNoteIndex,
                currentOctave = currentFreeOctave,
                onPitchSelected = onTargetPitchSelected
            )
        }
        // No specific controls shown for FREE_SINGING_MODE_NAME
    }
}


// --- Instrument Mode Controls ---
@Composable
private fun InstrumentModeControls(
    tuning: InstrumentTuning,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit
) {
    val currentPitch = tuning.pitches.getOrNull(currentIndex)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = { if (currentIndex > 0) onIndexChange(currentIndex - 1) },
            enabled = currentIndex > 0
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Cuerda anterior") }

        Text(
            text = currentPitch?.toString() ?: "--",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        IconButton(
            onClick = { if (currentIndex < tuning.pitches.size - 1) onIndexChange(currentIndex + 1) },
            enabled = currentIndex < tuning.pitches.size - 1
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Cuerda siguiente") }
    }
}

// --- Free/Chromatic Mode Controls (Ahora es "Cromático") ---
@Composable
private fun FreeModeControls( // El nombre de la función se mantiene, controla el modo ahora llamado "Cromático"
    currentNoteIndex: Int,
    currentOctave: Int,
    onPitchSelected: (Pitch) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Group: Note Selection
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                val newNoteIndex = (currentNoteIndex - 1 + STANDARD_NOTES.size) % STANDARD_NOTES.size
                onPitchSelected(Pitch(STANDARD_NOTES[newNoteIndex], currentOctave))
            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Nota anterior") }

            NoteDropdown(
                modifier = Modifier.widthIn(min = 75.dp, max = 85.dp),
                selectedIndex = currentNoteIndex,
                options = STANDARD_NOTES,
                label = "Nota",
                onSelected = { index ->
                    onPitchSelected(Pitch(STANDARD_NOTES[index], currentOctave))
                }
            )

            IconButton(onClick = {
                val newNoteIndex = (currentNoteIndex + 1) % STANDARD_NOTES.size
                onPitchSelected(Pitch(STANDARD_NOTES[newNoteIndex], currentOctave))
            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nota siguiente") }
        }

        // Group: Octave Selection
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                val newOctave = (currentOctave - 1).coerceIn(OCTAVE_RANGE.first, OCTAVE_RANGE.last)
                onPitchSelected(Pitch(STANDARD_NOTES[currentNoteIndex], newOctave))
            }, enabled = currentOctave > OCTAVE_RANGE.first) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Octava anterior") }

            OctaveDropdown(
                modifier = Modifier.widthIn(min = 75.dp, max = 85.dp),
                selectedOctave = currentOctave,
                range = OCTAVE_RANGE,
                label = "Octava",
                onSelected = { octave ->
                    onPitchSelected(Pitch(STANDARD_NOTES[currentNoteIndex], octave))
                }
            )

            IconButton(onClick = {
                val newOctave = (currentOctave + 1).coerceIn(OCTAVE_RANGE.first, OCTAVE_RANGE.last)
                onPitchSelected(Pitch(STANDARD_NOTES[currentNoteIndex], newOctave))
            }, enabled = currentOctave < OCTAVE_RANGE.last) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Octava siguiente") }
        }
    }
}


// --- NoteDropdown ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteDropdown(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    options: List<String>,
    label: String,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = options.getOrElse(selectedIndex) { "" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            ),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

// --- OctaveDropdown ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OctaveDropdown(
    modifier: Modifier = Modifier,
    selectedOctave: Int,
    range: IntRange,
    label: String,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOctave.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            ),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            range.forEach { octave ->
                DropdownMenuItem(
                    text = { Text(octave.toString()) },
                    onClick = {
                        onSelected(octave)
                        expanded = false
                    }
                )
            }
        }
    }
}


// --- Previews (Actualizar nombres y constantes) ---
@Preview(showBackground = true, widthDp = 350, name = "Selector - Modo Cromático") // Nombre actualizado
@Composable
private fun TargetNoteSelectorPreview_ChromaticMode() { // Nombre actualizado
    AfinadorTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            var selectedMode by remember { mutableStateOf(CHROMATIC_MODE_NAME) } // Constante actualizada
            var targetPitch by remember { mutableStateOf<Pitch?>(Pitch("A", 4)) }

            TargetNoteSelector(
                modifier = Modifier.padding(16.dp),
                selectedTuningModeName = selectedMode,
                currentTargetPitch = targetPitch,
                onTuningModeSelected = { modeName ->
                    selectedMode = modeName
                    targetPitch = if (modeName == CHROMATIC_MODE_NAME) { // Constante actualizada
                        Pitch("A", 4)
                    } else if (modeName == FREE_SINGING_MODE_NAME) { // Constante actualizada
                        null
                    } else {
                        Tunings.ALL_TUNINGS.find { it.name == modeName }?.pitches?.firstOrNull()
                    }
                },
                onTargetPitchSelected = { newPitch -> targetPitch = newPitch }
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 350, name = "Selector - Modo Guitarra")
@Composable
private fun TargetNoteSelectorPreview_GuitarMode() {
    AfinadorTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            var selectedMode by remember { mutableStateOf(Tunings.GUITAR_STANDARD.name) }
            var targetPitch by remember { mutableStateOf<Pitch?>(Tunings.GUITAR_STANDARD.pitches.first()) }

            TargetNoteSelector(
                modifier = Modifier.padding(16.dp),
                selectedTuningModeName = selectedMode,
                currentTargetPitch = targetPitch,
                onTuningModeSelected = { modeName ->
                    selectedMode = modeName
                    targetPitch = if (modeName == CHROMATIC_MODE_NAME) { // Constante actualizada
                        Pitch("A", 4)
                    } else if (modeName == FREE_SINGING_MODE_NAME) { // Constante actualizada
                        null
                    } else {
                        Tunings.ALL_TUNINGS.find { it.name == modeName }?.pitches?.firstOrNull()
                    }
                },
                onTargetPitchSelected = { newPitch -> targetPitch = newPitch }
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 350, name = "Selector - Modo Libre/Canto") // Nombre actualizado
@Composable
private fun TargetNoteSelectorPreview_FreeSingingMode() { // Nombre actualizado
    AfinadorTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            var selectedMode by remember { mutableStateOf(FREE_SINGING_MODE_NAME) } // Constante actualizada
            var targetPitch by remember { mutableStateOf<Pitch?>(null) }

            TargetNoteSelector(
                modifier = Modifier.padding(16.dp),
                selectedTuningModeName = selectedMode,
                currentTargetPitch = targetPitch,
                onTuningModeSelected = { modeName ->
                    selectedMode = modeName
                    targetPitch = if (modeName == CHROMATIC_MODE_NAME) { // Constante actualizada
                        Pitch("A", 4)
                    } else if (modeName == FREE_SINGING_MODE_NAME) { // Constante actualizada
                        null
                    } else {
                        Tunings.ALL_TUNINGS.find { it.name == modeName }?.pitches?.firstOrNull()
                    }
                },
                onTargetPitchSelected = { newPitch -> targetPitch = newPitch }
            )
        }
    }
}