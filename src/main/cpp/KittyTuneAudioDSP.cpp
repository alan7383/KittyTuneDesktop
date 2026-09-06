#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <float.h>
#include <cstdint>

extern "C" {
#include "ebur128/ebur128.h"
}

#define LOG_TAG "KittyTuneAudioDSP"
#define LOGI(...) do { printf("[%s] INFO: ", LOG_TAG); printf(__VA_ARGS__); printf("\n"); fflush(stdout); } while(0)
#define LOGE(...) do { fprintf(stderr, "[%s] ERROR: ", LOG_TAG); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while(0)

static const float kLimiterThresholdLinear = 0.8912509381337455f; // -1 dBFS in linear
static const float kLimiterAttackMs = 5.0f;
static const float kLimiterReleaseMs = 100.0f;

// Dedicated time constants for loudness normalization
static const float kNormAttackMs = 350.0f;   // 350ms smooth attenuation
static const float kNormReleaseMs = 3000.0f; // 3000ms pump-free release

typedef struct {
    ebur128_state* ebur128;
    int sampleRate;
    int channels;

    float currentGainLinear;
    float targetGainLinear;
    float targetLUFS;

    // Normalization gain smoothing coefficients
    float normAttackCoeff;
    float normReleaseCoeff;

    // Limiter state
    float* delayBuffer;
    int delaySamples;
    int delayWritePos;

    float envelope;
    float smoothedGain;
    float limiterAttackCoeff;
    float limiterReleaseCoeff;
    float envReleaseCoeff;     // fast release for envelope (instant rise)

    float shortTermLoudness;
    float momentaryLoudness;
    float integratedLoudness;
    float truePeakLinear;
    float maxTruePeakDb;

    long long framesProcessedSinceReset;

    int hasKnownTrackLoudness;
    float knownTrackLufs;
    float knownTrackPeakDb;

    float* processBuffer;
    int processBufferCapacity;

    long long frameCount;
    long long logInterval;
    int debugLogging;
} KittyTuneDSPState;

static float linearToDb(float linear) {
    if (linear <= 0.0f) return -120.0f;
    return 20.0f * log10f(linear);
}

static float dbToLinear(float db) {
    return powf(10.0f, db / 20.0f);
}

static void initLimiter(KittyTuneDSPState* s) {
    s->delaySamples = (int)(s->sampleRate * kLimiterAttackMs / 1000.0f);
    if (s->delaySamples < 1) s->delaySamples = 1;

    s->delayBuffer = (float*)calloc(s->delaySamples * s->channels, sizeof(float));
    s->delayWritePos = 0;
    s->envelope = 0.0f;
    s->smoothedGain = 1.0f;

    float limiterAttackTime = kLimiterAttackMs / 1000.0f;
    float limiterReleaseTime = kLimiterReleaseMs / 1000.0f;
    s->limiterAttackCoeff = expf(-1.0f / ((float)s->sampleRate * limiterAttackTime));
    s->limiterReleaseCoeff = expf(-1.0f / ((float)s->sampleRate * limiterReleaseTime));

    float normAttackTime = kNormAttackMs / 1000.0f;
    float normReleaseTime = kNormReleaseMs / 1000.0f;
    s->normAttackCoeff = expf(-1.0f / ((float)s->sampleRate * normAttackTime));
    s->normReleaseCoeff = expf(-1.0f / ((float)s->sampleRate * normReleaseTime));

    float envRelease = 0.02f; // 20ms release for envelope
    s->envReleaseCoeff = expf(-1.0f / ((float)s->sampleRate * envRelease));

    s->currentGainLinear = 1.0f;
    s->targetGainLinear = 1.0f;
}

static void destroyLimiter(KittyTuneDSPState* s) {
    free(s->delayBuffer);
    s->delayBuffer = NULL;
}

static void smoothGain(KittyTuneDSPState* s) {
    float gainCoeff = (s->targetGainLinear < s->currentGainLinear)
        ? s->normAttackCoeff : s->normReleaseCoeff;
    s->currentGainLinear = gainCoeff * s->currentGainLinear
        + (1.0f - gainCoeff) * s->targetGainLinear;
}

