#pragma once

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <stdatomic.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    float* buffer;
    size_t capacity;
    atomic_size_t write_pos;
    atomic_size_t read_pos;
} RingBuffer;

bool ring_buffer_init(RingBuffer* rb, size_t capacity);
void ring_buffer_destroy(RingBuffer* rb);
void ring_buffer_clear(RingBuffer* rb);

size_t ring_buffer_write(RingBuffer* rb, const float* data, size_t count);
size_t ring_buffer_read(RingBuffer* rb, float* data, size_t count);

size_t ring_buffer_available_read(const RingBuffer* rb);
size_t ring_buffer_available_write(const RingBuffer* rb);

bool ring_buffer_is_empty(const RingBuffer* rb);
bool ring_buffer_is_full(const RingBuffer* rb);

#ifdef __cplusplus
}
#endif
