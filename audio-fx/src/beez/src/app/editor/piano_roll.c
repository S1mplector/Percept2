#include "piano_roll.h"
#include "../ui/theme.h"
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <stdio.h>

static const char* note_names[] = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
static const bool is_black_key[] = {false, true, false, true, false, false, true, false, true, false, true, false};

typedef enum {
    PR_DRAG_NONE,
    PR_DRAG_MOVE,
    PR_DRAG_RESIZE,
    PR_DRAG_BOX,
    PR_DRAG_DRAW
} PianoRollDragMode;

static int clamp_i(int value, int min, int max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
}

static int max_i(int a, int b) {
    return a > b ? a : b;
}

static int min_i(int a, int b) {
    return a < b ? a : b;
}

static int snap_tick(const PianoRoll* pr, int tick) {
    if (!pr || pr->grid_snap <= 0) return tick;
    int snap = pr->grid_snap;
    if (tick < 0) tick = 0;
    return (tick / snap) * snap;
}

static void piano_roll_set_defaults(PianoRoll* pr) {
    pr->pixels_per_tick = 1;
    pr->note_height = PIANO_NOTE_HEIGHT;
    pr->view_x = 0;
    pr->view_y = 60 * pr->note_height;
    pr->grid_snap = 120;
    pr->zoom_level = 2;
    pr->selected_channel = 0;
    pr->filter_track = -1;
    pr->filter_channel = -1;
    pr->tool = PIANO_TOOL_DRAW;
    pr->next_note_id = 1;
    pr->drag_mode = PR_DRAG_NONE;
}

static void piano_roll_bind_defaults(PianoRoll* pr) {
    pr->bindings[PR_BIND_UNDO] = (Keybinding){KEY_Z, true, false, false};
    pr->bindings[PR_BIND_REDO] = (Keybinding){KEY_Y, true, false, false};
    pr->bindings[PR_BIND_TOOL_SELECT] = (Keybinding){KEY_1, false, false, false};
    pr->bindings[PR_BIND_TOOL_DRAW] = (Keybinding){KEY_2, false, false, false};
    pr->bindings[PR_BIND_TOOL_ERASE] = (Keybinding){KEY_3, false, false, false};
    pr->bindings[PR_BIND_TOOL_RESIZE] = (Keybinding){KEY_4, false, false, false};
    pr->bindings[PR_BIND_PLAY_PAUSE] = (Keybinding){KEY_SPACE, false, false, false};
    pr->bindings[PR_BIND_STOP] = (Keybinding){KEY_ENTER, false, false, false};
}

static KeyCode keycode_from_name(const char* name) {
    if (!name || !name[0]) return KEY_UNKNOWN;
    if (strlen(name) == 1 && isalpha((unsigned char)name[0])) {
        char c = (char)toupper((unsigned char)name[0]);
        return (KeyCode)(KEY_A + (c - 'A'));
    }
    if (strlen(name) == 1 && isdigit((unsigned char)name[0])) {
        char c = name[0];
        return (KeyCode)(KEY_0 + (c - '0'));
    }
    if (name[0] == 'F' && isdigit((unsigned char)name[1])) {
        int f = atoi(name + 1);
        if (f >= 1 && f <= 12) {
            return (KeyCode)(KEY_F1 + (f - 1));
        }
    }
    if (strcmp(name, "UP") == 0) return KEY_UP;
    if (strcmp(name, "DOWN") == 0) return KEY_DOWN;
    if (strcmp(name, "LEFT") == 0) return KEY_LEFT;
    if (strcmp(name, "RIGHT") == 0) return KEY_RIGHT;
    if (strcmp(name, "SPACE") == 0) return KEY_SPACE;
    if (strcmp(name, "ENTER") == 0) return KEY_ENTER;
    if (strcmp(name, "ESC") == 0 || strcmp(name, "ESCAPE") == 0) return KEY_ESCAPE;
    if (strcmp(name, "TAB") == 0) return KEY_TAB;
    if (strcmp(name, "BACKSPACE") == 0) return KEY_BACKSPACE;
    return KEY_UNKNOWN;
}

static void parse_keybinding(const char* value, Keybinding* out) {
    if (!value || !out) return;
    Keybinding kb = {KEY_UNKNOWN, false, false, false};
    char buf[64];
    snprintf(buf, sizeof(buf), "%s", value);
    for (char* p = buf; *p; ++p) {
        if (*p == '+' || *p == '-') *p = ' ';
    }
    char* token = strtok(buf, " ");
    while (token) {
        char upper[32];
        size_t len = strlen(token);
        if (len >= sizeof(upper)) len = sizeof(upper) - 1;
        for (size_t i = 0; i < len; i++) {
            upper[i] = (char)toupper((unsigned char)token[i]);
        }
        upper[len] = '\0';
        if (strcmp(upper, "CTRL") == 0 || strcmp(upper, "CONTROL") == 0) {
            kb.ctrl = true;
        } else if (strcmp(upper, "SHIFT") == 0) {
            kb.shift = true;
        } else if (strcmp(upper, "ALT") == 0 || strcmp(upper, "OPTION") == 0) {
            kb.alt = true;
        } else {
            KeyCode key = keycode_from_name(upper);
            if (key != KEY_UNKNOWN) {
                kb.key = key;
            }
        }
        token = strtok(NULL, " ");
    }
    if (kb.key != KEY_UNKNOWN) {
        *out = kb;
    }
}

