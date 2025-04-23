package com.isaacbegue.afinador.ui.screen

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.isaacbegue.afinador.model.CHROMATIC_MODE_NAME // <- Import actualizado
import com.isaacbegue.afinador.model.Pitch
import com.isaacbegue.afinador.model.FREE_SINGING_MODE_NAME // <- Import actualizado
import com.isaacbegue.afinador.model.Tunings
import com.isaacbegue.afinador.ui.composables.NoteIndicators
import com.isaacbegue.afinador.ui.composables.TargetNoteSelector
import com.isaacbegue.afinador.ui.composables.TuningVisualizer
import com.isaacbegue.afinador.ui.theme.AfinadorTheme
import com.isaacbegue.afinador.viewmodel.TunerUiState
import com.isaacbegue.afinador.viewmodel.TunerViewModel
import kotlin.math.abs

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen(
    modifier: Modifier = Modifier,
    tunerViewModel: TunerViewModel = viewModel()
) {
    val audioPermissionState = rememberPermissionState(
        permission = Manifest.permission.RECORD_AUDIO
    )
    val uiState by tunerViewModel.uiState.collectAsStateWithLifecycle()
    var showSettingsScreen by remember { mutableStateOf(false) }

    LaunchedEffect(audioPermissionState.status) {
        tunerViewModel.updatePermissionStatus(audioPermissionState.status == PermissionStatus.Granted)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Afinador") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = { showSettingsScreen = !showSettingsScreen }) {
                        Icon( imageVector = Icons.Filled.Settings, contentDescription = "Ajustes" )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (showSettingsScreen) {
            SettingsScreen(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                viewModel = tunerViewModel,
                onNavigateBack = { showSettingsScreen = false }
            )
        } else {
            TunerScreenContent(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp, vertical = 8.dp),
                uiState = uiState,
                onTuningModeSelected = tunerViewModel::selectTuningMode,
                onTargetPitchSelected = tunerViewModel::setTargetPitch,
                audioPermissionState = audioPermissionState
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun TunerScreenContent(
    modifier: Modifier = Modifier,
    uiState: TunerUiState,
    onTuningModeSelected: (String) -> Unit,
    onTargetPitchSelected: (Pitch?) -> Unit,
    audioPermissionState: PermissionState
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (audioPermissionState.status) {
            PermissionStatus.Granted -> {
                TargetNoteSelector(
                    selectedTuningModeName = uiState.selectedTuningModeName,
                    currentTargetPitch = uiState.targetPitch,
                    onTuningModeSelected = onTuningModeSelected,
                    onTargetPitchSelected = onTargetPitchSelected
                )

                Spacer(modifier = Modifier.height(16.dp))

                val targetNoteStringToDisplay = when (uiState.selectedTuningModeName) {
                    FREE_SINGING_MODE_NAME -> uiState.displayedNoteName?.let { "$it${uiState.displayedOctave ?: ""}${uiState.outsideRangeIndicator ?: ""}" } ?: "--" // <- Constante actualizada
                    CHROMATIC_MODE_NAME -> uiState.targetPitch?.toString() ?: CHROMATIC_MODE_NAME // <- Constante actualizada
                    else -> uiState.targetPitch?.toString()
                }

                NoteIndicators(
                    selectedTuningModeName = uiState.selectedTuningModeName, // Pasa el modo actual
                    displayedNoteName = uiState.displayedNoteName,
                    displayedOctave = uiState.displayedOctave,
                    outsideRangeIndicator = uiState.outsideRangeIndicator,
                    currentTargetNote = targetNoteStringToDisplay,
                    centsOffset = uiState.centsOffset,
                    isNoteDetected = uiState.isNoteDetected,
                    isTuned = uiState.isTuned,
                    showMicrotones = uiState.showMicrotones,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                TuningVisualizer(
                    tuningHistory = uiState.tuningHistory,
                    currentOffset = uiState.centsOffset,
                    isGraphCenteringDynamic = uiState.isGraphCenteringDynamic,
                    isNoteDetected = uiState.isNoteDetected,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } // End Granted

            is PermissionStatus.Denied -> {
                val showRationale = audioPermissionState.status.shouldShowRationale
                val msg = if (showRationale) { "El permiso para usar el micrófono es importante para que el afinador funcione.\nPor favor, concédelo." } else { "Necesitamos permiso para acceder al micrófono y poder afinar tu instrumento." }
                Column( modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center ) {
                    Text( text = msg, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp) )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { audioPermissionState.launchPermissionRequest() }) { Text("Conceder Permiso") }
                }
            } // End Denied
        } // Fin When
    } // Fin Column
}

// Preview con constantes y lógica actualizadas
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Preview(showBackground = true)
@Composable
fun TunerScreenPreview() {
    val CENTS_IN_TUNE_THRESHOLD = 10.0f; val VISUAL_CENTS_RANGE = 50.0f; val previewOffsetVsTarget = -15.0f; val previewIsNoteDetected = true; var previewSelectedMode by remember { mutableStateOf(Tunings.GUITAR_STANDARD.name) }; var previewTarget by remember { mutableStateOf<Pitch?>(Tunings.GUITAR_STANDARD.pitches[1]) }; var previewShowSettings by remember { mutableStateOf(false) }; var previewShowMicrotones by remember { mutableStateOf(true) };
    val previewIsDynamicCentering = previewSelectedMode == FREE_SINGING_MODE_NAME // <- Constante actualizada
    val previewHistory = remember(previewSelectedMode) { val now = System.currentTimeMillis(); if(previewSelectedMode == FREE_SINGING_MODE_NAME) { listOf( now - 4000L to (-10f / VISUAL_CENTS_RANGE), now - 3000L to (-8f / VISUAL_CENTS_RANGE), now - 2500L to null, now - 1500L to (2f / VISUAL_CENTS_RANGE), now - 500L to (5f / VISUAL_CENTS_RANGE), now to (5.0f / VISUAL_CENTS_RANGE) ).reversed() } else { listOf( now - 4000L to (-20f / VISUAL_CENTS_RANGE), now - 3000L to (-18f / VISUAL_CENTS_RANGE), now - 2500L to null, now - 1500L to (-12f / VISUAL_CENTS_RANGE), now - 500L to (-15f / VISUAL_CENTS_RANGE), now to (-15.0f / VISUAL_CENTS_RANGE) ).reversed() } } // <- Constante actualizada
    AfinadorTheme {
        Scaffold( topBar = { TopAppBar( title = { Text("Afinador (Preview)") }, actions = { IconButton(onClick = { previewShowSettings = !previewShowSettings }) { Icon(Icons.Filled.Settings, contentDescription = "Ajustes Preview") } } ) }
        ) { paddingValues ->
            if (previewShowSettings) { Box(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Settings Screen Area (Preview)"); Spacer(modifier = Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Mostrar Cents"); Switch(checked = previewShowMicrotones, onCheckedChange = { previewShowMicrotones = it }) }; Spacer(modifier = Modifier.height(16.dp)); Button(onClick = { previewShowSettings = false }) { Text("Volver") } } }
            } else {
                TunerScreenContent( modifier = Modifier.padding(paddingValues).padding(horizontal = 16.dp, vertical = 8.dp), uiState = TunerUiState( selectedTuningModeName = previewSelectedMode, targetPitch = previewTarget, displayedNoteName = if(previewSelectedMode == FREE_SINGING_MODE_NAME) "A#" else "G#", displayedOctave = 4, outsideRangeIndicator = null, centsOffset = previewOffsetVsTarget, isNoteDetected = previewIsNoteDetected, isTuned = abs(previewOffsetVsTarget) < CENTS_IN_TUNE_THRESHOLD, showMicrotones = previewShowMicrotones, tuningHistory = previewHistory, isGraphCenteringDynamic = previewIsDynamicCentering, hasAudioPermission = true, isRecording = true ), // <- Constante actualizada en la condición 'if'
                    onTuningModeSelected = { mode -> previewSelectedMode = mode; previewTarget = if (mode == CHROMATIC_MODE_NAME) Pitch("A", 4) else if (mode == FREE_SINGING_MODE_NAME) null else Tunings.ALL_TUNINGS.find { it.name == mode }?.pitches?.firstOrNull() }, // <- Constantes actualizadas
                    onTargetPitchSelected = { pitch -> previewTarget = pitch },
                    audioPermissionState = object : PermissionState { override val permission: String = Manifest.permission.RECORD_AUDIO; override val status: PermissionStatus = PermissionStatus.Granted; override fun launchPermissionRequest() {} } )
            }
        }
    }
}