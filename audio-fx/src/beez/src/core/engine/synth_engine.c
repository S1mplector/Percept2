#include "synth_engine.h"
#include <math.h>
#include <string.h>

static OscillatorWaveform default_waveforms[BEEZ_MAX_CHANNELS] = {
    OSC_SQUARE,
    OSC_SQUARE,
    OSC_TRIANGLE,
    OSC_NOISE_WHITE,
    OSC_PULSE_25,
    OSC_PULSE_12_5,
    OSC_SAWTOOTH,
    OSC_SQUARE
};

void synth_engine_init(SynthEngine* engine, float sample_rate) {
    if (!engine) return;
    engine->sample_rate = sample_rate > 1.0f ? sample_rate : 44100.0f;
    engine->master_volume = 0.5f;
    engine->samples_generated = 0;
    
    for (int i = 0; i < BEEZ_MAX_CHANNELS; i++) {
        channel_init(&engine->channels[i], default_waveforms[i]);
    }
}

void synth_engine_set_sample_rate(SynthEngine* engine, float sample_rate) {
    if (!engine) return;
    engine->sample_rate = sample_rate > 1.0f ? sample_rate : 44100.0f;
}

void synth_engine_set_master_volume(SynthEngine* engine, float volume) {
    if (!engine) return;
    if (volume < 0.0f) volume = 0.0f;
    if (volume > 1.0f) volume = 1.0f;
    engine->master_volume = volume;
}

void synth_engine_note_on(SynthEngine* engine, int channel, int note, float velocity) {
    if (!engine) return;
    if (channel >= 0 && channel < BEEZ_MAX_CHANNELS) {
        channel_note_on(&engine->channels[channel], note, velocity);
    }
}

void synth_engine_note_off(SynthEngine* engine, int channel) {
    if (!engine) return;
    if (channel >= 0 && channel < BEEZ_MAX_CHANNELS) {
        channel_note_off(&engine->channels[channel]);
    }
}

void synth_engine_set_channel_waveform(SynthEngine* engine, int channel, OscillatorWaveform waveform) {
    if (!engine) return;
    if (channel >= 0 && channel < BEEZ_MAX_CHANNELS) {
        engine->channels[channel].osc.waveform = waveform;
    }
}

void synth_engine_set_channel_adsr(SynthEngine* engine, int channel, float a, float d, float s, float r) {
    if (!engine) return;
    if (channel >= 0 && channel < BEEZ_MAX_CHANNELS) {
        envelope_set_adsr(&engine->channels[channel].env, a, d, s, r);
    }
}

void synth_engine_set_channel_volume(SynthEngine* engine, int channel, float volume) {
    if (!engine) return;
    if (channel >= 0 && channel < BEEZ_MAX_CHANNELS) {
        channel_set_base_volume(&engine->channels[channel], volume);
    }
}

void synth_engine_set_channel_pan(SynthEngine* engine, int channel, float pan) {
    if (!engine) return;
    if (channel >= 0 && channel < BEEZ_MAX_CHANNELS) {
        channel_set_pan(&engine->channels[channel], pan);
    }
}

void synth_engine_set_channel_enabled(SynthEngine* engine, int channel, bool enabled) {
    if (!engine) return;
    if (channel >= 0 && channel < BEEZ_MAX_CHANNELS) {
        engine->channels[channel].enabled = enabled;
    }
}

void synth_engine_generate_stereo(SynthEngine* engine, float* left, float* right, int num_samples) {
    if (!engine || !left || !right || num_samples <= 0) return;
    for (int i = 0; i < num_samples; i++) {
        float mix_l = 0.0f;
        float mix_r = 0.0f;
        
        for (int ch = 0; ch < BEEZ_MAX_CHANNELS; ch++) {
            float sample = channel_generate(&engine->channels[ch], engine->sample_rate);
            float pan = engine->channels[ch].pan_mod;
            
            float l_gain = (pan <= 0.0f) ? 1.0f : (1.0f - pan);
            float r_gain = (pan >= 0.0f) ? 1.0f : (1.0f + pan);
            
            mix_l += sample * l_gain;
            mix_r += sample * r_gain;
        }
        
        float out_l = mix_l * engine->master_volume;
        float out_r = mix_r * engine->master_volume;
        if (!isfinite(out_l)) out_l = 0.0f;
        if (!isfinite(out_r)) out_r = 0.0f;
        left[i] = out_l;
        right[i] = out_r;
        engine->samples_generated++;
    }
}

void synth_engine_generate_mono(SynthEngine* engine, float* buffer, int num_samples) {
    if (!engine || !buffer || num_samples <= 0) return;
    for (int i = 0; i < num_samples; i++) {
        float mix = 0.0f;
        
        for (int ch = 0; ch < BEEZ_MAX_CHANNELS; ch++) {
            mix += channel_generate(&engine->channels[ch], engine->sample_rate);
        }
        
        float out = mix * engine->master_volume;
        if (!isfinite(out)) out = 0.0f;
        buffer[i] = out;
        engine->samples_generated++;
    }
}

void synth_engine_reset(SynthEngine* engine) {
    if (!engine) return;
    for (int i = 0; i < BEEZ_MAX_CHANNELS; i++) {
        channel_reset(&engine->channels[i]);
    }
    engine->samples_generated = 0;
}
