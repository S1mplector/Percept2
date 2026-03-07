#pragma once

#include "../../ports/renderer_port.h"
#include "../../ports/input_port.h"
#include "../../core/midi/midi_file.h"
#include "../../core/midi/midi_player.h"
#include "../../core/engine/synth_engine.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define PIANO_ROLL_NOTE_MIN 24
#define PIANO_ROLL_NOTE_MAX 96
#define PIANO_KEY_WIDTH 40
#define PIANO_NOTE_HEIGHT 12

typedef struct {
    int note;
    uint32_t start_tick;
    uint32_t duration_ticks;
    uint8_t velocity;
    uint8_t channel;
    bool selected;
    uint32_t id;
} PianoNote;

typedef enum {
    PIANO_TOOL_SELECT,
    PIANO_TOOL_DRAW,
    PIANO_TOOL_ERASE,
    PIANO_TOOL_RESIZE
} PianoRollTool;

typedef struct {
    KeyCode key;
    bool ctrl;
    bool shift;
    bool alt;
} Keybinding;

typedef enum {
    PR_BIND_UNDO,
    PR_BIND_REDO,
    PR_BIND_TOOL_SELECT,
    PR_BIND_TOOL_DRAW,
    PR_BIND_TOOL_ERASE,
    PR_BIND_TOOL_RESIZE,
    PR_BIND_PLAY_PAUSE,
    PR_BIND_STOP,
    PR_BIND_COUNT
} PianoRollBinding;

typedef enum {
    PR_ACTION_ADD,
    PR_ACTION_DELETE,
    PR_ACTION_MOVE,
    PR_ACTION_RESIZE
} PianoRollActionType;

typedef struct {
    PianoRollActionType type;
    size_t count;
    PianoNote* before;
    PianoNote* after;
} PianoRollUndoEntry;

typedef struct {
    RendererPort* renderer;
    InputPort* input;
    SynthEngine* engine;
    MidiFile* midi;
    MidiPlayer* player;
    
    int view_x;
    int view_y;
    int pixels_per_tick;
    int note_height;
    int bounds_x;
    int bounds_y;
    int bounds_w;
    int bounds_h;
    
    int cursor_tick;
    int cursor_note;
    int selected_channel;
    int filter_track;
    int filter_channel;
    
    bool editing;
    bool dragging;
    bool box_selecting;
    int drag_start_x;
    int drag_start_y;
    int drag_last_x;
    int drag_last_y;
    uint32_t drag_note_id;
    PianoRollTool tool;
    int drag_mode;
    
    PianoNote* notes;
    size_t note_count;
    size_t note_capacity;
    uint32_t next_note_id;
    
    int grid_snap;
    int zoom_level;
    
    Keybinding bindings[PR_BIND_COUNT];
    
    PianoRollUndoEntry* undo_stack;
    size_t undo_count;
    size_t undo_capacity;
    size_t undo_cursor;
    
    PianoNote* drag_before;
    size_t drag_before_count;
} PianoRoll;

void piano_roll_init(PianoRoll* pr, RendererPort* renderer, InputPort* input, SynthEngine* engine);
void piano_roll_destroy(PianoRoll* pr);

void piano_roll_set_midi(PianoRoll* pr, MidiFile* midi);
void piano_roll_set_player(PianoRoll* pr, MidiPlayer* player);

void piano_roll_handle_input(PianoRoll* pr);
void piano_roll_update(PianoRoll* pr);
void piano_roll_render(PianoRoll* pr, int x, int y, int width, int height);

void piano_roll_load_from_midi_track(PianoRoll* pr, const MidiTrack* track, int channel_filter);
void piano_roll_add_note(PianoRoll* pr, int note, uint32_t start, uint32_t duration, uint8_t velocity);
void piano_roll_delete_selected(PianoRoll* pr);

void piano_roll_zoom_in(PianoRoll* pr);
void piano_roll_zoom_out(PianoRoll* pr);
void piano_roll_scroll(PianoRoll* pr, int dx, int dy);
void piano_roll_undo(PianoRoll* pr);
void piano_roll_redo(PianoRoll* pr);
void piano_roll_select_all(PianoRoll* pr);
void piano_roll_select_none(PianoRoll* pr);
void piano_roll_select_invert(PianoRoll* pr);

#ifdef __cplusplus
}
#endif
