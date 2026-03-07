#include "editor.h"
#include "../ui/theme.h"
#include "../ui/widgets.h"
#include "../ui/font.h"
#ifdef __APPLE__
#include "../../adapters/platform/macos_platform.h"
#endif
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#define ROW_HEIGHT 18
#define CHANNEL_WIDTH 96
#define HEADER_HEIGHT 50
#define MENU_BAR_HEIGHT 22
#define CHANNEL_HEADER_HEIGHT 66
#define ROW_NUM_WIDTH 45
#define STATUS_BAR_HEIGHT 28
#define SIDEBAR_WIDTH 220

#define COLUMN_NOTE 0
#define COLUMN_INSTRUMENT 1
#define COLUMN_VOLUME 2
#define COLUMN_EFFECT 3
#define COLUMN_PARAM 4
#define COLUMN_COUNT 5

static const int column_char_pos[COLUMN_COUNT] = {0, 4, 7, 10, 13};
static const int column_char_len[COLUMN_COUNT] = {3, 2, 2, 2, 2};
static const int cell_text_offset = 4;

static const KeyCode piano_keys[] = {
    KEY_Z, KEY_S, KEY_X, KEY_D, KEY_C, KEY_V, KEY_G, KEY_B, KEY_H, KEY_N, KEY_J, KEY_M
};

static int key_to_note(KeyCode key, int octave) {
    for (int i = 0; i < 12; i++) {
        if (piano_keys[i] == key) {
            return (octave + 1) * 12 + i;
        }
    }
    return -1;
}

static int key_to_hex(KeyCode key) {
    if (key >= KEY_0 && key <= KEY_9) {
        return key - KEY_0;
    }
    if (key >= KEY_A && key <= KEY_F) {
        return 10 + (key - KEY_A);
    }
    return -1;
}

static int column_from_local_x(int local_x) {
    if (local_x < cell_text_offset) return COLUMN_NOTE;
    int char_x = (local_x - cell_text_offset) / FONT_CHAR_WIDTH;
    if (char_x < 0) return COLUMN_NOTE;
    for (int col = 0; col < COLUMN_COUNT; col++) {
        int start = column_char_pos[col];
        int end = start + column_char_len[col];
        if (char_x >= start && char_x < end) return col;
    }
    return COLUMN_PARAM;
}

static void set_hex_nibble(uint8_t* value, int nibble, int hex) {
    if (nibble == 0) {
        *value = (uint8_t)((*value & 0x0F) | ((hex & 0x0F) << 4));
    } else {
        *value = (uint8_t)((*value & 0xF0) | (hex & 0x0F));
    }
}

static void clear_cell(PatternCell* cell) {
    if (!cell) return;
    memset(cell, 0, sizeof(PatternCell));
}

static void clear_row(Pattern* pattern, int row) {
    if (!pattern) return;
    if (row < 0 || row >= pattern->length) return;
    for (int ch = 0; ch < 8; ch++) {
        clear_cell(&pattern->cells[row][ch]);
    }
}

static void editor_apply_selected_instrument(Editor* ed) {
    if (!ed || !ed->engine || !ed->instruments || ed->instruments->count <= 0) return;
    if (ed->cursor_channel < 0 || ed->cursor_channel >= 8) return;
    Instrument* inst = instrument_bank_get(ed->instruments, ed->selected_instrument);
    if (inst) {
        instrument_apply_to_channel(inst, &ed->engine->channels[ed->cursor_channel]);
    }
}

static void editor_handle_mouse(Editor* ed) {
    if (!ed || !ed->input || !ed->renderer) return;
    InputPort* in = ed->input;
    if (!in->is_mouse_clicked(in, MOUSE_LEFT)) return;
    
    int mx = 0, my = 0;
    int width = 0, height = 0;
    in->get_mouse_position(in, &mx, &my);
    ed->renderer->get_size(ed->renderer, &width, &height);
    if (width <= 0 || height <= 0) return;
    
    int content_y = HEADER_HEIGHT + MENU_BAR_HEIGHT;
    int content_h = height - HEADER_HEIGHT - MENU_BAR_HEIGHT - STATUS_BAR_HEIGHT;
    int grid_width = ROW_NUM_WIDTH + 8 * CHANNEL_WIDTH;
    int sidebar_x = grid_width;
    int sidebar_width = width - grid_width;
    if (sidebar_width < 140) sidebar_width = 0;
    
    if (sidebar_width > 0 &&
        mx >= sidebar_x && mx < sidebar_x + sidebar_width &&
        my >= content_y && my < content_y + content_h) {
        int header_h = 24;
        int list_y = content_y + header_h + 6;
        int row_h = 16;
        int detail_reserve = 120;
        int max_visible = (content_h - header_h - detail_reserve) / row_h;
        if (max_visible < 4) max_visible = 4;
        
        if (my >= list_y && my < list_y + max_visible * row_h) {
        int count = ed->instruments ? ed->instruments->count : 0;
        if (count > 0) {
                int start = 0;
                if (count > max_visible) {
                    start = ed->selected_instrument - max_visible / 2;
                    if (start < 0) start = 0;
                    if (start > count - max_visible) start = count - max_visible;
                }
                
                int idx = start + (my - list_y) / row_h;
                if (idx >= 0 && idx < count) {
                    ed->selected_instrument = idx;
                    editor_apply_selected_instrument(ed);
                }
            }
            return;
        }
    }
    
    if (ed->show_piano_roll) {
        return;
    }
    
    int ch_header_y = MENU_BAR_HEIGHT + HEADER_HEIGHT;
    if (my >= ch_header_y && my < ch_header_y + CHANNEL_HEADER_HEIGHT) {
        if (mx >= ROW_NUM_WIDTH && mx < ROW_NUM_WIDTH + 8 * CHANNEL_WIDTH) {
            int ch = (mx - ROW_NUM_WIDTH) / CHANNEL_WIDTH;
            if (ch >= 0 && ch < 8) {
                int ch_x = ROW_NUM_WIDTH + ch * CHANNEL_WIDTH;
                int btn_y = ch_header_y + 26;
                if (my >= btn_y && my < btn_y + 12) {
                    if (mx >= ch_x + 4 && mx < ch_x + 22) {
                        editor_toggle_channel_mute(ed, ch);
                        return;
                    }
                    if (mx >= ch_x + 24 && mx < ch_x + 42) {
                        editor_toggle_channel_solo(ed, ch);
                        return;
                    }
                }
                
                ed->cursor_channel = ch;
                ed->cursor_column = COLUMN_NOTE;
                ed->cursor_nibble = 0;
            }
        }
        return;
    }
    
    int grid_y = MENU_BAR_HEIGHT + HEADER_HEIGHT + CHANNEL_HEADER_HEIGHT;
    int grid_h = height - grid_y - STATUS_BAR_HEIGHT;
    if (mx >= 0 && mx < grid_width && my >= grid_y && my < grid_y + grid_h) {
        Pattern* p = sequencer_get_current_pattern(ed->sequencer);
        if (!p) return;
        
        int row = ed->view_offset + (my - grid_y) / ROW_HEIGHT;
        if (row < 0) row = 0;
        if (row >= p->length) row = p->length - 1;
        
        ed->cursor_row = row;
        ed->cursor_nibble = 0;
        
        if (mx < ROW_NUM_WIDTH) {
            return;
        }
        
        int ch = (mx - ROW_NUM_WIDTH) / CHANNEL_WIDTH;
        if (ch >= 0 && ch < 8) {
            ed->cursor_channel = ch;
            int local_x = (mx - ROW_NUM_WIDTH) - ch * CHANNEL_WIDTH;
            ed->cursor_column = column_from_local_x(local_x);
        }
    }
}

