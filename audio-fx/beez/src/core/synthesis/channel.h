#pragma once

#include "oscillator.h"
#include "envelope.h"
#include "effects.h"
#include "filter.h"
#include "lfo.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    Oscillator osc;
    Envelope env;
    ChannelEffects fx;
    ChannelEffects inst_fx;
    Filter filter;
    float base_volume;
    float volume;
    float pan;
    float pan_mod;
    float base_duty_cycle;
    float base_filter_cutoff;
    float base_filter_resonance;
    LFO lfo;
    float base_frequency;
    float current_frequency;
    bool enabled;
    int note;
    int instrument;
} Channel;

void channel_init(Channel* ch, OscillatorWaveform waveform);
void channel_note_on(Channel* ch, int note, float velocity);
void channel_note_off(Channel* ch);
void channel_set_volume(Channel* ch, float volume);
void channel_set_base_volume(Channel* ch, float volume);
void channel_set_pan(Channel* ch, float pan);
void channel_set_duty_cycle(Channel* ch, float duty);
float channel_generate(Channel* ch, float sample_rate);
void channel_reset(Channel* ch);

void channel_process_tick(Channel* ch);
void channel_apply_portamento(Channel* ch, float target_freq, float speed);
void channel_set_arpeggio(Channel* ch, int note1, int note2, int speed);
void channel_set_vibrato(Channel* ch, float depth, float speed);
void channel_set_pitch_slide(Channel* ch, float amount);
ChannelEffects* channel_get_effects(Channel* ch);

void channel_set_filter(Channel* ch, float cutoff, float resonance);
void channel_enable_filter(Channel* ch, bool enabled);
void channel_set_filter_type(Channel* ch, FilterType type);

float note_to_frequency(int note);
int frequency_to_note(float freq);

#ifdef __cplusplus
}
#endif
