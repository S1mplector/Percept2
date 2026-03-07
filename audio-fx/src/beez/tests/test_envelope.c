#include "test_framework.h"
#include "../src/core/synthesis/envelope.h"

#define SAMPLE_RATE 44100.0f

static void test_envelope_init(void) {
    TEST_CASE("envelope_init");
    
    Envelope env;
    envelope_init(&env);
    
    ASSERT_FLOAT_EQ(0.01f, env.attack_time);
    ASSERT_FLOAT_EQ(0.1f, env.decay_time);
    ASSERT_FLOAT_EQ(0.7f, env.sustain_level);
    ASSERT_FLOAT_EQ(0.2f, env.release_time);
    ASSERT_EQ(ENV_IDLE, env.stage);
    ASSERT_FALSE(envelope_is_active(&env));
    
    TEST_PASS();
}

static void test_envelope_set_adsr(void) {
    TEST_CASE("envelope_set_adsr");
    
    Envelope env;
    envelope_init(&env);
    
    envelope_set_adsr(&env, 0.05f, 0.2f, 0.5f, 0.3f);
    
    ASSERT_FLOAT_EQ(0.05f, env.attack_time);
    ASSERT_FLOAT_EQ(0.2f, env.decay_time);
    ASSERT_FLOAT_EQ(0.5f, env.sustain_level);
    ASSERT_FLOAT_EQ(0.3f, env.release_time);
    
    TEST_PASS();
}

static void test_envelope_gate_on(void) {
    TEST_CASE("envelope_gate_on");
    
    Envelope env;
    envelope_init(&env);
    
    envelope_gate_on(&env);
    
    ASSERT_EQ(ENV_ATTACK, env.stage);
    ASSERT_TRUE(env.gate);
    ASSERT_TRUE(envelope_is_active(&env));
    
    TEST_PASS();
}

static void test_envelope_attack_phase(void) {
    TEST_CASE("attack phase rises to 1.0");
    
    Envelope env;
    envelope_init(&env);
    envelope_set_adsr(&env, 0.01f, 0.1f, 0.5f, 0.1f);
    envelope_gate_on(&env);
    
    float max_level = 0.0f;
    int attack_samples = (int)(0.01f * SAMPLE_RATE) + 10;
    
    for (int i = 0; i < attack_samples; i++) {
        float level = envelope_process(&env, SAMPLE_RATE);
        if (level > max_level) max_level = level;
    }
    
    ASSERT_FLOAT_NEAR(1.0f, max_level, 0.05f);
    
    TEST_PASS();
}

static void test_envelope_decay_phase(void) {
    TEST_CASE("decay phase drops to sustain level");
    
    Envelope env;
    envelope_init(&env);
    envelope_set_adsr(&env, 0.001f, 0.05f, 0.5f, 0.1f);
    envelope_gate_on(&env);
    
    for (int i = 0; i < (int)(0.001f * SAMPLE_RATE) + 10; i++) {
        envelope_process(&env, SAMPLE_RATE);
    }
    
    ASSERT_EQ(ENV_DECAY, env.stage);
    
    for (int i = 0; i < (int)(0.05f * SAMPLE_RATE) + 100; i++) {
        envelope_process(&env, SAMPLE_RATE);
    }
    
    ASSERT_EQ(ENV_SUSTAIN, env.stage);
    ASSERT_FLOAT_NEAR(0.5f, env.level, 0.05f);
    
    TEST_PASS();
}

static void test_envelope_sustain_holds(void) {
    TEST_CASE("sustain phase holds level");
    
    Envelope env;
    envelope_init(&env);
    envelope_set_adsr(&env, 0.001f, 0.001f, 0.6f, 0.1f);
    envelope_gate_on(&env);
    
    for (int i = 0; i < (int)(0.01f * SAMPLE_RATE); i++) {
        envelope_process(&env, SAMPLE_RATE);
    }
    
    float level1 = envelope_process(&env, SAMPLE_RATE);
    
    for (int i = 0; i < 1000; i++) {
        envelope_process(&env, SAMPLE_RATE);
    }
    
    float level2 = envelope_process(&env, SAMPLE_RATE);
    
    ASSERT_FLOAT_NEAR(level1, level2, 0.01f);
    
    TEST_PASS();
}

static void test_envelope_release_phase(void) {
    TEST_CASE("release phase drops to zero");
    
    Envelope env;
    envelope_init(&env);
    envelope_set_adsr(&env, 0.001f, 0.001f, 0.5f, 0.05f);
    envelope_gate_on(&env);
    
    for (int i = 0; i < (int)(0.01f * SAMPLE_RATE); i++) {
        envelope_process(&env, SAMPLE_RATE);
    }
    
    envelope_gate_off(&env);
    
    ASSERT_EQ(ENV_RELEASE, env.stage);
    
    for (int i = 0; i < (int)(0.1f * SAMPLE_RATE); i++) {
        envelope_process(&env, SAMPLE_RATE);
    }
    
    ASSERT_EQ(ENV_IDLE, env.stage);
    ASSERT_FLOAT_NEAR(0.0f, env.level, 0.01f);
    ASSERT_FALSE(envelope_is_active(&env));
    
    TEST_PASS();
}

static void test_envelope_reset(void) {
    TEST_CASE("envelope_reset");
    
    Envelope env;
    envelope_init(&env);
    envelope_gate_on(&env);
    
    for (int i = 0; i < 1000; i++) {
        envelope_process(&env, SAMPLE_RATE);
    }
    
    envelope_reset(&env);
    
    ASSERT_EQ(ENV_IDLE, env.stage);
    ASSERT_FLOAT_EQ(0.0f, env.level);
    ASSERT_FALSE(env.gate);
    
    TEST_PASS();
}

static void test_envelope_retrigger(void) {
    TEST_CASE("envelope retrigger during release");
    
    Envelope env;
    envelope_init(&env);
    envelope_set_adsr(&env, 0.01f, 0.01f, 0.5f, 0.1f);
    envelope_gate_on(&env);
    
    for (int i = 0; i < (int)(0.05f * SAMPLE_RATE); i++) {
        envelope_process(&env, SAMPLE_RATE);
    }
    
    envelope_gate_off(&env);
    
    for (int i = 0; i < (int)(0.02f * SAMPLE_RATE); i++) {
        envelope_process(&env, SAMPLE_RATE);
    }
    
    ASSERT_EQ(ENV_RELEASE, env.stage);
    
    envelope_gate_on(&env);
    
    ASSERT_EQ(ENV_ATTACK, env.stage);
    
    TEST_PASS();
}

static void test_envelope_zero_values(void) {
    TEST_CASE("envelope handles near-zero times");
    
    Envelope env;
    envelope_init(&env);
    envelope_set_adsr(&env, 0.0f, 0.0f, 0.5f, 0.0f);
    
    ASSERT_TRUE(env.attack_time >= 0.001f);
    ASSERT_TRUE(env.decay_time >= 0.001f);
    ASSERT_TRUE(env.release_time >= 0.001f);
    
    TEST_PASS();
}

void run_envelope_tests(void) {
    TEST_SUITE("Envelope Tests");
    
    test_envelope_init();
    test_envelope_set_adsr();
    test_envelope_gate_on();
    test_envelope_attack_phase();
    test_envelope_decay_phase();
    test_envelope_sustain_holds();
    test_envelope_release_phase();
    test_envelope_reset();
    test_envelope_retrigger();
    test_envelope_zero_values();
}
