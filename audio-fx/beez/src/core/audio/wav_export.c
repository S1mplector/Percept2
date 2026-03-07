#include "wav_export.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#pragma pack(push, 1)
typedef struct {
    char riff_id[4];
    uint32_t file_size;
    char wave_id[4];
} WavRiffHeader;

typedef struct {
    char fmt_id[4];
    uint32_t fmt_size;
    uint16_t audio_format;
    uint16_t num_channels;
    uint32_t sample_rate;
    uint32_t byte_rate;
    uint16_t block_align;
    uint16_t bits_per_sample;
} WavFmtChunk;

typedef struct {
    char data_id[4];
    uint32_t data_size;
} WavDataChunk;
#pragma pack(pop)

void wav_export_config_init(WavExportConfig* config) {
    if (!config) return;
    memset(config->filename, 0, sizeof(config->filename));
    config->sample_rate = 44100;
    config->num_channels = 2;
    config->bits_per_sample = 16;
    config->normalize = false;
    config->normalize_target = 0.95f;
}

static int16_t float_to_int16(float sample) {
    if (sample > 1.0f) sample = 1.0f;
    if (sample < -1.0f) sample = -1.0f;
    return (int16_t)(sample * 32767.0f);
}

bool wav_export_stereo(const char* filename, 
                       const float* left, 
                       const float* right, 
                       uint32_t num_samples, 
                       uint32_t sample_rate) {
    if (!filename || !left || !right || num_samples == 0 || sample_rate == 0) {
        return false;
    }
    FILE* file = fopen(filename, "wb");
    if (!file) {
        return false;
    }
    
    uint32_t data_size = num_samples * 2 * sizeof(int16_t);
    uint32_t file_size = sizeof(WavRiffHeader) + sizeof(WavFmtChunk) + sizeof(WavDataChunk) + data_size - 8;
    
    WavRiffHeader riff = {
        .riff_id = {'R', 'I', 'F', 'F'},
        .file_size = file_size,
        .wave_id = {'W', 'A', 'V', 'E'}
    };
    
    WavFmtChunk fmt = {
        .fmt_id = {'f', 'm', 't', ' '},
        .fmt_size = 16,
        .audio_format = 1,
        .num_channels = 2,
        .sample_rate = sample_rate,
        .byte_rate = sample_rate * 2 * sizeof(int16_t),
        .block_align = 2 * sizeof(int16_t),
        .bits_per_sample = 16
    };
    
    WavDataChunk data = {
        .data_id = {'d', 'a', 't', 'a'},
        .data_size = data_size
    };
    
    fwrite(&riff, sizeof(WavRiffHeader), 1, file);
    fwrite(&fmt, sizeof(WavFmtChunk), 1, file);
    fwrite(&data, sizeof(WavDataChunk), 1, file);
    
    int16_t* interleaved = (int16_t*)malloc((size_t)num_samples * 2 * sizeof(int16_t));
    if (!interleaved) {
        fclose(file);
        return false;
    }
    
    for (uint32_t i = 0; i < num_samples; i++) {
        interleaved[i * 2] = float_to_int16(left[i]);
        interleaved[i * 2 + 1] = float_to_int16(right[i]);
    }
    
    fwrite(interleaved, sizeof(int16_t), num_samples * 2, file);
    
    free(interleaved);
    fclose(file);
    return true;
}

