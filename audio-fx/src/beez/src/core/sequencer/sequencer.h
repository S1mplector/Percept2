#pragma once

#include "pattern.h"
#include "../engine/synth_engine.h"
#include "../synthesis/instrument.h"
#include "../midi/midi_file.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define BEEZ_MAX_SONG_LENGTH 256

typedef struct {
    Pattern patterns[BEEZ_MAX_PATTERNS];
    int pattern_order[BEEZ_MAX_SONG_LENGTH];
    int song_length;
    int num_patterns;
    
    int current_order;
    int current_row;
    float tempo;
    int speed;
    
    float tick_accumulator;
    int current_tick;
    bool playing;
    bool loop_enabled;
    
    SynthEngine* engine;
    InstrumentBank* instruments;
} Sequencer;

void sequencer_init(Sequencer* seq, SynthEngine* engine);
void sequencer_set_tempo(Sequencer* seq, float bpm);
void sequencer_set_speed(Sequencer* seq, int speed);

void sequencer_play(Sequencer* seq);
void sequencer_stop(Sequencer* seq);
void sequencer_pause(Sequencer* seq);
void sequencer_set_position(Sequencer* seq, int order, int row);
void sequencer_set_instrument_bank(Sequencer* seq, InstrumentBank* bank);

void sequencer_process(Sequencer* seq, int num_samples, float sample_rate);

Pattern* sequencer_get_pattern(Sequencer* seq, int index);
Pattern* sequencer_get_current_pattern(Sequencer* seq);
int sequencer_add_pattern(Sequencer* seq);
bool sequencer_import_midi(Sequencer* seq, const MidiFile* midi);

#ifdef __cplusplus
}
#endif
