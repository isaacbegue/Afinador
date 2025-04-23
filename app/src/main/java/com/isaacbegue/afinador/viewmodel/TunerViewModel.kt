package com.isaacbegue.afinador.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log // Keep essential logs
import androidx.annotation.Keep
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isaacbegue.afinador.data.PreferencesRepository
import com.isaacbegue.afinador.model.CHROMATIC_MODE_NAME // <- Import actualizado
import com.isaacbegue.afinador.model.OCTAVE_RANGE
import com.isaacbegue.afinador.model.Pitch
import com.isaacbegue.afinador.model.STANDARD_NOTES
import com.isaacbegue.afinador.model.FREE_SINGING_MODE_NAME // <- Import actualizado
import com.isaacbegue.afinador.model.Tunings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

// --- Constants ---
private const val SAMPLE_RATE = 44100
private const val BUFFER_SIZE = 1024
private const val MIN_VALID_FREQUENCY = 20.0f
private const val CENTS_IN_TUNE_THRESHOLD = 10.0f
private const val CENTS_RANGE_FOR_VISUALIZER = 50.0f // Visual range +/- 50 cents
private const val MIDI_NOTE_A4 = 69
private const val HISTORY_DURATION_MS = 5000L
private const val NO_DETECTION_TIMEOUT_MS = 200L
private const val CENTS_NOT_AVAILABLE = -1000.0f
private const val NOTE_INDEX_NOT_AVAILABLE = -1
private const val OCTAVE_NOT_AVAILABLE = -1
private const val SEARCH_RANGE_SEMITONES = 11
private const val TWELFTH_ROOT_OF_TWO = 1.059463094359f

// --- UI State Data Class ---
data class TunerUiState(
    val a4Frequency: Float = 440.0f,
    val targetPitch: Pitch? = null,
    val selectedTuningModeName: String = CHROMATIC_MODE_NAME, // <- Valor por defecto actualizado
    val showMicrotones: Boolean = false,
    val displayedNoteName: String? = null,
    val displayedOctave: Int? = null,
    val outsideRangeIndicator: String? = null,
    val centsOffset: Float = 0.0f,
    val isNoteDetected: Boolean = false,
    val isTuned: Boolean = false,
    val tuningHistory: List<Pair<Long, Float?>> = emptyList(),
    val isGraphCenteringDynamic: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val isRecording: Boolean = false
)

