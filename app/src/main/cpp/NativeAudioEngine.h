#ifndef NATIVE_AUDIO_ENGINE_H
#define NATIVE_AUDIO_ENGINE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// Starts the audio engine with the specified sample rate and buffer size.
JNIEXPORT jboolean JNICALL
Java_com_isaacbegue_afinador_viewmodel_TunerViewModel_startNativeAudioEngine(
        JNIEnv* env,
        jobject instance,
        jint sampleRate,
        jint bufferSize);

// Stops the audio engine.
JNIEXPORT void JNICALL
Java_com_isaacbegue_afinador_viewmodel_TunerViewModel_stopNativeAudioEngine(
        JNIEnv* env,
        jobject instance);

// Sets the reference frequency for A4.
JNIEXPORT void JNICALL
Java_com_isaacbegue_afinador_viewmodel_TunerViewModel_setA4Native(
        JNIEnv* env,
        jobject instance,
        jfloat frequency);

// Removed: Declaration for setTargetFrequencyNative as it's no longer needed in C++

#ifdef __cplusplus
}
#endif

#endif // NATIVE_AUDIO_ENGINE_H