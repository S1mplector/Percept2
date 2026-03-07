#include "sequencer.h"
#include "../synthesis/channel.h"
#include "../synthesis/effects.h"
#include <string.h>
#include <math.h>

static void process_effect(Sequencer* seq, int ch, uint8_t effect, uint8_t param);
static void process_tick_effects(Sequencer* seq);

void sequencer_init(Sequencer* seq, SynthEngine* engine) {
    if (!seq) return;
    seq->engine = engine;
    seq->instruments = NULL;
    seq->song_length = 1;
    seq->num_patterns = 1;
    
    for (int i = 0; i < BEEZ_MAX_PATTERNS; i++) {
        pattern_init(&seq->patterns[i], i);
    }
    
    memset(seq->pattern_order, 0, sizeof(seq->pattern_order));
    
    seq->current_order = 0;
    seq->current_row = 0;
    seq->tempo = 125.0f;
    seq->speed = 6;
    
    seq->tick_accumulator = 0.0f;
    seq->current_tick = 0;
    seq->playing = false;
    seq->loop_enabled = true;
}

void sequencer_set_instrument_bank(Sequencer* seq, InstrumentBank* bank) {
    if (!seq) return;
    seq->instruments = bank;
    if (!seq->engine || !bank || bank->count == 0) return;
    
    for (int ch = 0; ch < BEEZ_MAX_CHANNELS; ch++) {
        Instrument* inst = instrument_bank_get(bank, 0);
        if (inst) {
            instrument_apply_to_channel(inst, &seq->engine->channels[ch]);
            seq->engine->channels[ch].instrument = 0;
        }
    }
}

void sequencer_set_tempo(Sequencer* seq, float bpm) {
    if (!seq) return;
    if (bpm < 1.0f) bpm = 1.0f;
    if (bpm > 1000.0f) bpm = 1000.0f;
    seq->tempo = bpm;
}

void sequencer_set_speed(Sequencer* seq, int speed) {
    if (!seq) return;
    if (speed < 1) speed = 1;
    if (speed > 64) speed = 64;
    seq->speed = speed;
}

void sequencer_play(Sequencer* seq) {
    if (!seq) return;
    seq->playing = true;
}

void sequencer_stop(Sequencer* seq) {
    if (!seq) return;
    seq->playing = false;
    seq->current_order = 0;
    seq->current_row = 0;
    seq->current_tick = 0;
    seq->tick_accumulator = 0.0f;
    if (seq->engine) {
        synth_engine_reset(seq->engine);
    }
}

void sequencer_pause(Sequencer* seq) {
    if (!seq) return;
    seq->playing = false;
}

void sequencer_set_position(Sequencer* seq, int order, int row) {
    if (!seq) return;
    if (order >= 0 && order < seq->song_length) {
        seq->current_order = order;
    }
    Pattern* pattern = sequencer_get_current_pattern(seq);
    if (pattern && row >= 0 && row < pattern->length) {
        seq->current_row = row;
    }
    seq->current_tick = 0;
    seq->tick_accumulator = 0.0f;
}

static void process_row(Sequencer* seq) {
    if (!seq || !seq->engine) return;
    Pattern* pattern = sequencer_get_current_pattern(seq);
    if (!pattern) return;
    
    for (int ch = 0; ch < 8; ch++) {
        const PatternCell* cell = pattern_get_cell(pattern, seq->current_row, ch);
        if (!cell) continue;
        
        Channel* channel = &seq->engine->channels[ch];
        effects_reset(&channel->fx);
        
        if (seq->instruments && cell->instrument > 0) {
            int inst_index = (int)cell->instrument - 1;
            Instrument* inst = instrument_bank_get(seq->instruments, inst_index);
            if (inst) {
                instrument_apply_to_channel(inst, channel);
                channel->instrument = inst_index;
            }
        }
        
        if (cell->note == NOTE_OFF) {
            synth_engine_note_off(seq->engine, ch);
        } else if (cell->note >= NOTE_C0) {
            int midi_note = cell->note;
            float velocity = cell->volume > 0 ? cell->volume / 127.0f : 1.0f;
            
            if (cell->effect == FX_TONE_PORTAMENTO && channel->note >= 0) {
                float target = note_to_frequency(midi_note);
                float speed = (cell->effect_param > 0) ? cell->effect_param * 2.0f : 20.0f;
                channel_apply_portamento(channel, target, speed);
            } else {
                synth_engine_note_on(seq->engine, ch, midi_note, velocity);
            }
        }
        
        if (cell->effect != FX_NONE || cell->effect_param != 0) {
            process_effect(seq, ch, cell->effect, cell->effect_param);
        }
    }
}

