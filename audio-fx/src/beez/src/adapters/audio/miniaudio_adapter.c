#include "miniaudio_adapter.h"
#include "../../core/audio/audio_utils.h"
#include <stdlib.h>
#include <string.h>
#include <stdatomic.h>
#include <stdio.h>

#define MINIAUDIO_IMPLEMENTATION
#include "../../external/miniaudio.h"

#define MA_INTERNAL_BUFFER_SIZE 8192
#define MA_MAX_LATENCY_MS 100

typedef enum {
    AUDIO_STATE_UNINITIALIZED,
    AUDIO_STATE_STOPPED,
    AUDIO_STATE_STARTING,
    AUDIO_STATE_RUNNING,
    AUDIO_STATE_STOPPING,
    AUDIO_STATE_ERROR
} AudioState;

typedef struct {
    ma_device device;
    ma_device_config config;
    AudioCallback callback;
    void* user_data;
    float sample_rate;
    float actual_sample_rate;
    int buffer_size;
    int actual_buffer_size;
    
    atomic_int state;
    atomic_bool callback_active;
    
    float* temp_left;
    float* temp_right;
    
    atomic_uint_fast64_t frames_processed;
    atomic_uint_fast64_t callback_count;
    atomic_uint_fast64_t underrun_count;
    atomic_uint_fast64_t overrun_count;
    
    float peak_left;
    float peak_right;
    float cpu_load;
    
    char error_message[256];
} MiniaudioImpl;

static void ma_data_callback(ma_device* device, void* output, const void* input, ma_uint32 frame_count) {
    (void)input;
    if (!device || !output) return;
    MiniaudioImpl* impl = (MiniaudioImpl*)device->pUserData;
    if (!impl) {
        memset(output, 0, frame_count * 2 * sizeof(float));
        return;
    }
    
    int state = atomic_load(&impl->state);
    if (state != AUDIO_STATE_RUNNING) {
        memset(output, 0, frame_count * 2 * sizeof(float));
        return;
    }
    if (!impl->temp_left || !impl->temp_right) {
        memset(output, 0, frame_count * 2 * sizeof(float));
        return;
    }
    
    atomic_store(&impl->callback_active, true);
    atomic_fetch_add(&impl->callback_count, 1);
    
    float* out = (float*)output;
    ma_uint32 frames_remaining = frame_count;
    ma_uint32 offset = 0;
    
    while (frames_remaining > 0) {
        ma_uint32 chunk = frames_remaining > MA_INTERNAL_BUFFER_SIZE 
                         ? MA_INTERNAL_BUFFER_SIZE : frames_remaining;
        
        if (impl->callback) {
            impl->callback(impl->temp_left, impl->temp_right, (int)chunk, impl->user_data);
            
            float peak_l = 0.0f, peak_r = 0.0f;
            for (ma_uint32 i = 0; i < chunk; i++) {
                float l = audio_hard_clip(impl->temp_left[i]);
                float r = audio_hard_clip(impl->temp_right[i]);
                
                out[(offset + i) * 2] = l;
                out[(offset + i) * 2 + 1] = r;
                
                float abs_l = l > 0 ? l : -l;
                float abs_r = r > 0 ? r : -r;
                if (abs_l > peak_l) peak_l = abs_l;
                if (abs_r > peak_r) peak_r = abs_r;
            }
            impl->peak_left = peak_l;
            impl->peak_right = peak_r;
        } else {
            memset(out + offset * 2, 0, chunk * 2 * sizeof(float));
        }
        
        frames_remaining -= chunk;
        offset += chunk;
    }
    
    atomic_fetch_add(&impl->frames_processed, frame_count);
    atomic_store(&impl->callback_active, false);
}

static void ma_set_error(MiniaudioImpl* impl, const char* msg) {
    if (!impl) return;
    snprintf(impl->error_message, sizeof(impl->error_message), "%s", msg ? msg : "Unknown error");
    atomic_store(&impl->state, AUDIO_STATE_ERROR);
}