void editor_init(Editor* ed, Sequencer* seq, SynthEngine* engine, InstrumentBank* instruments,
                 RendererPort* renderer, InputPort* input) {
    if (!ed) return;
    memset(ed, 0, sizeof(Editor));
    
    ed->sequencer = seq;
    ed->engine = engine;
    ed->instruments = instruments;
    ed->renderer = renderer;
    ed->input = input;
    
    ed->mode = EDITOR_MODE_PATTERN;
    ed->cursor_row = 0;
    ed->cursor_channel = 0;
    ed->cursor_column = 0;
    ed->cursor_nibble = 0;
    ed->view_offset = 0;
    ed->octave = 4;
    ed->editing = false;
    ed->step = 1;
    ed->selected_pattern = 0;
    ed->selected_instrument = 0;
    ed->show_piano_roll = false;
    ed->row_clipboard_valid = false;
    toast_queue_init(&ed->toasts);
    ed->menu_open = -1;
    ed->request_quit = false;
    ed->last_width = 0;
    ed->last_height = 0;
    ed->resize_timer = 0.0f;
    
    for (int i = 0; i < 8; i++) {
        ed->channel_volumes[i] = 1.0f;
        ed->channel_pans[i] = 0.0f;
        ed->channel_muted[i] = false;
        ed->channel_solo[i] = false;
    }
    
    midi_player_init(&ed->midi_player, engine, BEEZ_DEFAULT_SAMPLE_RATE);
    piano_roll_init(&ed->piano_roll, renderer, input, engine);
}

void editor_destroy(Editor* ed) {
    if (!ed) return;
    if (ed->midi_file) {
        midi_file_destroy(ed->midi_file);
        ed->midi_file = NULL;
    }
    piano_roll_destroy(&ed->piano_roll);
    toast_queue_clear(&ed->toasts);
}

bool editor_load_midi(Editor* ed, const char* filename) {
    if (!ed || !filename) return false;
    if (ed->midi_file) {
        midi_file_destroy(ed->midi_file);
    }
    
    ed->midi_file = midi_file_create();
    if (!ed->midi_file) return false;
    
    if (!midi_file_load(ed->midi_file, filename)) {
        midi_file_destroy(ed->midi_file);
        ed->midi_file = NULL;
        toast_pushf(&ed->toasts, "MIDI load failed");
        return false;
    }
    
    midi_player_set_midi(&ed->midi_player, ed->midi_file);
    piano_roll_set_midi(&ed->piano_roll, ed->midi_file);
    piano_roll_set_player(&ed->piano_roll, &ed->midi_player);
    if (ed->sequencer) {
        sequencer_import_midi(ed->sequencer, ed->midi_file);
    }
    toast_pushf(&ed->toasts, "MIDI loaded");
    
    ed->show_piano_roll = true;
    ed->mode = EDITOR_MODE_PIANO_ROLL;
    
    return true;
}

void editor_toggle_view(Editor* ed) {
    if (!ed) return;
    ed->show_piano_roll = !ed->show_piano_roll;
    ed->mode = ed->show_piano_roll ? EDITOR_MODE_PIANO_ROLL : EDITOR_MODE_PATTERN;
}

void editor_set_channel_volume(Editor* ed, int channel, float volume) {
    if (!ed) return;
    if (channel >= 0 && channel < 8) {
        ed->channel_volumes[channel] = volume;
        if (ed->engine) {
            synth_engine_set_channel_volume(ed->engine, channel, volume);
        }
    }
}

void editor_set_channel_pan(Editor* ed, int channel, float pan) {
    if (!ed) return;
    if (channel >= 0 && channel < 8) {
        ed->channel_pans[channel] = pan;
        if (ed->engine) {
            synth_engine_set_channel_pan(ed->engine, channel, pan);
        }
    }
}

void editor_toggle_channel_mute(Editor* ed, int channel) {
    if (!ed) return;
    if (channel >= 0 && channel < 8) {
        ed->channel_muted[channel] = !ed->channel_muted[channel];
        if (ed->engine) {
            synth_engine_set_channel_enabled(ed->engine, channel, !ed->channel_muted[channel]);
        }
    }
}

void editor_toggle_channel_solo(Editor* ed, int channel) {
    if (!ed) return;
    if (channel >= 0 && channel < 8) {
        ed->channel_solo[channel] = !ed->channel_solo[channel];
        
        bool any_solo = false;
        for (int i = 0; i < 8; i++) {
            if (ed->channel_solo[i]) any_solo = true;
        }
        
        for (int i = 0; i < 8; i++) {
            bool enabled = any_solo ? ed->channel_solo[i] : !ed->channel_muted[i];
            if (ed->engine) {
                synth_engine_set_channel_enabled(ed->engine, i, enabled);
            }
        }
    }
}

typedef enum {
    MENU_FILE_NEW,
    MENU_FILE_OPEN_MIDI,
    MENU_FILE_EXPORT_WAV,
    MENU_FILE_QUIT,
    MENU_EDIT_UNDO,
    MENU_EDIT_REDO,
    MENU_EDIT_CUT,
    MENU_EDIT_COPY,
    MENU_EDIT_PASTE,
    MENU_SEL_ALL,
    MENU_SEL_NONE,
    MENU_SEL_INVERT,
    MENU_VIEW_PATTERN,
    MENU_VIEW_PIANO,
    MENU_VIEW_MIXER,
    MENU_WIN_MINIMIZE,
    MENU_WIN_CLOSE
} MenuAction;

typedef struct {
    const char* label;
    const char* items[8];
    MenuAction actions[8];
    int item_count;
} MenuDef;

static const MenuDef g_menus[] = {
    {"File", {"New", "Open MIDI", "Export WAV", "Quit"}, {MENU_FILE_NEW, MENU_FILE_OPEN_MIDI, MENU_FILE_EXPORT_WAV, MENU_FILE_QUIT}, 4},
    {"Edit", {"Undo", "Redo", "Cut", "Copy", "Paste"}, {MENU_EDIT_UNDO, MENU_EDIT_REDO, MENU_EDIT_CUT, MENU_EDIT_COPY, MENU_EDIT_PASTE}, 5},
    {"Selection", {"Select All", "Deselect", "Invert"}, {MENU_SEL_ALL, MENU_SEL_NONE, MENU_SEL_INVERT}, 3},
    {"View", {"Pattern", "Piano Roll", "Mixer"}, {MENU_VIEW_PATTERN, MENU_VIEW_PIANO, MENU_VIEW_MIXER}, 3},
    {"Window", {"Minimize", "Close"}, {MENU_WIN_MINIMIZE, MENU_WIN_CLOSE}, 2}
};

static void render_menu_bar(Editor* ed, int width) {
    RendererPort* r = ed->renderer;
    r->draw_rect_filled(r, 0, 0, width, MENU_BAR_HEIGHT, g_theme.bg_dark);
    r->draw_line(r, 0, MENU_BAR_HEIGHT - 1, width, MENU_BAR_HEIGHT - 1, g_theme.panel_border);
    
    int x = 10;
    for (int i = 0; i < (int)(sizeof(g_menus) / sizeof(g_menus[0])); i++) {
        const MenuDef* menu = &g_menus[i];
        int w = (int)strlen(menu->label) * FONT_CHAR_WIDTH + 12;
        if (ed->menu_open == i) {
            r->draw_rect_filled(r, x - 2, 2, w, MENU_BAR_HEIGHT - 4, g_theme.selection);
        }
        font_draw_string(r, x + 2, 6, menu->label, g_theme.text_primary);
        x += w + 6;
    }
}

