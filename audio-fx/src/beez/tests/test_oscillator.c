#include "test_framework.h"
#include "../src/core/synthesis/oscillator.h"

#define SAMPLE_RATE 44100.0f

static void test_oscillator_init(void) {
    TEST_CASE("oscillator_init");
    
    Oscillator osc;
    oscillator_init(&osc, OSC_SQUARE);
    
    ASSERT_EQ(OSC_SQUARE, osc.waveform);
    ASSERT_FLOAT_EQ(440.0f, osc.frequency);
    ASSERT_FLOAT_EQ(0.0f, osc.phase);
    ASSERT_FLOAT_EQ(0.5f, osc.duty_cycle);
    
    TEST_PASS();
}

static void test_oscillator_set_frequency(void) {
    TEST_CASE("oscillator_set_frequency");
    
    Oscillator osc;
    oscillator_init(&osc, OSC_SQUARE);
    
    oscillator_set_frequency(&osc, 880.0f);
    ASSERT_FLOAT_EQ(880.0f, osc.frequency);
    
    oscillator_set_frequency(&osc, 20.0f);
    ASSERT_FLOAT_EQ(20.0f, osc.frequency);
    
    TEST_PASS();
}

static void test_square_wave_output(void) {
    TEST_CASE("square wave output range");
    
    Oscillator osc;
    oscillator_init(&osc, OSC_SQUARE);
    oscillator_set_frequency(&osc, 440.0f);
    
    int samples = 1000;
    int positive_count = 0;
    int negative_count = 0;
    
    for (int i = 0; i < samples; i++) {
        float sample = oscillator_generate(&osc, SAMPLE_RATE);
        
        ASSERT_IN_RANGE(sample, -1.0f, 1.0f);
        
        if (sample > 0.5f) positive_count++;
        else if (sample < -0.5f) negative_count++;
    }
    
    ASSERT_TRUE(positive_count > 0);
    ASSERT_TRUE(negative_count > 0);
    
    TEST_PASS();
}

static void test_pulse_25_duty_cycle(void) {
    TEST_CASE("pulse 25% duty cycle");
    
    Oscillator osc;
    oscillator_init(&osc, OSC_PULSE_25);
    oscillator_set_frequency(&osc, 100.0f);
    
    int samples_per_cycle = (int)(SAMPLE_RATE / 100.0f);
    int high_count = 0;
    
    for (int i = 0; i < samples_per_cycle; i++) {
        float sample = oscillator_generate(&osc, SAMPLE_RATE);
        if (sample > 0.0f) high_count++;
    }
    
    float ratio = (float)high_count / (float)samples_per_cycle;
    ASSERT_FLOAT_NEAR(0.25f, ratio, 0.05f);
    
    TEST_PASS();
}

static void test_triangle_wave_continuity(void) {
    TEST_CASE("triangle wave continuity");
    
    Oscillator osc;
    oscillator_init(&osc, OSC_TRIANGLE);
    oscillator_set_frequency(&osc, 440.0f);
    
    float prev_sample = oscillator_generate(&osc, SAMPLE_RATE);
    
    for (int i = 0; i < 1000; i++) {
        float sample = oscillator_generate(&osc, SAMPLE_RATE);
        float diff = fabsf(sample - prev_sample);
        
        ASSERT_TRUE(diff < 0.1f);
        
        prev_sample = sample;
    }
    
    TEST_PASS();
}

static void test_sawtooth_wave_range(void) {
    TEST_CASE("sawtooth wave range");
    
    Oscillator osc;
    oscillator_init(&osc, OSC_SAWTOOTH);
    oscillator_set_frequency(&osc, 440.0f);
    
    float min_sample = 1.0f;
    float max_sample = -1.0f;
    
    int samples_per_cycle = (int)(SAMPLE_RATE / 440.0f);
    for (int i = 0; i < samples_per_cycle * 2; i++) {
        float sample = oscillator_generate(&osc, SAMPLE_RATE);
        if (sample < min_sample) min_sample = sample;
        if (sample > max_sample) max_sample = sample;
    }
    
    ASSERT_FLOAT_NEAR(-1.0f, min_sample, 0.1f);
    ASSERT_FLOAT_NEAR(1.0f, max_sample, 0.1f);
    
    TEST_PASS();
}

static void test_noise_generator(void) {
    TEST_CASE("noise generator randomness");
    
    Oscillator osc;
    oscillator_init(&osc, OSC_NOISE_WHITE);
    
    float prev_sample = oscillator_generate(&osc, SAMPLE_RATE);
    int change_count = 0;
    
    for (int i = 0; i < 100; i++) {
        float sample = oscillator_generate(&osc, SAMPLE_RATE);
        if (sample != prev_sample) change_count++;
        prev_sample = sample;
    }
    
    ASSERT_TRUE(change_count > 10);
    
    TEST_PASS();
}

static void test_oscillator_reset(void) {
    TEST_CASE("oscillator_reset");
    
    Oscillator osc;
    oscillator_init(&osc, OSC_SQUARE);
    
    for (int i = 0; i < 100; i++) {
        oscillator_generate(&osc, SAMPLE_RATE);
    }
    
    ASSERT_TRUE(osc.phase > 0.0f);
    
    oscillator_reset(&osc);
    
    ASSERT_FLOAT_EQ(0.0f, osc.phase);
    
    TEST_PASS();
}

static void test_phase_wrap(void) {
    TEST_CASE("phase wrapping");
    
    Oscillator osc;
    oscillator_init(&osc, OSC_SQUARE);
    oscillator_set_frequency(&osc, 44100.0f);
    
    for (int i = 0; i < 100; i++) {
        oscillator_generate(&osc, SAMPLE_RATE);
        ASSERT_IN_RANGE(osc.phase, 0.0f, 1.0f);
    }
    
    TEST_PASS();
}

void run_oscillator_tests(void) {
    TEST_SUITE("Oscillator Tests");
    
    test_oscillator_init();
    test_oscillator_set_frequency();
    test_square_wave_output();
    test_pulse_25_duty_cycle();
    test_triangle_wave_continuity();
    test_sawtooth_wave_range();
    test_noise_generator();
    test_oscillator_reset();
    test_phase_wrap();
}