static bool ma_initialize(AudioPort* port, float sample_rate, int buffer_size) {
    if (!port) return false;
    MiniaudioImpl* impl = (MiniaudioImpl*)port->impl;
    if (!impl) return false;
    
    if (atomic_load(&impl->state) != AUDIO_STATE_UNINITIALIZED) {
        ma_set_error(impl, "Already initialized");
        return false;
    }
    
    impl->temp_left = (float*)calloc(MA_INTERNAL_BUFFER_SIZE, sizeof(float));
    impl->temp_right = (float*)calloc(MA_INTERNAL_BUFFER_SIZE, sizeof(float));
    
    if (!impl->temp_left || !impl->temp_right) {
        ma_set_error(impl, "Failed to allocate internal buffers");
        free(impl->temp_left);
        free(impl->temp_right);
        impl->temp_left = NULL;
        impl->temp_right = NULL;
        return false;
    }
    
    impl->sample_rate = sample_rate > 1.0f ? sample_rate : 44100.0f;
    impl->buffer_size = buffer_size > 0 ? buffer_size : 512;
    
    impl->config = ma_device_config_init(ma_device_type_playback);
    impl->config.playback.format = ma_format_f32;
    impl->config.playback.channels = 2;
    impl->config.sampleRate = (ma_uint32)impl->sample_rate;
    impl->config.periodSizeInFrames = (ma_uint32)impl->buffer_size;
    impl->config.periods = 2;
    impl->config.performanceProfile = ma_performance_profile_low_latency;
    impl->config.dataCallback = ma_data_callback;
    impl->config.pUserData = impl;
    
    ma_result result = ma_device_init(NULL, &impl->config, &impl->device);
    if (result != MA_SUCCESS) {
        snprintf(impl->error_message, sizeof(impl->error_message), 
                 "ma_device_init failed: %d", result);
        atomic_store(&impl->state, AUDIO_STATE_ERROR);
        free(impl->temp_left);
        free(impl->temp_right);
        impl->temp_left = NULL;
        impl->temp_right = NULL;
        return false;
    }
    
    impl->actual_sample_rate = (float)impl->device.sampleRate;
    impl->actual_buffer_size = (int)impl->device.playback.internalPeriodSizeInFrames;
    
    atomic_store(&impl->state, AUDIO_STATE_STOPPED);
    return true;
}

static void ma_shutdown(AudioPort* port) {
    if (!port) return;
    MiniaudioImpl* impl = (MiniaudioImpl*)port->impl;
    if (!impl) return;
    int state = atomic_load(&impl->state);
    
    if (state == AUDIO_STATE_UNINITIALIZED) {
        return;
    }
    
    if (state == AUDIO_STATE_RUNNING || state == AUDIO_STATE_STARTING) {
        ma_device_stop(&impl->device);
    }
    
    while (atomic_load(&impl->callback_active)) {
        // Spin-wait for callback to complete
    }
    
    ma_device_uninit(&impl->device);
    
    free(impl->temp_left);
    free(impl->temp_right);
    impl->temp_left = NULL;
    impl->temp_right = NULL;
    
    atomic_store(&impl->state, AUDIO_STATE_UNINITIALIZED);
}

static bool ma_start(AudioPort* port) {
    if (!port) return false;
    MiniaudioImpl* impl = (MiniaudioImpl*)port->impl;
    if (!impl) return false;
    int state = atomic_load(&impl->state);
    
    if (state != AUDIO_STATE_STOPPED) {
        return false;
    }
    
    atomic_store(&impl->state, AUDIO_STATE_STARTING);
    atomic_store(&impl->frames_processed, 0);
    atomic_store(&impl->callback_count, 0);
    atomic_store(&impl->underrun_count, 0);
    atomic_store(&impl->overrun_count, 0);
    
    ma_result result = ma_device_start(&impl->device);
    if (result != MA_SUCCESS) {
        snprintf(impl->error_message, sizeof(impl->error_message),
                 "ma_device_start failed: %d", result);
        atomic_store(&impl->state, AUDIO_STATE_ERROR);
        return false;
    }
    
    atomic_store(&impl->state, AUDIO_STATE_RUNNING);
    return true;
}

