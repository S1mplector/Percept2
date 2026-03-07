#include "test_framework.h"
#include "../src/core/engine/synth_engine.h"

#define SAMPLE_RATE 44100.0f

static void test_synth_engine_init(void) {
    TEST_CASE("synth_engine_init");
    
    SynthEngine engine;
    synth_engine_init(&engine, SAMPLE_RATE);
    
    ASSERT_FLOAT_EQ(SAMPLE_RATE, engine.sample_rate);
    ASSERT_FLOAT_EQ(0.5f, engine.master_volume);
    ASSERT_EQ(0, engine.samples_generated);
    
    TEST_PASS();
}

static void test_synth_engine_note_on(void) {
    TEST_CASE("synth_engine_note_on");
    
    SynthEngine engine;
    synth_engine_init(&engine, SAMPLE_RATE);
    
    synth_engine_note_on(&engine, 0, 69, 1.0f);
    
    ASSERT_EQ(69, engine.channels[0].note);
    ASSERT_TRUE(envelope_is_active(&engine.channels[0].env));
    
    TEST_PASS();
}

static void test_synth_engine_note_off(void) {
    TEST_CASE("synth_engine_note_off");
    
    SynthEngine engine;
    synth_engine_init(&engine, SAMPLE_RATE);
    
    synth_engine_note_on(&engine, 0, 69, 1.0f);
    
    float left[256], right[256];
    synth_engine_generate_stereo(&engine, left, right, 256);
    
    synth_engine_note_off(&engine, 0);
    
    ASSERT_EQ(ENV_RELEASE, engine.channels[0].env.stage);
    
    TEST_PASS();
}

static void test_synth_engine_generates_stereo(void) {
    TEST_CASE("synth_engine generates stereo output");
    
    SynthEngine engine;
    synth_engine_init(&engine, SAMPLE_RATE);
    
    synth_engine_note_on(&engine, 0, 69, 1.0f);
    
    float left[512], right[512];
    synth_engine_generate_stereo(&engine, left, right, 512);
    
    bool has_left = false, has_right = false;
    for (int i = 0; i < 512; i++) {
        if (fabsf(left[i]) > 0.001f) has_left = true;
        if (fabsf(right[i]) > 0.001f) has_right = true;
    }
    
    ASSERT_TRUE(has_left);
    ASSERT_TRUE(has_right);
    
    TEST_PASS();
}

static void test_synth_engine_multiple_channels(void) {
    TEST_CASE("multiple channels mix together");
    
    SynthEngine engine;
    synth_engine_init(&engine, SAMPLE_RATE);
    
    synth_engine_note_on(&engine, 0, 60, 0.5f);
    synth_engine_note_on(&engine, 1, 64, 0.5f);
    synth_engine_note_on(&engine, 2, 67, 0.5f);
    
    float left[256], right[256];
    synth_engine_generate_stereo(&engine, left, right, 256);
    
    float peak = 0.0f;
    for (int i = 0; i < 256; i++) {
        float abs_l = fabsf(left[i]);
        float abs_r = fabsf(right[i]);
        if (abs_l > peak) peak = abs_l;
        if (abs_r > peak) peak = abs_r;
    }
    
    ASSERT_TRUE(peak > 0.1f);
    
    TEST_PASS();
}

static void test_synth_engine_master_volume(void) {
    TEST_CASE("master volume affects output");
    
    SynthEngine engine;
    synth_engine_init(&engine, SAMPLE_RATE);
    
    synth_engine_note_on(&engine, 0, 69, 1.0f);
    
    synth_engine_set_master_volume(&engine, 1.0f);
    float left1[256], right1[256];
    synth_engine_generate_stereo(&engine, left1, right1, 256);
    
    synth_engine_reset(&engine);
    synth_engine_note_on(&engine, 0, 69, 1.0f);
    
    synth_engine_set_master_volume(&engine, 0.25f);
    float left2[256], right2[256];
    synth_engine_generate_stereo(&engine, left2, right2, 256);
    
    float sum1 = 0.0f, sum2 = 0.0f;
    for (int i = 0; i < 256; i++) {
        sum1 += fabsf(left1[i]);
        sum2 += fabsf(left2[i]);
    }
    
    ASSERT_TRUE(sum1 > sum2 * 2.0f);
    
    TEST_PASS();
}