static void piano_roll_load_keybindings(PianoRoll* pr, const char* path) {
    if (!pr || !path) return;
    FILE* f = fopen(path, "r");
    if (!f) return;
    
    char line[128];
    while (fgets(line, sizeof(line), f)) {
        char* eq = strchr(line, '=');
        if (!eq) continue;
        *eq = '\0';
        char* key = line;
        char* val = eq + 1;
        while (*key == ' ' || *key == '\t') key++;
        while (*val == ' ' || *val == '\t') val++;
        char* end = val + strlen(val);
        while (end > val && (end[-1] == '\n' || end[-1] == '\r' || end[-1] == ' ' || end[-1] == '\t')) {
            end--;
        }
        *end = '\0';
        
        if (strcmp(key, "pianoroll.undo") == 0) parse_keybinding(val, &pr->bindings[PR_BIND_UNDO]);
        else if (strcmp(key, "pianoroll.redo") == 0) parse_keybinding(val, &pr->bindings[PR_BIND_REDO]);
        else if (strcmp(key, "pianoroll.tool_select") == 0) parse_keybinding(val, &pr->bindings[PR_BIND_TOOL_SELECT]);
        else if (strcmp(key, "pianoroll.tool_draw") == 0) parse_keybinding(val, &pr->bindings[PR_BIND_TOOL_DRAW]);
        else if (strcmp(key, "pianoroll.tool_erase") == 0) parse_keybinding(val, &pr->bindings[PR_BIND_TOOL_ERASE]);
        else if (strcmp(key, "pianoroll.tool_resize") == 0) parse_keybinding(val, &pr->bindings[PR_BIND_TOOL_RESIZE]);
        else if (strcmp(key, "pianoroll.play_pause") == 0) parse_keybinding(val, &pr->bindings[PR_BIND_PLAY_PAUSE]);
        else if (strcmp(key, "pianoroll.stop") == 0) parse_keybinding(val, &pr->bindings[PR_BIND_STOP]);
    }
    fclose(f);
}

static bool keybinding_pressed(InputPort* in, const Keybinding* kb) {
    if (!in || !kb || kb->key == KEY_UNKNOWN) return false;
    bool ctrl = in->is_key_down(in, KEY_CTRL);
    bool shift = in->is_key_down(in, KEY_SHIFT);
    bool alt = in->is_key_down(in, KEY_ALT);
    if (ctrl != kb->ctrl || shift != kb->shift || alt != kb->alt) return false;
    return in->is_key_pressed(in, kb->key);
}

static void undo_entry_free(PianoRollUndoEntry* entry) {
    if (!entry) return;
    free(entry->before);
    free(entry->after);
    entry->before = NULL;
    entry->after = NULL;
    entry->count = 0;
}

static void piano_roll_clear_undo(PianoRoll* pr) {
    if (!pr) return;
    for (size_t i = 0; i < pr->undo_count; i++) {
        undo_entry_free(&pr->undo_stack[i]);
    }
    free(pr->undo_stack);
    pr->undo_stack = NULL;
    pr->undo_count = 0;
    pr->undo_capacity = 0;
    pr->undo_cursor = 0;
}

static void piano_roll_push_undo(PianoRoll* pr, PianoRollActionType type,
                                 const PianoNote* before, const PianoNote* after, size_t count) {
    if (!pr || count == 0) return;
    
    while (pr->undo_count > pr->undo_cursor) {
        undo_entry_free(&pr->undo_stack[pr->undo_count - 1]);
        pr->undo_count--;
    }
    
    if (pr->undo_count >= pr->undo_capacity) {
        size_t new_cap = pr->undo_capacity > 0 ? pr->undo_capacity * 2 : 64;
        PianoRollUndoEntry* next = (PianoRollUndoEntry*)realloc(pr->undo_stack, new_cap * sizeof(PianoRollUndoEntry));
        if (!next) return;
        pr->undo_stack = next;
        pr->undo_capacity = new_cap;
    }
    
    PianoRollUndoEntry* entry = &pr->undo_stack[pr->undo_count++];
    entry->type = type;
    entry->count = count;
    entry->before = NULL;
    entry->after = NULL;
    if (before) {
        entry->before = (PianoNote*)malloc(sizeof(PianoNote) * count);
        if (entry->before) memcpy(entry->before, before, sizeof(PianoNote) * count);
    }
    if (after) {
        entry->after = (PianoNote*)malloc(sizeof(PianoNote) * count);
        if (entry->after) memcpy(entry->after, after, sizeof(PianoNote) * count);
    }
    pr->undo_cursor = pr->undo_count;
}

static int find_note_index_by_id(const PianoRoll* pr, uint32_t id) {
    if (!pr) return -1;
    for (size_t i = 0; i < pr->note_count; i++) {
        if (pr->notes[i].id == id) return (int)i;
    }
    return -1;
}

static void piano_roll_remove_note_by_id(PianoRoll* pr, uint32_t id) {
    int idx = find_note_index_by_id(pr, id);
    if (idx < 0) return;
    for (size_t i = (size_t)idx + 1; i < pr->note_count; i++) {
        pr->notes[i - 1] = pr->notes[i];
    }
    if (pr->note_count > 0) pr->note_count--;
}