static void resetDspState(KittyTuneDSPState* s, float knownLufs, float knownPeakDb) {
    if (!s) return;

    int mode = EBUR128_MODE_I | EBUR128_MODE_S | EBUR128_MODE_TRUE_PEAK | EBUR128_MODE_HISTOGRAM;
    if (s->ebur128) {
        ebur128_destroy(&s->ebur128);
    }
    s->ebur128 = ebur128_init((unsigned int)s->channels, (unsigned long)s->sampleRate, mode);

    s->shortTermLoudness = -70.0f;
    s->momentaryLoudness = -70.0f;
    s->integratedLoudness = -70.0f;
    s->truePeakLinear = 0.0f;
    s->maxTruePeakDb = -120.0f;
    s->framesProcessedSinceReset = 0;

    s->envelope = 0.0f;
    s->smoothedGain = 1.0f;

    if (knownLufs > -60.0f && knownLufs < 0.0f) {
        s->hasKnownTrackLoudness = 1;
        s->knownTrackLufs = knownLufs;
        s->knownTrackPeakDb = knownPeakDb;

        float targetGainDb = s->targetLUFS - knownLufs;
        if (targetGainDb > 0.0f && knownPeakDb > -120.0f) {
            float maxHeadroom = -1.0f - knownPeakDb;
            if (targetGainDb > maxHeadroom) targetGainDb = maxHeadroom;
        }
        if (targetGainDb < -24.0f) targetGainDb = -24.0f;
        if (targetGainDb > 12.0f) targetGainDb = 12.0f;

        s->currentGainLinear = dbToLinear(targetGainDb);
        s->targetGainLinear = s->currentGainLinear;
    } else {
        s->hasKnownTrackLoudness = 0;
        s->knownTrackLufs = -70.0f;
        s->knownTrackPeakDb = -120.0f;

        float nominalGainDb = s->targetLUFS - (-10.0f);
        if (nominalGainDb > 0.0f) nominalGainDb = 0.0f;
        if (nominalGainDb < -15.0f) nominalGainDb = -15.0f;

        s->currentGainLinear = dbToLinear(nominalGainDb);
        s->targetGainLinear = s->currentGainLinear;
    }
}

static KittyTuneDSPState* createDspState(int channels, int sampleRate, int mode, float targetLUFS) {
    KittyTuneDSPState* s = (KittyTuneDSPState*)calloc(1, sizeof(KittyTuneDSPState));
    if (!s) {
        LOGE("Failed to allocate DSP state");
        return NULL;
    }

    s->sampleRate = sampleRate;
    s->channels = channels;
    s->targetLUFS = targetLUFS;
    s->shortTermLoudness = -70.0f;
    s->momentaryLoudness = -70.0f;
    s->integratedLoudness = -70.0f;
    s->truePeakLinear = 0.0f;
    s->maxTruePeakDb = -120.0f;
    s->framesProcessedSinceReset = 0;
    s->hasKnownTrackLoudness = 0;
    s->knownTrackLufs = -70.0f;
    s->knownTrackPeakDb = -120.0f;

    s->frameCount = 0;
    s->logInterval = (long long)sampleRate / 2; // log every 0.5s

    s->processBufferCapacity = 16384 * channels;
    s->processBuffer = (float*)malloc(s->processBufferCapacity * sizeof(float));

    s->ebur128 = ebur128_init((unsigned int)channels, (unsigned long)sampleRate, mode);
    if (!s->ebur128) {
        LOGE("Failed to initialize ebur128 (ch=%d, sr=%d)", channels, sampleRate);
        free(s);
        return NULL;
    }

    initLimiter(s);
    resetDspState(s, -70.0f, -120.0f);
    return s;
}

// Returns max true peak across all channels in dBTP
static float computeMaxTruePeakDb(KittyTuneDSPState* s) {
    double maxTp = 0.0;
    for (int ch = 0; ch < s->channels; ch++) {
        double tp;
        if (ebur128_true_peak(s->ebur128, (unsigned int)ch, &tp) == EBUR128_SUCCESS && isfinite(tp)) {
            if (tp > maxTp) maxTp = tp;
        }
    }
    if (maxTp <= 0.0) return -120.0f;
    return (float)(20.0 * log10(maxTp));
}

