#pragma once

#include "../../core/sequencer/sequencer.h"
#include "../../core/engine/synth_engine.h"
#include "../../core/midi/midi_file.h"
#include "../../core/midi/midi_player.h"
#include "../../core/synthesis/instrument.h"
#include "../../ports/renderer_port.h"
#include "../../ports/input_port.h"
#include "piano_roll.h"
#include "../ui/toast.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    EDITOR_MODE_PATTERN,
    EDITOR_MODE_PIANO_ROLL,
    EDITOR_MODE_SONG,
    EDITOR_MODE_INSTRUMENT,
    EDITOR_MODE_MIXER
} EditorMode;

typedef struct {
    Sequencer* sequencer;
    SynthEngine* engine;
    RendererPort* renderer;
    InputPort* input;
    
    EditorMode mode;
    int cursor_row;
    int cursor_channel;
    int cursor_column;
    int cursor_nibble;
    int view_offset;
    int octave;
    bool editing;
    int step;
    
    int selected_pattern;
    int selected_instrument;
    PatternCell row_clipboard[8];
    bool row_clipboard_valid;
    InstrumentBank* instruments;
    
    MidiFile* midi_file;
    MidiPlayer midi_player;
    PianoRoll piano_roll;
    
    bool show_piano_roll;
    float channel_volumes[8];
    float channel_pans[8];
    bool channel_muted[8];
    bool channel_solo[8];
    ToastQueue toasts;

    int menu_open;
    bool request_quit;
    int last_width;
    int last_height;
    float resize_timer;
} Editor;

void editor_init(Editor* ed, Sequencer* seq, SynthEngine* engine, InstrumentBank* instruments,
                 RendererPort* renderer, InputPort* input);
void editor_destroy(Editor* ed);
void editor_update(Editor* ed, float dt);
void editor_render(Editor* ed);
void editor_handle_input(Editor* ed);

bool editor_load_midi(Editor* ed, const char* filename);
void editor_toggle_view(Editor* ed);
void editor_set_channel_volume(Editor* ed, int channel, float volume);
void editor_set_channel_pan(Editor* ed, int channel, float pan);
void editor_toggle_channel_mute(Editor* ed, int channel);
void editor_toggle_channel_solo(Editor* ed, int channel);

#ifdef __cplusplus
}
#endif
