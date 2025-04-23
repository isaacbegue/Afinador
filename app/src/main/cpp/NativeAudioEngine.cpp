#include "NativeAudioEngine.h"
#include <oboe/Oboe.h>
#include <android/log.h>
#include <jni.h>
#include <cmath>
#include <limits>
#include <numeric>
#include <vector>
#include <atomic>

// --- Logging ---
#define LOG_TAG "NativeAudioEngine"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
// ALOGD removed for production clean code

// --- Constants ---
constexpr float MIN_VALID_FREQUENCY = 20.0f;
constexpr float MIN_RMS_THRESHOLD = 0.008f; // Adjust sensitivity as needed
constexpr float CENTS_NOT_AVAILABLE = -1000.0f;
constexpr int NOTE_INDEX_NOT_AVAILABLE = -1;
constexpr int OCTAVE_NOT_AVAILABLE = -1;
constexpr int MIDI_NOTE_A4 = 69;
const float TWELFTH_ROOT_OF_TWO = powf(2.0f, 1.0f / 12.0f);
// YIN Constants
constexpr float YIN_DEFAULT_THRESHOLD = 0.15f; // Default YIN threshold

// --- Global Variables ---
using namespace oboe;
static std::shared_ptr<oboe::AudioStream> gStream = nullptr;
static std::atomic<float> gA4Freq(440.0f); // A4 reference frequency
static std::atomic<bool> isEngineRunning(false);

static JavaVM* gJvm = nullptr;
static jobject gJavaInstance = nullptr;


// --- YIN Algorithm Implementation (no changes) ---
static void difference(const float* buffer, int size, int tau, std::vector<float>& yinBuffer) {
    yinBuffer[tau] = 0;
    for (int j = 0; j < size - tau; ++j) {
        float delta = buffer[j] - buffer[j + tau];
        yinBuffer[tau] += delta * delta;
    }
}
static void cumulativeMeanNormalizedDifference(std::vector<float>& yinBuffer, int size) {
    yinBuffer[0] = 1.0f;
    float runningSum = 0.0f;
    for (int tau = 1; tau < yinBuffer.size(); ++tau) {
        runningSum += yinBuffer[tau];
        // Avoid division by zero or very small numbers
        yinBuffer[tau] = (runningSum > std::numeric_limits<float>::epsilon()) ? (yinBuffer[tau] * tau / runningSum) : 1.0f;
    }
}
static int absoluteThreshold(const std::vector<float>& yinBuffer, float threshold) {
    for (int tau = 2; tau < yinBuffer.size(); ++tau) { // Start from tau=2
        if (yinBuffer[tau] < threshold) {
            // Find the local minimum within this dip
            while (tau + 1 < yinBuffer.size() && yinBuffer[tau + 1] < yinBuffer[tau]) {
                tau++;
            }
            // Ensure the minimum is actually below the threshold
            if(yinBuffer[tau] < threshold) return tau;
            // If the minimum wasn't below threshold, continue searching for the next dip
        }
    }
    return -1; // No pitch detected below the threshold
}
static float parabolicInterpolation(const std::vector<float>& yinBuffer, int tauEstimate) {
    if (tauEstimate <= 0 || tauEstimate >= yinBuffer.size() - 1) {
        // Cannot interpolate at the edges
        return static_cast<float>(tauEstimate);
    }
    float yMinus = yinBuffer[tauEstimate - 1];
    float yCenter = yinBuffer[tauEstimate];
    float yPlus = yinBuffer[tauEstimate + 1];
    float denominator = yMinus + yPlus - 2.0f * yCenter; // Corrected denominator
    if (std::abs(denominator) > std::numeric_limits<float>::epsilon()) {
        // Corrected numerator (yMinus - yPlus)
        float peakShift = (yMinus - yPlus) / (2.0f * denominator);
        return tauEstimate + peakShift;
    } else {
        // Denominator is too small, return original estimate
        return static_cast<float>(tauEstimate);
    }
}
static float computeYIN(const float* audioBuffer, int bufferSize, int sampleRate) {
    if (bufferSize <= 0 || sampleRate <= 0) return 0.0f;
    // Calculate tauMax based on minimum frequency we want to detect reliably
    int tauMax = bufferSize / 2; // Limit search range
    if (tauMax <= 1) return 0.0f; // Need at least tau=2

    std::vector<float> yinBuffer(tauMax, 0.0f);

    // Step 2: Autocorrelation using difference function
    for (int tau = 0; tau < tauMax; ++tau) {
        difference(audioBuffer, bufferSize, tau, yinBuffer);
    }

    // Step 3: Cumulative mean normalized difference
    cumulativeMeanNormalizedDifference(yinBuffer, tauMax);

    // Step 4: Absolute thresholding
    int tauEstimate = absoluteThreshold(yinBuffer, YIN_DEFAULT_THRESHOLD);

    // Step 5 & 6: Parabolic interpolation (if threshold found)
    float refinedTau = (tauEstimate != -1) ? parabolicInterpolation(yinBuffer, tauEstimate) : -1.0f;

    if (refinedTau > 0.0f) {
        return static_cast<float>(sampleRate) / refinedTau;
    } else {
        return 0.0f; // No reliable pitch detected
    }
}
// --- End YIN ---

