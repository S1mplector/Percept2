#include "test_framework.h"
#include "../src/core/audio/audio_utils.h"
#include <math.h>

static void test_audio_clamp(void) {
    TEST_CASE("audio_clamp");
    
    ASSERT_FLOAT_EQ(0.5f, audio_clamp(0.5f, 0.0f, 1.0f));
    ASSERT_FLOAT_EQ(0.0f, audio_clamp(-0.5f, 0.0f, 1.0f));
    ASSERT_FLOAT_EQ(1.0f, audio_clamp(1.5f, 0.0f, 1.0f));
    
    TEST_PASS();
}

static void test_audio_hard_clip(void) {
    TEST_CASE("audio_hard_clip");
    
    ASSERT_FLOAT_EQ(0.5f, audio_hard_clip(0.5f));
    ASSERT_FLOAT_EQ(1.0f, audio_hard_clip(2.0f));
    ASSERT_FLOAT_EQ(-1.0f, audio_hard_clip(-2.0f));
    ASSERT_FLOAT_EQ(-0.5f, audio_hard_clip(-0.5f));
    
    TEST_PASS();
}

static void test_audio_soft_clip(void) {
    TEST_CASE("audio_soft_clip");
    
    float clipped_high = audio_soft_clip(2.0f);
    ASSERT_TRUE(clipped_high < 1.0f);
    ASSERT_TRUE(clipped_high > 0.5f);
    
    float clipped_low = audio_soft_clip(-2.0f);
    ASSERT_TRUE(clipped_low > -1.0f);
    ASSERT_TRUE(clipped_low < -0.5f);
    
    float unclipped = audio_soft_clip(0.3f);
    ASSERT_FLOAT_NEAR(0.3f, unclipped, 0.05f);
    
    TEST_PASS();
}

static void test_float_int16_conversion(void) {
    TEST_CASE("float <-> int16 conversion");
    
    ASSERT_EQ(0, float_to_int16(0.0f));
    ASSERT_EQ(32767, float_to_int16(1.0f));
    ASSERT_EQ(-32767, float_to_int16(-1.0f));
    
    ASSERT_FLOAT_NEAR(0.0f, int16_to_float(0), 0.001f);
    ASSERT_FLOAT_NEAR(1.0f, int16_to_float(32767), 0.001f);
    ASSERT_FLOAT_NEAR(-1.0f, int16_to_float(-32768), 0.001f);
    
    TEST_PASS();
}

static void test_audio_interleave(void) {
    TEST_CASE("audio_interleave_stereo");
    
    float left[4] = {1.0f, 2.0f, 3.0f, 4.0f};
    float right[4] = {5.0f, 6.0f, 7.0f, 8.0f};
    float interleaved[8];
    
    audio_interleave_stereo(left, right, interleaved, 4);
    
    ASSERT_FLOAT_EQ(1.0f, interleaved[0]);
    ASSERT_FLOAT_EQ(5.0f, interleaved[1]);
    ASSERT_FLOAT_EQ(2.0f, interleaved[2]);
    ASSERT_FLOAT_EQ(6.0f, interleaved[3]);
    ASSERT_FLOAT_EQ(3.0f, interleaved[4]);
    ASSERT_FLOAT_EQ(7.0f, interleaved[5]);
    ASSERT_FLOAT_EQ(4.0f, interleaved[6]);
    ASSERT_FLOAT_EQ(8.0f, interleaved[7]);
    
    TEST_PASS();
}

static void test_audio_deinterleave(void) {
    TEST_CASE("audio_deinterleave_stereo");
    
    float interleaved[8] = {1.0f, 5.0f, 2.0f, 6.0f, 3.0f, 7.0f, 4.0f, 8.0f};
    float left[4], right[4];
    
    audio_deinterleave_stereo(interleaved, left, right, 4);
    
    ASSERT_FLOAT_EQ(1.0f, left[0]);
    ASSERT_FLOAT_EQ(2.0f, left[1]);
    ASSERT_FLOAT_EQ(3.0f, left[2]);
    ASSERT_FLOAT_EQ(4.0f, left[3]);
    ASSERT_FLOAT_EQ(5.0f, right[0]);
    ASSERT_FLOAT_EQ(6.0f, right[1]);
    ASSERT_FLOAT_EQ(7.0f, right[2]);
    ASSERT_FLOAT_EQ(8.0f, right[3]);
    
    TEST_PASS();
}