static void piano_roll_add_note_data(PianoRoll* pr, const PianoNote* note) {
    if (!pr || !note) return;
    if (pr->note_count >= pr->note_capacity) {
        size_t next_cap = pr->note_capacity > 0 ? pr->note_capacity * 2 : 256;
        PianoNote* next = (PianoNote*)realloc(pr->notes, next_cap * sizeof(PianoNote));
        if (!next) return;
        pr->notes = next;
        pr->note_capacity = next_cap;
    }
    pr->notes[pr->note_count++] = *note;
}

static void piano_roll_apply_notes(PianoRoll* pr, const PianoNote* notes, size_t count) {
    if (!pr || !notes || count == 0) return;
    for (size_t i = 0; i < count; i++) {
        int idx = find_note_index_by_id(pr, notes[i].id);
        if (idx >= 0) {
            pr->notes[idx] = notes[i];
        }
    }
}

void piano_roll_undo(PianoRoll* pr) {
    if (!pr || pr->undo_cursor == 0) return;
    PianoRollUndoEntry* entry = &pr->undo_stack[pr->undo_cursor - 1];
    
    if (entry->type == PR_ACTION_ADD) {
        for (size_t i = 0; i < entry->count; i++) {
            piano_roll_remove_note_by_id(pr, entry->after[i].id);
        }
    } else if (entry->type == PR_ACTION_DELETE) {
        for (size_t i = 0; i < entry->count; i++) {
            piano_roll_add_note_data(pr, &entry->before[i]);
        }
    } else {
        piano_roll_apply_notes(pr, entry->before, entry->count);
    }
    
    pr->undo_cursor--;
}

void piano_roll_redo(PianoRoll* pr) {
    if (!pr || pr->undo_cursor >= pr->undo_count) return;
    PianoRollUndoEntry* entry = &pr->undo_stack[pr->undo_cursor];
    
    if (entry->type == PR_ACTION_ADD) {
        for (size_t i = 0; i < entry->count; i++) {
            piano_roll_add_note_data(pr, &entry->after[i]);
        }
    } else if (entry->type == PR_ACTION_DELETE) {
        for (size_t i = 0; i < entry->count; i++) {
            piano_roll_remove_note_by_id(pr, entry->before[i].id);
        }
    } else {
        piano_roll_apply_notes(pr, entry->after, entry->count);
    }
    
    pr->undo_cursor++;
}

void piano_roll_select_all(PianoRoll* pr) {
    if (!pr) return;
    for (size_t i = 0; i < pr->note_count; i++) {
        pr->notes[i].selected = true;
    }
}

void piano_roll_select_none(PianoRoll* pr) {
    if (!pr) return;
    for (size_t i = 0; i < pr->note_count; i++) {
        pr->notes[i].selected = false;
    }
}

void piano_roll_select_invert(PianoRoll* pr) {
    if (!pr) return;
    for (size_t i = 0; i < pr->note_count; i++) {
        pr->notes[i].selected = !pr->notes[i].selected;
    }
}

static bool note_hit_test(const PianoRoll* pr, const PianoNote* n, int mx, int my) {
    if (!pr || !n) return false;
    int grid_x = pr->bounds_x + PIANO_KEY_WIDTH;
    int x = grid_x + (int)(n->start_tick * pr->pixels_per_tick) - pr->view_x;
    int w = (int)(n->duration_ticks * pr->pixels_per_tick);
    if (w < 2) w = 2;
    int note_offset = PIANO_ROLL_NOTE_MAX - 1 - n->note;
    int y = pr->bounds_y + note_offset * pr->note_height - pr->view_y;
    return (mx >= x && mx <= x + w && my >= y && my <= y + pr->note_height);
}

static void note_rect(const PianoRoll* pr, const PianoNote* n, int* x, int* y, int* w, int* h) {
    if (!pr || !n || !x || !y || !w || !h) return;
    int grid_x = pr->bounds_x + PIANO_KEY_WIDTH;
    *x = grid_x + (int)(n->start_tick * pr->pixels_per_tick) - pr->view_x;
    *w = (int)(n->duration_ticks * pr->pixels_per_tick);
    if (*w < 2) *w = 2;
    int note_offset = PIANO_ROLL_NOTE_MAX - 1 - n->note;
    *y = pr->bounds_y + note_offset * pr->note_height - pr->view_y;
    *h = pr->note_height;
}

static bool rects_intersect(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
    if (aw <= 0 || ah <= 0 || bw <= 0 || bh <= 0) return false;
    return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
}

static int tick_from_mouse(const PianoRoll* pr, int mx) {
    if (!pr || pr->pixels_per_tick <= 0) return 0;
    int grid_x = pr->bounds_x + PIANO_KEY_WIDTH;
    int x = mx - grid_x + pr->view_x;
    if (x < 0) x = 0;
    return x / pr->pixels_per_tick;
}

