#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    OSC_SQUARE,
    OSC_PULSE_25,
    OSC_PULSE_12_5,
    OSC_PULSE_75,
    OSC_TRIANGLE,
    OSC_SAWTOOTH,
    OSC_NOISE_WHITE,
    OSC_NOISE_PERIODIC,
    OSC_NOISE_METALLIC,
    OSC_WAVETABLE,
    OSC_WAVE_COUNT
} OscillatorWaveform;

typedef enum {
    NOISE_MODE_WHITE,
    NOISE_MODE_PERIODIC,
    NOISE_MODE_METALLIC
} NoiseMode;

#define WAVETABLE_SIZE 32

typedef struct {
    OscillatorWaveform waveform;
    float frequency;
    float phase;
    float duty_cycle;
    uint16_t lfsr;
    float last_noise;
    NoiseMode noise_mode;
    int noise_period;
    int noise_counter;
    float wavetable[WAVETABLE_SIZE];
    int wavetable_pos;
} Oscillator;

void oscillator_init(Oscillator* osc, OscillatorWaveform waveform);
void oscillator_set_frequency(Oscillator* osc, float frequency);
void oscillator_set_duty_cycle(Oscillator* osc, float duty);
void oscillator_set_noise_mode(Oscillator* osc, NoiseMode mode, int period);
void oscillator_set_wavetable(Oscillator* osc, const float* table, int size);
void oscillator_load_preset_wavetable(Oscillator* osc, int preset);
float oscillator_generate(Oscillator* osc, float sample_rate);
void oscillator_reset(Oscillator* osc);

#ifdef __cplusplus
}
#endif