// --- Helper: Calculate Frequency for MIDI Note ---
static float calculateFrequencyForMidiNote(int midiNote, float a4Frequency) {
    return a4Frequency * powf(TWELFTH_ROOT_OF_TWO, static_cast<float>(midiNote - MIDI_NOTE_A4));
}

// --- Helper: Get Note Info from Frequency ---
struct DetectedNoteInfo {
    int noteIndex = NOTE_INDEX_NOT_AVAILABLE;
    int octave = OCTAVE_NOT_AVAILABLE;
    int midiNote = -1;
};
static DetectedNoteInfo getNoteInfoFromFrequency(float frequency) {
    DetectedNoteInfo info;
    if (frequency < MIN_VALID_FREQUENCY) {
        return info; // Below valid range
    }
    float a4 = gA4Freq.load(); // Use the current A4 reference
    // Calculate the floating-point MIDI note number
    float midiNoteFloat = 12.0f * log2f(frequency / a4) + static_cast<float>(MIDI_NOTE_A4);
    int roundedMidiNote = static_cast<int>(roundf(midiNoteFloat));

    if (roundedMidiNote >= 0) { // Basic validity check for MIDI note number
        info.noteIndex = roundedMidiNote % 12;
        info.octave = (roundedMidiNote / 12) - 1; // Standard octave calculation
        info.midiNote = roundedMidiNote;
    }
    return info;
}