bool wav_export_mono(const char* filename, 
                     const float* buffer, 
                     uint32_t num_samples, 
                     uint32_t sample_rate) {
    if (!filename || !buffer || num_samples == 0 || sample_rate == 0) {
        return false;
    }
    FILE* file = fopen(filename, "wb");
    if (!file) {
        return false;
    }
    
    uint32_t data_size = num_samples * sizeof(int16_t);
    uint32_t file_size = sizeof(WavRiffHeader) + sizeof(WavFmtChunk) + sizeof(WavDataChunk) + data_size - 8;
    
    WavRiffHeader riff = {
        .riff_id = {'R', 'I', 'F', 'F'},
        .file_size = file_size,
        .wave_id = {'W', 'A', 'V', 'E'}
    };
    
    WavFmtChunk fmt = {
        .fmt_id = {'f', 'm', 't', ' '},
        .fmt_size = 16,
        .audio_format = 1,
        .num_channels = 1,
        .sample_rate = sample_rate,
        .byte_rate = sample_rate * sizeof(int16_t),
        .block_align = sizeof(int16_t),
        .bits_per_sample = 16
    };
    
    WavDataChunk data = {
        .data_id = {'d', 'a', 't', 'a'},
        .data_size = data_size
    };
    
    fwrite(&riff, sizeof(WavRiffHeader), 1, file);
    fwrite(&fmt, sizeof(WavFmtChunk), 1, file);
    fwrite(&data, sizeof(WavDataChunk), 1, file);
    
    int16_t* samples = (int16_t*)malloc((size_t)num_samples * sizeof(int16_t));
    if (!samples) {
        fclose(file);
        return false;
    }
    
    for (uint32_t i = 0; i < num_samples; i++) {
        samples[i] = float_to_int16(buffer[i]);
    }
    
    fwrite(samples, sizeof(int16_t), num_samples, file);
    
    free(samples);
    fclose(file);
    return true;
}

bool wav_export_with_config(const WavExportConfig* config, 
                            const float* left, 
                            const float* right, 
                            uint32_t num_samples) {
    if (!config || !left || num_samples == 0) return false;
    float* left_copy = NULL;
    float* right_copy = NULL;
    bool result = false;
    
    if (config->normalize) {
        left_copy = (float*)malloc(num_samples * sizeof(float));
        if (!left_copy) return false;
        memcpy(left_copy, left, num_samples * sizeof(float));
        
        if (config->num_channels == 2 && right) {
            right_copy = (float*)malloc(num_samples * sizeof(float));
            if (!right_copy) {
                free(left_copy);
                return false;
            }
            memcpy(right_copy, right, num_samples * sizeof(float));
            wav_normalize_stereo(left_copy, right_copy, num_samples, config->normalize_target);
        } else {
            wav_normalize_buffer(left_copy, num_samples, config->normalize_target);
        }
        
        left = left_copy;
        right = right_copy;
    }
    
    if (config->num_channels == 2 && right) {
        result = wav_export_stereo(config->filename, left, right, num_samples, config->sample_rate);
    } else {
        result = wav_export_mono(config->filename, left, num_samples, config->sample_rate);
    }
    
    if (left_copy) free(left_copy);
    if (right_copy) free(right_copy);
    
    return result;
}

float wav_get_peak(const float* buffer, uint32_t num_samples) {
    if (!buffer || num_samples == 0) return 0.0f;
    float peak = 0.0f;
    for (uint32_t i = 0; i < num_samples; i++) {
        float abs_val = fabsf(buffer[i]);
        if (abs_val > peak) {
            peak = abs_val;
        }
    }
    return peak;
}

float wav_get_peak_stereo(const float* left, const float* right, uint32_t num_samples) {
    if (!left || !right || num_samples == 0) return 0.0f;
    float peak_l = wav_get_peak(left, num_samples);
    float peak_r = wav_get_peak(right, num_samples);
    return peak_l > peak_r ? peak_l : peak_r;
}

void wav_normalize_buffer(float* buffer, uint32_t num_samples, float target) {
    if (!buffer || num_samples == 0) return;
    float peak = wav_get_peak(buffer, num_samples);
    if (peak < 0.0001f) return;
    
    float gain = target / peak;
    for (uint32_t i = 0; i < num_samples; i++) {
        buffer[i] *= gain;
    }
}

void wav_normalize_stereo(float* left, float* right, uint32_t num_samples, float target) {
    if (!left || !right || num_samples == 0) return;
    float peak = wav_get_peak_stereo(left, right, num_samples);
    if (peak < 0.0001f) return;
    
    float gain = target / peak;
    for (uint32_t i = 0; i < num_samples; i++) {
        left[i] *= gain;
        right[i] *= gain;
    }
}