static int note_from_mouse(const PianoRoll* pr, int my) {
    if (!pr || pr->note_height <= 0) return PIANO_ROLL_NOTE_MIN;
    int y = my - pr->bounds_y + pr->view_y;
    int note_offset = y / pr->note_height;
    int note = PIANO_ROLL_NOTE_MAX - 1 - note_offset;
    return clamp_i(note, PIANO_ROLL_NOTE_MIN, PIANO_ROLL_NOTE_MAX - 1);
}

static void clear_selection(PianoRoll* pr) {
    for (size_t i = 0; i < pr->note_count; i++) {
        pr->notes[i].selected = false;
    }
}

static size_t collect_selected_notes(const PianoRoll* pr, PianoNote* out, size_t max_count) {
    size_t count = 0;
    for (size_t i = 0; i < pr->note_count && count < max_count; i++) {
        if (pr->notes[i].selected) {
            out[count++] = pr->notes[i];
        }
    }
    return count;
}

void piano_roll_init(PianoRoll* pr, RendererPort* renderer, InputPort* input, SynthEngine* engine) {
    if (!pr) return;
    memset(pr, 0, sizeof(PianoRoll));
    
    pr->renderer = renderer;
    pr->input = input;
    pr->engine = engine;
    piano_roll_set_defaults(pr);
    piano_roll_bind_defaults(pr);
    piano_roll_load_keybindings(pr, "keybindings.cfg");
    
    pr->note_capacity = 256;
    pr->notes = (PianoNote*)malloc(pr->note_capacity * sizeof(PianoNote));
    pr->note_count = 0;
}

void piano_roll_destroy(PianoRoll* pr) {
    if (!pr) return;
    free(pr->notes);
    pr->notes = NULL;
    pr->note_count = 0;
    piano_roll_clear_undo(pr);
    free(pr->drag_before);
    pr->drag_before = NULL;
    pr->drag_before_count = 0;
}

void piano_roll_set_midi(PianoRoll* pr, MidiFile* midi) {
    if (!pr) return;
    pr->midi = midi;
    pr->note_count = 0;
    
    if (midi) {
        int best_track = 0;
        size_t best_count = 0;
        for (int t = 0; t < midi->num_tracks; t++) {
            size_t count = 0;
            const MidiTrack* track = &midi->tracks[t];
            for (size_t i = 0; i < track->event_count; i++) {
                const MidiEvent* ev = &track->events[i];
                if (ev->type == MIDI_EVENT_NOTE_ON && ev->data2 > 0) {
                    count++;
                }
            }
            if (count > best_count) {
                best_count = count;
                best_track = t;
            }
        }
        pr->filter_track = midi->num_tracks > 0 ? best_track : -1;
        pr->filter_channel = -1;
        for (int t = 0; t < midi->num_tracks; t++) {
            if (pr->filter_track == -1 || pr->filter_track == t) {
                piano_roll_load_from_midi_track(pr, &midi->tracks[t], pr->filter_channel);
            }
        }
    }
}

void piano_roll_set_player(PianoRoll* pr, MidiPlayer* player) {
    if (!pr) return;
    pr->player = player;
}

void piano_roll_load_from_midi_track(PianoRoll* pr, const MidiTrack* track, int channel_filter) {
    if (!pr || !track) return;
    
    uint32_t note_starts[16][128] = {{0}};
    uint8_t note_velocities[16][128] = {{0}};
    bool note_active[16][128] = {{false}};
    
    for (size_t i = 0; i < track->event_count; i++) {
        const MidiEvent* ev = &track->events[i];
        int ch = ev->channel & 0x0F;
        if (channel_filter >= 0 && ch != channel_filter) continue;
        
        if (ev->type == MIDI_EVENT_NOTE_ON && ev->data2 > 0) {
            int note = ev->data1;
            if (note < 128) {
                note_starts[ch][note] = ev->tick;
                note_velocities[ch][note] = ev->data2;
                note_active[ch][note] = true;
            }
        } else if (ev->type == MIDI_EVENT_NOTE_OFF || 
                   (ev->type == MIDI_EVENT_NOTE_ON && ev->data2 == 0)) {
            int note = ev->data1;
            if (note < 128 && note_active[ch][note]) {
                uint32_t duration = ev->tick - note_starts[ch][note];
                PianoNote n = {0};
                n.note = note;
                n.start_tick = note_starts[ch][note];
                n.duration_ticks = duration > 0 ? duration : 1;
                n.velocity = note_velocities[ch][note];
                n.channel = (uint8_t)(ch % 8);
                n.selected = false;
                n.id = pr->next_note_id++;
                piano_roll_add_note_data(pr, &n);
                note_active[ch][note] = false;
            }
        }
    }
}

void piano_roll_add_note(PianoRoll* pr, int note, uint32_t start, uint32_t duration, uint8_t velocity) {
    if (!pr || note < 0 || note >= 128) return;
    if (pr->note_count >= pr->note_capacity) {
        pr->note_capacity *= 2;
        pr->notes = (PianoNote*)realloc(pr->notes, pr->note_capacity * sizeof(PianoNote));
        if (!pr->notes) {
            pr->note_capacity = pr->note_count;
            return;
        }
    }
    
    PianoNote* n = &pr->notes[pr->note_count++];
    n->note = note;
    n->start_tick = start;
    n->duration_ticks = duration;
    n->velocity = velocity;
    n->channel = pr->selected_channel;
    n->selected = false;
    n->id = pr->next_note_id++;
}

