#include "midi_file.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdarg.h>

typedef struct {
    const uint8_t* data;
    size_t size;
    size_t pos;
} MidiReader;

static char g_midi_error[256];

static void midi_set_error(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vsnprintf(g_midi_error, sizeof(g_midi_error), fmt, args);
    va_end(args);
}

static const char* midi_get_error(void) {
    return g_midi_error[0] ? g_midi_error : "Unknown error";
}

static uint8_t read_byte(MidiReader* r) {
    if (!r || !r->data || r->pos >= r->size) {
        if (r) r->pos = r->size;
        return 0;
    }
    return r->data[r->pos++];
}

static uint16_t read_u16_be(MidiReader* r) {
    uint16_t v = (uint16_t)read_byte(r) << 8;
    v |= read_byte(r);
    return v;
}

static uint32_t read_u32_be(MidiReader* r) {
    uint32_t v = (uint32_t)read_byte(r) << 24;
    v |= (uint32_t)read_byte(r) << 16;
    v |= (uint32_t)read_byte(r) << 8;
    v |= read_byte(r);
    return v;
}

static uint32_t read_variable_length_limited(MidiReader* r, size_t limit, bool* ok) {
    uint32_t value = 0;
    uint8_t byte = 0;
    if (ok) *ok = false;
    if (!r || !r->data) return 0;
    
    int count = 0;
    do {
        if (r->pos >= limit) return value;
        byte = read_byte(r);
        value = (value << 7) | (byte & 0x7F);
        count++;
    } while ((byte & 0x80) && count < 4);
    
    if (byte & 0x80) {
        while ((byte & 0x80) && r->pos < limit) {
            byte = read_byte(r);
        }
    }
    
    if (ok) *ok = true;
    return value;
}

static bool check_header(MidiReader* r, const char* expected) {
    if (!r || !expected) return false;
    for (int i = 0; i < 4; i++) {
        if (read_byte(r) != (uint8_t)expected[i]) return false;
    }
    return true;
}

MidiFile* midi_file_create(void) {
    MidiFile* midi = (MidiFile*)calloc(1, sizeof(MidiFile));
    if (!midi) return NULL;
    
    midi->tempo = 500000;
    midi->ticks_per_quarter = 480;
    
    return midi;
}

void midi_file_destroy(MidiFile* midi) {
    if (!midi) return;
    
    for (int i = 0; i < MIDI_MAX_TRACKS; i++) {
        free(midi->tracks[i].events);
        midi->tracks[i].events = NULL;
        midi->tracks[i].event_count = 0;
        midi->tracks[i].event_capacity = 0;
    }
    free(midi);
}

