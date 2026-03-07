#pragma once

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    uint32_t sample_rate;
    uint16_t num_channels;
    uint16_t bits_per_sample;
    uint32_t num_samples;
    float* left_buffer;
    float* right_buffer;
} WavExportData;

typedef struct {
    char filename[256];
    uint32_t sample_rate;
    uint16_t num_channels;
    uint16_t bits_per_sample;
    bool normalize;
    float normalize_target;
} WavExportConfig;

void wav_export_config_init(WavExportConfig* config);

bool wav_export_stereo(const char* filename, 
                       const float* left, 
                       const float* right, 
                       uint32_t num_samples, 
                       uint32_t sample_rate);

bool wav_export_mono(const char* filename, 
                     const float* buffer, 
                     uint32_t num_samples, 
                     uint32_t sample_rate);

bool wav_export_with_config(const WavExportConfig* config, 
                            const float* left, 
                            const float* right, 
                            uint32_t num_samples);

void wav_normalize_buffer(float* buffer, uint32_t num_samples, float target);
void wav_normalize_stereo(float* left, float* right, uint32_t num_samples, float target);

float wav_get_peak(const float* buffer, uint32_t num_samples);
float wav_get_peak_stereo(const float* left, const float* right, uint32_t num_samples);

#ifdef __cplusplus
}
#endif