void piano_roll_delete_selected(PianoRoll* pr) {
    if (!pr) return;
    size_t selected_count = 0;
    for (size_t i = 0; i < pr->note_count; i++) {
        if (pr->notes[i].selected) selected_count++;
    }
    if (selected_count == 0) return;
    
    PianoNote* before = (PianoNote*)malloc(sizeof(PianoNote) * selected_count);
    if (!before) return;
    size_t idx = 0;
    size_t write = 0;
    for (size_t read = 0; read < pr->note_count; read++) {
        if (!pr->notes[read].selected) {
            if (write != read) {
                pr->notes[write] = pr->notes[read];
            }
            write++;
        } else if (idx < selected_count) {
            before[idx++] = pr->notes[read];
        }
    }
    pr->note_count = write;
    piano_roll_push_undo(pr, PR_ACTION_DELETE, before, NULL, selected_count);
    free(before);
}

void piano_roll_handle_input(PianoRoll* pr) {
    if (!pr || !pr->input) return;
    InputPort* in = pr->input;
    
    if (keybinding_pressed(in, &pr->bindings[PR_BIND_UNDO])) {
        piano_roll_undo(pr);
    }
    if (keybinding_pressed(in, &pr->bindings[PR_BIND_REDO])) {
        piano_roll_redo(pr);
    }
    if (keybinding_pressed(in, &pr->bindings[PR_BIND_TOOL_SELECT])) pr->tool = PIANO_TOOL_SELECT;
    if (keybinding_pressed(in, &pr->bindings[PR_BIND_TOOL_DRAW])) pr->tool = PIANO_TOOL_DRAW;
    if (keybinding_pressed(in, &pr->bindings[PR_BIND_TOOL_ERASE])) pr->tool = PIANO_TOOL_ERASE;
    if (keybinding_pressed(in, &pr->bindings[PR_BIND_TOOL_RESIZE])) pr->tool = PIANO_TOOL_RESIZE;
    
    if (keybinding_pressed(in, &pr->bindings[PR_BIND_PLAY_PAUSE]) && pr->player) {
        if (midi_player_is_playing(pr->player)) {
            midi_player_pause(pr->player);
        } else {
            midi_player_play(pr->player);
        }
    }
    if (keybinding_pressed(in, &pr->bindings[PR_BIND_STOP]) && pr->player) {
        midi_player_stop(pr->player);
    }
    
    if (pr->midi) {
        if (in->is_key_pressed(in, KEY_F8)) {
            int tracks = pr->midi->num_tracks;
            if (tracks > 0) {
                if (in->is_key_down(in, KEY_SHIFT)) {
                    pr->filter_channel--;
                    if (pr->filter_channel < -1) pr->filter_channel = 15;
                } else {
                    pr->filter_track--;
                    if (pr->filter_track < 0) pr->filter_track = tracks - 1;
                }
                pr->note_count = 0;
                for (int t = 0; t < tracks; t++) {
                    if (pr->filter_track == -1 || pr->filter_track == t) {
                        piano_roll_load_from_midi_track(pr, &pr->midi->tracks[t], pr->filter_channel);
                    }
                }
            }
        }
        if (in->is_key_pressed(in, KEY_F9)) {
            int tracks = pr->midi->num_tracks;
            if (tracks > 0) {
                if (in->is_key_down(in, KEY_SHIFT)) {
                    pr->filter_channel++;
                    if (pr->filter_channel > 15) pr->filter_channel = -1;
                } else {
                    pr->filter_track++;
                    if (pr->filter_track >= tracks) pr->filter_track = 0;
                }
                pr->note_count = 0;
                for (int t = 0; t < tracks; t++) {
                    if (pr->filter_track == -1 || pr->filter_track == t) {
                        piano_roll_load_from_midi_track(pr, &pr->midi->tracks[t], pr->filter_channel);
                    }
                }
            }
        }
    }
    
    if (in->is_key_down(in, KEY_LEFT)) {
        pr->view_x -= 20;
        if (pr->view_x < 0) pr->view_x = 0;
    }
    if (in->is_key_down(in, KEY_RIGHT)) {
        pr->view_x += 20;
    }
    if (in->is_key_down(in, KEY_UP)) {
        pr->view_y -= 10;
        if (pr->view_y < 0) pr->view_y = 0;
    }
    if (in->is_key_down(in, KEY_DOWN)) {
        pr->view_y += 10;
    }
    
    int wheel = in->get_mouse_wheel(in);
    if (wheel > 0) {
        piano_roll_zoom_in(pr);
    } else if (wheel < 0) {
        piano_roll_zoom_out(pr);
    }
    
    int mx = 0, my = 0;
    in->get_mouse_position(in, &mx, &my);
    bool inside = (mx >= pr->bounds_x && mx < pr->bounds_x + pr->bounds_w &&
                   my >= pr->bounds_y && my < pr->bounds_y + pr->bounds_h);
    
    if (inside && in->is_mouse_clicked(in, MOUSE_LEFT)) {
        pr->dragging = true;
        pr->drag_start_x = mx;
        pr->drag_start_y = my;
        pr->drag_last_x = mx;
        pr->drag_last_y = my;
        pr->drag_note_id = 0;
        pr->drag_mode = PR_DRAG_NONE;
        
        PianoNote* hit = NULL;
        for (size_t i = pr->note_count; i-- > 0;) {
            if (note_hit_test(pr, &pr->notes[i], mx, my)) {
                hit = &pr->notes[i];
                break;
            }
        }
        
        if (pr->tool == PIANO_TOOL_ERASE) {
            if (hit) {
                PianoNote before = *hit;
                piano_roll_remove_note_by_id(pr, hit->id);
                piano_roll_push_undo(pr, PR_ACTION_DELETE, &before, NULL, 1);
            }
            pr->dragging = false;
            return;
        }
        
        if (hit) {
            if (!hit->selected || !in->is_key_down(in, KEY_SHIFT)) {
                clear_selection(pr);
                hit->selected = true;
            } else if (in->is_key_down(in, KEY_SHIFT)) {
                hit->selected = !hit->selected;
            }
            pr->drag_note_id = hit->id;
            
            int grid_x = pr->bounds_x + PIANO_KEY_WIDTH;
            int note_x = grid_x + (int)(hit->start_tick * pr->pixels_per_tick) - pr->view_x;
            int note_w = (int)(hit->duration_ticks * pr->pixels_per_tick);
            if (note_w < 2) note_w = 2;
            bool near_right = mx >= note_x + note_w - 6 && mx <= note_x + note_w + 2;
            
            if (pr->tool == PIANO_TOOL_RESIZE || near_right) {
                pr->drag_mode = PR_DRAG_RESIZE;
            } else {
                pr->drag_mode = PR_DRAG_MOVE;
            }
        } else {
            if (pr->tool == PIANO_TOOL_DRAW) {
                clear_selection(pr);
                PianoNote n = {0};
                n.note = note_from_mouse(pr, my);
                n.start_tick = (uint32_t)snap_tick(pr, tick_from_mouse(pr, mx));
                n.duration_ticks = (uint32_t)max_i(pr->grid_snap, 1);
                n.velocity = 100;
                n.channel = (uint8_t)pr->selected_channel;
                n.selected = true;
                n.id = pr->next_note_id++;
                piano_roll_add_note_data(pr, &n);
                pr->drag_note_id = n.id;
                pr->drag_mode = PR_DRAG_DRAW;
            } else {
                clear_selection(pr);
                pr->drag_mode = PR_DRAG_BOX;
                pr->box_selecting = true;
            }
        }
        
        free(pr->drag_before);
        pr->drag_before = NULL;
        pr->drag_before_count = 0;
        if (pr->drag_mode == PR_DRAG_MOVE || pr->drag_mode == PR_DRAG_RESIZE) {
            size_t sel_count = 0;
            for (size_t i = 0; i < pr->note_count; i++) {
                if (pr->notes[i].selected) sel_count++;
            }
            if (sel_count > 0) {
                pr->drag_before = (PianoNote*)malloc(sizeof(PianoNote) * sel_count);
                if (pr->drag_before) {
                    pr->drag_before_count = collect_selected_notes(pr, pr->drag_before, sel_count);
                }
            }
        }
    }
    
    if (pr->dragging && in->is_mouse_down(in, MOUSE_LEFT)) {
        int dx = mx - pr->drag_start_x;
        int dy = my - pr->drag_start_y;
        if (pr->drag_mode == PR_DRAG_MOVE && pr->drag_before) {
            int tick_delta = dx / pr->pixels_per_tick;
            int note_delta = -(dy / pr->note_height);
            for (size_t i = 0; i < pr->drag_before_count; i++) {
                PianoNote n = pr->drag_before[i];
                int new_tick = (int)n.start_tick + tick_delta;
                int new_note = n.note + note_delta;
                n.start_tick = (uint32_t)snap_tick(pr, max_i(0, new_tick));
                n.note = clamp_i(new_note, PIANO_ROLL_NOTE_MIN, PIANO_ROLL_NOTE_MAX - 1);
                piano_roll_apply_notes(pr, &n, 1);
            }
        } else if (pr->drag_mode == PR_DRAG_RESIZE && pr->drag_before) {
            int tick_delta = dx / pr->pixels_per_tick;
            for (size_t i = 0; i < pr->drag_before_count; i++) {
                PianoNote n = pr->drag_before[i];
                int new_len = (int)n.duration_ticks + tick_delta;
                if (new_len < pr->grid_snap) new_len = pr->grid_snap;
                n.duration_ticks = (uint32_t)new_len;
                piano_roll_apply_notes(pr, &n, 1);
            }
        } else if (pr->drag_mode == PR_DRAG_BOX) {
            pr->drag_last_x = mx;
            pr->drag_last_y = my;
        } else if (pr->drag_mode == PR_DRAG_DRAW) {
            int idx = find_note_index_by_id(pr, pr->drag_note_id);
            if (idx >= 0) {
                int start_tick = (int)pr->notes[idx].start_tick;
                int current = tick_from_mouse(pr, mx);
                int end_tick = max_i(start_tick + 1, snap_tick(pr, current));
                pr->notes[idx].duration_ticks = (uint32_t)(end_tick - start_tick);
            }
        }
    }
    
    if (pr->dragging && !in->is_mouse_down(in, MOUSE_LEFT)) {
        if (pr->drag_mode == PR_DRAG_MOVE && pr->drag_before && pr->drag_before_count > 0) {
            PianoNote* after = (PianoNote*)malloc(sizeof(PianoNote) * pr->drag_before_count);
            if (after) {
                for (size_t i = 0; i < pr->drag_before_count; i++) {
                    int idx = find_note_index_by_id(pr, pr->drag_before[i].id);
                    after[i] = (idx >= 0) ? pr->notes[idx] : pr->drag_before[i];
                }
                piano_roll_push_undo(pr, PR_ACTION_MOVE, pr->drag_before, after, pr->drag_before_count);
                free(after);
            }
        } else if (pr->drag_mode == PR_DRAG_RESIZE && pr->drag_before && pr->drag_before_count > 0) {
            PianoNote* after = (PianoNote*)malloc(sizeof(PianoNote) * pr->drag_before_count);
            if (after) {
                for (size_t i = 0; i < pr->drag_before_count; i++) {
                    int idx = find_note_index_by_id(pr, pr->drag_before[i].id);
                    after[i] = (idx >= 0) ? pr->notes[idx] : pr->drag_before[i];
                }
                piano_roll_push_undo(pr, PR_ACTION_RESIZE, pr->drag_before, after, pr->drag_before_count);
                free(after);
            }
        } else if (pr->drag_mode == PR_DRAG_BOX) {
            int x1 = min_i(pr->drag_start_x, pr->drag_last_x);
            int x2 = max_i(pr->drag_start_x, pr->drag_last_x);
            int y1 = min_i(pr->drag_start_y, pr->drag_last_y);
            int y2 = max_i(pr->drag_start_y, pr->drag_last_y);
            int rw = x2 - x1 + 1;
            int rh = y2 - y1 + 1;
            clear_selection(pr);
            for (size_t i = 0; i < pr->note_count; i++) {
                int nx, ny, nw, nh;
                note_rect(pr, &pr->notes[i], &nx, &ny, &nw, &nh);
                if (rects_intersect(x1, y1, rw, rh, nx, ny, nw, nh)) {
                    pr->notes[i].selected = true;
                }
            }
        } else if (pr->drag_mode == PR_DRAG_DRAW) {
            int idx = find_note_index_by_id(pr, pr->drag_note_id);
            if (idx >= 0) {
                PianoNote note = pr->notes[idx];
                piano_roll_push_undo(pr, PR_ACTION_ADD, NULL, &note, 1);
            }
        }
        
        pr->dragging = false;
        pr->box_selecting = false;
        pr->drag_mode = PR_DRAG_NONE;
        free(pr->drag_before);
        pr->drag_before = NULL;
        pr->drag_before_count = 0;
    }
}

