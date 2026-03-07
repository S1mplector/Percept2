#include "test_framework.h"
#include "../src/core/audio/ring_buffer.h"

static void test_ring_buffer_init(void) {
    TEST_CASE("ring_buffer_init");
    
    RingBuffer rb;
    bool result = ring_buffer_init(&rb, 1024);
    
    ASSERT_TRUE(result);
    ASSERT_NOT_NULL(rb.buffer);
    ASSERT_EQ(1024, rb.capacity);
    ASSERT_TRUE(ring_buffer_is_empty(&rb));
    ASSERT_FALSE(ring_buffer_is_full(&rb));
    
    ring_buffer_destroy(&rb);
    
    TEST_PASS();
}

static void test_ring_buffer_power_of_two(void) {
    TEST_CASE("capacity rounds to power of two");
    
    RingBuffer rb;
    ring_buffer_init(&rb, 1000);
    
    ASSERT_EQ(1024, rb.capacity);
    
    ring_buffer_destroy(&rb);
    
    TEST_PASS();
}

static void test_ring_buffer_write_read(void) {
    TEST_CASE("basic write and read");
    
    RingBuffer rb;
    ring_buffer_init(&rb, 256);
    
    float write_data[64];
    for (int i = 0; i < 64; i++) {
        write_data[i] = (float)i;
    }
    
    size_t written = ring_buffer_write(&rb, write_data, 64);
    ASSERT_EQ(64, written);
    ASSERT_EQ(64, ring_buffer_available_read(&rb));
    
    float read_data[64];
    size_t read = ring_buffer_read(&rb, read_data, 64);
    ASSERT_EQ(64, read);
    
    for (int i = 0; i < 64; i++) {
        ASSERT_FLOAT_EQ((float)i, read_data[i]);
    }
    
    ASSERT_TRUE(ring_buffer_is_empty(&rb));
    
    ring_buffer_destroy(&rb);
    
    TEST_PASS();
}

static void test_ring_buffer_wrap_around(void) {
    TEST_CASE("wrap around behavior");
    
    RingBuffer rb;
    ring_buffer_init(&rb, 64);
    
    float data[32];
    for (int i = 0; i < 32; i++) data[i] = (float)i;
    
    ring_buffer_write(&rb, data, 32);
    ring_buffer_read(&rb, data, 32);
    
    for (int i = 0; i < 32; i++) data[i] = (float)(i + 100);
    ring_buffer_write(&rb, data, 32);
    
    for (int i = 0; i < 32; i++) data[i] = (float)(i + 200);
    ring_buffer_write(&rb, data, 32);
    
    float read[64];
    ring_buffer_read(&rb, read, 64);
    
    for (int i = 0; i < 32; i++) {
        ASSERT_FLOAT_EQ((float)(i + 100), read[i]);
    }
    for (int i = 0; i < 32; i++) {
        ASSERT_FLOAT_EQ((float)(i + 200), read[32 + i]);
    }
    
    ring_buffer_destroy(&rb);
    
    TEST_PASS();
}

static void test_ring_buffer_full(void) {
    TEST_CASE("full buffer behavior");
    
    RingBuffer rb;
    ring_buffer_init(&rb, 64);
    
    float data[64];
    for (int i = 0; i < 64; i++) data[i] = (float)i;
    
    size_t written = ring_buffer_write(&rb, data, 64);
    ASSERT_EQ(64, written);
    ASSERT_TRUE(ring_buffer_is_full(&rb));
    
    written = ring_buffer_write(&rb, data, 10);
    ASSERT_EQ(0, written);
    
    ring_buffer_destroy(&rb);
    
    TEST_PASS();
}

static void test_ring_buffer_empty_read(void) {
    TEST_CASE("empty buffer read");
    
    RingBuffer rb;
    ring_buffer_init(&rb, 64);
    
    float data[32];
    size_t read = ring_buffer_read(&rb, data, 32);
    
    ASSERT_EQ(0, read);
    
    ring_buffer_destroy(&rb);
    
    TEST_PASS();
}

static void test_ring_buffer_partial_read(void) {
    TEST_CASE("partial read when less data available");
    
    RingBuffer rb;
    ring_buffer_init(&rb, 64);
    
    float write_data[16];
    for (int i = 0; i < 16; i++) write_data[i] = (float)i;
    ring_buffer_write(&rb, write_data, 16);
    
    float read_data[32];
    size_t read = ring_buffer_read(&rb, read_data, 32);
    
    ASSERT_EQ(16, read);
    
    ring_buffer_destroy(&rb);
    
    TEST_PASS();
}

static void test_ring_buffer_clear(void) {
    TEST_CASE("ring_buffer_clear");
    
    RingBuffer rb;
    ring_buffer_init(&rb, 64);
    
    float data[32];
    ring_buffer_write(&rb, data, 32);
    
    ASSERT_FALSE(ring_buffer_is_empty(&rb));
    
    ring_buffer_clear(&rb);
    
    ASSERT_TRUE(ring_buffer_is_empty(&rb));
    ASSERT_EQ(0, ring_buffer_available_read(&rb));
    
    ring_buffer_destroy(&rb);
    
    TEST_PASS();
}

static void test_ring_buffer_available_write(void) {
    TEST_CASE("available_write calculation");
    
    RingBuffer rb;
    ring_buffer_init(&rb, 64);
    
    ASSERT_EQ(64, ring_buffer_available_write(&rb));
    
    float data[20];
    ring_buffer_write(&rb, data, 20);
    
    ASSERT_EQ(44, ring_buffer_available_write(&rb));
    
    ring_buffer_destroy(&rb);
    
    TEST_PASS();
}

static void test_ring_buffer_null_handling(void) {
    TEST_CASE("null parameter handling");
    
    bool result = ring_buffer_init(NULL, 64);
    ASSERT_FALSE(result);
    
    RingBuffer rb;
    result = ring_buffer_init(&rb, 0);
    ASSERT_FALSE(result);
    
    TEST_PASS();
}

void run_ring_buffer_tests(void) {
    TEST_SUITE("Ring Buffer Tests");
    
    test_ring_buffer_init();
    test_ring_buffer_power_of_two();
    test_ring_buffer_write_read();
    test_ring_buffer_wrap_around();
    test_ring_buffer_full();
    test_ring_buffer_empty_read();
    test_ring_buffer_partial_read();
    test_ring_buffer_clear();
    test_ring_buffer_available_write();
    test_ring_buffer_null_handling();
}