static void advance_row(Sequencer* seq) {
    if (!seq) return;
    seq->current_row++;
    
    Pattern* pattern = sequencer_get_current_pattern(seq);
    if (!pattern || seq->current_row >= pattern->length) {
        seq->current_row = 0;
        seq->current_order++;
        
        if (seq->current_order >= seq->song_length) {
            if (seq->loop_enabled) {
                seq->current_order = 0;
            } else {
                sequencer_stop(seq);
            }
        }
    }
}

void sequencer_process(Sequencer* seq, int num_samples, float sample_rate) {
    if (!seq || !seq->playing) return;
    if (num_samples <= 0 || sample_rate <= 1.0f || seq->tempo <= 0.0f) return;
    
    float samples_per_tick = (sample_rate * 60.0f) / (seq->tempo * 24.0f);
    if (samples_per_tick <= 0.0f || !isfinite(samples_per_tick)) return;
    
    seq->tick_accumulator += num_samples;
    
    while (seq->tick_accumulator >= samples_per_tick) {
        seq->tick_accumulator -= samples_per_tick;
        
        if (seq->current_tick == 0) {
            process_row(seq);
        } else {
            process_tick_effects(seq);
        }
        
        seq->current_tick++;
        
        if (seq->current_tick >= seq->speed) {
            seq->current_tick = 0;
            advance_row(seq);
        }
    }
}

Pattern* sequencer_get_pattern(Sequencer* seq, int index) {
    if (seq && index >= 0 && index < BEEZ_MAX_PATTERNS) {
        return &seq->patterns[index];
    }
    return NULL;
}

Pattern* sequencer_get_current_pattern(Sequencer* seq) {
    if (!seq || seq->song_length <= 0) return NULL;
    int order = seq->current_order;
    if (order < 0 || order >= seq->song_length) order = 0;
    int pattern_idx = seq->pattern_order[order];
    if (pattern_idx < 0 || pattern_idx >= seq->num_patterns) {
        pattern_idx = 0;
    }
    return sequencer_get_pattern(seq, pattern_idx);
}

int sequencer_add_pattern(Sequencer* seq) {
    if (seq && seq->num_patterns < BEEZ_MAX_PATTERNS) {
        int idx = seq->num_patterns;
        seq->num_patterns++;
        return idx;
    }
    return -1;
}

bool sequencer_import_midi(Sequencer* seq, const MidiFile* midi) {
    if (!seq || !midi) return false;
    
    uint32_t tpq = midi->ticks_per_quarter > 0 ? midi->ticks_per_quarter : 480;
    uint32_t row_ticks = tpq / 4;
    if (row_ticks == 0) row_ticks = 1;
    uint32_t total_rows = (midi->total_ticks + row_ticks - 1) / row_ticks;
    
    int patterns_needed = (int)((total_rows + BEEZ_PATTERN_ROWS - 1) / BEEZ_PATTERN_ROWS);
    if (patterns_needed < 1) patterns_needed = 1;
    if (patterns_needed > BEEZ_MAX_PATTERNS) patterns_needed = BEEZ_MAX_PATTERNS;
    if (patterns_needed > BEEZ_MAX_SONG_LENGTH) patterns_needed = BEEZ_MAX_SONG_LENGTH;
    
    seq->num_patterns = patterns_needed;
    seq->song_length = patterns_needed;
    for (int i = 0; i < patterns_needed; i++) {
        seq->pattern_order[i] = i;
        pattern_init(&seq->patterns[i], i);
    }
    
    if (midi->tempo > 0) {
        float bpm = 60000000.0f / (float)midi->tempo;
        sequencer_set_tempo(seq, bpm);
    }
    
    int active_note[8];
    for (int i = 0; i < 8; i++) active_note[i] = -1;
    
    for (int t = 0; t < midi->num_tracks; t++) {
        const MidiTrack* track = &midi->tracks[t];
        for (size_t i = 0; i < track->event_count; i++) {
            const MidiEvent* ev = &track->events[i];
            if (ev->type != MIDI_EVENT_NOTE_ON && ev->type != MIDI_EVENT_NOTE_OFF) {
                continue;
            }
            
            int ch = ev->channel % 8;
            uint32_t row = ev->tick / row_ticks;
            if (row >= (uint32_t)(patterns_needed * BEEZ_PATTERN_ROWS)) {
                continue;
            }
            int pat = (int)(row / BEEZ_PATTERN_ROWS);
            int row_in = (int)(row % BEEZ_PATTERN_ROWS);
            Pattern* pattern = &seq->patterns[pat];
            
            PatternCell cell = pattern->cells[row_in][ch];
            if (ev->type == MIDI_EVENT_NOTE_ON && ev->data2 > 0) {
                if (active_note[ch] >= 0) {
                    PatternCell off = {0};
                    off.note = NOTE_OFF;
                    pattern_set_cell(pattern, row_in, ch, &off);
                }
                cell.note = ev->data1;
                cell.volume = ev->data2;
                pattern_set_cell(pattern, row_in, ch, &cell);
                active_note[ch] = ev->data1;
            } else {
                PatternCell off = {0};
                off.note = NOTE_OFF;
                pattern_set_cell(pattern, row_in, ch, &off);
                active_note[ch] = -1;
            }
        }
    }
    
    seq->current_order = 0;
    seq->current_row = 0;
    seq->current_tick = 0;
    seq->tick_accumulator = 0.0f;
    return true;
}

