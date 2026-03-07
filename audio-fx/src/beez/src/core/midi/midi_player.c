#include "midi_player.h"
#include <string.h>

#define MAX_CHANNELS BEEZ_MAX_CHANNELS

void midi_player_init(MidiPlayer* player, SynthEngine* engine, float sample_rate) {
    if (!player) return;
    memset(player, 0, sizeof(MidiPlayer));
    player->engine = engine;
    player->sample_rate = sample_rate > 1.0f ? sample_rate : 44100.0f;
    player->tempo = 500000;
    player->loop_enabled = false;
}

void midi_player_reset(MidiPlayer* player) {
    if (!player) return;
    player->playing = false;
    player->paused = false;
    player->current_time = 0.0;
    player->current_tick = 0;
    memset(player->track_positions, 0, sizeof(player->track_positions));
    
    if (player->engine) {
        for (int i = 0; i < MAX_CHANNELS; i++) {
            synth_engine_note_off(player->engine, i);
        }
    }
}

static void update_tempo(MidiPlayer* player) {
    if (!player) return;
    if (player->sample_rate <= 1.0f) {
        player->sample_rate = 44100.0f;
    }
    if (player->midi && player->midi->ticks_per_quarter > 0 && player->tempo > 0) {
        double us_per_tick = (double)player->tempo / player->midi->ticks_per_quarter;
        double seconds_per_tick = us_per_tick / 1000000.0;
        player->samples_per_tick = player->sample_rate * seconds_per_tick;
    } else {
        player->samples_per_tick = 0.0;
    }
}

bool midi_player_load(MidiPlayer* player, const char* filename) {
    if (!player || !filename) return false;
    if (player->midi) {
        midi_file_destroy(player->midi);
    }
    
    player->midi = midi_file_create();
    if (!player->midi) return false;
    
    if (!midi_file_load(player->midi, filename)) {
        midi_file_destroy(player->midi);
        player->midi = NULL;
        return false;
    }
    
    player->tempo = player->midi->tempo;
    update_tempo(player);
    
    player->total_time = midi_file_ticks_to_seconds(player->midi, player->midi->total_ticks);
    
    midi_player_reset(player);
    return true;
}

void midi_player_set_midi(MidiPlayer* player, MidiFile* midi) {
    if (!player) return;
    player->midi = midi;
    if (midi) {
        player->tempo = midi->tempo;
        update_tempo(player);
        player->total_time = midi_file_ticks_to_seconds(midi, midi->total_ticks);
    }
    midi_player_reset(player);
}

void midi_player_play(MidiPlayer* player) {
    if (!player) return;
    if (player->paused) {
        player->paused = false;
        player->playing = true;
    } else if (!player->playing) {
        midi_player_reset(player);
        player->playing = true;
    }
}

void midi_player_pause(MidiPlayer* player) {
    if (!player) return;
    if (player->playing) {
        player->paused = true;
        player->playing = false;
        
        for (int i = 0; i < MAX_CHANNELS; i++) {
            synth_engine_note_off(player->engine, i);
        }
    }
}

void midi_player_stop(MidiPlayer* player) {
    if (!player) return;
    midi_player_reset(player);
}

void midi_player_seek(MidiPlayer* player, double time) {
    if (!player || !player->midi) return;
    if (time < 0.0) time = 0.0;
    if (player->total_time > 0.0 && time > player->total_time) {
        time = player->total_time;
    }
    
    for (int i = 0; i < MAX_CHANNELS; i++) {
        synth_engine_note_off(player->engine, i);
    }
    
    player->current_time = time;
    player->current_tick = midi_file_seconds_to_ticks(player->midi, time);
    
    for (int t = 0; t < player->midi->num_tracks; t++) {
        const MidiTrack* track = &player->midi->tracks[t];
        player->track_positions[t] = 0;
        
        for (size_t i = 0; i < track->event_count; i++) {
            if (track->events[i].tick >= player->current_tick) {
                player->track_positions[t] = i;
                break;
            }
        }
    }
}

static void process_event(MidiPlayer* player, const MidiEvent* ev) {
    if (!player || !player->engine || !ev) return;
    
    int channel = ev->channel % MAX_CHANNELS;
    
    switch (ev->type) {
        case MIDI_EVENT_NOTE_ON:
            if (ev->data2 > 0) {
                float velocity = ev->data2 / 127.0f;
                synth_engine_note_on(player->engine, channel, ev->data1, velocity);
            } else {
                synth_engine_note_off(player->engine, channel);
            }
            break;
            
        case MIDI_EVENT_NOTE_OFF:
            synth_engine_note_off(player->engine, channel);
            break;
            
        case MIDI_EVENT_META:
            if (ev->data1 == MIDI_META_TEMPO) {
                player->tempo = ev->tempo;
                update_tempo(player);
            }
            break;
            
        default:
            break;
    }
}

void midi_player_process(MidiPlayer* player, int num_samples) {
    if (!player || !player->playing || !player->midi || player->paused) return;
    if (num_samples <= 0 || player->samples_per_tick <= 0.0) return;
    
    double samples_elapsed = 0;
    
    while (samples_elapsed < num_samples) {
        uint32_t next_event_tick = UINT32_MAX;
        int next_track = -1;
        size_t next_event_idx = 0;
        
        for (int t = 0; t < player->midi->num_tracks; t++) {
            const MidiTrack* track = &player->midi->tracks[t];
            size_t pos = player->track_positions[t];
            
            if (pos < track->event_count) {
                uint32_t tick = track->events[pos].tick;
                if (tick < next_event_tick) {
                    next_event_tick = tick;
                    next_track = t;
                    next_event_idx = pos;
                }
            }
        }
        
        if (next_track < 0) {
            if (player->loop_enabled) {
                midi_player_seek(player, 0);
                continue;
            } else {
                player->playing = false;
                break;
            }
        }
        
        double samples_to_event = (next_event_tick - player->current_tick) * player->samples_per_tick;
        
        if (samples_elapsed + samples_to_event > num_samples) {
            double remaining = num_samples - samples_elapsed;
            player->current_tick += (uint32_t)(remaining / player->samples_per_tick);
            break;
        }
        
        samples_elapsed += samples_to_event;
        player->current_tick = next_event_tick;
        
        const MidiTrack* track = &player->midi->tracks[next_track];
        process_event(player, &track->events[next_event_idx]);
        player->track_positions[next_track]++;
    }
    
    player->current_time = midi_file_ticks_to_seconds(player->midi, player->current_tick);
}

bool midi_player_is_playing(const MidiPlayer* player) {
    return player && player->playing && !player->paused;
}

double midi_player_get_position(const MidiPlayer* player) {
    return player ? player->current_time : 0.0;
}

double midi_player_get_duration(const MidiPlayer* player) {
    return player ? player->total_time : 0.0;
}
