#pragma once

#include "midi_file.h"
#include "../engine/synth_engine.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    MidiFile* midi;
    SynthEngine* engine;
    
    bool playing;
    bool paused;
    bool loop_enabled;
    
    double current_time;
    double total_time;
    uint32_t current_tick;
    
    size_t track_positions[MIDI_MAX_TRACKS];
    
    float sample_rate;
    double samples_per_tick;
    
    uint32_t tempo;
} MidiPlayer;

void midi_player_init(MidiPlayer* player, SynthEngine* engine, float sample_rate);
void midi_player_reset(MidiPlayer* player);

bool midi_player_load(MidiPlayer* player, const char* filename);
void midi_player_set_midi(MidiPlayer* player, MidiFile* midi);

void midi_player_play(MidiPlayer* player);
void midi_player_pause(MidiPlayer* player);
void midi_player_stop(MidiPlayer* player);
void midi_player_seek(MidiPlayer* player, double time);

void midi_player_process(MidiPlayer* player, int num_samples);

bool midi_player_is_playing(const MidiPlayer* player);
double midi_player_get_position(const MidiPlayer* player);
double midi_player_get_duration(const MidiPlayer* player);

#ifdef __cplusplus
}
#endif