static void process_tick_effects(Sequencer* seq) {
    if (!seq || !seq->engine) return;
    Pattern* pattern = sequencer_get_current_pattern(seq);
    if (!pattern) return;
    
    for (int ch = 0; ch < 8; ch++) {
        Channel* channel = &seq->engine->channels[ch];
        channel_process_tick(channel);
        
        const PatternCell* cell = pattern_get_cell(pattern, seq->current_row, ch);
        if (!cell) continue;
        
        uint8_t effect = cell->effect;
        uint8_t param = cell->effect_param;
        
        switch (effect) {
            case FX_PORTAMENTO_UP:
                channel_set_pitch_slide(channel, param * 2.0f);
                break;
            case FX_PORTAMENTO_DOWN:
                channel_set_pitch_slide(channel, -param * 2.0f);
                break;
            case FX_VOLUME_SLIDE: {
                float slide = 0.0f;
                if ((param & 0xF0) > 0) {
                    slide = (param >> 4) / 64.0f;
                } else if ((param & 0x0F) > 0) {
                    slide = -(param & 0x0F) / 64.0f;
                }
                float new_vol = channel->volume + slide;
                if (new_vol < 0.0f) new_vol = 0.0f;
                if (new_vol > 1.0f) new_vol = 1.0f;
                channel_set_volume(channel, new_vol);
                break;
            }
            default:
                break;
        }
    }
}

static void process_effect(Sequencer* seq, int ch, uint8_t effect, uint8_t param) {
    if (!seq || !seq->engine || ch < 0 || ch >= BEEZ_MAX_CHANNELS) return;
    Channel* channel = &seq->engine->channels[ch];
    
    switch (effect) {
        case FX_ARPEGGIO:
            if (param != 0) {
                int note1 = (param >> 4) & 0x0F;
                int note2 = param & 0x0F;
                channel_set_arpeggio(channel, note1, note2, 1);
            }
            break;
            
        case FX_PORTAMENTO_UP:
        case FX_PORTAMENTO_DOWN:
            break;
            
        case FX_TONE_PORTAMENTO:
            break;
            
        case FX_VIBRATO: {
            float speed = ((param >> 4) & 0x0F) * 0.5f;
            float depth = (param & 0x0F) * 1.0f;
            channel_set_vibrato(channel, depth, speed);
            break;
        }
        
        case FX_VOLUME_SLIDE:
            break;
            
        case FX_JUMP_TO_ORDER:
            if (param < seq->song_length) {
                seq->current_order = param;
                seq->current_row = 0;
            }
            break;
            
        case FX_SET_VOLUME:
            channel_set_volume(channel, param / 64.0f);
            break;
            
        case FX_PATTERN_BREAK: {
            int row = ((param >> 4) * 10) + (param & 0x0F);
            seq->current_order++;
            if (seq->current_order >= seq->song_length) {
                if (seq->loop_enabled) {
                    seq->current_order = 0;
                } else {
                    sequencer_stop(seq);
                    return;
                }
            }
            Pattern* next_pattern = sequencer_get_current_pattern(seq);
            if (next_pattern && row < next_pattern->length) {
                seq->current_row = row - 1;
            } else {
                seq->current_row = -1;
            }
            break;
        }
        
        case FX_SET_SPEED:
            if (param >= 0x20) {
                sequencer_set_tempo(seq, (float)param);
            } else if (param > 0) {
                sequencer_set_speed(seq, param);
            }
            break;
            
        case FX_SET_DUTY_CYCLE:
            channel_set_duty_cycle(channel, param / 255.0f);
            break;
            
        case FX_RETRIGGER:
            effects_set_retrigger(&channel->fx, param);
            break;
            
        case FX_NOTE_CUT:
            effects_set_note_cut(&channel->fx, param);
            break;
            
        case FX_NOTE_DELAY:
            effects_set_note_delay(&channel->fx, param);
            break;
            
        case FX_PITCH_ENVELOPE: {
            float amount = ((param >> 4) & 0x0F) * 10.0f;
            float speed = (param & 0x0F) * 2.0f;
            if (param & 0x80) amount = -amount;
            effects_set_pitch_envelope(&channel->fx, amount, speed);
            break;
        }
        
        default:
            break;
    }
}
