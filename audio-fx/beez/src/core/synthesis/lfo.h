#pragma once

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    LFO_SINE,
    LFO_TRIANGLE,
    LFO_SQUARE,
    LFO_SAWTOOTH,
    LFO_SAWTOOTH_DOWN,
    LFO_RANDOM
} LFOWaveform;

typedef enum {
    LFO_TARGET_NONE,
    LFO_TARGET_PITCH,
    LFO_TARGET_VOLUME,
    LFO_TARGET_FILTER_CUTOFF,
    LFO_TARGET_FILTER_RESONANCE,
    LFO_TARGET_PAN,
    LFO_TARGET_DUTY_CYCLE
} LFOTarget;

typedef struct {
    LFOWaveform waveform;
    LFOTarget target;
    float rate;
    float depth;
    float phase;
    float delay;
    float delay_counter;
    float last_random;
    bool enabled;
    bool synced;
} LFO;

void lfo_init(LFO* lfo);
void lfo_set_waveform(LFO* lfo, LFOWaveform waveform);
void lfo_set_target(LFO* lfo, LFOTarget target);
void lfo_set_rate(LFO* lfo, float rate);
void lfo_set_depth(LFO* lfo, float depth);
void lfo_set_delay(LFO* lfo, float delay);
void lfo_enable(LFO* lfo, bool enabled);
void lfo_sync(LFO* lfo);

float lfo_process(LFO* lfo, float sample_rate);
float lfo_get_value(const LFO* lfo);
void lfo_reset(LFO* lfo);

#ifdef __cplusplus
}
#endif
