#include "test_framework.h"
#include "../src/core/synthesis/channel.h"

#define SAMPLE_RATE 44100.0f

static void test_channel_init(void) {
    TEST_CASE("channel_init");
    
    Channel ch;
    channel_init(&ch, OSC_SQUARE);
    
    ASSERT_EQ(OSC_SQUARE, ch.osc.waveform);
    ASSERT_FLOAT_EQ(0.8f, ch.volume);
    ASSERT_FLOAT_EQ(0.0f, ch.pan);
    ASSERT_TRUE(ch.enabled);
    ASSERT_EQ(-1, ch.note);
    
    TEST_PASS();
}

static void test_note_to_frequency(void) {
    TEST_CASE("note_to_frequency");
    
    ASSERT_FLOAT_NEAR(440.0f, note_to_frequency(69), 0.1f);
    ASSERT_FLOAT_NEAR(261.63f, note_to_frequency(60), 1.0f);
    ASSERT_FLOAT_NEAR(880.0f, note_to_frequency(81), 0.1f);
    ASSERT_FLOAT_NEAR(220.0f, note_to_frequency(57), 0.1f);
    
    TEST_PASS();
}

static void test_channel_note_on(void) {
    TEST_CASE("channel_note_on");
    
    Channel ch;
    channel_init(&ch, OSC_SQUARE);
    
    channel_note_on(&ch, 69, 0.8f);
    
    ASSERT_EQ(69, ch.note);
    ASSERT_FLOAT_NEAR(440.0f, ch.osc.frequency, 0.1f);
    ASSERT_FLOAT_EQ(0.8f, ch.volume);
    ASSERT_TRUE(envelope_is_active(&ch.env));
    
    TEST_PASS();
}

static void test_channel_note_off(void) {
    TEST_CASE("channel_note_off");
    
    Channel ch;
    channel_init(&ch, OSC_SQUARE);
    
    channel_note_on(&ch, 69, 1.0f);
    
    for (int i = 0; i < 1000; i++) {
        channel_generate(&ch, SAMPLE_RATE);
    }
    
    channel_note_off(&ch);
    
    ASSERT_EQ(ENV_RELEASE, ch.env.stage);
    
    TEST_PASS();
}

static void test_channel_generates_audio(void) {
    TEST_CASE("channel generates audio when active");
    
    Channel ch;
    channel_init(&ch, OSC_SQUARE);
    channel_note_on(&ch, 69, 1.0f);
    
    bool has_nonzero = false;
    for (int i = 0; i < 1000; i++) {
        float sample = channel_generate(&ch, SAMPLE_RATE);
        if (fabsf(sample) > 0.001f) {
            has_nonzero = true;
            break;
        }
    }
    
    ASSERT_TRUE(has_nonzero);
    
    TEST_PASS();
}

static void test_channel_silent_when_idle(void) {
    TEST_CASE("channel silent when idle");
    
    Channel ch;
    channel_init(&ch, OSC_SQUARE);
    
    for (int i = 0; i < 100; i++) {
        float sample = channel_generate(&ch, SAMPLE_RATE);
        ASSERT_FLOAT_EQ(0.0f, sample);
    }
    
    TEST_PASS();
}

static void test_channel_volume(void) {
    TEST_CASE("channel volume scaling");
    
    Channel ch1, ch2;
    channel_init(&ch1, OSC_SQUARE);
    channel_init(&ch2, OSC_SQUARE);
    
    channel_note_on(&ch1, 69, 1.0f);
    channel_note_on(&ch2, 69, 0.5f);
    
    float sum1 = 0.0f, sum2 = 0.0f;
    for (int i = 0; i < 100; i++) {
        sum1 += fabsf(channel_generate(&ch1, SAMPLE_RATE));
        sum2 += fabsf(channel_generate(&ch2, SAMPLE_RATE));
    }
    
    ASSERT_TRUE(sum1 > sum2 * 1.5f);
    
    TEST_PASS();
}

static void test_channel_reset(void) {
    TEST_CASE("channel_reset");
    
    Channel ch;
    channel_init(&ch, OSC_SQUARE);
    
    channel_note_on(&ch, 69, 1.0f);
    
    for (int i = 0; i < 100; i++) {
        channel_generate(&ch, SAMPLE_RATE);
    }
    
    channel_reset(&ch);
    
    ASSERT_EQ(-1, ch.note);
    ASSERT_FLOAT_EQ(0.0f, ch.osc.phase);
    ASSERT_EQ(ENV_IDLE, ch.env.stage);
    
    TEST_PASS();
}

static void test_channel_disabled(void) {
    TEST_CASE("disabled channel is silent");
    
    Channel ch;
    channel_init(&ch, OSC_SQUARE);
    ch.enabled = false;
    
    channel_note_on(&ch, 69, 1.0f);
    
    for (int i = 0; i < 100; i++) {
        float sample = channel_generate(&ch, SAMPLE_RATE);
        ASSERT_FLOAT_EQ(0.0f, sample);
    }
    
    TEST_PASS();
}

void run_channel_tests(void) {
    TEST_SUITE("Channel Tests");
    
    test_channel_init();
    test_note_to_frequency();
    test_channel_note_on();
    test_channel_note_off();
    test_channel_generates_audio();
    test_channel_silent_when_idle();
    test_channel_volume();
    test_channel_reset();
    test_channel_disabled();
}
