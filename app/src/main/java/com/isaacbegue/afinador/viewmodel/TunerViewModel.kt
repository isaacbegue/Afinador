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
import com.isaacbegue.afinador.model.CHROMATIC_MODE_NAME
import com.isaacbegue.afinador.model.OCTAVE_RANGE
import com.isaacbegue.afinador.model.Pitch
import com.isaacbegue.afinador.model.STANDARD_NOTES
import com.isaacbegue.afinador.model.FREE_SINGING_MODE_NAME
import com.isaacbegue.afinador.model.InstrumentTuning
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
import kotlin.math.roundToInt

// --- Constants ---
private const val SAMPLE_RATE = 44100
// Increased buffer size for better low-frequency detection
private const val BUFFER_SIZE = 2048 // Adjusted from 1024
private const val MIN_VALID_FREQUENCY = 20.0f
private const val CENTS_IN_TUNE_THRESHOLD = 10.0f
private const val CENTS_RANGE_FOR_VISUALIZER = 50.0f // Visual range +/- 50 cents
private const val MIDI_NOTE_A4 = 69
private const val HISTORY_DURATION_MS = 5000L
private const val NO_DETECTION_TIMEOUT_MS = 200L
private const val CENTS_NOT_AVAILABLE = -1000.0f
private const val NOTE_INDEX_NOT_AVAILABLE = -1
private const val OCTAVE_NOT_AVAILABLE = -1
private const val AUTO_SELECT_SEMITONE_THRESHOLD = 2
private const val DISPLAY_NOTE_BOUNDARY_SEMITONES = 11
private const val TWELFTH_ROOT_OF_TWO = 1.059463094359f

