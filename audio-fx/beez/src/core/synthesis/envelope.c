#include "envelope.h"

void envelope_init(Envelope* env) {
    if (!env) return;
    env->attack_time = 0.01f;
    env->decay_time = 0.1f;
    env->sustain_level = 0.7f;
    env->release_time = 0.2f;
    
    env->stage = ENV_IDLE;
    env->level = 0.0f;
    env->time_in_stage = 0.0f;
    env->gate = false;
}

void envelope_set_adsr(Envelope* env, float attack, float decay, float sustain, float release) {
    if (!env) return;
    env->attack_time = attack > 0.001f ? attack : 0.001f;
    env->decay_time = decay > 0.001f ? decay : 0.001f;
    if (sustain < 0.0f) sustain = 0.0f;
    if (sustain > 1.0f) sustain = 1.0f;
    env->sustain_level = sustain;
    env->release_time = release > 0.001f ? release : 0.001f;
}

void envelope_gate_on(Envelope* env) {
    if (!env) return;
    env->gate = true;
    env->stage = ENV_ATTACK;
    env->time_in_stage = 0.0f;
}

void envelope_gate_off(Envelope* env) {
    if (!env) return;
    env->gate = false;
    if (env->stage != ENV_IDLE) {
        env->stage = ENV_RELEASE;
        env->time_in_stage = 0.0f;
    }
}

float envelope_process(Envelope* env, float sample_rate) {
    if (!env || sample_rate <= 1.0f) {
        return env ? env->level : 0.0f;
    }
    float dt = 1.0f / sample_rate;
    
    switch (env->stage) {
        case ENV_IDLE:
            env->level = 0.0f;
            break;
            
        case ENV_ATTACK:
            env->level = env->time_in_stage / env->attack_time;
            if (env->level >= 1.0f) {
                env->level = 1.0f;
                env->stage = ENV_DECAY;
                env->time_in_stage = 0.0f;
            }
            break;
            
        case ENV_DECAY:
            env->level = 1.0f - (1.0f - env->sustain_level) * (env->time_in_stage / env->decay_time);
            if (env->time_in_stage >= env->decay_time) {
                env->level = env->sustain_level;
                env->stage = ENV_SUSTAIN;
                env->time_in_stage = 0.0f;
            }
            break;
            
        case ENV_SUSTAIN:
            env->level = env->sustain_level;
            break;
            
        case ENV_RELEASE:
            {
                float start_level = env->sustain_level;
                if (env->time_in_stage == 0.0f) {
                    start_level = env->level;
                }
                env->level = start_level * (1.0f - env->time_in_stage / env->release_time);
                if (env->level <= 0.0f || env->time_in_stage >= env->release_time) {
                    env->level = 0.0f;
                    env->stage = ENV_IDLE;
                }
            }
            break;
    }
    
    env->time_in_stage += dt;
    return env->level;
}

void envelope_reset(Envelope* env) {
    if (!env) return;
    env->stage = ENV_IDLE;
    env->level = 0.0f;
    env->time_in_stage = 0.0f;
    env->gate = false;
}

bool envelope_is_active(const Envelope* env) {
    return env && env->stage != ENV_IDLE;
}