static void render_menu_popup(Editor* ed) {
    if (ed->menu_open < 0) return;
    RendererPort* r = ed->renderer;
    int x = 10;
    for (int i = 0; i < ed->menu_open; i++) {
        int w = (int)strlen(g_menus[i].label) * FONT_CHAR_WIDTH + 12;
        x += w + 6;
    }
    const MenuDef* menu = &g_menus[ed->menu_open];
    int item_h = 16;
    int box_w = 140;
    int box_h = menu->item_count * item_h + 6;
    int y = MENU_BAR_HEIGHT + 2;
    
    r->draw_rect_filled(r, x - 2, y, box_w, box_h, g_theme.bg_dark);
    r->draw_rect(r, x - 2, y, box_w, box_h, g_theme.panel_border);
    
    for (int i = 0; i < menu->item_count; i++) {
        font_draw_string(r, x + 6, y + 4 + i * item_h, menu->items[i], g_theme.text_secondary);
    }
}

static void editor_menu_action(Editor* ed, MenuAction action) {
    if (!ed) return;
    switch (action) {
        case MENU_FILE_NEW: {
            if (ed->sequencer) {
                sequencer_init(ed->sequencer, ed->engine);
                sequencer_set_instrument_bank(ed->sequencer, ed->instruments);
                toast_push(&ed->toasts, "New song");
            }
            break;
        }
        case MENU_FILE_OPEN_MIDI:
        {
#ifdef __APPLE__
            char* path = macos_open_midi_file_dialog();
            if (path) {
                if (editor_load_midi(ed, path)) {
                    toast_push(&ed->toasts, "MIDI loaded");
                } else {
                    toast_push(&ed->toasts, "MIDI load failed");
                }
                free(path);
            } else {
                toast_push(&ed->toasts, "Open MIDI canceled");
            }
#else
            toast_push(&ed->toasts, "Open MIDI: use CLI for now");
#endif
            break;
        }
        case MENU_FILE_EXPORT_WAV:
            toast_push(&ed->toasts, "Export WAV: not wired yet");
            break;
        case MENU_FILE_QUIT:
            ed->request_quit = true;
            toast_push(&ed->toasts, "Quitting");
            break;
        case MENU_EDIT_UNDO:
            if (ed->show_piano_roll) {
                piano_roll_undo(&ed->piano_roll);
                toast_push(&ed->toasts, "Undo");
            } else {
                toast_push(&ed->toasts, "Undo not available");
            }
            break;
        case MENU_EDIT_REDO:
            if (ed->show_piano_roll) {
                piano_roll_redo(&ed->piano_roll);
                toast_push(&ed->toasts, "Redo");
            } else {
                toast_push(&ed->toasts, "Redo not available");
            }
            break;
        case MENU_EDIT_CUT:
            if (!ed->show_piano_roll) {
                if (ed->sequencer) {
                    Pattern* p = sequencer_get_current_pattern(ed->sequencer);
                    if (p) {
                        for (int ch = 0; ch < 8; ch++) {
                            ed->row_clipboard[ch] = p->cells[ed->cursor_row][ch];
                        }
                        ed->row_clipboard_valid = true;
                        clear_row(p, ed->cursor_row);
                        toast_push(&ed->toasts, "Cut row");
                    }
                }
            } else {
                toast_push(&ed->toasts, "Cut not wired for piano roll");
            }
            break;
        case MENU_EDIT_COPY:
            if (!ed->show_piano_roll) {
                if (ed->sequencer) {
                    Pattern* p = sequencer_get_current_pattern(ed->sequencer);
                    if (p) {
                        for (int ch = 0; ch < 8; ch++) {
                            ed->row_clipboard[ch] = p->cells[ed->cursor_row][ch];
                        }
                        ed->row_clipboard_valid = true;
                        toast_push(&ed->toasts, "Copied row");
                    }
                }
            } else {
                toast_push(&ed->toasts, "Copy not wired for piano roll");
            }
            break;
        case MENU_EDIT_PASTE:
            if (!ed->show_piano_roll) {
                if (ed->sequencer && ed->row_clipboard_valid) {
                    Pattern* p = sequencer_get_current_pattern(ed->sequencer);
                    if (p) {
                        for (int ch = 0; ch < 8; ch++) {
                            p->cells[ed->cursor_row][ch] = ed->row_clipboard[ch];
                        }
                        toast_push(&ed->toasts, "Pasted row");
                    }
                }
            } else {
                toast_push(&ed->toasts, "Paste not wired for piano roll");
            }
            break;
        case MENU_SEL_ALL:
            if (ed->show_piano_roll) {
                piano_roll_select_all(&ed->piano_roll);
                toast_push(&ed->toasts, "Select all");
            }
            break;
        case MENU_SEL_NONE:
            if (ed->show_piano_roll) {
                piano_roll_select_none(&ed->piano_roll);
                toast_push(&ed->toasts, "Deselect");
            }
            break;
        case MENU_SEL_INVERT:
            if (ed->show_piano_roll) {
                piano_roll_select_invert(&ed->piano_roll);
                toast_push(&ed->toasts, "Invert selection");
            }
            break;
        case MENU_VIEW_PATTERN:
            ed->show_piano_roll = false;
            ed->mode = EDITOR_MODE_PATTERN;
            toast_push(&ed->toasts, "Pattern view");
            break;
        case MENU_VIEW_PIANO:
            ed->show_piano_roll = true;
            ed->mode = EDITOR_MODE_PIANO_ROLL;
            toast_push(&ed->toasts, "Piano roll");
            break;
        case MENU_VIEW_MIXER:
            toast_push(&ed->toasts, "Mixer not implemented");
            break;
        case MENU_WIN_MINIMIZE:
            toast_push(&ed->toasts, "Minimize not implemented");
            break;
        case MENU_WIN_CLOSE:
            ed->request_quit = true;
            toast_push(&ed->toasts, "Closing");
            break;
    }
}

static void editor_handle_menu(Editor* ed) {
    InputPort* in = ed->input;
    int mx = 0, my = 0;
    in->get_mouse_position(in, &mx, &my);
    
    if (in->is_mouse_clicked(in, MOUSE_LEFT)) {
        if (my < MENU_BAR_HEIGHT) {
            int x = 10;
            for (int i = 0; i < (int)(sizeof(g_menus) / sizeof(g_menus[0])); i++) {
                int w = (int)strlen(g_menus[i].label) * FONT_CHAR_WIDTH + 12;
                if (mx >= x - 2 && mx <= x - 2 + w) {
                    ed->menu_open = (ed->menu_open == i) ? -1 : i;
                    return;
                }
                x += w + 6;
            }
            ed->menu_open = -1;
        } else if (ed->menu_open >= 0) {
            int x = 10;
            for (int i = 0; i < ed->menu_open; i++) {
                int w = (int)strlen(g_menus[i].label) * FONT_CHAR_WIDTH + 12;
                x += w + 6;
            }
            int y = MENU_BAR_HEIGHT + 2;
            int item_h = 16;
            int box_w = 140;
            int box_h = g_menus[ed->menu_open].item_count * item_h + 6;
            if (mx >= x - 2 && mx <= x - 2 + box_w && my >= y && my <= y + box_h) {
                int idx = (my - y - 4) / item_h;
                if (idx >= 0 && idx < g_menus[ed->menu_open].item_count) {
                    editor_menu_action(ed, g_menus[ed->menu_open].actions[idx]);
                }
            }
            ed->menu_open = -1;
        }
    }
}