// --- UI State Data Class ---
data class TunerUiState(
    val a4Frequency: Float = 440.0f,
    val targetPitch: Pitch? = null,
    val selectedTuningModeName: String = CHROMATIC_MODE_NAME,
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

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    internal val defaultTuningName: StateFlow<String> = preferencesRepository.defaultTuningNameFlow
        .map { it ?: CHROMATIC_MODE_NAME }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CHROMATIC_MODE_NAME
        )

    private var noDetectionJob: Job? = null
    private var startJob: Job? = null
    private var lastDetectedMidiNote: Int? = null


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

    // --- Preference Management ---
    fun saveDefaultTuning(modeName: String) {
        viewModelScope.launch {
            preferencesRepository.saveDefaultTuningName(modeName)
        }
    }

    // --- Permission Handling & Audio Engine Control ---
    private fun checkPermission(): Boolean = ActivityCompat.checkSelfPermission(
        getApplication(), Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun updatePermissionStatus(granted: Boolean) {
        val hadPermission = _uiState.value.hasAudioPermission
        if (granted != hadPermission) {
            _uiState.update { it.copy(hasAudioPermission = granted) }
        }

        if (granted && !_uiState.value.isRecording && startJob?.isActive != true) {
            startAudioProcessing()
        } else if (!granted && (_uiState.value.isRecording || startJob?.isActive == true)) {
            stopAudioProcessing()
        }
    }


    fun startAudioProcessing() {
        if (startJob?.isActive == true || _uiState.value.isRecording) {
            Log.w("TunerViewModel", "Start ignored: Already starting or running.")
            return
        }
        if (!checkPermission()) {
            Log.e("TunerViewModel", "Start request failed: No audio permission.")
            _uiState.update { it.copy(hasAudioPermission = false, isRecording = false) }
            return
        }
        if (!_uiState.value.hasAudioPermission) { _uiState.update { it.copy(hasAudioPermission = true)} }

        startJob = viewModelScope.launch(Dispatchers.IO) {
            Log.i("TunerViewModel", "[IO Thread] Requesting to start native audio engine...")
            setA4Native(_uiState.value.a4Frequency)
            // Pass the updated BUFFER_SIZE constant here
            val started = try {
                startNativeAudioEngine(SAMPLE_RATE, BUFFER_SIZE)
            } catch (e: Throwable) {
                Log.e("TunerViewModel", "[IO Thread] Error starting engine", e)
                false
            }

            if (started) {
                Log.i("TunerViewModel", "[IO Thread] Native engine started successfully.")
                withContext(Dispatchers.Main) { _uiState.update { it.copy(isRecording = true) } }
            } else {
                Log.e("TunerViewModel", "[IO Thread] Failed to start native engine.")
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isRecording = false, hasAudioPermission = checkPermission()) }
                }
            }
        }
        startJob?.invokeOnCompletion { cause ->
            startJob = null
            if (cause != null && cause !is kotlinx.coroutines.CancellationException) {
                Log.e("TunerViewModel", "Start job failed", cause)
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.update { it.copy(isRecording = false, hasAudioPermission = checkPermission()) }
                }
            }
        }
    }

    fun stopAudioProcessing() {
        if (startJob?.isActive == true) {
            Log.w("TunerViewModel", "Cancelling pending start job during stop request.")
            startJob?.cancel()
            startJob = null
        }

        if (!_uiState.value.isRecording) {
            Log.w("TunerViewModel", "Stop ignored: Engine not recording.")
            // Still attempt to stop native engine just in case it's in a weird state
            viewModelScope.launch(Dispatchers.IO) { tryStopNativeEngine() }
            return
        }

        Log.i("TunerViewModel", "Stopping audio processing...")
        _uiState.update { it.copy(isRecording = false) }
        viewModelScope.launch(Dispatchers.IO) { tryStopNativeEngine() }

        resetDetectionState()
        cancelNoDetectionTimer()
    }

    private suspend fun tryStopNativeEngine() {
        try {
            Log.i("TunerViewModel", "[IO Thread] Requesting to stop native audio engine...")
            stopNativeAudioEngine()
            Log.i("TunerViewModel", "[IO Thread] Native engine stop requested.")
        } catch (t: Throwable) {
            Log.e("TunerViewModel", "[IO Thread] Error stopping native engine", t)
        }
    }

    // --- Tuning Parameter Adjustments ---

    fun setA4Frequency(newFreq: Float) {
        val clampedFreq = newFreq.coerceIn(400.0f, 500.0f)
        if (abs(clampedFreq - _uiState.value.a4Frequency) > 0.01f) {
            _uiState.update { it.copy(a4Frequency = clampedFreq) }
            // Update native engine only if it's running or starting
            if (_uiState.value.isRecording || startJob?.isActive == true) {
                setA4Native(clampedFreq)
            }
        }
    }

    fun setTargetPitch(newTarget: Pitch?) {
        if (newTarget != _uiState.value.targetPitch) {
            Log.d("TunerViewModel", "Manual target pitch selected: $newTarget")
            _uiState.update {
                it.copy(
                    targetPitch = newTarget,
                    isTuned = false, // Reset tuned status on target change
                    centsOffset = 0.0f // Reset offset display
                )
            }
            // Reset display immediately when target changes manually
            cancelNoDetectionTimer()
            resetDetectionState(keepTarget = true)
        }
    }

    fun selectTuningMode(modeName: String) {
        Log.d("TunerViewModel", "User selected tuning mode: $modeName")
        if (modeName != _uiState.value.selectedTuningModeName) {
            updateUiForNewMode(modeName)
        }
    }

    // Updates the state based on a mode name. Called internally by selectTuningMode or init.
    private fun updateUiForNewMode(modeName: String) {
        Log.d("TunerViewModel", "Updating UI state for new mode: $modeName")
        val selectedTuning = Tunings.ALL_TUNINGS.find { it.name == modeName }
        val isInstrumentMode = selectedTuning != null && modeName != FREE_SINGING_MODE_NAME
        val isChromaticMode = modeName == CHROMATIC_MODE_NAME
        val isSingingMode = modeName == FREE_SINGING_MODE_NAME

        // Determine the initial target pitch for the new mode
        var initialPitchForMode: Pitch? = null // Start with null
        val currentTarget = _uiState.value.targetPitch // Get current target before update

        when {
            isInstrumentMode -> {
                initialPitchForMode = selectedTuning?.pitches?.firstOrNull() // Default to first string
            }
            isChromaticMode -> {
                // If switching TO Chromatic, ensure a target exists.
                // Prefer existing target if already set (e.g., user manually selected before), else default to A4.
                initialPitchForMode = currentTarget ?: Pitch("A", 4)
                Log.d("TunerViewModel", "Switched to Chromatic. Initial target set to: $initialPitchForMode")
            }
            isSingingMode -> {
                initialPitchForMode = null // Singing mode has no target
            }
        }


        val isDynamicCentering = isSingingMode
        Log.d("TunerViewModel", "Mode: $modeName, Target: $initialPitchForMode, Dynamic: $isDynamicCentering")

        // Update state in one go
        _uiState.update {
            it.copy(
                selectedTuningModeName = modeName,
                targetPitch = initialPitchForMode, // Use the determined target
                isGraphCenteringDynamic = isDynamicCentering
            )
        }

        resetDetectionState(keepTarget = initialPitchForMode != null) // Reset display, keep target if mode has one
        cancelNoDetectionTimer()
    }

    fun toggleMicrotoneDisplay() {
        _uiState.update { it.copy(showMicrotones = !it.showMicrotones) }
    }


    // --- Native Callback Processing ---

    @Keep // Ensure Proguard doesn't remove this method called from JNI
    private fun onNativeResult(noteIndex: Int, octave: Int, centsOffsetVsDetectedChromatic: Float) {
        val currentTime = System.currentTimeMillis()
        val currentFrameMidiNote = if (noteIndex != NOTE_INDEX_NOT_AVAILABLE && octave != OCTAVE_NOT_AVAILABLE) {
            noteIndex + (octave + 1) * 12
        } else {
            null
        }
        lastDetectedMidiNote = currentFrameMidiNote // Update class property (use with caution)
        val offsetAvailable = abs(centsOffsetVsDetectedChromatic - CENTS_NOT_AVAILABLE) > 1e-5

        viewModelScope.launch(Dispatchers.Main) {
            val stateAtStart = _uiState.value // Capture state at coroutine start for consistency

            // --- Auto-Select Target Pitch for Instrument Modes ---
            val currentTuning = Tunings.ALL_TUNINGS.find { it.name == stateAtStart.selectedTuningModeName }
            val isInstrumentModeAutoSelect = currentTuning != null &&
                    stateAtStart.selectedTuningModeName != CHROMATIC_MODE_NAME &&
                    stateAtStart.selectedTuningModeName != FREE_SINGING_MODE_NAME

            // Use the state's target pitch captured at the start for consistency within this execution
            var targetForCalculation: Pitch? = stateAtStart.targetPitch

            if (isInstrumentModeAutoSelect && currentFrameMidiNote != null && currentTuning != null) {
                var closestPitch: Pitch? = null
                var minSemitoneDiff = Int.MAX_VALUE

                if (!currentTuning.pitches.isNullOrEmpty()) {
                    currentTuning.pitches.forEach { stringPitch ->
                        val stringMidiNote = getMidiNoteFromPitch(stringPitch)
                        if (stringMidiNote != null) {
                            val diff = abs(currentFrameMidiNote - stringMidiNote)
                            if (diff < minSemitoneDiff) {
                                minSemitoneDiff = diff
                                closestPitch = stringPitch
                            }
                        }
                    }
                }

                // Auto-select if close enough and different from current target
                if (minSemitoneDiff <= AUTO_SELECT_SEMITONE_THRESHOLD && closestPitch != stateAtStart.targetPitch) {
                    Log.d("TunerViewModel", "Auto-selecting target: $closestPitch (Detected MIDI: ${currentFrameMidiNote}, String MIDI: ${getMidiNoteFromPitch(closestPitch!!)}, Diff: $minSemitoneDiff)")
                    targetForCalculation = closestPitch
                    // Only update the state's target pitch here if it changed via auto-select
                    if (stateAtStart.targetPitch != targetForCalculation) {
                        _uiState.update { it.copy(targetPitch = targetForCalculation) }
                    }
                }
            }
            // --- End Auto-Select ---

            val noteDetected = currentFrameMidiNote != null
            cancelNoDetectionTimer() // Cancel any pending reset timer

            if (noteDetected && offsetAvailable) {
                val detectedPitch = getPitchFromMidiNote(currentFrameMidiNote!!)!!
                val a4 = stateAtStart.a4Frequency
                val detectedExactFreq = calculateFrequency(detectedPitch, a4) * TWELFTH_ROOT_OF_TWO.pow(centsOffsetVsDetectedChromatic / 100f)

                var finalNoteName: String? = detectedPitch.noteName
                var finalOctave: Int? = detectedPitch.octave
                var rangeIndicator: String? = null
                var finalCentsOffset = centsOffsetVsDetectedChromatic // Default for Canto mode
                var valueForHistory = centsOffsetVsDetectedChromatic  // Default for Canto mode

                // Recalculate offset relative to target for Instrument/Chromatic modes
                if (!stateAtStart.isGraphCenteringDynamic && targetForCalculation != null) {
                    val targetMidiNote = getMidiNoteFromPitch(targetForCalculation)
                    val targetFreq = calculateFrequency(targetForCalculation, a4)

                    if (targetMidiNote != null && targetFreq > 1e-6f && detectedExactFreq > 1e-6f) {
                        finalCentsOffset = 1200.0f * log2(detectedExactFreq / targetFreq)
                        valueForHistory = finalCentsOffset // Use offset vs target for history in these modes

                        // Adjust displayed note if too far from target (Instrument/Chromatic only)
                        val deltaSemitones = currentFrameMidiNote - targetMidiNote
                        when {
                            deltaSemitones > DISPLAY_NOTE_BOUNDARY_SEMITONES -> {
                                val boundaryPitch = getPitchFromMidiNote(targetMidiNote + DISPLAY_NOTE_BOUNDARY_SEMITONES)
                                finalNoteName = boundaryPitch?.noteName; finalOctave = boundaryPitch?.octave; rangeIndicator = "+"
                            }
                            deltaSemitones < -DISPLAY_NOTE_BOUNDARY_SEMITONES -> {
                                val boundaryPitch = getPitchFromMidiNote(targetMidiNote - DISPLAY_NOTE_BOUNDARY_SEMITONES)
                                finalNoteName = boundaryPitch?.noteName; finalOctave = boundaryPitch?.octave; rangeIndicator = "-"
                            }
                            else -> {
                                // Display the actually detected note if within range
                                finalNoteName = detectedPitch.noteName; finalOctave = detectedPitch.octave; rangeIndicator = null
                            }
                        }
                    } else {
                        // Fallback if target frequency calculation fails (shouldn't happen often)
                        Log.w("TunerViewModel", "WARN: Fallback offset calculation used for non-dynamic mode!")
                        finalCentsOffset = centsOffsetVsDetectedChromatic
                        valueForHistory = centsOffsetVsDetectedChromatic
                    }
                }

                val normalizedValueForHistory = (valueForHistory / CENTS_RANGE_FOR_VISUALIZER).coerceIn(-1.0f, 1.0f)
                addHistoryPoint(normalizedValueForHistory, currentTime)
                val isTuned = abs(finalCentsOffset) <= CENTS_IN_TUNE_THRESHOLD

                // Update the UI state based on calculated values
                _uiState.update {
                    // Use the potentially auto-updated target from earlier logic
                    it.copy(
                        targetPitch = targetForCalculation,
                        displayedNoteName = finalNoteName,
                        displayedOctave = finalOctave,
                        outsideRangeIndicator = rangeIndicator,
                        centsOffset = finalCentsOffset,
                        isNoteDetected = true,
                        isTuned = isTuned
                    )
                }

            } else { // No valid detection or offset wasn't available from native code
                startNoDetectionTimer() // Start timer to potentially clear display
                addHistoryPoint(null, currentTime) // Add gap to history
                // Only update if state was previously showing detection
                if (stateAtStart.isNoteDetected) {
                    _uiState.update { it.copy(isNoteDetected = false, isTuned = false) }
                }
            }
        } // End coroutine launch
    }


    // --- History, Timers, and State Reset ---

    private fun addHistoryPoint(normalizedValue: Float?, timestamp: Long) {
        _uiState.update { currentState ->
            val updatedHistory = currentState.tuningHistory + (timestamp to normalizedValue)
            // Filter history to keep only points within the duration window
            val filteredHistory = updatedHistory.filter { (ts, _) ->
                timestamp - ts <= HISTORY_DURATION_MS
            }
            currentState.copy(tuningHistory = filteredHistory)
        }
    }

    private fun startNoDetectionTimer() {
        cancelNoDetectionTimer() // Ensure only one timer runs
        noDetectionJob = viewModelScope.launch(Dispatchers.Main) {
            delay(NO_DETECTION_TIMEOUT_MS)
            // Check isActive and if still no detection when timer finishes
            if (isActive && !_uiState.value.isNoteDetected) {
                Log.d("TunerViewModel", "No detection timeout reached. Clearing display.")
                // Clear display fields, keep target pitch
                _uiState.update {
                    it.copy(
                        displayedNoteName = null,
                        displayedOctave = null,
                        outsideRangeIndicator = null,
                        centsOffset = 0.0f,
                        isNoteDetected = false,
                        isTuned = false
                    )
                }
            }
            noDetectionJob = null // Mark timer as complete
        }
    }

    private fun cancelNoDetectionTimer() {
        if (noDetectionJob?.isActive == true) {
            noDetectionJob?.cancel()
        }
        noDetectionJob = null
    }

    // Resets display fields, optionally keeping the target pitch
    private fun resetDetectionState(keepTarget: Boolean = false) {
        Log.d("TunerViewModel", "Resetting detection state. Keep target: $keepTarget")
        val targetToKeep = if (keepTarget) _uiState.value.targetPitch else null
        _uiState.update {
            it.copy(
                targetPitch = targetToKeep, // Use variable determined outside
                displayedNoteName = null,
                displayedOctave = null,
                outsideRangeIndicator = null,
                centsOffset = 0.0f,
                isNoteDetected = false,
                isTuned = false,
                tuningHistory = emptyList() // Clear history as well
            )
        }
        cancelNoDetectionTimer() // Cancel any pending reset timer
    }

    // --- Utility Functions ---
    private fun calculateFrequency(pitch: Pitch, a4Freq: Float): Float {
        val midiNote = getMidiNoteFromPitch(pitch) ?: return 0.0f
        return a4Freq * 2.0f.pow((midiNote - MIDI_NOTE_A4) / 12.0f)
    }

    private fun getMidiNoteFromPitch(pitch: Pitch): Int? {
        val noteIndex = STANDARD_NOTES.indexOf(pitch.noteName)
        if (noteIndex == -1 || pitch.octave !in OCTAVE_RANGE) return null
        // MIDI note calculation: index + (octave + 1) * 12
        // Example: C0 -> 0 + (0+1)*12 = 12
        // Example: A4 -> 9 + (4+1)*12 = 69
        return noteIndex + (pitch.octave + 1) * 12
    }

    private fun getPitchFromMidiNote(midiNote: Int): Pitch? {
        if (midiNote < 12) return null // MIDI notes below C0 are uncommon for tuning
        val noteIndex = midiNote % 12
        val octave = (midiNote / 12) - 1 // Convert MIDI octave to standard scientific pitch notation octave
        if (octave !in OCTAVE_RANGE) {
            Log.w("TunerViewModel", "Calculated octave $octave from MIDI $midiNote is outside valid range $OCTAVE_RANGE")
            return null
        }
        return Pitch(STANDARD_NOTES[noteIndex], octave)
    }


    // --- JNI Declarations & Native Library Loading ---
    private external fun startNativeAudioEngine(sampleRate: Int, bufferSize: Int): Boolean
    private external fun stopNativeAudioEngine()
    private external fun setA4Native(frequency: Float)

    companion object {
        init {
            try {
                System.loadLibrary("afinador_native")
                Log.i("TunerViewModel", "Native library 'afinador_native' loaded successfully.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("TunerViewModel", "FATAL: Error loading native library 'afinador_native'", e)
                // Consider adding mechanisms to inform the user or disable functionality
            } catch (t: Throwable) {
                Log.e("TunerViewModel", "FATAL: Unexpected error loading native library", t)
            }
        }
    }

    // --- ViewModel Cleanup ---
    override fun onCleared() {
        super.onCleared()
        Log.i("TunerViewModel", "ViewModel cleared. Stopping audio processing.")
        stopAudioProcessing() // Ensure native engine is stopped
        cancelNoDetectionTimer() // Clean up any running timers
    }
}