static void ma_stop(AudioPort* port) {
    if (!port) return;
    MiniaudioImpl* impl = (MiniaudioImpl*)port->impl;
    if (!impl) return;
    int state = atomic_load(&impl->state);
    
    if (state != AUDIO_STATE_RUNNING && state != AUDIO_STATE_STARTING) {
        return;
    }
    
    atomic_store(&impl->state, AUDIO_STATE_STOPPING);
    ma_device_stop(&impl->device);
    
    while (atomic_load(&impl->callback_active)) {
        // Spin-wait for callback to complete
    }
    
    atomic_store(&impl->state, AUDIO_STATE_STOPPED);
}

static void ma_set_callback(AudioPort* port, AudioCallback callback, void* user_data) {
    if (!port) return;
    MiniaudioImpl* impl = (MiniaudioImpl*)port->impl;
    if (!impl) return;
    impl->callback = callback;
    impl->user_data = user_data;
}

static float ma_get_sample_rate(const AudioPort* port) {
    if (!port) return 0.0f;
    MiniaudioImpl* impl = (MiniaudioImpl*)port->impl;
    if (!impl) return 0.0f;
    return impl->sample_rate;
}

static int ma_get_buffer_size(const AudioPort* port) {
    if (!port) return 0;
    MiniaudioImpl* impl = (MiniaudioImpl*)port->impl;
    if (!impl) return 0;
    return impl->buffer_size;
}

static const char* beez_get_backend_name(const AudioPort* port) {
    if (!port) return "miniaudio (no port)";
    MiniaudioImpl* impl = (MiniaudioImpl*)port->impl;
    if (!impl) return "miniaudio (no impl)";
    int state = atomic_load(&impl->state);
    
    if (state == AUDIO_STATE_UNINITIALIZED) {
        return "miniaudio (not initialized)";
    }
    
    if (!impl->device.pContext) {
        return "miniaudio (no context)";
    }
    return ma_get_backend_name(impl->device.pContext->backend);
}

static bool ma_is_running(const AudioPort* port) {
    if (!port) return false;
    MiniaudioImpl* impl = (MiniaudioImpl*)port->impl;
    if (!impl) return false;
    return atomic_load(&impl->state) == AUDIO_STATE_RUNNING;
}

static const char* ma_get_error(const AudioPort* port) {
    if (!port) return "No port";
    MiniaudioImpl* impl = (MiniaudioImpl*)port->impl;
    if (!impl) return "No impl";
    return impl->error_message;
}

static void ma_get_stats(const AudioPort* port, uint64_t* frames, uint64_t* callbacks,
                         float* peak_l, float* peak_r) {
    if (!port) return;
    MiniaudioImpl* impl = (MiniaudioImpl*)port->impl;
    if (!impl) return;
    if (frames) *frames = atomic_load(&impl->frames_processed);
    if (callbacks) *callbacks = atomic_load(&impl->callback_count);
    if (peak_l) *peak_l = impl->peak_left;
    if (peak_r) *peak_r = impl->peak_right;
}

AudioPort* miniaudio_adapter_create(void) {
    AudioPort* port = (AudioPort*)calloc(1, sizeof(AudioPort));
    MiniaudioImpl* impl = (MiniaudioImpl*)calloc(1, sizeof(MiniaudioImpl));
    
    if (!port || !impl) {
        free(port);
        free(impl);
        return NULL;
    }
    
    atomic_store(&impl->state, AUDIO_STATE_UNINITIALIZED);
    atomic_store(&impl->callback_active, false);
    
    port->impl = impl;
    port->initialize = ma_initialize;
    port->shutdown = ma_shutdown;
    port->start = ma_start;
    port->stop = ma_stop;
    port->set_callback = ma_set_callback;
    port->get_sample_rate = ma_get_sample_rate;
    port->get_buffer_size = ma_get_buffer_size;
    port->get_backend_name = beez_get_backend_name;
    
    return port;
}

void miniaudio_adapter_destroy(AudioPort* port) {
    if (port) {
        if (port->impl) {
            ma_shutdown(port);
            free(port->impl);
        }
        free(port);
    }
}
