#include "audio_utils.h"
#include <math.h>

void audio_interleave_stereo(const float* left, const float* right, float* interleaved, size_t frames) {
    if (!left || !right || !interleaved) return;
    for (size_t i = 0; i < frames; i++) {
        interleaved[i * 2] = left[i];
        interleaved[i * 2 + 1] = right[i];
    }
}

void audio_deinterleave_stereo(const float* interleaved, float* left, float* right, size_t frames) {
    if (!interleaved || !left || !right) return;
    for (size_t i = 0; i < frames; i++) {
        left[i] = interleaved[i * 2];
        right[i] = interleaved[i * 2 + 1];
    }
}

void audio_apply_gain(float* buffer, size_t count, float gain) {
    if (!buffer) return;
    for (size_t i = 0; i < count; i++) {
        buffer[i] *= gain;
    }
}

void audio_mix(const float* src, float* dst, size_t count, float gain) {
    if (!src || !dst) return;
    for (size_t i = 0; i < count; i++) {
        dst[i] += src[i] * gain;
    }
}

void audio_fade(float* buffer, size_t count, float start_gain, float end_gain) {
    if (!buffer || count == 0) return;
    
    float delta = (end_gain - start_gain) / (float)count;
    float gain = start_gain;
    
    for (size_t i = 0; i < count; i++) {
        buffer[i] *= gain;
        gain += delta;
    }
}

float audio_rms(const float* buffer, size_t count) {
    if (!buffer || count == 0) return 0.0f;
    
    float sum = 0.0f;
    for (size_t i = 0; i < count; i++) {
        sum += buffer[i] * buffer[i];
    }
    return sqrtf(sum / (float)count);
}

float audio_peak(const float* buffer, size_t count) {
    if (!buffer || count == 0) return 0.0f;
    float peak = 0.0f;
    for (size_t i = 0; i < count; i++) {
        float abs_val = fabsf(buffer[i]);
        if (abs_val > peak) {
            peak = abs_val;
        }
    }
    return peak;
}
