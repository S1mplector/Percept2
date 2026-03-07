#include "oscillator.h"
#include <math.h>
#include <string.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static const float PRESET_SINE[WAVETABLE_SIZE] = {
    0.000f, 0.195f, 0.383f, 0.556f, 0.707f, 0.831f, 0.924f, 0.981f,
    1.000f, 0.981f, 0.924f, 0.831f, 0.707f, 0.556f, 0.383f, 0.195f,
    0.000f,-0.195f,-0.383f,-0.556f,-0.707f,-0.831f,-0.924f,-0.981f,
   -1.000f,-0.981f,-0.924f,-0.831f,-0.707f,-0.556f,-0.383f,-0.195f
};

static const float PRESET_ORGAN[WAVETABLE_SIZE] = {
    0.0f, 0.5f, 0.8f, 1.0f, 0.8f, 0.5f, 0.0f,-0.3f,
   -0.5f,-0.3f, 0.0f, 0.3f, 0.5f, 0.3f, 0.0f,-0.5f,
   -0.8f,-1.0f,-0.8f,-0.5f, 0.0f, 0.3f, 0.5f, 0.3f,
    0.0f,-0.3f,-0.5f,-0.3f, 0.0f, 0.5f, 0.8f, 0.5f
};

static const float PRESET_BASS[WAVETABLE_SIZE] = {
    1.0f, 1.0f, 1.0f, 1.0f, 0.8f, 0.6f, 0.4f, 0.2f,
    0.0f,-0.2f,-0.4f,-0.6f,-0.8f,-1.0f,-1.0f,-1.0f,
   -1.0f,-1.0f,-0.8f,-0.6f,-0.4f,-0.2f, 0.0f, 0.2f,
    0.4f, 0.6f, 0.8f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f
};

static const float PRESET_BELL[WAVETABLE_SIZE] = {
    0.0f, 0.7f, 1.0f, 0.7f, 0.0f,-0.3f, 0.0f, 0.5f,
    0.8f, 0.5f, 0.0f,-0.5f,-0.8f,-0.5f, 0.0f, 0.3f,
    0.0f,-0.7f,-1.0f,-0.7f, 0.0f, 0.3f, 0.0f,-0.5f,
   -0.8f,-0.5f, 0.0f, 0.5f, 0.8f, 0.5f, 0.0f,-0.3f
};

void oscillator_init(Oscillator* osc, OscillatorWaveform waveform) {
    if (!osc) return;
    memset(osc, 0, sizeof(Oscillator));
    osc->waveform = waveform;
    osc->frequency = 440.0f;
    osc->phase = 0.0f;
    osc->duty_cycle = 0.5f;
    osc->lfsr = 0x7FFF;
    osc->last_noise = 0.0f;
    osc->noise_mode = NOISE_MODE_WHITE;
    osc->noise_period = 1;
    osc->noise_counter = 0;
    osc->wavetable_pos = 0;
    
    for (int i = 0; i < WAVETABLE_SIZE; i++) {
        osc->wavetable[i] = PRESET_SINE[i];
    }
}

void oscillator_set_frequency(Oscillator* osc, float frequency) {
    if (!osc) return;
    osc->frequency = frequency;
}

void oscillator_set_duty_cycle(Oscillator* osc, float duty) {
    if (!osc) return;
    osc->duty_cycle = duty;
}

void oscillator_set_noise_mode(Oscillator* osc, NoiseMode mode, int period) {
    if (!osc) return;
    osc->noise_mode = mode;
    osc->noise_period = period > 0 ? period : 1;
    osc->noise_counter = 0;
}

void oscillator_set_wavetable(Oscillator* osc, const float* table, int size) {
    if (!osc || !table || size <= 0) return;
    int copy_size = size < WAVETABLE_SIZE ? size : WAVETABLE_SIZE;
    for (int i = 0; i < copy_size; i++) {
        osc->wavetable[i] = table[i];
    }
    for (int i = copy_size; i < WAVETABLE_SIZE; i++) {
        osc->wavetable[i] = 0.0f;
    }
}