void editor_handle_input(Editor* ed) {
    if (!ed || !ed->input) return;
    InputPort* in = ed->input;
    editor_handle_menu(ed);
    bool shift = in->is_key_down(in, KEY_SHIFT);
    bool ctrl = in->is_key_down(in, KEY_CTRL);
    bool channel_shortcut_used = false;
    
    if (in->is_key_pressed(in, KEY_F3)) {
        editor_toggle_view(ed);
        return;
    }
    
    if (in->is_key_pressed(in, KEY_F5)) {
        ed->sequencer->loop_enabled = !ed->sequencer->loop_enabled;
        ed->midi_player.loop_enabled = ed->sequencer->loop_enabled;
    }
    
    if (ed->instruments && ed->instruments->count > 0) {
        if (in->is_key_pressed(in, KEY_F6)) {
            ed->selected_instrument--;
            if (ed->selected_instrument < 0) {
                ed->selected_instrument = ed->instruments->count - 1;
            }
            editor_apply_selected_instrument(ed);
        }
        if (in->is_key_pressed(in, KEY_F7)) {
            ed->selected_instrument++;
            if (ed->selected_instrument >= ed->instruments->count) {
                ed->selected_instrument = 0;
            }
            editor_apply_selected_instrument(ed);
        }
    }
    
    if (in->is_key_pressed(in, KEY_Q)) {
        if (ed->step > 1) ed->step /= 2;
    }
    if (in->is_key_pressed(in, KEY_W)) {
        if (ed->step < 8) ed->step *= 2;
    }
    
    if (!ed->show_piano_roll) {
        bool allow_channel_shortcut = (!ed->editing || ed->cursor_column == COLUMN_NOTE ||
                                       in->is_key_down(in, KEY_M) || in->is_key_down(in, KEY_S));
        if (allow_channel_shortcut) {
            for (int i = 0; i < 8; i++) {
                if (in->is_key_pressed(in, KEY_1 + i)) {
                    channel_shortcut_used = true;
                    if (in->is_key_down(in, KEY_M)) {
                        editor_toggle_channel_mute(ed, i);
                    } else if (in->is_key_down(in, KEY_S)) {
                        editor_toggle_channel_solo(ed, i);
                    } else {
                        ed->cursor_channel = i;
                        ed->cursor_column = COLUMN_NOTE;
                        ed->cursor_nibble = 0;
                    }
                }
            }
        }
    }
    
    editor_handle_mouse(ed);
    
    if (ed->show_piano_roll) {
        if (in->is_key_pressed(in, KEY_TAB)) {
            ed->editing = !ed->editing;
            ed->piano_roll.editing = ed->editing;
        }
        piano_roll_handle_input(&ed->piano_roll);
        return;
    }
    
    Pattern* p = sequencer_get_current_pattern(ed->sequencer);
    
    int width = 0, height = 0;
    if (ed->renderer) {
        ed->renderer->get_size(ed->renderer, &width, &height);
    }
    int grid_h = height - MENU_BAR_HEIGHT - HEADER_HEIGHT - CHANNEL_HEADER_HEIGHT - STATUS_BAR_HEIGHT;
    int visible_rows = grid_h / ROW_HEIGHT;
    if (visible_rows < 1) visible_rows = 1;
    int page_step = visible_rows / 2;
    if (page_step < 1) page_step = 1;
    
    int row_step = shift ? 4 : 1;
    if (in->is_key_pressed(in, KEY_UP) || (ctrl && in->is_key_pressed(in, KEY_K))) {
        ed->cursor_row -= row_step;
        if (ed->cursor_row < 0) ed->cursor_row = 0;
        ed->cursor_nibble = 0;
    }
    if (in->is_key_pressed(in, KEY_DOWN) || (ctrl && in->is_key_pressed(in, KEY_J))) {
        ed->cursor_row += row_step;
        if (p && ed->cursor_row >= p->length) {
            ed->cursor_row = p->length - 1;
        }
        ed->cursor_nibble = 0;
    }
    if (in->is_key_pressed(in, KEY_LEFT) || (ctrl && in->is_key_pressed(in, KEY_H))) {
        if (ed->cursor_column > 0) {
            ed->cursor_column--;
        } else if (ed->cursor_channel > 0) {
            ed->cursor_channel--;
            ed->cursor_column = COLUMN_COUNT - 1;
        }
        ed->cursor_nibble = 0;
    }
    if (in->is_key_pressed(in, KEY_RIGHT) || (ctrl && in->is_key_pressed(in, KEY_L))) {
        if (ed->cursor_column < COLUMN_COUNT - 1) {
            ed->cursor_column++;
        } else if (ed->cursor_channel < 7) {
            ed->cursor_channel++;
            ed->cursor_column = COLUMN_NOTE;
        }
        ed->cursor_nibble = 0;
    }
    
    if (ctrl && in->is_key_pressed(in, KEY_U)) {
        ed->cursor_row -= page_step;
        if (ed->cursor_row < 0) ed->cursor_row = 0;
        ed->cursor_nibble = 0;
    }
    if (ctrl && in->is_key_pressed(in, KEY_D)) {
        ed->cursor_row += page_step;
        if (p && ed->cursor_row >= p->length) ed->cursor_row = p->length - 1;
        ed->cursor_nibble = 0;
    }
    if (ctrl && in->is_key_pressed(in, KEY_G)) {
        if (shift) {
            if (p) ed->cursor_row = p->length - 1;
        } else {
            ed->cursor_row = 0;
        }
        ed->cursor_nibble = 0;
    }
    
    if (in->is_key_pressed(in, KEY_SPACE)) {
        if (ed->sequencer->playing) {
            sequencer_pause(ed->sequencer);
        } else {
            sequencer_play(ed->sequencer);
        }
    }
    
    if (in->is_key_pressed(in, KEY_ENTER)) {
        sequencer_stop(ed->sequencer);
    }
    
    if (in->is_key_pressed(in, KEY_F1)) ed->octave = ed->octave > 0 ? ed->octave - 1 : 0;
    if (in->is_key_pressed(in, KEY_F2)) ed->octave = ed->octave < 8 ? ed->octave + 1 : 8;
    
    if (ctrl && p && in->is_key_pressed(in, KEY_C)) {
        for (int ch = 0; ch < 8; ch++) {
            ed->row_clipboard[ch] = p->cells[ed->cursor_row][ch];
        }
        ed->row_clipboard_valid = true;
    }
    if (ctrl && p && in->is_key_pressed(in, KEY_X)) {
        for (int ch = 0; ch < 8; ch++) {
            ed->row_clipboard[ch] = p->cells[ed->cursor_row][ch];
        }
        ed->row_clipboard_valid = true;
        clear_row(p, ed->cursor_row);
    }
    if (ctrl && p && in->is_key_pressed(in, KEY_V) && ed->row_clipboard_valid) {
        for (int ch = 0; ch < 8; ch++) {
            p->cells[ed->cursor_row][ch] = ed->row_clipboard[ch];
        }
    }
    
    if (p && in->is_key_pressed(in, KEY_BACKSPACE)) {
        if (shift) {
            clear_row(p, ed->cursor_row);
        } else {
            clear_cell(&p->cells[ed->cursor_row][ed->cursor_channel]);
        }
    }
    
    if (ed->editing && p && ed->cursor_column != COLUMN_NOTE && !channel_shortcut_used) {
        int hex = -1;
        for (KeyCode key = KEY_0; key <= KEY_9; key++) {
            if (in->is_key_pressed(in, key)) {
                hex = key_to_hex(key);
                break;
            }
        }
        if (hex < 0) {
            for (KeyCode key = KEY_A; key <= KEY_F; key++) {
                if (in->is_key_pressed(in, key)) {
                    hex = key_to_hex(key);
                    break;
                }
            }
        }
        
        if (hex >= 0) {
            PatternCell* cell = &p->cells[ed->cursor_row][ed->cursor_channel];
            
            switch (ed->cursor_column) {
                case COLUMN_INSTRUMENT:
                    set_hex_nibble(&cell->instrument, ed->cursor_nibble, hex);
                    if (cell->instrument > MAX_INSTRUMENTS) cell->instrument = MAX_INSTRUMENTS;
                    break;
                case COLUMN_VOLUME:
                    set_hex_nibble(&cell->volume, ed->cursor_nibble, hex);
                    if (cell->volume > 0x7F) cell->volume = 0x7F;
                    break;
                case COLUMN_EFFECT:
                    set_hex_nibble(&cell->effect, ed->cursor_nibble, hex);
                    break;
                case COLUMN_PARAM:
                    set_hex_nibble(&cell->effect_param, ed->cursor_nibble, hex);
                    break;
                default:
                    break;
            }
            
            ed->cursor_nibble ^= 1;
            if (ed->cursor_nibble == 0) {
                if (ed->cursor_column < COLUMN_PARAM) {
                    ed->cursor_column++;
                } else {
                    ed->cursor_row += ed->step;
                    if (p && ed->cursor_row >= p->length) ed->cursor_row = 0;
                }
            }
        }
    }
    
    if (!ctrl) {
        for (int i = 0; i < 12; i++) {
            if (in->is_key_pressed(in, piano_keys[i])) {
                int note = key_to_note(piano_keys[i], ed->octave);
                if (note >= 0) {
                    if (ed->instruments && ed->instruments->count > 0) {
                        Instrument* inst = instrument_bank_get(ed->instruments, ed->selected_instrument);
                        if (inst) {
                            instrument_apply_to_channel(inst, &ed->engine->channels[ed->cursor_channel]);
                        }
                    }
                    synth_engine_note_on(ed->engine, ed->cursor_channel, note, 1.0f);
                    
                    if (ed->editing && ed->cursor_column == COLUMN_NOTE && p) {
                        PatternCell* cell = &p->cells[ed->cursor_row][ed->cursor_channel];
                        cell->note = note;
                        if (cell->volume == 0) cell->volume = 127;
                        if (ed->instruments && ed->instruments->count > 0) {
                            cell->instrument = (uint8_t)(ed->selected_instrument + 1);
                        }
                        ed->cursor_row += ed->step;
                        if (p && ed->cursor_row >= p->length) ed->cursor_row = 0;
                        ed->cursor_nibble = 0;
                    }
                }
            }
            if (in->is_key_released(in, piano_keys[i])) {
                synth_engine_note_off(ed->engine, ed->cursor_channel);
            }
        }
    }
    
    if (in->is_key_pressed(in, KEY_TAB)) {
        ed->editing = !ed->editing;
    }
}