// Shared chunk-processing core used by both the playback path (nativeProcessShort)
// and the diagnostics harness. forcedGainDb = NaN means "auto" (compute from LUFS +
// headroom, as in production). Otherwise the given fixed gain in dB is used so the
// limiter can be stressed in isolation.
static void processChunk(KittyTuneDSPState* s, short* in, short* out, int numFrames, float forcedGainDb) {
    int totalSamples = numFrames * s->channels;

    if (totalSamples > s->processBufferCapacity) {
        float* newBuf = (float*)realloc(s->processBuffer, totalSamples * sizeof(float));
        if (!newBuf) return;
        s->processBuffer = newBuf;
        s->processBufferCapacity = totalSamples;
    }
    float* floatBuf = s->processBuffer;

    for (int i = 0; i < totalSamples; i++) {
        floatBuf[i] = (float)in[i] / 32768.0f;
    }

    ebur128_add_frames_float(s->ebur128, floatBuf, (size_t)numFrames);
    s->framesProcessedSinceReset += numFrames;

    // 1. Measure momentary loudness (400ms window) with warmup energy correction
    double mMomentary = -HUGE_VAL;
    float currentMomentaryLufs = -70.0f;
    if (ebur128_loudness_momentary(s->ebur128, &mMomentary) == EBUR128_SUCCESS && isfinite(mMomentary) && mMomentary > -HUGE_VAL) {
        size_t momentaryWindowFrames = (size_t)(s->sampleRate * 0.4);
        if (s->framesProcessedSinceReset < (long long)momentaryWindowFrames && s->framesProcessedSinceReset > 0) {
            double correctionDb = 10.0 * log10((double)momentaryWindowFrames / (double)s->framesProcessedSinceReset);
            if (correctionDb > 30.0) correctionDb = 30.0;
            currentMomentaryLufs = (float)(mMomentary + correctionDb);
        } else {
            currentMomentaryLufs = (float)mMomentary;
        }
        s->momentaryLoudness = currentMomentaryLufs;
    }

    // 2. Measure short-term loudness (3s window) with warmup energy correction
    double stLoudness = -HUGE_VAL;
    float currentShortTermLufs = -70.0f;
    if (ebur128_loudness_shortterm(s->ebur128, &stLoudness) == EBUR128_SUCCESS && isfinite(stLoudness) && stLoudness > -HUGE_VAL) {
        size_t shortTermWindowFrames = (size_t)(s->sampleRate * 3.0);
        if (s->framesProcessedSinceReset < (long long)shortTermWindowFrames && s->framesProcessedSinceReset > 0) {
            double correctionDb = 10.0 * log10((double)shortTermWindowFrames / (double)s->framesProcessedSinceReset);
            if (correctionDb > 40.0) correctionDb = 40.0;
            currentShortTermLufs = (float)(stLoudness + correctionDb);
        } else {
            currentShortTermLufs = (float)stLoudness;
        }
        s->shortTermLoudness = currentShortTermLufs;
    }

    // 3. Measure integrated global loudness so far
    double integrated = -HUGE_VAL;
    if (ebur128_loudness_global(s->ebur128, &integrated) == EBUR128_SUCCESS && isfinite(integrated) && integrated > -HUGE_VAL) {
        s->integratedLoudness = (float)integrated;
    }

    s->maxTruePeakDb = computeMaxTruePeakDb(s);

    // 4. Determine target gain in dB
    float gainDb;
    int isForced = (forcedGainDb != forcedGainDb) ? 0 : 1;  // NaN check
    if (isForced) {
        gainDb = forcedGainDb;
    } else if (s->hasKnownTrackLoudness) {
        gainDb = s->targetLUFS - s->knownTrackLufs;
        float maxTpDb = (s->knownTrackPeakDb > s->maxTruePeakDb) ? s->knownTrackPeakDb : s->maxTruePeakDb;
        if (gainDb > 0.0f && maxTpDb > -120.0f) {
            float maxGainForHeadroom = -1.0f - maxTpDb;
            if (gainDb > maxGainForHeadroom) gainDb = maxGainForHeadroom;
        }
    } else {
        float effectiveLufs = -70.0f;
        size_t momentaryWindowFrames = (size_t)(s->sampleRate * 0.4);
        size_t shortTermWindowFrames = (size_t)(s->sampleRate * 3.0);

        if (s->framesProcessedSinceReset < (long long)momentaryWindowFrames) {
            effectiveLufs = currentMomentaryLufs;
        } else if (s->framesProcessedSinceReset < (long long)shortTermWindowFrames) {
            effectiveLufs = (currentShortTermLufs > -60.0f) ? currentShortTermLufs : currentMomentaryLufs;
        } else {
            if (s->integratedLoudness > -60.0f) {
                if (s->shortTermLoudness > s->integratedLoudness) {
                    effectiveLufs = s->shortTermLoudness;
                } else {
                    effectiveLufs = s->integratedLoudness;
                }
            } else if (s->shortTermLoudness > -60.0f) {
                effectiveLufs = s->shortTermLoudness;
            } else {
                effectiveLufs = currentMomentaryLufs;
            }
        }

        // Silence / Gating logic:
        if (effectiveLufs < -42.0f) {
            if (s->integratedLoudness > -60.0f) {
                // Pause / silence during song: freeze at integrated level
                gainDb = s->targetLUFS - s->integratedLoudness;
            } else {
                // Intro silence: safe nominal gain, never boost
                gainDb = s->targetLUFS - (-10.0f);
                if (gainDb > 0.0f) gainDb = 0.0f;
            }
        } else {
            gainDb = s->targetLUFS - effectiveLufs;
        }

        float maxTpDb = s->maxTruePeakDb;
        if (gainDb > 0.0f && maxTpDb > -120.0f) {
            float maxGainForHeadroom = -1.0f - maxTpDb;
            if (gainDb > maxGainForHeadroom) {
                gainDb = maxGainForHeadroom;
            }
        }

        if (gainDb < -24.0f) gainDb = -24.0f;
        if (gainDb > 12.0f) gainDb = 12.0f;
    }

    if (!isfinite(gainDb)) gainDb = 0.0f;
    s->targetGainLinear = dbToLinear(gainDb);

    // If audio is detected right at start of track, snap currentGain to targetGain
    // so there is NEVER an initial loud burst!
    if (s->framesProcessedSinceReset <= (long long)(s->sampleRate * 0.15f) && !isForced && !s->hasKnownTrackLoudness) {
        if (s->momentaryLoudness > -42.0f) {
            s->currentGainLinear = s->targetGainLinear;
        }
    }

    for (int frame = 0; frame < numFrames; frame++) {
        smoothGain(s);

        // Max sample across channels for stereo-linked limiter
        float maxAbs = 0.0f;
        for (int ch = 0; ch < s->channels; ch++) {
            int sampleIdx = frame * s->channels + ch;
            float gained = floatBuf[sampleIdx] * s->currentGainLinear;
            s->delayBuffer[s->delayWritePos * s->channels + ch] = gained;
            float a = fabsf(gained);
            if (a > maxAbs) maxAbs = a;
        }

        if (maxAbs > s->envelope) {
            s->envelope = maxAbs;
        } else {
            s->envelope = s->envReleaseCoeff * s->envelope;
        }

        float targetLimiterGain = kLimiterThresholdLinear / fmaxf(s->envelope, kLimiterThresholdLinear);
        if (targetLimiterGain < s->smoothedGain) {
            s->smoothedGain = targetLimiterGain; // instant attack
        } else {
            s->smoothedGain = s->limiterReleaseCoeff * s->smoothedGain
                + (1.0f - s->limiterReleaseCoeff) * targetLimiterGain;
        }

        int readPos = (s->delayWritePos + 1) % s->delaySamples;
        for (int ch = 0; ch < s->channels; ch++) {
            int sampleIdx = frame * s->channels + ch;
            float delayed = s->delayBuffer[readPos * s->channels + ch];
            float processed = delayed * s->smoothedGain;

            if (processed > 1.0f) processed = 1.0f;
            if (processed < -1.0f) processed = -1.0f;
            out[sampleIdx] = (short)(processed * 32767.0f);
        }

        s->delayWritePos = readPos;
    }

    s->frameCount += numFrames;
    if (s->debugLogging && s->frameCount >= s->logInterval) {
        float currentDb = linearToDb(s->currentGainLinear);
        float limiterDb = linearToDb(s->smoothedGain);
        LOGI("LUFS mom=%.1f short=%.1f int=%.1f | gain=%.1fdB lim=%.1fdB | TP=%.1fdB target=%.1f%s",
             s->momentaryLoudness, s->shortTermLoudness, s->integratedLoudness,
             currentDb, limiterDb,
             s->maxTruePeakDb, s->targetLUFS,
             isForced ? " (forced)" : "");
        s->frameCount = 0;
    }
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_alananasss_kittytune_audio_NormalizationAudioProcessor_nativeInit(
    JNIEnv* env, jclass clazz, jint channels, jint sampleRate) {

    int mode = EBUR128_MODE_I | EBUR128_MODE_S | EBUR128_MODE_TRUE_PEAK | EBUR128_MODE_HISTOGRAM;
    KittyTuneDSPState* s = createDspState(channels, sampleRate, mode, -14.0f);
    if (!s) return 0;

    s->debugLogging = 1;
    LOGI("DSP initialized: ch=%d, sr=%d, mode=I+S+TP+HIST", channels, sampleRate);
    return (jlong)(intptr_t)s;
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_NormalizationAudioProcessor_nativeDestroy(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return;

    if (s->ebur128) {
        ebur128_destroy(&s->ebur128);
        s->ebur128 = NULL;
    }

    destroyLimiter(s);
    if (s->processBuffer) {
        free(s->processBuffer);
        s->processBuffer = NULL;
    }
    free(s);
    LOGI("DSP destroyed");
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_NormalizationAudioProcessor_nativeSetTargetLevel(
    JNIEnv* env, jclass clazz, jlong handle, jfloat targetLUFS) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return;
    s->targetLUFS = targetLUFS;
    if (s->hasKnownTrackLoudness) {
        float targetGainDb = s->targetLUFS - s->knownTrackLufs;
        if (targetGainDb > 0.0f && s->knownTrackPeakDb > -120.0f) {
            float maxHeadroom = -1.0f - s->knownTrackPeakDb;
            if (targetGainDb > maxHeadroom) targetGainDb = maxHeadroom;
        }
        if (targetGainDb < -24.0f) targetGainDb = -24.0f;
        if (targetGainDb > 12.0f) targetGainDb = 12.0f;
        s->targetGainLinear = dbToLinear(targetGainDb);
    }
    LOGI("Target LUFS set to %.1f", targetLUFS);
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_NormalizationAudioProcessor_nativeResetLoudness(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s || !s->ebur128) return;

    if (s->hasKnownTrackLoudness) {
        resetDspState(s, s->knownTrackLufs, s->knownTrackPeakDb);
    } else {
        resetDspState(s, -70.0f, -120.0f);
    }
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_NormalizationAudioProcessor_nativeSetTrackLoudness(
    JNIEnv* env, jclass clazz, jlong handle, jfloat trackLufs, jfloat maxPeakDb) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return;
    resetDspState(s, trackLufs, maxPeakDb);
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_NormalizationAudioProcessor_nativeClearTrackLoudness(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return;
    resetDspState(s, -70.0f, -120.0f);
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_NormalizationAudioProcessor_nativeProcessShort(
    JNIEnv* env, jclass clazz, jlong handle,
    jobject inputBuffer, jobject outputBuffer, jint numFrames, jint inOffset) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s || !s->ebur128) return;

    short* in = (short*)((char*)env->GetDirectBufferAddress(inputBuffer) + inOffset);
    short* out = (short*)env->GetDirectBufferAddress(outputBuffer);
    if (!in || !out) return;

    processChunk(s, in, out, numFrames, NAN);
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_audio_NormalizationAudioProcessor_nativeGetShortTermLoudness(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return -70.0f;
    return s->shortTermLoudness;
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_audio_NormalizationAudioProcessor_nativeGetMomentaryLoudness(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return -70.0f;
    return s->momentaryLoudness;
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_audio_NormalizationAudioProcessor_nativeGetIntegratedLoudness(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return -70.0f;
    return s->integratedLoudness;
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_audio_NormalizationAudioProcessor_nativeGetTruePeakDb(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return -120.0f;
    return s->maxTruePeakDb;
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_audio_NormalizationAudioProcessor_nativeGetCurrentGainDb(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return 0.0f;
    return linearToDb(s->currentGainLinear);
}

// --- Scanner functions ---

JNIEXPORT jlong JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeCreateAnalyzer(
    JNIEnv* env, jclass clazz, jint channels, jint sampleRate) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)calloc(1, sizeof(KittyTuneDSPState));
    if (!s) return 0;

    s->sampleRate = sampleRate;
    s->channels = channels;
    s->integratedLoudness = -70.0f;
    s->truePeakLinear = 0.0f;

    int mode = EBUR128_MODE_I | EBUR128_MODE_TRUE_PEAK | EBUR128_MODE_HISTOGRAM;
    s->ebur128 = ebur128_init((unsigned int)channels, (unsigned long)sampleRate, mode);
    if (!s->ebur128) {
        free(s);
        return 0;
    }

    LOGI("Scanner initialized: ch=%d, sr=%d", channels, sampleRate);
    return (jlong)(intptr_t)s;
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeDestroyAnalyzer(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return;
    if (s->ebur128) {
        ebur128_destroy(&s->ebur128);
    }
    if (s->processBuffer) {
        free(s->processBuffer);
    }
    free(s);
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeAddFramesFloat(
    JNIEnv* env, jclass clazz, jlong handle,
    jfloatArray samples, jint numFrames) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s || !s->ebur128) return;

    jfloat* data = env->GetFloatArrayElements(samples, NULL);
    if (!data) return;

    ebur128_add_frames_float(s->ebur128, data, (size_t)numFrames);

    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeAddFramesShort(
    JNIEnv* env, jclass clazz, jlong handle,
    jshortArray samples, jint numFrames) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s || !s->ebur128) return;

    int totalSamples = numFrames * s->channels;
    if (totalSamples > s->processBufferCapacity) {
        float* newBuf = (float*)realloc(s->processBuffer, totalSamples * sizeof(float));
        if (!newBuf) return;
        s->processBuffer = newBuf;
        s->processBufferCapacity = totalSamples;
    }

    jshort* data = env->GetShortArrayElements(samples, NULL);
    if (!data) return;

    for (int i = 0; i < totalSamples; i++) {
        s->processBuffer[i] = (float)data[i] / 32768.0f;
    }

    env->ReleaseShortArrayElements(samples, data, JNI_ABORT);

    ebur128_add_frames_float(s->ebur128, s->processBuffer, (size_t)numFrames);
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeGetAnalyzedLoudness(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s || !s->ebur128) return -70.0f;

    double integrated = -HUGE_VAL;
    if (ebur128_loudness_global(s->ebur128, &integrated) == EBUR128_SUCCESS) {
        if (isfinite((float)integrated) && integrated > -HUGE_VAL) {
            return (jfloat)integrated;
        }
    }
    return -70.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeGetAnalyzedTruePeakDb(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s || !s->ebur128) return -120.0f;

    double maxTp = 0.0;
    for (int ch = 0; ch < s->channels; ch++) {
        double tp;
        if (ebur128_true_peak(s->ebur128, (unsigned int)ch, &tp) == EBUR128_SUCCESS && isfinite(tp)) {
            if (tp > maxTp) maxTp = tp;
        }
    }

    if (maxTp <= 0.0) return -120.0f;
    return (jfloat)(20.0 * log10(maxTp));
}

} // extern "C"
