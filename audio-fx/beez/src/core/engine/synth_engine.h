#pragma once

#include "../synthesis/channel.h"
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define BEEZ_MAX_CHANNELS 8
#define BEEZ_DEFAULT_SAMPLE_RATE 44100.0f

typedef struct {
    Channel channels[BEEZ_MAX_CHANNELS];
    float sample_rate;
    float master_volume;
    uint32_t samples_generated;
} SynthEngine;

void synth_engine_init(SynthEngine* engine, float sample_rate);
void synth_engine_set_sample_rate(SynthEngine* engine, float sample_rate);
void synth_engine_set_master_volume(SynthEngine* engine, float volume);

void synth_engine_note_on(SynthEngine* engine, int channel, int note, float velocity);
void synth_engine_note_off(SynthEngine* engine, int channel);
void synth_engine_set_channel_waveform(SynthEngine* engine, int channel, OscillatorWaveform waveform);
void synth_engine_set_channel_adsr(SynthEngine* engine, int channel, float a, float d, float s, float r);
void synth_engine_set_channel_volume(SynthEngine* engine, int channel, float volume);
void synth_engine_set_channel_pan(SynthEngine* engine, int channel, float pan);
void synth_engine_set_channel_enabled(SynthEngine* engine, int channel, bool enabled);

void synth_engine_generate_stereo(SynthEngine* engine, float* left, float* right, int num_samples);
void synth_engine_generate_mono(SynthEngine* engine, float* buffer, int num_samples);

void synth_engine_reset(SynthEngine* engine);

#ifdef __cplusplus
}
#endif