void editor_update(Editor* ed, float dt) {
    if (!ed) return;
    toast_update(&ed->toasts, dt);
    if (ed->resize_timer > 0.0f) {
        ed->resize_timer -= dt;
        if (ed->resize_timer < 0.0f) ed->resize_timer = 0.0f;
    }
    if (ed->show_piano_roll) {
        piano_roll_update(&ed->piano_roll);
        return;
    }
    
    if (!ed->renderer) return;
    int width = 0, height = 0;
    ed->renderer->get_size(ed->renderer, &width, &height);
    if (width > 0 && height > 0) {
        if (ed->last_width == 0 && ed->last_height == 0) {
            ed->last_width = width;
            ed->last_height = height;
        } else if (ed->last_width != width || ed->last_height != height) {
            ed->last_width = width;
            ed->last_height = height;
            ed->resize_timer = 0.8f;
        }
    }
    int grid_h = height - MENU_BAR_HEIGHT - HEADER_HEIGHT - CHANNEL_HEADER_HEIGHT - STATUS_BAR_HEIGHT;
    int visible_rows = grid_h / ROW_HEIGHT;
    if (visible_rows < 1) visible_rows = 1;
    
    if (ed->cursor_row < ed->view_offset) {
        ed->view_offset = ed->cursor_row;
    } else if (ed->cursor_row >= ed->view_offset + visible_rows) {
        ed->view_offset = ed->cursor_row - visible_rows + 1;
    }
}

static const char* note_names[] = {"C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-"};
static const char* channel_names[] = {"PU1", "PU2", "TRI", "NOI", "PU3", "PU4", "SAW", "SQ4"};

static void format_note(char* out, uint8_t note) {
    if (note == NOTE_OFF) {
        strcpy(out, "OFF");
        return;
    }
    if (note >= NOTE_C0) {
        int note_val = note % 12;
        int octave = (note / 12) - 1;
        out[0] = note_names[note_val][0];
        out[1] = note_names[note_val][1];
        out[2] = (octave >= 0 && octave <= 9) ? (char)('0' + octave) : '?';
        out[3] = '\0';
        return;
    }
    strcpy(out, "...");
}

static void format_hex2(char* out, uint8_t value, bool blank_zero) {
    if (blank_zero && value == 0) {
        out[0] = '.';
        out[1] = '.';
        out[2] = '\0';
        return;
    }
    out[0] = "0123456789ABCDEF"[(value >> 4) & 0x0F];
    out[1] = "0123456789ABCDEF"[value & 0x0F];
    out[2] = '\0';
}

static const char* waveform_name(OscillatorWaveform wf) {
    switch (wf) {
        case OSC_SQUARE: return "Square";
        case OSC_PULSE_25: return "Pulse 25";
        case OSC_PULSE_12_5: return "Pulse 12";
        case OSC_PULSE_75: return "Pulse 75";
        case OSC_TRIANGLE: return "Triangle";
        case OSC_SAWTOOTH: return "Saw";
        case OSC_NOISE_WHITE: return "Noise W";
        case OSC_NOISE_PERIODIC: return "Noise P";
        case OSC_NOISE_METALLIC: return "Noise M";
        case OSC_WAVETABLE: return "Wavetable";
        default: return "Wave";
    }
}

static const char* filter_type_name(FilterType type) {
    switch (type) {
        case FILTER_LOWPASS: return "LP";
        case FILTER_HIGHPASS: return "HP";
        case FILTER_BANDPASS: return "BP";
        case FILTER_NOTCH: return "Notch";
        default: return "Filter";
    }
}

static const char* lfo_wave_name(LFOWaveform wave) {
    switch (wave) {
        case LFO_SINE: return "Sine";
        case LFO_TRIANGLE: return "Tri";
        case LFO_SQUARE: return "Square";
        case LFO_SAWTOOTH: return "Saw";
        case LFO_SAWTOOTH_DOWN: return "SawDn";
        case LFO_RANDOM: return "Rand";
        default: return "LFO";
    }
}

static const char* lfo_target_name(LFOTarget target) {
    switch (target) {
        case LFO_TARGET_PITCH: return "Pitch";
        case LFO_TARGET_VOLUME: return "Volume";
        case LFO_TARGET_FILTER_CUTOFF: return "Cutoff";
        case LFO_TARGET_FILTER_RESONANCE: return "Reso";
        case LFO_TARGET_PAN: return "Pan";
        case LFO_TARGET_DUTY_CYCLE: return "Duty";
        default: return "Off";
    }
}

