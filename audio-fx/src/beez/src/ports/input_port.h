#pragma once

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    KEY_UNKNOWN = 0,
    KEY_A, KEY_B, KEY_C, KEY_D, KEY_E, KEY_F, KEY_G, KEY_H,
    KEY_I, KEY_J, KEY_K, KEY_L, KEY_M, KEY_N, KEY_O, KEY_P,
    KEY_Q, KEY_R, KEY_S, KEY_T, KEY_U, KEY_V, KEY_W, KEY_X,
    KEY_Y, KEY_Z,
    KEY_0, KEY_1, KEY_2, KEY_3, KEY_4, KEY_5, KEY_6, KEY_7, KEY_8, KEY_9,
    KEY_F1, KEY_F2, KEY_F3, KEY_F4, KEY_F5, KEY_F6,
    KEY_F7, KEY_F8, KEY_F9, KEY_F10, KEY_F11, KEY_F12,
    KEY_UP, KEY_DOWN, KEY_LEFT, KEY_RIGHT,
    KEY_SPACE, KEY_ENTER, KEY_ESCAPE, KEY_TAB, KEY_BACKSPACE,
    KEY_SHIFT, KEY_CTRL, KEY_ALT,
    KEY_COUNT
} KeyCode;

typedef enum {
    MOUSE_LEFT,
    MOUSE_RIGHT,
    MOUSE_MIDDLE
} MouseButton;

typedef struct InputPort InputPort;

struct InputPort {
    void (*poll_events)(InputPort* port);
    bool (*is_key_down)(const InputPort* port, KeyCode key);
    bool (*is_key_pressed)(const InputPort* port, KeyCode key);
    bool (*is_key_released)(const InputPort* port, KeyCode key);
    bool (*is_mouse_down)(const InputPort* port, MouseButton button);
    bool (*is_mouse_clicked)(const InputPort* port, MouseButton button);
    void (*get_mouse_position)(const InputPort* port, int* x, int* y);
    int (*get_mouse_wheel)(const InputPort* port);
    void* impl;
};

#ifdef __cplusplus
}
#endif
