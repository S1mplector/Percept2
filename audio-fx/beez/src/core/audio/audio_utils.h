#pragma once

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <math.h>

#ifdef __cplusplus
extern "C" {
#endif

static inline float audio_clamp(float value, float min, float max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
}

static inline float audio_soft_clip(float sample) {
    if (sample >= 1.0f) {
        return 0.6666667f;
    } else if (sample <= -1.0f) {
        return -0.6666667f;
    }
    return sample - (sample * sample * sample) / 3.0f;
}

static inline float audio_hard_clip(float sample) {
    return audio_clamp(sample, -1.0f, 1.0f);
}

static inline float db_to_linear(float db) {
    return (db <= -80.0f) ? 0.0f : powf(10.0f, db * 0.05f);
}

static inline float linear_to_db(float linear) {
    return (linear <= 0.0f) ? -80.0f : 20.0f * log10f(linear);
}

static inline int16_t float_to_int16(float sample) {
    sample = audio_hard_clip(sample);
    return (int16_t)(sample * 32767.0f);
}

static inline float int16_to_float(int16_t sample) {
    return (float)sample / 32768.0f;
}

void audio_interleave_stereo(const float* left, const float* right, float* interleaved, size_t frames);
void audio_deinterleave_stereo(const float* interleaved, float* left, float* right, size_t frames);

void audio_apply_gain(float* buffer, size_t count, float gain);
void audio_mix(const float* src, float* dst, size_t count, float gain);
void audio_fade(float* buffer, size_t count, float start_gain, float end_gain);

float audio_rms(const float* buffer, size_t count);
float audio_peak(const float* buffer, size_t count);

#ifdef __cplusplus
}
#endif
