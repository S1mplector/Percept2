#pragma once

#include "oscillator.h"
#include "envelope.h"
#include "effects.h"
#include "filter.h"
#include "lfo.h"
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define MAX_INSTRUMENTS 64
#define INSTRUMENT_NAME_LEN 16

typedef struct {
    char name[INSTRUMENT_NAME_LEN];
    
    OscillatorWaveform waveform;
    float duty_cycle;
    int wavetable_preset;
    
    float attack;
    float decay;
    float sustain;
    float release;
    
    bool vibrato_enabled;
    float vibrato_depth;
    float vibrato_speed;
    float vibrato_delay;
    
    bool pitch_env_enabled;
    float pitch_env_amount;
    float pitch_env_speed;
    
    bool arp_enabled;
    int arp_note1;
    int arp_note2;
    int arp_speed;
    
    float volume;
    float pan;
    
    int noise_mode;
    int noise_period;

    bool filter_enabled;
    FilterType filter_type;
    float filter_cutoff;
    float filter_resonance;

    bool lfo_enabled;
    LFOWaveform lfo_waveform;
    LFOTarget lfo_target;
    float lfo_rate;
    float lfo_depth;
    float lfo_delay;
} Instrument;

typedef struct {
    Instrument instruments[MAX_INSTRUMENTS];
    int count;
} InstrumentBank;

void instrument_init(Instrument* inst);
void instrument_set_preset(Instrument* inst, int preset);
void instrument_apply_to_channel(const Instrument* inst, void* channel);

void instrument_bank_init(InstrumentBank* bank);
void instrument_bank_load_defaults(InstrumentBank* bank);
int instrument_bank_add(InstrumentBank* bank, const Instrument* inst);
Instrument* instrument_bank_get(InstrumentBank* bank, int index);

typedef enum {
    PRESET_PULSE_LEAD,
    PRESET_PULSE_BASS,
    PRESET_TRIANGLE_BASS,
    PRESET_TRIANGLE_LEAD,
    PRESET_NOISE_DRUM,
    PRESET_NOISE_HAT,
    PRESET_ARP_CHORD,
    PRESET_VIBRATO_LEAD,
    PRESET_PLUCK,
    PRESET_PAD,
    PRESET_BELL,
    PRESET_ORGAN,
    PRESET_COUNT
} InstrumentPreset;

#ifdef __cplusplus
}
#endif
