#pragma once

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void (*AudioCallback)(float* left, float* right, int num_samples, void* user_data);

typedef struct AudioPort AudioPort;

struct AudioPort {
    bool (*initialize)(AudioPort* port, float sample_rate, int buffer_size);
    void (*shutdown)(AudioPort* port);
    bool (*start)(AudioPort* port);
    void (*stop)(AudioPort* port);
    void (*set_callback)(AudioPort* port, AudioCallback callback, void* user_data);
    float (*get_sample_rate)(const AudioPort* port);
    int (*get_buffer_size)(const AudioPort* port);
    const char* (*get_backend_name)(const AudioPort* port);
    bool (*is_running)(const AudioPort* port);
    const char* (*get_error)(const AudioPort* port);
    void (*get_stats)(const AudioPort* port, uint64_t* frames, uint64_t* callbacks,
                      float* peak_l, float* peak_r);
    void* impl;
};

#ifdef __cplusplus
}
#endif