void piano_roll_update(PianoRoll* pr) {
    if (!pr) return;
    if (pr->player && pr->player->playing) {
        int playhead_x = (int)(pr->player->current_tick * pr->pixels_per_tick);
        if (playhead_x < pr->view_x || playhead_x > pr->view_x + 600) {
            pr->view_x = playhead_x - 100;
            if (pr->view_x < 0) pr->view_x = 0;
        }
    }
}

static void render_piano_keys(PianoRoll* pr, int x, int y, int height) {
    if (!pr || !pr->renderer) return;
    RendererPort* r = pr->renderer;
    
    int total_notes = PIANO_ROLL_NOTE_MAX - PIANO_ROLL_NOTE_MIN;
    int start_note = pr->view_y / pr->note_height;
    int visible_notes = height / pr->note_height + 2;
    
    for (int i = 0; i < visible_notes && (start_note + i) < total_notes; i++) {
        int note = PIANO_ROLL_NOTE_MAX - 1 - start_note - i;
        if (note < PIANO_ROLL_NOTE_MIN) continue;
        
        int note_class = note % 12;
        int ky = y + i * pr->note_height - (pr->view_y % pr->note_height);
        
        Color key_color = is_black_key[note_class] ? g_theme.bg_dark : g_theme.text_primary;
        Color border_color = g_theme.panel_border;
        
        r->draw_rect_filled(r, x, ky, PIANO_KEY_WIDTH, pr->note_height - 1, key_color);
        r->draw_line(r, x, ky + pr->note_height - 1, x + PIANO_KEY_WIDTH, ky + pr->note_height - 1, border_color);
        
        if (note_class == 0) {
            r->draw_rect_filled(r, x + PIANO_KEY_WIDTH - 12, ky + 2, 10, pr->note_height - 4, g_theme.accent_primary);
        }
    }
    
    r->draw_line(r, x + PIANO_KEY_WIDTH - 1, y, x + PIANO_KEY_WIDTH - 1, y + height, g_theme.panel_border);
    (void)note_names;
}