static void render_header(Editor* ed, int width) {
    RendererPort* r = ed->renderer;
    
    int header_y = MENU_BAR_HEIGHT;
    r->draw_rect_filled(r, 0, header_y, width, HEADER_HEIGHT, g_theme.panel_header);
    r->draw_line(r, 0, header_y + HEADER_HEIGHT - 1, width, header_y + HEADER_HEIGHT - 1, g_theme.panel_border);
    
    int x = 12;
    int btn_size = 32;
    int btn_y = header_y + (HEADER_HEIGHT - btn_size) / 2;
    
    draw_transport_button(r, x, btn_y, btn_size, TRANSPORT_STOP, !ed->sequencer->playing);
    x += btn_size + 6;
    draw_transport_button(r, x, btn_y, btn_size, TRANSPORT_PLAY, ed->sequencer->playing);
    x += btn_size + 6;
    draw_transport_button(r, x, btn_y, btn_size, TRANSPORT_RECORD, ed->editing);
    x += btn_size + 6;
    draw_transport_button(r, x, btn_y, btn_size, TRANSPORT_LOOP, ed->sequencer->loop_enabled);
    
    x += btn_size + 20;
    r->draw_rect_filled(r, x, btn_y, 70, btn_size, g_theme.bg_dark);
    r->draw_rect(r, x, btn_y, 70, btn_size, g_theme.panel_border);
    draw_number_3digit(r, x + 8, btn_y + 8, (int)ed->sequencer->tempo, g_theme.accent_primary);
    r->draw_rect_filled(r, x + 40, btn_y + 6, 24, 10, g_theme.bg_light);
    r->draw_rect_filled(r, x + 40, btn_y + 18, 24, 10, g_theme.bg_light);
    
    x += 80;
    r->draw_rect_filled(r, x, btn_y, 50, btn_size, g_theme.bg_dark);
    r->draw_rect(r, x, btn_y, 50, btn_size, g_theme.panel_border);
    draw_number_2digit(r, x + 8, btn_y + 4, ed->sequencer->current_order, g_theme.text_secondary);
    draw_number_2digit(r, x + 8, btn_y + 18, ed->sequencer->current_row, g_theme.accent_warning);
    
    x += 60;
    r->draw_rect_filled(r, x, btn_y + 4, 30, 24, g_theme.bg_dark);
    r->draw_rect(r, x, btn_y + 4, 30, 24, g_theme.panel_border);
    draw_number_2digit(r, x + 6, btn_y + 12, ed->octave, g_theme.accent_secondary);
    
    int vu_x = width - 80;
    draw_vu_meter_v(r, vu_x, btn_y, 12, btn_size, 0.6f, 0.75f);
    draw_vu_meter_v(r, vu_x + 16, btn_y, 12, btn_size, 0.5f, 0.65f);
    
    r->draw_rect_filled(r, vu_x + 36, btn_y, 36, btn_size, g_theme.bg_dark);
    r->draw_rect(r, vu_x + 36, btn_y, 36, btn_size, g_theme.panel_border);
    draw_knob(r, vu_x + 54, btn_y + btn_size/2, 12, ed->engine->master_volume, g_theme.accent_primary);
}

static void render_channel_headers(Editor* ed, int y_start, int width) {
    RendererPort* r = ed->renderer;
    (void)width;
    
    r->draw_rect_filled(r, 0, y_start, ROW_NUM_WIDTH, CHANNEL_HEADER_HEIGHT, g_theme.panel_header);
    
    for (int ch = 0; ch < 8; ch++) {
        int x = ROW_NUM_WIDTH + ch * CHANNEL_WIDTH;
        Color ch_color = g_theme.channel_colors[ch];
        
        r->draw_rect_filled(r, x, y_start, CHANNEL_WIDTH - 1, CHANNEL_HEADER_HEIGHT, g_theme.panel_bg);
        r->draw_rect_filled(r, x, y_start, CHANNEL_WIDTH - 1, 4, ch_color);
        
        Color name_bg = {ch_color.r / 3, ch_color.g / 3, ch_color.b / 3, 255};
        r->draw_rect_filled(r, x + 4, y_start + 8, 36, 14, name_bg);
        font_draw_string(r, x + 8, y_start + 11, channel_names[ch], g_theme.text_primary);
        
        int btn_y = y_start + 26;
        Color mute_color = ed->channel_muted[ch] ? g_theme.accent_error : g_theme.button_bg;
        Color solo_color = ed->channel_solo[ch] ? g_theme.accent_warning : g_theme.button_bg;
        r->draw_rect_filled(r, x + 4, btn_y, 18, 12, mute_color);
        r->draw_rect_filled(r, x + 24, btn_y, 18, 12, solo_color);
        font_draw_string(r, x + 6, btn_y + 2, "M", ed->channel_muted[ch] ? g_theme.bg_dark : g_theme.text_dim);
        font_draw_string(r, x + 26, btn_y + 2, "S", ed->channel_solo[ch] ? g_theme.bg_dark : g_theme.text_dim);
        
        int wave_type = ch < 2 ? 0 : (ch == 2 ? 2 : (ch == 3 ? 5 : 0));
        draw_waveform_icon(r, x + 46, btn_y - 2, 36, 16, wave_type);
        
        int vu_y = y_start + 42;
        draw_vu_meter_h(r, x + 4, vu_y, CHANNEL_WIDTH - 12, 8, 0.4f + ch * 0.05f);

        int label_y = y_start + CHANNEL_HEADER_HEIGHT - 14;
        font_draw_string(r, x + cell_text_offset, label_y, "NOTE INS VOL FX PRM", g_theme.text_dim);
        
        r->draw_line(r, x + CHANNEL_WIDTH - 1, y_start, x + CHANNEL_WIDTH - 1, y_start + CHANNEL_HEADER_HEIGHT, g_theme.panel_border);
    }
    
    r->draw_line(r, 0, y_start + CHANNEL_HEADER_HEIGHT - 1, ROW_NUM_WIDTH + 8 * CHANNEL_WIDTH, y_start + CHANNEL_HEADER_HEIGHT - 1, g_theme.panel_border);
}

