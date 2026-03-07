#pragma once

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    ENV_IDLE,
    ENV_ATTACK,
    ENV_DECAY,
    ENV_SUSTAIN,
    ENV_RELEASE
} EnvelopeStage;

typedef struct {
    float attack_time;
    float decay_time;
    float sustain_level;
    float release_time;
    
    EnvelopeStage stage;
    float level;
    float time_in_stage;
    bool gate;
} Envelope;

void envelope_init(Envelope* env);
void envelope_set_adsr(Envelope* env, float attack, float decay, float sustain, float release);
void envelope_gate_on(Envelope* env);
void envelope_gate_off(Envelope* env);
float envelope_process(Envelope* env, float sample_rate);
void envelope_reset(Envelope* env);
bool envelope_is_active(const Envelope* env);

#ifdef __cplusplus
}
#endif