static void render_grid(PianoRoll* pr, int x, int y, int width, int height) {
    if (!pr || !pr->renderer) return;
    RendererPort* r = pr->renderer;
    
    int start_note = pr->view_y / pr->note_height;
    int visible_notes = height / pr->note_height + 2;
    
    for (int i = 0; i < visible_notes; i++) {
        int note = PIANO_ROLL_NOTE_MAX - 1 - start_note - i;
        if (note < PIANO_ROLL_NOTE_MIN) continue;
        
        int note_class = note % 12;
        int ky = y + i * pr->note_height - (pr->view_y % pr->note_height);
        
        Color row_bg = is_black_key[note_class] ? g_theme.bg_dark : g_theme.bg_medium;
        r->draw_rect_filled(r, x, ky, width, pr->note_height - 1, row_bg);
        r->draw_line(r, x, ky + pr->note_height - 1, x + width, ky + pr->note_height - 1, g_theme.grid_line);
    }
    
    int ticks_per_beat = pr->midi ? pr->midi->ticks_per_quarter : 480;
    int beat_width = ticks_per_beat * pr->pixels_per_tick;
    
    int start_beat = pr->view_x / beat_width;
    int visible_beats = width / beat_width + 2;
    
    for (int b = 0; b < visible_beats; b++) {
        int beat = start_beat + b;
        int bx = x + beat * beat_width - pr->view_x;
        
        Color line_color = (beat % 4 == 0) ? g_theme.grid_bar : g_theme.grid_beat;
        r->draw_line(r, bx, y, bx, y + height, line_color);
    }
}