static void render_pattern_grid(Editor* ed, int y_start, int grid_height) {
    RendererPort* r = ed->renderer;
    
    Pattern* pattern = sequencer_get_current_pattern(ed->sequencer);
    if (!pattern) return;
    
    int visible_rows = grid_height / ROW_HEIGHT;
    
    for (int i = 0; i < visible_rows && (ed->view_offset + i) < pattern->length; i++) {
        int row = ed->view_offset + i;
        int y = y_start + i * ROW_HEIGHT;
        
        Color row_bg;
        if (row % 16 == 0) {
            row_bg = g_theme.bg_highlight;
        } else if (row % 4 == 0) {
            row_bg = g_theme.bg_light;
        } else {
            row_bg = g_theme.bg_medium;
        }
        
        bool is_playing_row = (row == ed->sequencer->current_row && ed->sequencer->playing);
        bool is_cursor_row = (row == ed->cursor_row);
        
        if (is_playing_row) {
            row_bg = (Color){40, 70, 50, 255};
        }
        
        r->draw_rect_filled(r, 0, y, ROW_NUM_WIDTH + 8 * CHANNEL_WIDTH, ROW_HEIGHT, row_bg);
        
        Color row_num_bg = g_theme.bg_dark;
        if (row % 16 == 0) {
            row_num_bg = g_theme.bg_light;
        }
        r->draw_rect_filled(r, 0, y, ROW_NUM_WIDTH - 2, ROW_HEIGHT, row_num_bg);
        
        Color num_color = (row % 4 == 0) ? g_theme.text_secondary : g_theme.text_dim;
        if (is_playing_row) num_color = g_theme.playhead;
        draw_number_2digit(r, 8, y + 5, row, num_color);
        
        for (int ch = 0; ch < 8; ch++) {
            int x = ROW_NUM_WIDTH + ch * CHANNEL_WIDTH;
            Color ch_color = g_theme.channel_colors[ch];
            
            const PatternCell* cell = pattern_get_cell(pattern, row, ch);
            bool has_note = cell && cell->note >= NOTE_C0 && cell->note != NOTE_OFF;
            bool note_off = cell && cell->note == NOTE_OFF;
            
            if (has_note) {
                draw_note_cell(r, x, y, CHANNEL_WIDTH - 2, ROW_HEIGHT, cell->note, true, ch_color);
            } else if (note_off) {
                Color off_bg = {60, 35, 35, 255};
                r->draw_rect_filled(r, x + 2, y + 2, CHANNEL_WIDTH - 6, ROW_HEIGHT - 4, off_bg);
            }
            
            if (is_cursor_row && ch == ed->cursor_channel) {
                int col = ed->cursor_column;
                int col_x = x + cell_text_offset + column_char_pos[col] * FONT_CHAR_WIDTH - 2;
                int col_w = column_char_len[col] * FONT_CHAR_WIDTH + 4;
                r->draw_rect_filled(r, col_x, y + 2, col_w, ROW_HEIGHT - 4, g_theme.selection);
            }
            
            char note_str[4];
            char inst_str[3];
            char vol_str[3];
            char fx_str[3];
            char param_str[3];
            
            uint8_t note_val = cell ? cell->note : 0;
            format_note(note_str, note_val);
            
            if (cell && cell->instrument > 0) {
                format_hex2(inst_str, cell->instrument, false);
            } else {
                strcpy(inst_str, "--");
            }
            
            format_hex2(vol_str, cell ? cell->volume : 0, true);
            format_hex2(fx_str, cell ? cell->effect : 0, true);
            if (!cell || (cell->effect == 0 && cell->effect_param == 0)) {
                strcpy(param_str, "..");
            } else {
                format_hex2(param_str, cell->effect_param, false);
            }
            
            int text_y = y + 5;
            int base_x = x + cell_text_offset;
            Color note_color = note_off ? g_theme.accent_error : (has_note ? g_theme.text_primary : g_theme.text_dim);
            Color inst_color = (cell && cell->instrument > 0) ? g_theme.text_secondary : g_theme.text_dim;
            Color vol_color = (cell && cell->volume > 0) ? g_theme.text_secondary : g_theme.text_dim;
            Color fx_color = (cell && cell->effect > 0) ? g_theme.accent_secondary : g_theme.text_dim;
            Color param_color = (cell && (cell->effect > 0 || cell->effect_param > 0)) ? g_theme.accent_secondary : g_theme.text_dim;
            
            font_draw_string(r, base_x + column_char_pos[COLUMN_NOTE] * FONT_CHAR_WIDTH, text_y, note_str, note_color);
            font_draw_string(r, base_x + column_char_pos[COLUMN_INSTRUMENT] * FONT_CHAR_WIDTH, text_y, inst_str, inst_color);
            font_draw_string(r, base_x + column_char_pos[COLUMN_VOLUME] * FONT_CHAR_WIDTH, text_y, vol_str, vol_color);
            font_draw_string(r, base_x + column_char_pos[COLUMN_EFFECT] * FONT_CHAR_WIDTH, text_y, fx_str, fx_color);
            font_draw_string(r, base_x + column_char_pos[COLUMN_PARAM] * FONT_CHAR_WIDTH, text_y, param_str, param_color);
            
            if (is_cursor_row && ch == ed->cursor_channel) {
                r->draw_rect(r, x, y, CHANNEL_WIDTH - 2, ROW_HEIGHT, g_theme.cursor);
                r->draw_rect(r, x + 1, y + 1, CHANNEL_WIDTH - 4, ROW_HEIGHT - 2, g_theme.cursor);
            }
            
            r->draw_line(r, x + CHANNEL_WIDTH - 2, y, x + CHANNEL_WIDTH - 2, y + ROW_HEIGHT, g_theme.grid_line);
        }
        
        if (is_playing_row) {
            r->draw_rect_filled(r, ROW_NUM_WIDTH - 4, y, 3, ROW_HEIGHT, g_theme.playhead);
        }
    }
    
    r->draw_line(r, ROW_NUM_WIDTH - 2, y_start, ROW_NUM_WIDTH - 2, y_start + visible_rows * ROW_HEIGHT, g_theme.panel_border);
}

static void render_sidebar(Editor* ed, int x, int y, int width, int height) {
    RendererPort* r = ed->renderer;
    draw_panel(r, x, y, width, height);
    
    int header_h = 24;
    r->draw_rect_filled(r, x, y, width, header_h, g_theme.panel_header);
    r->draw_line(r, x, y + header_h - 1, x + width, y + header_h - 1, g_theme.panel_border);
    font_draw_string(r, x + 8, y + 8, "INSTRUMENTS", g_theme.text_secondary);
    
    int list_y = y + header_h + 6;
    int row_h = 16;
    int detail_reserve = 120;
    int max_visible = (height - header_h - detail_reserve) / row_h;
    if (max_visible < 4) max_visible = 4;
    
    int count = ed->instruments ? ed->instruments->count : 0;
    int start = 0;
    if (count > max_visible) {
        start = ed->selected_instrument - max_visible / 2;
        if (start < 0) start = 0;
        if (start > count - max_visible) start = count - max_visible;
    }
    
    for (int i = 0; i < max_visible; i++) {
        int idx = start + i;
        if (idx >= count) break;
        Instrument* inst = instrument_bank_get(ed->instruments, idx);
        if (!inst) continue;
        int row_y = list_y + i * row_h;
        
        if (idx == ed->selected_instrument) {
            r->draw_rect_filled(r, x + 4, row_y + 2, width - 8, row_h - 2, g_theme.selection);
        }
        
        char line[32];
        snprintf(line, sizeof(line), "%02X %s", idx + 1, inst->name);
        font_draw_string(r, x + 8, row_y + 5, line, idx == ed->selected_instrument ? g_theme.text_primary : g_theme.text_secondary);
    }
    
    int details_y = list_y + max_visible * row_h + 8;
    font_draw_string(r, x + 8, details_y, "DETAILS", g_theme.text_dim);
    details_y += 14;
    
    if (ed->instruments && ed->instruments->count > 0) {
        Instrument* inst = instrument_bank_get(ed->instruments, ed->selected_instrument);
        if (inst) {
            char line[48];
            snprintf(line, sizeof(line), "Wave: %s", waveform_name(inst->waveform));
            font_draw_string(r, x + 8, details_y, line, g_theme.text_secondary);
            details_y += 12;
            
            snprintf(line, sizeof(line), "ADSR A%.2f D%.2f", inst->attack, inst->decay);
            font_draw_string(r, x + 8, details_y, line, g_theme.text_secondary);
            details_y += 12;
            
            snprintf(line, sizeof(line), "S%.2f R%.2f", inst->sustain, inst->release);
            font_draw_string(r, x + 8, details_y, line, g_theme.text_secondary);
            details_y += 12;
            
            if (inst->filter_enabled) {
                snprintf(line, sizeof(line), "Filter: %s %.0fHz", filter_type_name(inst->filter_type), inst->filter_cutoff);
            } else {
                snprintf(line, sizeof(line), "Filter: Off");
            }
            font_draw_string(r, x + 8, details_y, line, g_theme.text_secondary);
            details_y += 12;
            
            if (inst->lfo_enabled) {
                snprintf(line, sizeof(line), "LFO: %s %s %.2fHz", lfo_wave_name(inst->lfo_waveform),
                         lfo_target_name(inst->lfo_target), inst->lfo_rate);
            } else {
                snprintf(line, sizeof(line), "LFO: Off");
            }
            font_draw_string(r, x + 8, details_y, line, g_theme.text_secondary);
            details_y += 12;
        }
    }
    
    int help_y = y + height - 106;
    font_draw_string(r, x + 8, help_y, "Click: Select", g_theme.text_dim);
    help_y += 12;
    font_draw_string(r, x + 8, help_y, "Ctrl+HJKL Move", g_theme.text_dim);
    help_y += 12;
    font_draw_string(r, x + 8, help_y, "Ctrl+U/D Page", g_theme.text_dim);
    help_y += 12;
    font_draw_string(r, x + 8, help_y, "Ctrl+G Top/Bottom", g_theme.text_dim);
    help_y += 12;
    font_draw_string(r, x + 8, help_y, "Ctrl+C/X/V Row", g_theme.text_dim);
    help_y += 12;
    font_draw_string(r, x + 8, help_y, "F6/F7 Inst", g_theme.text_dim);
    help_y += 12;
    font_draw_string(r, x + 8, help_y, "Q/W Step", g_theme.text_dim);
}