// --- JNI Callback Function ---
static void notifyNativeResult(int noteIndex, int octave, float centsOffsetVsDetected) {
    if (!gJvm || !gJavaInstance) {
        ALOGE("notifyNativeResult: gJvm or gJavaInstance is NULL. Aborting callback.");
        return;
    }
    JNIEnv* env = nullptr;
    jint attachResult = gJvm->AttachCurrentThread(&env, nullptr);
    if (attachResult != JNI_OK || env == nullptr) {
        ALOGE("notifyNativeResult: Failed to attach JNI thread. Error: %d", attachResult);
        return;
    }

    jclass clazz = env->GetObjectClass(gJavaInstance);
    if (!clazz) {
        ALOGE("notifyNativeResult: Failed to get object class.");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        gJvm->DetachCurrentThread();
        return;
    }

    // Find the method ID for the callback in TunerViewModel
    jmethodID methodId = env->GetMethodID(clazz, "onNativeResult", "(IIF)V");

    if (methodId) {
        env->CallVoidMethod(gJavaInstance, methodId, noteIndex, octave, centsOffsetVsDetected);
        if (env->ExceptionCheck()) {
            ALOGE("notifyNativeResult: JNI Exception occurred calling onNativeResult");
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
    } else {
        ALOGE("notifyNativeResult: Failed to find JNI method: onNativeResult(IIF)V");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
    }

    env->DeleteLocalRef(clazz); // Clean up local reference
    gJvm->DetachCurrentThread();
}


// --- Oboe Audio Callback Implementation ---
class AudioCallback : public AudioStreamCallback {
public:
    DataCallbackResult onAudioReady(AudioStream* stream, void* audioData, int32_t numFrames) override {
        if (!stream || !isEngineRunning.load() || numFrames <= 0) {
            // If stopped intentionally or error, return Stop. Otherwise Continue (maybe silence).
            return isEngineRunning.load() ? DataCallbackResult::Continue : DataCallbackResult::Stop;
        }

        const auto* input = static_cast<const float*>(audioData);

        // 1. Calculate RMS to check for silence/noise
        float sumOfSquares = 0.0f;
        for (int i = 0; i < numFrames; ++i) {
            sumOfSquares += input[i] * input[i];
        }
        float rms = sqrtf(sumOfSquares / numFrames);

        // 2. Check RMS against threshold
        if (rms < MIN_RMS_THRESHOLD) {
            // Below threshold, likely silence or noise, notify with invalid data
            notifyNativeResult(NOTE_INDEX_NOT_AVAILABLE, OCTAVE_NOT_AVAILABLE, CENTS_NOT_AVAILABLE);
            return DataCallbackResult::Continue; // Keep stream running
        }

        // 3. Process audio if above threshold
        int currentSampleRate = stream->getSampleRate();
        float detectedFreq = computeYIN(input, numFrames, currentSampleRate);

        float centsOffset = CENTS_NOT_AVAILABLE; // Default if frequency is invalid
        DetectedNoteInfo detectedNote = getNoteInfoFromFrequency(detectedFreq);

        // Calculate cents offset relative to the CLOSEST CHROMATIC note if frequency is valid
        if (detectedNote.noteIndex != NOTE_INDEX_NOT_AVAILABLE && detectedFreq >= MIN_VALID_FREQUENCY) {
            float theoreticalFreq = calculateFrequencyForMidiNote(detectedNote.midiNote, gA4Freq.load());
            if (theoreticalFreq > std::numeric_limits<float>::epsilon()) { // Avoid division by zero
                centsOffset = 1200.0f * log2f(detectedFreq / theoreticalFreq);
            } else {
                centsOffset = 0.0f; // Should not happen if detectedFreq is valid
            }
        }

        // Notify Kotlin with the detected note index, octave, and cents offset (or invalid values)
        notifyNativeResult(detectedNote.noteIndex, detectedNote.octave, centsOffset);

        return DataCallbackResult::Continue;
    }

    // --- Error Callbacks ---
    void onErrorBeforeClose(AudioStream *stream, Result error) override {
        ALOGE("onErrorBeforeClose: %s", convertToText(error));
        isEngineRunning = false;
        // Clean up JNI reference ONLY if it hasn't been cleaned elsewhere
        if (gJavaInstance && gJvm) {
            JNIEnv* env = nullptr;
            jint attachResult = gJvm->AttachCurrentThread(&env, nullptr);
            if (attachResult == JNI_OK && env != nullptr) {
                ALOGW("Cleaning JNI global ref in onErrorBeforeClose");
                env->DeleteGlobalRef(gJavaInstance);
                gJavaInstance = nullptr; // Mark as cleaned
                gJvm->DetachCurrentThread();
            } else { ALOGE("Failed to attach thread in onErrorBeforeClose"); }
        } else { ALOGW("JNI env/instance null or already cleaned in onErrorBeforeClose"); }
        if (gStream.get() == stream) { gStream.reset(); } // Prevent use after error
    }

    void onErrorAfterClose(AudioStream *stream, Result error) override {
        ALOGE("onErrorAfterClose: %s", convertToText(error));
        isEngineRunning = false;
        // Double-check and clean up JNI reference if needed
        if (gJavaInstance && gJvm) {
            JNIEnv* env = nullptr;
            jint attachResult = gJvm->AttachCurrentThread(&env, nullptr);
            if (attachResult == JNI_OK && env != nullptr) {
                ALOGW("Cleaning JNI global ref in onErrorAfterClose");
                env->DeleteGlobalRef(gJavaInstance);
                gJavaInstance = nullptr; // Mark as cleaned
                gJvm->DetachCurrentThread();
            } else { ALOGE("Failed to attach thread in onErrorAfterClose"); }
        } else { ALOGW("JNI env/instance null or already cleaned in onErrorAfterClose"); }
        if (gStream.get() == stream) { gStream.reset(); } // Prevent use after error
    }
};

static AudioCallback audioCallbackInstance; // Single instance


// --- JNI Exported Functions ---
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_isaacbegue_afinador_viewmodel_TunerViewModel_startNativeAudioEngine(
        JNIEnv* env, jobject instance, jint sampleRate, jint bufferSize) {

    if (isEngineRunning.load()) { ALOGW("Engine already running, start ignored."); return JNI_FALSE; }

    // Store JVM and create a global reference to the TunerViewModel instance
    env->GetJavaVM(&gJvm);
    if (!gJvm) { ALOGE("Failed to get JVM."); return JNI_FALSE; }
    // Clean up any previous instance before creating a new one
    if (gJavaInstance) {
        ALOGW("Existing JNI global ref found on start, deleting old one.");
        env->DeleteGlobalRef(gJavaInstance);
        gJavaInstance = nullptr;
    }
    gJavaInstance = env->NewGlobalRef(instance);
    if (!gJavaInstance) { ALOGE("Failed to create JNI global ref."); return JNI_FALSE; }

    ALOGI("Attempting to start native audio engine: %d Hz, %d frames buffer requested.", sampleRate, bufferSize);

    AudioStreamBuilder builder;
    builder.setDirection(Direction::Input)
            ->setPerformanceMode(PerformanceMode::LowLatency)
            ->setSharingMode(SharingMode::Exclusive) // Try exclusive first
            ->setSampleRate(sampleRate)
            ->setChannelCount(1) // Mono input
            ->setFormat(AudioFormat::Float)
            ->setFramesPerCallback(bufferSize) // Set initial desired buffer size
            ->setDataCallback(&audioCallbackInstance)
            ->setErrorCallback(&audioCallbackInstance);

    Result result = builder.openStream(gStream);

    if (result != Result::OK) {
        ALOGW("Exclusive stream failed (%s), trying Shared mode...", convertToText(result));
        builder.setSharingMode(SharingMode::Shared); // Fallback to shared
        result = builder.openStream(gStream);
        if (result != Result::OK) {
            ALOGE("Shared stream also failed: %s", convertToText(result));
            env->DeleteGlobalRef(gJavaInstance); gJavaInstance = nullptr; // Cleanup ref on failure
            gStream.reset();
            return JNI_FALSE;
        } else { ALOGI("Opened stream successfully in Shared mode."); }
    } else { ALOGI("Opened stream successfully in Exclusive mode."); }

    // --- Optional: Set buffer size after opening ---
    // It's often better to let Oboe manage this, but you can try setting it.
    // int optimalBufferSize = gStream->getFramesPerBurst() * 2; // Example multiplier
    // if (optimalBufferSize > 0) {
    //     Result bufferResult = gStream->setBufferSizeInFrames(optimalBufferSize);
    //     if(bufferResult == Result::OK){ ALOGI("Internal buffer size set to: %d frames", gStream->getBufferSizeInFrames()); }
    //     else { ALOGW("Failed setBufferSizeInFrames(%d): %s. Using default: %d", optimalBufferSize, convertToText(bufferResult), gStream->getBufferSizeInFrames()); }
    // }
    // -----------------------------------------------

    ALOGI("Actual Stream Params: Rate=%d, Format=%s, Ch=%d, PerfMode=%s, Sharing=%s, BufferSize=%d, Burst=%d",
          gStream->getSampleRate(), convertToText(gStream->getFormat()), gStream->getChannelCount(),
          convertToText(gStream->getPerformanceMode()), convertToText(gStream->getSharingMode()),
          gStream->getBufferSizeInFrames(), gStream->getFramesPerBurst());

    result = gStream->requestStart();
    if (result != Result::OK) {
        ALOGE("requestStart failed: %s", convertToText(result));
        gStream->close(); // Close the stream on failure
        gStream.reset();
        env->DeleteGlobalRef(gJavaInstance); gJavaInstance = nullptr; // Cleanup ref
        return JNI_FALSE;
    }

    ALOGI("Native audio engine started successfully!");
    isEngineRunning = true;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_isaacbegue_afinador_viewmodel_TunerViewModel_stopNativeAudioEngine(
        JNIEnv* env, jobject /*instance*/) {

    bool wasRunning = isEngineRunning.exchange(false); // Atomically set to false and get previous value

    if (!wasRunning || !gStream) {
        ALOGW("Engine not running or stream null, stop request ignored/already stopped.");
        // Attempt cleanup JNI ref just in case it was left hanging
        if (gJavaInstance && gJvm) {
            JNIEnv* currentEnv = nullptr;
            jint getEnvResult = gJvm->GetEnv(reinterpret_cast<void**>(&currentEnv), JNI_VERSION_1_6);
            bool attached = false;
            if (getEnvResult == JNI_EDETACHED) { getEnvResult = gJvm->AttachCurrentThread(&currentEnv, nullptr); attached = (getEnvResult == JNI_OK); }
            if (getEnvResult == JNI_OK && currentEnv) {
                ALOGW("Cleaning up potentially dangling JNI ref during stop (engine wasn't running).");
                currentEnv->DeleteGlobalRef(gJavaInstance);
                gJavaInstance = nullptr; // Mark as cleaned
            }
            if (attached) { gJvm->DetachCurrentThread(); }
        }
        gStream.reset(); // Ensure stream pointer is null
        return;
    }

    ALOGI("Stopping native audio engine requested...");

    Result stopResult = gStream->requestStop();
    if (stopResult != Result::OK) { ALOGE("requestStop failed: %s", convertToText(stopResult)); }
    else { ALOGI("Stream stop requested successfully."); }

    Result closeResult = gStream->close();
    if (closeResult != Result::OK) { ALOGE("Stream close failed: %s", convertToText(closeResult)); }
    else { ALOGI("Stream closed successfully."); }

    gStream.reset(); // Release the stream pointer

    // Clean up JNI global reference
    if (gJavaInstance && gJvm) {
        JNIEnv* currentEnv = nullptr;
        jint getEnvResult = gJvm->GetEnv(reinterpret_cast<void**>(&currentEnv), JNI_VERSION_1_6);
        bool attached = false;
        // Attach if not already attached
        if (getEnvResult == JNI_EDETACHED) {
            getEnvResult = gJvm->AttachCurrentThread(&currentEnv, nullptr);
            attached = (getEnvResult == JNI_OK);
        }
        if (getEnvResult == JNI_OK && currentEnv) {
            ALOGI("Deleting JNI global ref during stop...");
            currentEnv->DeleteGlobalRef(gJavaInstance);
            gJavaInstance = nullptr; // Nullify after deletion
        } else {
            ALOGE("Could not get/attach JNIEnv to delete global ref during stop. Result: %d", getEnvResult);
        }
        // Detach only if we attached it here
        if (attached) {
            gJvm->DetachCurrentThread();
        }
    } else { ALOGW("JNI global ref was already null or JVM null during stop."); }
    gJavaInstance = nullptr; // Ensure it's null

    ALOGI("Native audio engine stopped.");
}

JNIEXPORT void JNICALL
Java_com_isaacbegue_afinador_viewmodel_TunerViewModel_setA4Native(
        JNIEnv* /*env*/, jobject /*instance*/, jfloat frequency) {
    // Basic validation for plausible A4 range
    if (frequency >= 300.0f && frequency <= 600.0f) {
        gA4Freq.store(frequency);
        // ALOGD removed
    } else {
        ALOGW("Invalid A4 frequency received: %.2f Hz. Request ignored.", frequency);
    }
}

// Removed: setTargetFrequencyNative implementation

} // extern "C"