static bool parse_track(MidiFile* midi, MidiReader* r, int track_idx) {
    if (!midi || !r) return false;
    if (!check_header(r, "MTrk")) {
        midi_set_error("Missing MTrk header (track %d at %zu)", track_idx, r ? r->pos : 0);
        return false;
    }
    
    uint32_t track_length = read_u32_be(r);
    if (track_length > r->size || r->pos > r->size - track_length) {
        track_length = (r->pos < r->size) ? (uint32_t)(r->size - r->pos) : 0;
    }
    size_t track_end = r->pos + track_length;
    
    MidiTrack* track = &midi->tracks[track_idx];
    track->event_capacity = 1024;
    track->events = (MidiEvent*)malloc(track->event_capacity * sizeof(MidiEvent));
    if (!track->events) return false;
    track->event_count = 0;
    
    uint32_t tick = 0;
    uint8_t running_status = 0;
    
    while (r->pos < track_end) {
        bool ok = false;
        uint32_t delta = read_variable_length_limited(r, track_end, &ok);
        if (!ok) {
            midi_set_error("Invalid delta time (track %d at %zu)", track_idx, r->pos);
            break;
        }
        tick += delta;
        
        if (r->pos >= track_end) break;
        uint8_t status = read_byte(r);
        
        if (status == 0xFF) {
            if (r->pos >= track_end) {
                midi_set_error("Truncated meta event (track %d)", track_idx);
                break;
            }
            uint8_t meta_type = read_byte(r);
            uint32_t meta_len = read_variable_length_limited(r, track_end, &ok);
            if (!ok) {
                midi_set_error("Invalid meta length (track %d)", track_idx);
                break;
            }
            
            if (r->pos > track_end) break;
            if (meta_len > track_end - r->pos) {
                r->pos = track_end;
                midi_set_error("Meta length exceeds track (track %d)", track_idx);
                break;
            }
            
            if (meta_type == MIDI_META_TEMPO && meta_len == 3) {
                uint32_t tempo = read_byte(r) << 16;
                tempo |= read_byte(r) << 8;
                tempo |= read_byte(r);
                
                MidiEvent ev = {0};
                ev.tick = tick;
                ev.type = MIDI_EVENT_META;
                ev.data1 = MIDI_META_TEMPO;
                ev.tempo = tempo;
                midi_track_add_event(track, &ev);
                
                if (track_idx == 0 && midi->tempo == 500000) {
                    midi->tempo = tempo;
                }
            } else if (meta_type == MIDI_META_TRACK_NAME && meta_len < 64) {
                for (uint32_t i = 0; i < meta_len && i < 63; i++) {
                    track->name[i] = read_byte(r);
                }
                track->name[meta_len < 63 ? meta_len : 63] = '\0';
            } else if (meta_type == MIDI_META_END_OF_TRACK) {
                if (tick > midi->total_ticks) {
                    midi->total_ticks = tick;
                }
                break;
            } else {
                r->pos += meta_len;
            }
        } else if (status == 0xF0 || status == 0xF7) {
            uint32_t sysex_len = read_variable_length_limited(r, track_end, &ok);
            if (!ok) {
                midi_set_error("Invalid sysex length (track %d)", track_idx);
                break;
            }
            if (sysex_len > track_end - r->pos) {
                r->pos = track_end;
                midi_set_error("Sysex length exceeds track (track %d)", track_idx);
                break;
            }
            r->pos += sysex_len;
        } else if (status >= 0xF0) {
            size_t needed = 0;
            switch (status) {
                case 0xF1: needed = 1; break;
                case 0xF2: needed = 2; break;
                case 0xF3: needed = 1; break;
                case 0xF6: needed = 0; break;
                case 0xF8: case 0xF9: case 0xFA:
                case 0xFB: case 0xFC: case 0xFD:
                case 0xFE: needed = 0; break;
                default: needed = 0; break;
            }
            if (needed > 0) {
                if (r->pos + needed > track_end) {
                    r->pos = track_end;
                    break;
                }
                r->pos += needed;
            }
            continue;
        } else {
            uint8_t cmd, channel, data1, data2 = 0;
            
            if (status & 0x80) {
                running_status = status;
                cmd = status & 0xF0;
                channel = status & 0x0F;
                if (r->pos >= track_end) {
                    midi_set_error("Truncated channel event (track %d)", track_idx);
                    break;
                }
                data1 = read_byte(r);
            } else {
                if (running_status == 0) {
                    continue;
                }
                cmd = running_status & 0xF0;
                channel = running_status & 0x0F;
                data1 = status;
            }
            
            if (cmd == MIDI_EVENT_NOTE_OFF || cmd == MIDI_EVENT_NOTE_ON ||
                cmd == MIDI_EVENT_POLY_PRESSURE || cmd == MIDI_EVENT_CONTROL_CHANGE ||
                cmd == MIDI_EVENT_PITCH_BEND) {
                if (r->pos >= track_end) {
                    midi_set_error("Missing data2 (track %d)", track_idx);
                    break;
                }
                data2 = read_byte(r);
            }
            
            MidiEvent ev = {0};
            ev.tick = tick;
            ev.type = cmd;
            ev.channel = channel;
            ev.data1 = data1;
            ev.data2 = data2;
            
            if (cmd == MIDI_EVENT_NOTE_ON && data2 == 0) {
                ev.type = MIDI_EVENT_NOTE_OFF;
            }
            
            if (cmd == MIDI_EVENT_NOTE_OFF || cmd == MIDI_EVENT_NOTE_ON) {
                midi_track_add_event(track, &ev);
            }
        }
    }
    
    r->pos = track_end;
    return true;
}