static void render_status_bar(Editor* ed, int y, int width) {
    RendererPort* r = ed->renderer;
    
    r->draw_rect_filled(r, 0, y, width, STATUS_BAR_HEIGHT, g_theme.panel_header);
    r->draw_line(r, 0, y, width, y, g_theme.panel_border);
    
    int x = 10;
    
    Color mode_color = ed->editing ? g_theme.accent_error : g_theme.text_dim;
    r->draw_rect_filled(r, x, y + 6, 50, 16, ed->editing ? (Color){80, 40, 40, 255} : g_theme.bg_dark);
    r->draw_rect(r, x, y + 6, 50, 16, mode_color);
    font_draw_string(r, x + 6, y + 10, ed->editing ? "EDIT" : "VIEW", mode_color);
    
    x += 60;
    const char* view_name = ed->show_piano_roll ? "PIANO ROLL" : "PATTERN";
    r->draw_rect_filled(r, x, y + 6, 70, 16, g_theme.bg_dark);
    font_draw_string(r, x + 4, y + 10, view_name, g_theme.text_secondary);
    
    x += 80;
    char pos_str[16];
    pos_str[0] = 'R'; pos_str[1] = ':';
    pos_str[2] = '0' + (ed->cursor_row / 10);
    pos_str[3] = '0' + (ed->cursor_row % 10);
    pos_str[4] = ' '; pos_str[5] = 'C'; pos_str[6] = ':';
    pos_str[7] = '1' + ed->cursor_channel;
    pos_str[8] = '\0';
    font_draw_string(r, x, y + 10, pos_str, g_theme.text_dim);
    
    x += 60;
    char inst_str[16];
    if (ed->instruments && ed->instruments->count > 0) {
        snprintf(inst_str, sizeof(inst_str), "I:%02X", ed->selected_instrument + 1);
    } else {
        snprintf(inst_str, sizeof(inst_str), "I:--");
    }
    font_draw_string(r, x, y + 10, inst_str, g_theme.text_secondary);
    
    x += 40;
    char step_str[16];
    snprintf(step_str, sizeof(step_str), "Step:%d", ed->step);
    font_draw_string(r, x, y + 10, step_str, g_theme.text_dim);
    
    x = width - 180;
    font_draw_string(r, x, y + 10, "F1/F2:Oct", g_theme.text_dim);
    x += 60;
    font_draw_string(r, x, y + 10, "F3:View", g_theme.text_dim);
    x += 50;
    font_draw_string(r, x, y + 10, "TAB:Edit", g_theme.text_dim);
}

static void render_resize_overlay(Editor* ed, int width, int height) {
    if (!ed || ed->resize_timer <= 0.0f) return;
    RendererPort* r = ed->renderer;
    const char* title = "Resizing...";
    char size_str[32];
    snprintf(size_str, sizeof(size_str), "%dx %d pixels", width, height);
    
    int text_w = (int)strlen(size_str) * FONT_CHAR_WIDTH;
    int title_w = (int)strlen(title) * FONT_CHAR_WIDTH;
    int box_w = (text_w > title_w ? text_w : title_w) + 24;
    int box_h = FONT_CHAR_HEIGHT * 2 + 20;
    int x = (width - box_w) / 2;
    int y = (height - box_h) / 2;
    
    Color bg = g_theme.bg_dark;
    bg.a = 230;
    r->draw_rect_filled(r, x, y, box_w, box_h, bg);
    r->draw_rect(r, x, y, box_w, box_h, g_theme.panel_border);
    font_draw_string(r, x + 12, y + 6, title, g_theme.text_secondary);
    font_draw_string(r, x + 12, y + 6 + FONT_CHAR_HEIGHT + 4, size_str, g_theme.text_primary);
}

void editor_render(Editor* ed) {
    if (!ed || !ed->renderer) return;
    RendererPort* r = ed->renderer;
    int width, height;
    r->get_size(r, &width, &height);
    if (width <= 0 || height <= 0) return;
    
    r->clear(r, g_theme.bg_dark);
    
    render_menu_bar(ed, width);
    render_header(ed, width);
    
    int content_y = HEADER_HEIGHT + MENU_BAR_HEIGHT;
    int content_h = height - HEADER_HEIGHT - MENU_BAR_HEIGHT - STATUS_BAR_HEIGHT;
    int grid_width = ROW_NUM_WIDTH + 8 * CHANNEL_WIDTH;
    int sidebar_x = grid_width;
    int sidebar_width = width - grid_width;
    if (sidebar_width < 140) {
        sidebar_width = 0;
    }
    
    if (ed->show_piano_roll) {
        int roll_width = sidebar_width > 0 ? sidebar_x : width;
        piano_roll_render(&ed->piano_roll, 0, content_y, roll_width, content_h);
    } else {
        int ch_header_y = MENU_BAR_HEIGHT + HEADER_HEIGHT;
        render_channel_headers(ed, ch_header_y, width);
        
        int grid_y = MENU_BAR_HEIGHT + HEADER_HEIGHT + CHANNEL_HEADER_HEIGHT;
        int grid_h = height - grid_y - STATUS_BAR_HEIGHT;
        render_pattern_grid(ed, grid_y, grid_h);
    }
    
    if (sidebar_width > 0) {
        render_sidebar(ed, sidebar_x, content_y, sidebar_width, content_h);
        r->draw_line(r, sidebar_x, content_y, sidebar_x, content_y + content_h, g_theme.panel_border);
    }
    
    render_status_bar(ed, height - STATUS_BAR_HEIGHT, width);
    toast_render(&ed->toasts, r, width, height);
    render_resize_overlay(ed, width, height);
    render_menu_popup(ed);
    
    (void)note_names;
}