static void test_audio_apply_gain(void) {
    TEST_CASE("audio_apply_gain");
    
    float buffer[4] = {1.0f, -0.5f, 0.25f, -0.75f};
    audio_apply_gain(buffer, 4, 0.5f);
    
    ASSERT_FLOAT_EQ(0.5f, buffer[0]);
    ASSERT_FLOAT_EQ(-0.25f, buffer[1]);
    ASSERT_FLOAT_EQ(0.125f, buffer[2]);
    ASSERT_FLOAT_EQ(-0.375f, buffer[3]);
    
    TEST_PASS();
}

static void test_audio_mix(void) {
    TEST_CASE("audio_mix");
    
    float src[4] = {1.0f, 1.0f, 1.0f, 1.0f};
    float dst[4] = {0.5f, 0.5f, 0.5f, 0.5f};
    
    audio_mix(src, dst, 4, 0.5f);
    
    ASSERT_FLOAT_EQ(1.0f, dst[0]);
    ASSERT_FLOAT_EQ(1.0f, dst[1]);
    ASSERT_FLOAT_EQ(1.0f, dst[2]);
    ASSERT_FLOAT_EQ(1.0f, dst[3]);
    
    TEST_PASS();
}

static void test_audio_fade(void) {
    TEST_CASE("audio_fade");
    
    float buffer[4] = {1.0f, 1.0f, 1.0f, 1.0f};
    audio_fade(buffer, 4, 1.0f, 0.0f);
    
    ASSERT_FLOAT_NEAR(1.0f, buffer[0], 0.01f);
    ASSERT_TRUE(buffer[1] < buffer[0]);
    ASSERT_TRUE(buffer[2] < buffer[1]);
    ASSERT_TRUE(buffer[3] < buffer[2]);
    
    TEST_PASS();
}

static void test_audio_rms(void) {
    TEST_CASE("audio_rms");
    
    float buffer[4] = {1.0f, -1.0f, 1.0f, -1.0f};
    float rms = audio_rms(buffer, 4);
    
    ASSERT_FLOAT_EQ(1.0f, rms);
    
    float silence[4] = {0.0f, 0.0f, 0.0f, 0.0f};
    rms = audio_rms(silence, 4);
    
    ASSERT_FLOAT_EQ(0.0f, rms);
    
    TEST_PASS();
}

static void test_audio_peak(void) {
    TEST_CASE("audio_peak");
    
    float buffer[4] = {0.5f, -0.8f, 0.3f, -0.2f};
    float peak = audio_peak(buffer, 4);
    
    ASSERT_FLOAT_EQ(0.8f, peak);
    
    TEST_PASS();
}

static void test_db_conversion(void) {
    TEST_CASE("dB conversion");
    
    ASSERT_FLOAT_NEAR(1.0f, db_to_linear(0.0f), 0.001f);
    ASSERT_FLOAT_NEAR(0.5f, db_to_linear(-6.0f), 0.05f);
    ASSERT_FLOAT_NEAR(2.0f, db_to_linear(6.0f), 0.2f);
    ASSERT_FLOAT_EQ(0.0f, db_to_linear(-80.0f));
    
    ASSERT_FLOAT_NEAR(0.0f, linear_to_db(1.0f), 0.001f);
    ASSERT_FLOAT_NEAR(-6.0f, linear_to_db(0.5f), 0.5f);
    ASSERT_FLOAT_EQ(-80.0f, linear_to_db(0.0f));
    
    TEST_PASS();
}

void run_audio_utils_tests(void) {
    TEST_SUITE("Audio Utils Tests");
    
    test_audio_clamp();
    test_audio_hard_clip();
    test_audio_soft_clip();
    test_float_int16_conversion();
    test_audio_interleave();
    test_audio_deinterleave();
    test_audio_apply_gain();
    test_audio_mix();
    test_audio_fade();
    test_audio_rms();
    test_audio_peak();
    test_db_conversion();
}
