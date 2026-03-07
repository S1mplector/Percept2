#include "coreaudio_adapter.h"
#include "../../core/audio/audio_utils.h"
#include <AudioToolbox/AudioToolbox.h>
#include <stdlib.h>
#include <string.h>
#include <stdatomic.h>

#define CA_INTERNAL_BUFFER_SIZE 4096

typedef enum {
    CA_STATE_UNINITIALIZED,
    CA_STATE_STOPPED,
    CA_STATE_RUNNING,
    CA_STATE_ERROR
} CoreAudioState;

typedef struct {
    AudioQueueRef queue;
    AudioQueueBufferRef buffers[3];
    int num_buffers;
    
    AudioCallback callback;
    void* user_data;
    
    float sample_rate;
    int buffer_size;
    
    atomic_int state;
    atomic_bool callback_active;
    
    float* temp_left;
    float* temp_right;
    
    atomic_uint_fast64_t frames_processed;
    char error_message[256];
} CoreAudioImpl;

static void ca_audio_queue_callback(void* user_data, AudioQueueRef queue, AudioQueueBufferRef buffer) {
    CoreAudioImpl* impl = (CoreAudioImpl*)user_data;
    if (!impl || !buffer) return;
    
    if (atomic_load(&impl->state) != CA_STATE_RUNNING) {
        memset(buffer->mAudioData, 0, buffer->mAudioDataByteSize);
        AudioQueueEnqueueBuffer(queue, buffer, 0, NULL);
        return;
    }
    if (!impl->temp_left || !impl->temp_right) {
        memset(buffer->mAudioData, 0, buffer->mAudioDataByteSize);
        AudioQueueEnqueueBuffer(queue, buffer, 0, NULL);
        return;
    }
    
    atomic_store(&impl->callback_active, true);
    
    int frames = buffer->mAudioDataByteSize / (2 * sizeof(float));
    float* out = (float*)buffer->mAudioData;
    
    if (impl->callback) {
        int offset = 0;
        int remaining = frames;
        
        while (remaining > 0) {
            int chunk = remaining > CA_INTERNAL_BUFFER_SIZE ? CA_INTERNAL_BUFFER_SIZE : remaining;
            
            impl->callback(impl->temp_left, impl->temp_right, chunk, impl->user_data);
            
            for (int i = 0; i < chunk; i++) {
                out[(offset + i) * 2] = audio_hard_clip(impl->temp_left[i]);
                out[(offset + i) * 2 + 1] = audio_hard_clip(impl->temp_right[i]);
            }
            
            offset += chunk;
            remaining -= chunk;
        }
    } else {
        memset(out, 0, buffer->mAudioDataByteSize);
    }
    
    atomic_fetch_add(&impl->frames_processed, frames);
    atomic_store(&impl->callback_active, false);
    
    AudioQueueEnqueueBuffer(queue, buffer, 0, NULL);
}

static bool ca_initialize(AudioPort* port, float sample_rate, int buffer_size) {
    if (!port) return false;
    CoreAudioImpl* impl = (CoreAudioImpl*)port->impl;
    if (!impl) return false;
    
    if (atomic_load(&impl->state) != CA_STATE_UNINITIALIZED) {
        snprintf(impl->error_message, sizeof(impl->error_message), "Already initialized");
        return false;
    }
    
    impl->sample_rate = sample_rate > 1.0f ? sample_rate : 44100.0f;
    impl->buffer_size = buffer_size > 0 ? buffer_size : 512;
    impl->num_buffers = 3;
    
    impl->temp_left = (float*)calloc(CA_INTERNAL_BUFFER_SIZE, sizeof(float));
    impl->temp_right = (float*)calloc(CA_INTERNAL_BUFFER_SIZE, sizeof(float));
    
    if (!impl->temp_left || !impl->temp_right) {
        snprintf(impl->error_message, sizeof(impl->error_message), "Failed to allocate buffers");
        free(impl->temp_left);
        free(impl->temp_right);
        return false;
    }
    
    AudioStreamBasicDescription format = {0};
    format.mSampleRate = impl->sample_rate;
    format.mFormatID = kAudioFormatLinearPCM;
    format.mFormatFlags = kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked;
    format.mBytesPerPacket = 2 * sizeof(float);
    format.mFramesPerPacket = 1;
    format.mBytesPerFrame = 2 * sizeof(float);
    format.mChannelsPerFrame = 2;
    format.mBitsPerChannel = 32;
    
    OSStatus status = AudioQueueNewOutput(
        &format,
        ca_audio_queue_callback,
        impl,
        NULL,
        kCFRunLoopCommonModes,
        0,
        &impl->queue
    );
    
    if (status != noErr) {
        snprintf(impl->error_message, sizeof(impl->error_message), 
                 "AudioQueueNewOutput failed: %d", (int)status);
        free(impl->temp_left);
        free(impl->temp_right);
        atomic_store(&impl->state, CA_STATE_ERROR);
        return false;
    }
    
    UInt32 buffer_bytes = (UInt32)(impl->buffer_size * 2 * sizeof(float));
    for (int i = 0; i < impl->num_buffers; i++) {
        status = AudioQueueAllocateBuffer(impl->queue, buffer_bytes, &impl->buffers[i]);
        if (status != noErr) {
            snprintf(impl->error_message, sizeof(impl->error_message),
                     "AudioQueueAllocateBuffer failed: %d", (int)status);
            AudioQueueDispose(impl->queue, true);
            free(impl->temp_left);
            free(impl->temp_right);
            atomic_store(&impl->state, CA_STATE_ERROR);
            return false;
        }
        impl->buffers[i]->mAudioDataByteSize = buffer_bytes;
    }
    
    atomic_store(&impl->state, CA_STATE_STOPPED);
    return true;
}