bool midi_file_load_from_memory(MidiFile* midi, const uint8_t* data, size_t size) {
    g_midi_error[0] = '\0';
    if (!midi) {
        midi_set_error("Null midi");
        return false;
    }
    if (!data || size < 14) {
        midi_set_error("Invalid data size: %zu", size);
        return false;
    }
    
    MidiReader reader = {data, size, 0};
    
    if (!check_header(&reader, "MThd")) {
        midi_set_error("Missing MThd header");
        return false;
    }
    
    uint32_t header_length = read_u32_be(&reader);
    if (header_length < 6) {
        midi_set_error("Invalid header length: %u", header_length);
        return false;
    }
    if (header_length > size || 8 + header_length > size) {
        midi_set_error("Header length exceeds file size");
        return false;
    }
    
    midi->format = read_u16_be(&reader);
    midi->num_tracks = read_u16_be(&reader);
    midi->ticks_per_quarter = read_u16_be(&reader);
    
    if (midi->num_tracks > MIDI_MAX_TRACKS) {
        midi->num_tracks = MIDI_MAX_TRACKS;
    }
    
    reader.pos = 8 + header_length;
    
    for (int i = 0; i < midi->num_tracks; i++) {
        if (!parse_track(midi, &reader, i)) {
            if (!g_midi_error[0]) {
                midi_set_error("Failed to parse track %d", i);
            }
            return false;
        }
    }
    
    return true;
}

bool midi_file_load(MidiFile* midi, const char* filename) {
    if (!midi || !filename) {
        midi_set_error("Invalid filename");
        return false;
    }
    FILE* f = fopen(filename, "rb");
    if (!f) {
        midi_set_error("Failed to open file");
        fprintf(stderr, "MIDI load error: %s\n", midi_get_error());
        return false;
    }
    
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    if (size <= 0 || size > 10 * 1024 * 1024) {
        fclose(f);
        midi_set_error("Invalid file size: %ld", size);
        fprintf(stderr, "MIDI load error: %s\n", midi_get_error());
        return false;
    }
    
    size_t file_size = (size_t)size;
    uint8_t* data = (uint8_t*)malloc(file_size);
    if (!data) {
        fclose(f);
        midi_set_error("Failed to allocate %ld bytes", size);
        fprintf(stderr, "MIDI load error: %s\n", midi_get_error());
        return false;
    }
    
    size_t read = fread(data, 1, file_size, f);
    fclose(f);
    
    if (read != file_size) {
        free(data);
        midi_set_error("Short read: %zu/%ld", read, size);
        fprintf(stderr, "MIDI load error: %s\n", midi_get_error());
        return false;
    }
    
    bool result = midi_file_load_from_memory(midi, data, size);
    free(data);
    if (!result) {
        if (!g_midi_error[0]) {
            midi_set_error("Unknown parse error");
        }
        fprintf(stderr, "MIDI load error: %s\n", midi_get_error());
    }
    return result;
}

size_t midi_file_get_track_count(const MidiFile* midi) {
    return midi ? midi->num_tracks : 0;
}

const MidiTrack* midi_file_get_track(const MidiFile* midi, int track_index) {
    if (!midi || track_index < 0 || track_index >= midi->num_tracks) {
        return NULL;
    }
    return &midi->tracks[track_index];
}

double midi_file_ticks_to_seconds(const MidiFile* midi, uint32_t ticks) {
    if (!midi || midi->ticks_per_quarter == 0) return 0.0;
    double us_per_tick = (double)midi->tempo / midi->ticks_per_quarter;
    return (ticks * us_per_tick) / 1000000.0;
}

uint32_t midi_file_seconds_to_ticks(const MidiFile* midi, double seconds) {
    if (!midi || midi->tempo == 0) return 0;
    double us = seconds * 1000000.0;
    double us_per_tick = (double)midi->tempo / midi->ticks_per_quarter;
    return (uint32_t)(us / us_per_tick);
}

void midi_track_add_event(MidiTrack* track, const MidiEvent* event) {
    if (!track || !event) return;
    if (track->event_count >= MIDI_MAX_EVENTS) return;
    
    if (track->event_count >= track->event_capacity) {
        size_t new_cap = track->event_capacity > 0 ? track->event_capacity * 2 : 1024;
        if (new_cap > MIDI_MAX_EVENTS) new_cap = MIDI_MAX_EVENTS;
        MidiEvent* new_events = (MidiEvent*)realloc(track->events, new_cap * sizeof(MidiEvent));
        if (!new_events) return;
        track->events = new_events;
        track->event_capacity = new_cap;
    }
    
    track->events[track->event_count++] = *event;
}

static int compare_events(const void* a, const void* b) {
    const MidiEvent* ea = (const MidiEvent*)a;
    const MidiEvent* eb = (const MidiEvent*)b;
    if (ea->tick != eb->tick) return ea->tick < eb->tick ? -1 : 1;
    return 0;
}

void midi_track_sort_events(MidiTrack* track) {
    if (track && track->events && track->event_count > 1) {
        qsort(track->events, track->event_count, sizeof(MidiEvent), compare_events);
    }
}