// --- ViewModel ---
class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application.applicationContext)

    // Initialize state with defaults
    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    // Initialize the preference flow
    internal val defaultTuningName: StateFlow<String> = preferencesRepository.defaultTuningNameFlow
        .map { it ?: CHROMATIC_MODE_NAME } // <- Valor por defecto actualizado
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CHROMATIC_MODE_NAME // <- Valor inicial actualizado
        )

    private var noDetectionJob: Job? = null
    private var startJob: Job? = null

    init {
        observeDefaultTuningPreference()
        viewModelScope.launch {
            delay(50)
            val initialMode = defaultTuningName.value
            Log.d("TunerViewModel", "Init: Applying initial mode setup for: $initialMode")
            updateUiForNewMode(initialMode)
            updatePermissionStatus(checkPermission())
            Log.d("TunerViewModel", "Init: Initial setup complete.")
        }
    }

    private fun observeDefaultTuningPreference() {
        defaultTuningName.onEach { loadedModeName ->
            if (loadedModeName != _uiState.value.selectedTuningModeName && !_uiState.value.isRecording) {
                Log.d("TunerViewModel", "Preference changed to: $loadedModeName. Updating UI.")
                updateUiForNewMode(loadedModeName)
            }
        }.launchIn(viewModelScope)
    }


    fun saveDefaultTuning(modeName: String) {
        viewModelScope.launch {
            preferencesRepository.saveDefaultTuningName(modeName)
        }
    }

    private fun checkPermission(): Boolean = ActivityCompat.checkSelfPermission(
        getApplication(), Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun updatePermissionStatus(granted: Boolean) {
        val currentPermission = uiState.value.hasAudioPermission
        var shouldStartAudio = false
        if (granted != currentPermission) {
            _uiState.update { it.copy(hasAudioPermission = granted) }
            if (granted && !_uiState.value.isRecording && startJob?.isActive != true) {
                shouldStartAudio = true
            }
        } else if (granted && !_uiState.value.isRecording && startJob?.isActive != true) {
            shouldStartAudio = true
        }

        if (shouldStartAudio) {
            if (viewModelScope.isActive) {
                startAudioProcessing()
            } else {
                Log.w("TunerViewModel", "ViewModel scope not active during permission check, cannot start audio.")
            }
        } else if (!granted) {
            if (uiState.value.isRecording || startJob?.isActive == true) {
                stopAudioProcessing()
            }
        }
    }

    fun startAudioProcessing() {
        if (startJob?.isActive == true || uiState.value.isRecording) return
        if (!checkPermission()) {
            Log.e("TunerViewModel", "Start request failed: No audio permission.")
            _uiState.update { it.copy(hasAudioPermission = false, isRecording = false) }
            return
        }
        if (!uiState.value.hasAudioPermission) { _uiState.update { it.copy(hasAudioPermission = true)} }

        startJob = viewModelScope.launch(Dispatchers.IO) {
            Log.i("TunerViewModel", "[IO Thread] Requesting to start native audio engine...")
            setA4Native(_uiState.value.a4Frequency)
            val started = try { startNativeAudioEngine(SAMPLE_RATE, BUFFER_SIZE) } catch (e: Throwable) { Log.e("TunerViewModel", "[IO Thread] Error starting engine", e); false }
            if (started) { withContext(Dispatchers.Main) { _uiState.update { it.copy(isRecording = true) } } }
            else { Log.e("TunerViewModel", "[IO Thread] Failed to start native engine."); withContext(Dispatchers.Main) { _uiState.update { it.copy(isRecording = false, hasAudioPermission = checkPermission()) } } }
        }
        startJob?.invokeOnCompletion { cause -> startJob = null; if (cause != null && cause !is kotlinx.coroutines.CancellationException) { Log.e("TunerViewModel", "Start job failed", cause); viewModelScope.launch(Dispatchers.Main) { _uiState.update { it.copy(isRecording = false, hasAudioPermission = checkPermission()) } } } }
    }

    fun stopAudioProcessing() {
        if (!uiState.value.isRecording && startJob?.isActive != true) return
        startJob?.cancel(); startJob = null
        if (uiState.value.isRecording) { _uiState.update { it.copy(isRecording = false) }; viewModelScope.launch(Dispatchers.IO) { try { stopNativeAudioEngine() } catch (t: Throwable) { Log.e("TunerViewModel", "[IO] Error stopping engine", t) } } }
        resetDetectionState(); cancelNoDetectionTimer()
    }

    fun setA4Frequency(newFreq: Float) {
        val clampedFreq = newFreq.coerceIn(400.0f, 500.0f)
        if (abs(clampedFreq - uiState.value.a4Frequency) > 0.01f) { _uiState.update { it.copy(a4Frequency = clampedFreq) }; setA4Native(clampedFreq) }
    }

    fun setTargetPitch(newTarget: Pitch?) {
        if (newTarget != uiState.value.targetPitch) { _uiState.update { it.copy(targetPitch = newTarget, isTuned = false, centsOffset = 0.0f) }; cancelNoDetectionTimer() }
    }

    // Called MANUALLY by user selecting a mode in UI OR by init block
    fun selectTuningMode(modeName: String) {
        Log.d("TunerViewModel", "selectTuningMode called with: $modeName")
        if (modeName != _uiState.value.selectedTuningModeName || _uiState.value.targetPitch == null) { // Ensure target is set if mode implies it
            updateUiForNewMode(modeName)
        }
    }

    // Updates the state based on a mode name. Called by selectTuningMode or init.
    private fun updateUiForNewMode(modeName: String) {
        Log.d("TunerViewModel", "Updating UI state for mode: $modeName")
        val selectedTuning = Tunings.ALL_TUNINGS.find { it.name == modeName }
        val initialPitchForMode = when (modeName) {
            CHROMATIC_MODE_NAME -> _uiState.value.targetPitch ?: Pitch("A", 4) // <- Constante actualizada
            FREE_SINGING_MODE_NAME -> null // <- Constante actualizada
            else -> selectedTuning?.pitches?.firstOrNull()
        }
        // Preserve user selection in Chromatic Mode if already set
        if (modeName == CHROMATIC_MODE_NAME && _uiState.value.selectedTuningModeName == CHROMATIC_MODE_NAME && _uiState.value.targetPitch != null) { // <- Constante actualizada
            Log.d("TunerViewModel", "Keeping existing target pitch for Chromatic Mode: ${_uiState.value.targetPitch}")
        } else {
            _uiState.update { it.copy(targetPitch = initialPitchForMode)}
        }

        val isDynamicCentering = (modeName == FREE_SINGING_MODE_NAME) // <- Constante actualizada
        // Update mode name and dynamic flag regardless
        _uiState.update { it.copy( selectedTuningModeName = modeName, isGraphCenteringDynamic = isDynamicCentering ) }

        resetDetectionState()
    }


    // Helper functions
    private fun calculateFrequency(pitch: Pitch, a4Freq: Float): Float { val noteIndex = STANDARD_NOTES.indexOf(pitch.noteName); if (noteIndex == -1) return 0.0f; val midiNote = noteIndex + (pitch.octave + 1) * 12; return a4Freq * 2.0f.pow((midiNote - MIDI_NOTE_A4) / 12.0f) }
    private fun getMidiNoteFromPitch(pitch: Pitch): Int? { val noteIndex = STANDARD_NOTES.indexOf(pitch.noteName); if (noteIndex == -1) return null; return noteIndex + (pitch.octave + 1) * 12 }
    private fun getPitchFromMidiNote(midiNote: Int): Pitch? { if (midiNote < 0) return null; val noteIndex = midiNote % 12; val octave = (midiNote / 12) - 1; if (octave !in OCTAVE_RANGE) return null; return Pitch(STANDARD_NOTES[noteIndex], octave) }
    fun toggleMicrotoneDisplay() { _uiState.update { it.copy(showMicrotones = !it.showMicrotones) } }

    // Native callback
    @Keep
    private fun onNativeResult(noteIndex: Int, octave: Int, centsOffsetVsDetectedChromatic: Float) {
        viewModelScope.launch(Dispatchers.Main) {
            val noteDetected = noteIndex != NOTE_INDEX_NOT_AVAILABLE && octave != OCTAVE_NOT_AVAILABLE
            val offsetAvailable = abs(centsOffsetVsDetectedChromatic - CENTS_NOT_AVAILABLE) > 1e-5
            cancelNoDetectionTimer()

            if (noteDetected && offsetAvailable) {
                val detectedPitch = Pitch(STANDARD_NOTES[noteIndex], octave)
                val a4 = uiState.value.a4Frequency
                val detectedExactFreq = calculateFrequency(detectedPitch, a4) * TWELFTH_ROOT_OF_TWO.pow(centsOffsetVsDetectedChromatic / 100f)

                var finalNoteName: String? = detectedPitch.noteName
                var finalOctave: Int? = detectedPitch.octave
                var rangeIndicator: String? = null
                var finalCentsOffset = centsOffsetVsDetectedChromatic
                var valueForHistory = centsOffsetVsDetectedChromatic // Default for Libre/Canto

                val currentTargetPitch = _uiState.value.targetPitch
                val isDynamicCentering = _uiState.value.isGraphCenteringDynamic // Flag checked

                if (!isDynamicCentering && currentTargetPitch != null) {
                    // Fixed Mode (Instrument/Cromático con Target)
                    val targetMidiNote = getMidiNoteFromPitch(currentTargetPitch)
                    val targetFreq = calculateFrequency(currentTargetPitch, a4)
                    if (targetMidiNote != null && targetFreq > 1e-6f && detectedExactFreq > 1e-6f) {
                        finalCentsOffset = 1200.0f * log2(detectedExactFreq / targetFreq)
                        valueForHistory = finalCentsOffset // Store Offset vs Target
                        val detectedMidiNote = noteIndex + (octave + 1) * 12
                        val deltaSemitones = detectedMidiNote - targetMidiNote
                        when {
                            deltaSemitones > SEARCH_RANGE_SEMITONES -> { val boundaryPitch = getPitchFromMidiNote(targetMidiNote + SEARCH_RANGE_SEMITONES); finalNoteName = boundaryPitch?.noteName; finalOctave = boundaryPitch?.octave; rangeIndicator = "+" }
                            deltaSemitones < -SEARCH_RANGE_SEMITONES -> { val boundaryPitch = getPitchFromMidiNote(targetMidiNote - SEARCH_RANGE_SEMITONES); finalNoteName = boundaryPitch?.noteName; finalOctave = boundaryPitch?.octave; rangeIndicator = "-" }
                            else -> { finalNoteName = detectedPitch.noteName; finalOctave = detectedPitch.octave; rangeIndicator = null }
                        }
                    } else { finalCentsOffset = centsOffsetVsDetectedChromatic; valueForHistory = centsOffsetVsDetectedChromatic }
                }
                // En modo Libre/Canto (isDynamicCentering == true), finalCentsOffset y valueForHistory se quedan como centsOffsetVsDetectedChromatic

                val normalizedValueForHistory = (valueForHistory / CENTS_RANGE_FOR_VISUALIZER).coerceIn(-1.0f, 1.0f)
                addHistoryPoint(normalizedValueForHistory)
                val isTuned = abs(finalCentsOffset) <= CENTS_IN_TUNE_THRESHOLD
                _uiState.update { it.copy( displayedNoteName = finalNoteName, displayedOctave = finalOctave, outsideRangeIndicator = rangeIndicator, centsOffset = finalCentsOffset, isNoteDetected = true, isTuned = isTuned ) }
            } else { // No valid detection
                startNoDetectionTimer(); addHistoryPoint(null)
                if (_uiState.value.isNoteDetected) { _uiState.update { it.copy(isNoteDetected = false, isTuned = false) } }
            }
        }
    }

    // History/Timer/Reset
    private fun addHistoryPoint(normalizedValue: Float?) { val now = System.currentTimeMillis(); _uiState.update { currentState -> val updatedHistory = currentState.tuningHistory + (now to normalizedValue); val filteredHistory = updatedHistory.filter { (timestamp, _) -> now - timestamp <= HISTORY_DURATION_MS }; currentState.copy(tuningHistory = filteredHistory) } }
    private fun startNoDetectionTimer() { cancelNoDetectionTimer(); noDetectionJob = viewModelScope.launch(Dispatchers.Main) { delay(NO_DETECTION_TIMEOUT_MS); if (!_uiState.value.isNoteDetected) { _uiState.update { it.copy( displayedNoteName = null, displayedOctave = null, outsideRangeIndicator = null, centsOffset = 0.0f, isNoteDetected = false, isTuned = false ) } }; noDetectionJob = null } }
    private fun cancelNoDetectionTimer() { if (noDetectionJob?.isActive == true) { noDetectionJob?.cancel(); }; noDetectionJob = null }
    private fun resetDetectionState() { _uiState.update { it.copy( displayedNoteName = null, displayedOctave = null, outsideRangeIndicator = null, centsOffset = 0.0f, isNoteDetected = false, isTuned = false, tuningHistory = emptyList() ) }; cancelNoDetectionTimer() }

    // JNI/Companion/Cleanup
    private external fun startNativeAudioEngine(sampleRate: Int, bufferSize: Int): Boolean
    private external fun stopNativeAudioEngine()
    private external fun setA4Native(frequency: Float)
    companion object { init { try { System.loadLibrary("afinador_native"); Log.i("TunerViewModel", "Native library loaded.") } catch (t: Throwable) { Log.e("TunerViewModel", "FATAL: Error loading native library", t) } } }
    override fun onCleared() { super.onCleared(); if (uiState.value.isRecording || startJob?.isActive == true) { stopAudioProcessing() } else { viewModelScope.launch(Dispatchers.IO) { try { stopNativeAudioEngine() } catch (t: Throwable) { /* Ignore */ } } }; cancelNoDetectionTimer() }
}