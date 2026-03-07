#include "ring_buffer.h"
#include <stdlib.h>
#include <string.h>

static size_t next_power_of_two(size_t n) {
    n--;
    n |= n >> 1;
    n |= n >> 2;
    n |= n >> 4;
    n |= n >> 8;
    n |= n >> 16;
    n |= n >> 32;
    n++;
    return n;
}

bool ring_buffer_init(RingBuffer* rb, size_t capacity) {
    if (!rb || capacity == 0) {
        return false;
    }
    
    capacity = next_power_of_two(capacity);
    
    rb->buffer = (float*)calloc(capacity, sizeof(float));
    if (!rb->buffer) {
        return false;
    }
    
    rb->capacity = capacity;
    atomic_store(&rb->write_pos, 0);
    atomic_store(&rb->read_pos, 0);
    
    return true;
}

void ring_buffer_destroy(RingBuffer* rb) {
    if (rb && rb->buffer) {
        free(rb->buffer);
        rb->buffer = NULL;
        rb->capacity = 0;
    }
}

void ring_buffer_clear(RingBuffer* rb) {
    if (rb) {
        atomic_store(&rb->write_pos, 0);
        atomic_store(&rb->read_pos, 0);
    }
}

size_t ring_buffer_available_read(const RingBuffer* rb) {
    if (!rb || rb->capacity == 0) return 0;
    size_t write_pos = atomic_load(&rb->write_pos);
    size_t read_pos = atomic_load(&rb->read_pos);
    return write_pos - read_pos;
}

size_t ring_buffer_available_write(const RingBuffer* rb) {
    if (!rb || rb->capacity == 0) return 0;
    return rb->capacity - ring_buffer_available_read(rb);
}

bool ring_buffer_is_empty(const RingBuffer* rb) {
    return ring_buffer_available_read(rb) == 0;
}

bool ring_buffer_is_full(const RingBuffer* rb) {
    if (!rb || rb->capacity == 0) return true;
    return ring_buffer_available_read(rb) >= rb->capacity;
}

size_t ring_buffer_write(RingBuffer* rb, const float* data, size_t count) {
    if (!rb || !rb->buffer || !data || rb->capacity == 0) return 0;
    size_t available = ring_buffer_available_write(rb);
    if (count > available) {
        count = available;
    }
    
    if (count == 0) {
        return 0;
    }
    
    size_t write_pos = atomic_load(&rb->write_pos);
    size_t mask = rb->capacity - 1;
    
    for (size_t i = 0; i < count; i++) {
        rb->buffer[(write_pos + i) & mask] = data[i];
    }
    
    atomic_fetch_add(&rb->write_pos, count);
    return count;
}

size_t ring_buffer_read(RingBuffer* rb, float* data, size_t count) {
    if (!rb || !rb->buffer || !data || rb->capacity == 0) return 0;
    size_t available = ring_buffer_available_read(rb);
    if (count > available) {
        count = available;
    }
    
    if (count == 0) {
        return 0;
    }
    
    size_t read_pos = atomic_load(&rb->read_pos);
    size_t mask = rb->capacity - 1;
    
    for (size_t i = 0; i < count; i++) {
        data[i] = rb->buffer[(read_pos + i) & mask];
    }
    
    atomic_fetch_add(&rb->read_pos, count);
    return count;
}
