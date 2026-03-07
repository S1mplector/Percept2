#pragma once

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define MIDI_MAX_TRACKS 16
#define MIDI_MAX_EVENTS 65536

typedef enum {
    MIDI_EVENT_NOTE_OFF = 0x80,
    MIDI_EVENT_NOTE_ON = 0x90,
    MIDI_EVENT_POLY_PRESSURE = 0xA0,
    MIDI_EVENT_CONTROL_CHANGE = 0xB0,
    MIDI_EVENT_PROGRAM_CHANGE = 0xC0,
    MIDI_EVENT_CHANNEL_PRESSURE = 0xD0,
    MIDI_EVENT_PITCH_BEND = 0xE0,
    MIDI_EVENT_META = 0xFF
} MidiEventType;

typedef enum {
    MIDI_META_SEQUENCE_NUM = 0x00,
    MIDI_META_TEXT = 0x01,
    MIDI_META_COPYRIGHT = 0x02,
    MIDI_META_TRACK_NAME = 0x03,
    MIDI_META_INSTRUMENT = 0x04,
    MIDI_META_LYRIC = 0x05,
    MIDI_META_MARKER = 0x06,
    MIDI_META_CUE_POINT = 0x07,
    MIDI_META_CHANNEL_PREFIX = 0x20,
    MIDI_META_END_OF_TRACK = 0x2F,
    MIDI_META_TEMPO = 0x51,
    MIDI_META_SMPTE_OFFSET = 0x54,
    MIDI_META_TIME_SIG = 0x58,
    MIDI_META_KEY_SIG = 0x59
} MidiMetaType;

typedef struct {
    uint32_t tick;
    uint8_t type;
    uint8_t channel;
    uint8_t data1;
    uint8_t data2;
    uint32_t tempo;
} MidiEvent;

typedef struct {
    char name[64];
    MidiEvent* events;
    size_t event_count;
    size_t event_capacity;
} MidiTrack;

typedef struct {
    uint16_t format;
    uint16_t num_tracks;
    uint16_t ticks_per_quarter;
    uint32_t tempo;
    MidiTrack tracks[MIDI_MAX_TRACKS];
    uint32_t total_ticks;
} MidiFile;

MidiFile* midi_file_create(void);
void midi_file_destroy(MidiFile* midi);

bool midi_file_load(MidiFile* midi, const char* filename);
bool midi_file_load_from_memory(MidiFile* midi, const uint8_t* data, size_t size);

size_t midi_file_get_track_count(const MidiFile* midi);
const MidiTrack* midi_file_get_track(const MidiFile* midi, int track_index);

double midi_file_ticks_to_seconds(const MidiFile* midi, uint32_t ticks);
uint32_t midi_file_seconds_to_ticks(const MidiFile* midi, double seconds);

void midi_track_add_event(MidiTrack* track, const MidiEvent* event);
void midi_track_sort_events(MidiTrack* track);

#ifdef __cplusplus
}
#endif