static void test_synth_engine_set_waveform(void) {
    TEST_CASE("synth_engine_set_channel_waveform");
    
    SynthEngine engine;
    synth_engine_init(&engine, SAMPLE_RATE);
    
    synth_engine_set_channel_waveform(&engine, 0, OSC_TRIANGLE);
    
    ASSERT_EQ(OSC_TRIANGLE, engine.channels[0].osc.waveform);
    
    TEST_PASS();
}

static void test_synth_engine_set_adsr(void) {
    TEST_CASE("synth_engine_set_channel_adsr");
    
    SynthEngine engine;
    synth_engine_init(&engine, SAMPLE_RATE);
    
    synth_engine_set_channel_adsr(&engine, 0, 0.05f, 0.1f, 0.6f, 0.3f);
    
    ASSERT_FLOAT_EQ(0.05f, engine.channels[0].env.attack_time);
    ASSERT_FLOAT_EQ(0.1f, engine.channels[0].env.decay_time);
    ASSERT_FLOAT_EQ(0.6f, engine.channels[0].env.sustain_level);
    ASSERT_FLOAT_EQ(0.3f, engine.channels[0].env.release_time);
    
    TEST_PASS();
}

static void test_synth_engine_samples_generated(void) {
    TEST_CASE("samples_generated counter");
    
    SynthEngine engine;
    synth_engine_init(&engine, SAMPLE_RATE);
    
    synth_engine_note_on(&engine, 0, 69, 1.0f);
    
    float left[512], right[512];
    synth_engine_generate_stereo(&engine, left, right, 512);
    
    ASSERT_EQ(512, engine.samples_generated);
    
    synth_engine_generate_stereo(&engine, left, right, 256);
    
    ASSERT_EQ(768, engine.samples_generated);
    
    TEST_PASS();
}

static void test_synth_engine_reset(void) {
    TEST_CASE("synth_engine_reset");
    
    SynthEngine engine;
    synth_engine_init(&engine, SAMPLE_RATE);
    
    synth_engine_note_on(&engine, 0, 69, 1.0f);
    synth_engine_note_on(&engine, 1, 72, 1.0f);
    
    float left[256], right[256];
    synth_engine_generate_stereo(&engine, left, right, 256);
    
    synth_engine_reset(&engine);
    
    ASSERT_EQ(0, engine.samples_generated);
    ASSERT_EQ(-1, engine.channels[0].note);
    ASSERT_EQ(-1, engine.channels[1].note);
    
    TEST_PASS();
}

static void test_synth_engine_channel_bounds(void) {
    TEST_CASE("channel bounds checking");
    
    SynthEngine engine;
    synth_engine_init(&engine, SAMPLE_RATE);
    
    synth_engine_note_on(&engine, -1, 69, 1.0f);
    synth_engine_note_on(&engine, 100, 69, 1.0f);
    
    synth_engine_note_off(&engine, -1);
    synth_engine_note_off(&engine, 100);
    
    TEST_PASS();
}

void run_synth_engine_tests(void) {
    TEST_SUITE("Synth Engine Tests");
    
    test_synth_engine_init();
    test_synth_engine_note_on();
    test_synth_engine_note_off();
    test_synth_engine_generates_stereo();
    test_synth_engine_multiple_channels();
    test_synth_engine_master_volume();
    test_synth_engine_set_waveform();
    test_synth_engine_set_adsr();
    test_synth_engine_samples_generated();
    test_synth_engine_reset();
    test_synth_engine_channel_bounds();
}