static void render_notes(PianoRoll* pr, int x, int y, int width, int height) {
    if (!pr || !pr->renderer) return;
    RendererPort* r = pr->renderer;
    (void)height;
    
    for (size_t i = 0; i < pr->note_count; i++) {
        PianoNote* n = &pr->notes[i];
        
        int nx = x + (int)(n->start_tick * pr->pixels_per_tick) - pr->view_x;
        int nw = (int)(n->duration_ticks * pr->pixels_per_tick);
        if (nw < 2) nw = 2;
        
        int note_offset = PIANO_ROLL_NOTE_MAX - 1 - n->note;
        int ny = y + note_offset * pr->note_height - pr->view_y;
        
        if (nx + nw < x || nx > x + width) continue;
        if (ny + pr->note_height < y || ny > y + height) continue;
        
        Color note_color = g_theme.channel_colors[n->channel % 8];
        if (n->selected) {
            note_color = g_theme.accent_primary;
        }
        
        float vel_factor = 0.3f + (n->velocity / 127.0f) * 0.7f;
        note_color.r = (uint8_t)(note_color.r * vel_factor);
        note_color.g = (uint8_t)(note_color.g * vel_factor);
        note_color.b = (uint8_t)(note_color.b * vel_factor);
        
        r->draw_rect_filled(r, nx, ny + 1, nw - 1, pr->note_height - 2, note_color);
        r->draw_rect(r, nx, ny + 1, nw - 1, pr->note_height - 2, g_theme.panel_border);
    }
}

static void render_playhead(PianoRoll* pr, int x, int y, int height) {
    if (!pr || !pr->player) return;
    
    RendererPort* r = pr->renderer;
    
    int px = x + (int)(pr->player->current_tick * pr->pixels_per_tick) - pr->view_x;
    if (px >= x) {
        r->draw_line(r, px, y, px, y + height, g_theme.playhead);
        r->draw_line(r, px + 1, y, px + 1, y + height, g_theme.playhead);
    }
}

void piano_roll_render(PianoRoll* pr, int x, int y, int width, int height) {
    if (!pr || !pr->renderer || width <= 0 || height <= 0) return;
    RendererPort* r = pr->renderer;
    pr->bounds_x = x;
    pr->bounds_y = y;
    pr->bounds_w = width;
    pr->bounds_h = height;
    
    r->draw_rect_filled(r, x, y, width, height, g_theme.bg_dark);
    
    int grid_x = x + PIANO_KEY_WIDTH;
    int grid_width = width - PIANO_KEY_WIDTH;
    
    render_grid(pr, grid_x, y, grid_width, height);
    render_notes(pr, grid_x, y, grid_width, height);
    render_playhead(pr, grid_x, y, height);
    render_piano_keys(pr, x, y, height);
    
    if (pr->box_selecting) {
        int x1 = min_i(pr->drag_start_x, pr->drag_last_x);
        int x2 = max_i(pr->drag_start_x, pr->drag_last_x);
        int y1 = min_i(pr->drag_start_y, pr->drag_last_y);
        int y2 = max_i(pr->drag_start_y, pr->drag_last_y);
        int rw = x2 - x1 + 1;
        int rh = y2 - y1 + 1;
        Color box = g_theme.selection;
        r->draw_rect(r, x1, y1, rw, rh, box);
    }
    
    r->draw_rect(r, x, y, width, height, g_theme.panel_border);
}

void piano_roll_zoom_in(PianoRoll* pr) {
    if (!pr) return;
    if (pr->zoom_level < 8) {
        pr->zoom_level++;
        pr->pixels_per_tick = pr->zoom_level;
    }
}

void piano_roll_zoom_out(PianoRoll* pr) {
    if (!pr) return;
    if (pr->zoom_level > 1) {
        pr->zoom_level--;
        pr->pixels_per_tick = pr->zoom_level;
    }
}

void piano_roll_scroll(PianoRoll* pr, int dx, int dy) {
    if (!pr) return;
    pr->view_x += dx;
    pr->view_y += dy;
    if (pr->view_x < 0) pr->view_x = 0;
    if (pr->view_y < 0) pr->view_y = 0;
}