static void ca_shutdown(AudioPort* port) {
    if (!port) return;
    CoreAudioImpl* impl = (CoreAudioImpl*)port->impl;
    if (!impl) return;
    int state = atomic_load(&impl->state);
    
    if (state == CA_STATE_UNINITIALIZED) {
        return;
    }
    
    if (state == CA_STATE_RUNNING) {
        AudioQueueStop(impl->queue, true);
    }
    
    while (atomic_load(&impl->callback_active)) {
        // Spin-wait
    }
    
    AudioQueueDispose(impl->queue, true);
    
    free(impl->temp_left);
    free(impl->temp_right);
    impl->temp_left = NULL;
    impl->temp_right = NULL;
    
    atomic_store(&impl->state, CA_STATE_UNINITIALIZED);
}

static bool ca_start(AudioPort* port) {
    if (!port) return false;
    CoreAudioImpl* impl = (CoreAudioImpl*)port->impl;
    if (!impl) return false;
    
    if (atomic_load(&impl->state) != CA_STATE_STOPPED) {
        return false;
    }
    
    atomic_store(&impl->frames_processed, 0);
    
    for (int i = 0; i < impl->num_buffers; i++) {
        memset(impl->buffers[i]->mAudioData, 0, impl->buffers[i]->mAudioDataByteSize);
        AudioQueueEnqueueBuffer(impl->queue, impl->buffers[i], 0, NULL);
    }
    
    OSStatus status = AudioQueueStart(impl->queue, NULL);
    if (status != noErr) {
        snprintf(impl->error_message, sizeof(impl->error_message),
                 "AudioQueueStart failed: %d", (int)status);
        atomic_store(&impl->state, CA_STATE_ERROR);
        return false;
    }
    
    atomic_store(&impl->state, CA_STATE_RUNNING);
    return true;
}

static void ca_stop(AudioPort* port) {
    if (!port) return;
    CoreAudioImpl* impl = (CoreAudioImpl*)port->impl;
    if (!impl) return;
    
    if (atomic_load(&impl->state) != CA_STATE_RUNNING) {
        return;
    }
    
    AudioQueueStop(impl->queue, true);
    
    while (atomic_load(&impl->callback_active)) {
        // Spin-wait
    }
    
    atomic_store(&impl->state, CA_STATE_STOPPED);
}

static void ca_set_callback(AudioPort* port, AudioCallback callback, void* user_data) {
    if (!port) return;
    CoreAudioImpl* impl = (CoreAudioImpl*)port->impl;
    if (!impl) return;
    impl->callback = callback;
    impl->user_data = user_data;
}

static float ca_get_sample_rate(const AudioPort* port) {
    if (!port) return 0.0f;
    CoreAudioImpl* impl = (CoreAudioImpl*)port->impl;
    if (!impl) return 0.0f;
    return impl->sample_rate;
}

static int ca_get_buffer_size(const AudioPort* port) {
    if (!port) return 0;
    CoreAudioImpl* impl = (CoreAudioImpl*)port->impl;
    if (!impl) return 0;
    return impl->buffer_size;
}

static const char* ca_get_backend_name(const AudioPort* port) {
    (void)port;
    return "Core Audio";
}

AudioPort* coreaudio_adapter_create(void) {
    AudioPort* port = (AudioPort*)calloc(1, sizeof(AudioPort));
    CoreAudioImpl* impl = (CoreAudioImpl*)calloc(1, sizeof(CoreAudioImpl));
    
    if (!port || !impl) {
        free(port);
        free(impl);
        return NULL;
    }
    
    atomic_store(&impl->state, CA_STATE_UNINITIALIZED);
    atomic_store(&impl->callback_active, false);
    
    port->impl = impl;
    port->initialize = ca_initialize;
    port->shutdown = ca_shutdown;
    port->start = ca_start;
    port->stop = ca_stop;
    port->set_callback = ca_set_callback;
    port->get_sample_rate = ca_get_sample_rate;
    port->get_buffer_size = ca_get_buffer_size;
    port->get_backend_name = ca_get_backend_name;
    
    return port;
}

void coreaudio_adapter_destroy(AudioPort* port) {
    if (port) {
        if (port->impl) {
            ca_shutdown(port);
            free(port->impl);
        }
        free(port);
    }
}