void oscillator_load_preset_wavetable(Oscillator* osc, int preset) {
    if (!osc) return;
    const float* src;
    switch (preset) {
        case 0: src = PRESET_SINE; break;
        case 1: src = PRESET_ORGAN; break;
        case 2: src = PRESET_BASS; break;
        case 3: src = PRESET_BELL; break;
        default: src = PRESET_SINE; break;
    }
    for (int i = 0; i < WAVETABLE_SIZE; i++) {
        osc->wavetable[i] = src[i];
    }
}

static float generate_square(float phase, float duty) {
    return phase < duty ? 1.0f : -1.0f;
}

static float generate_triangle(float phase) {
    if (phase < 0.5f) {
        return 4.0f * phase - 1.0f;
    }
    return 3.0f - 4.0f * phase;
}

static float generate_sawtooth(float phase) {
    return 2.0f * phase - 1.0f;
}

static float generate_noise_white(uint16_t* lfsr) {
    uint16_t bit = ((*lfsr >> 0) ^ (*lfsr >> 1)) & 1;
    *lfsr = (*lfsr >> 1) | (bit << 14);
    return (*lfsr & 1) ? 1.0f : -1.0f;
}

static float generate_noise_periodic(uint16_t* lfsr) {
    uint16_t bit = ((*lfsr >> 0) ^ (*lfsr >> 6)) & 1;
    *lfsr = (*lfsr >> 1) | (bit << 14);
    return (*lfsr & 1) ? 1.0f : -1.0f;
}

static float generate_noise_metallic(uint16_t* lfsr) {
    uint16_t bit = ((*lfsr >> 0) ^ (*lfsr >> 1) ^ (*lfsr >> 5)) & 1;
    *lfsr = (*lfsr >> 1) | (bit << 6);
    return ((*lfsr & 0x3F) / 31.5f) - 1.0f;
}

static float generate_wavetable(Oscillator* osc) {
    int pos = (int)(osc->phase * WAVETABLE_SIZE) % WAVETABLE_SIZE;
    int next_pos = (pos + 1) % WAVETABLE_SIZE;
    float frac = (osc->phase * WAVETABLE_SIZE) - pos;
    return osc->wavetable[pos] * (1.0f - frac) + osc->wavetable[next_pos] * frac;
}

float oscillator_generate(Oscillator* osc, float sample_rate) {
    if (!osc || sample_rate <= 1.0f) return 0.0f;
    float output = 0.0f;
    
    switch (osc->waveform) {
        case OSC_SQUARE:
            output = generate_square(osc->phase, osc->duty_cycle);
            break;
        case OSC_PULSE_25:
            output = generate_square(osc->phase, 0.25f);
            break;
        case OSC_PULSE_12_5:
            output = generate_square(osc->phase, 0.125f);
            break;
        case OSC_PULSE_75:
            output = generate_square(osc->phase, 0.75f);
            break;
        case OSC_TRIANGLE:
            output = generate_triangle(osc->phase);
            break;
        case OSC_SAWTOOTH:
            output = generate_sawtooth(osc->phase);
            break;
        case OSC_NOISE_WHITE:
            if (++osc->noise_counter >= osc->noise_period) {
                osc->noise_counter = 0;
                osc->last_noise = generate_noise_white(&osc->lfsr);
            }
            output = osc->last_noise;
            break;
        case OSC_NOISE_PERIODIC:
            if (++osc->noise_counter >= osc->noise_period) {
                osc->noise_counter = 0;
                osc->last_noise = generate_noise_periodic(&osc->lfsr);
            }
            output = osc->last_noise;
            break;
        case OSC_NOISE_METALLIC:
            if (++osc->noise_counter >= osc->noise_period) {
                osc->noise_counter = 0;
                osc->last_noise = generate_noise_metallic(&osc->lfsr);
            }
            output = osc->last_noise;
            break;
        case OSC_WAVETABLE:
            output = generate_wavetable(osc);
            break;
        default:
            break;
    }
    
    float phase_inc = osc->frequency / sample_rate;
    osc->phase += phase_inc;
    if (osc->phase >= 1.0f) {
        osc->phase -= 1.0f;
    }
    
    return output;
}

void oscillator_reset(Oscillator* osc) {
    if (!osc) return;
    osc->phase = 0.0f;
    osc->lfsr = 0x7FFF;
    osc->last_noise = 0.0f;
}
