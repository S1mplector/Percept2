#include "sdl_adapter.h"
#include <SDL2/SDL.h>
#include <stdlib.h>
#include <string.h>

typedef struct {
    SDL_Window* window;
    SDL_Renderer* renderer;
    int width;
    int height;
    bool should_close;
} SDLRendererImpl;

typedef struct {
    bool keys_current[KEY_COUNT];
    bool keys_previous[KEY_COUNT];
    bool mouse_current[3];
    bool mouse_previous[3];
    int mouse_x;
    int mouse_y;
    int mouse_wheel;
} SDLInputImpl;

static KeyCode sdl_scancode_to_keycode(SDL_Scancode sc) {
    switch (sc) {
        case SDL_SCANCODE_A: return KEY_A;
        case SDL_SCANCODE_B: return KEY_B;
        case SDL_SCANCODE_C: return KEY_C;
        case SDL_SCANCODE_D: return KEY_D;
        case SDL_SCANCODE_E: return KEY_E;
        case SDL_SCANCODE_F: return KEY_F;
        case SDL_SCANCODE_G: return KEY_G;
        case SDL_SCANCODE_H: return KEY_H;
        case SDL_SCANCODE_I: return KEY_I;
        case SDL_SCANCODE_J: return KEY_J;
        case SDL_SCANCODE_K: return KEY_K;
        case SDL_SCANCODE_L: return KEY_L;
        case SDL_SCANCODE_M: return KEY_M;
        case SDL_SCANCODE_N: return KEY_N;
        case SDL_SCANCODE_O: return KEY_O;
        case SDL_SCANCODE_P: return KEY_P;
        case SDL_SCANCODE_Q: return KEY_Q;
        case SDL_SCANCODE_R: return KEY_R;
        case SDL_SCANCODE_S: return KEY_S;
        case SDL_SCANCODE_T: return KEY_T;
        case SDL_SCANCODE_U: return KEY_U;
        case SDL_SCANCODE_V: return KEY_V;
        case SDL_SCANCODE_W: return KEY_W;
        case SDL_SCANCODE_X: return KEY_X;
        case SDL_SCANCODE_Y: return KEY_Y;
        case SDL_SCANCODE_Z: return KEY_Z;
        case SDL_SCANCODE_0: return KEY_0;
        case SDL_SCANCODE_1: return KEY_1;
        case SDL_SCANCODE_2: return KEY_2;
        case SDL_SCANCODE_3: return KEY_3;
        case SDL_SCANCODE_4: return KEY_4;
        case SDL_SCANCODE_5: return KEY_5;
        case SDL_SCANCODE_6: return KEY_6;
        case SDL_SCANCODE_7: return KEY_7;
        case SDL_SCANCODE_8: return KEY_8;
        case SDL_SCANCODE_9: return KEY_9;
        case SDL_SCANCODE_UP: return KEY_UP;
        case SDL_SCANCODE_DOWN: return KEY_DOWN;
        case SDL_SCANCODE_LEFT: return KEY_LEFT;
        case SDL_SCANCODE_RIGHT: return KEY_RIGHT;
        case SDL_SCANCODE_SPACE: return KEY_SPACE;
        case SDL_SCANCODE_RETURN: return KEY_ENTER;
        case SDL_SCANCODE_ESCAPE: return KEY_ESCAPE;
        case SDL_SCANCODE_TAB: return KEY_TAB;
        case SDL_SCANCODE_BACKSPACE: return KEY_BACKSPACE;
        case SDL_SCANCODE_LSHIFT: return KEY_SHIFT;
        case SDL_SCANCODE_RSHIFT: return KEY_SHIFT;
        case SDL_SCANCODE_LCTRL: return KEY_CTRL;
        case SDL_SCANCODE_RCTRL: return KEY_CTRL;
        case SDL_SCANCODE_LALT: return KEY_ALT;
        case SDL_SCANCODE_RALT: return KEY_ALT;
        case SDL_SCANCODE_F1: return KEY_F1;
        case SDL_SCANCODE_F2: return KEY_F2;
        case SDL_SCANCODE_F3: return KEY_F3;
        case SDL_SCANCODE_F4: return KEY_F4;
        case SDL_SCANCODE_F5: return KEY_F5;
        case SDL_SCANCODE_F6: return KEY_F6;
        case SDL_SCANCODE_F7: return KEY_F7;
        case SDL_SCANCODE_F8: return KEY_F8;
        case SDL_SCANCODE_F9: return KEY_F9;
        case SDL_SCANCODE_F10: return KEY_F10;
        case SDL_SCANCODE_F11: return KEY_F11;
        case SDL_SCANCODE_F12: return KEY_F12;
        default: return KEY_UNKNOWN;
    }
}

static bool sdl_renderer_initialize(RendererPort* port, int width, int height, const char* title) {
    if (!port || width <= 0 || height <= 0) return false;
    SDLRendererImpl* impl = (SDLRendererImpl*)port->impl;
    if (!impl) return false;
    
    if (SDL_Init(SDL_INIT_VIDEO) < 0) {
        return false;
    }
    
    impl->window = SDL_CreateWindow(title,
        SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED,
        width, height, SDL_WINDOW_SHOWN | SDL_WINDOW_RESIZABLE);
    
    if (!impl->window) {
        return false;
    }
    
    impl->renderer = SDL_CreateRenderer(impl->window, -1,
        SDL_RENDERER_ACCELERATED | SDL_RENDERER_PRESENTVSYNC);
    
    if (!impl->renderer) {
        SDL_DestroyWindow(impl->window);
        return false;
    }
    
    impl->width = width;
    impl->height = height;
    impl->should_close = false;
    g_renderer_impl = impl;
    
    return true;
}

static void sdl_renderer_shutdown(RendererPort* port) {
    if (!port) return;
    SDLRendererImpl* impl = (SDLRendererImpl*)port->impl;
    if (!impl) return;
    if (impl->renderer) {
        SDL_DestroyRenderer(impl->renderer);
        impl->renderer = NULL;
    }
    if (impl->window) {
        SDL_DestroyWindow(impl->window);
        impl->window = NULL;
    }
    SDL_Quit();
    g_renderer_impl = NULL;
}

static void sdl_renderer_begin_frame(RendererPort* port) {
    (void)port;
}

static void sdl_renderer_end_frame(RendererPort* port) {
    SDLRendererImpl* impl = port ? (SDLRendererImpl*)port->impl : NULL;
    if (!impl) return;
    SDL_RenderPresent(impl->renderer);
}

static void sdl_renderer_clear(RendererPort* port, Color color) {
    SDLRendererImpl* impl = port ? (SDLRendererImpl*)port->impl : NULL;
    if (!impl) return;
    SDL_SetRenderDrawColor(impl->renderer, color.r, color.g, color.b, color.a);
    SDL_RenderClear(impl->renderer);
}

static void sdl_renderer_draw_rect(RendererPort* port, int x, int y, int w, int h, Color color) {
    SDLRendererImpl* impl = port ? (SDLRendererImpl*)port->impl : NULL;
    if (!impl || w <= 0 || h <= 0) return;
    SDL_SetRenderDrawColor(impl->renderer, color.r, color.g, color.b, color.a);
    SDL_Rect rect = {x, y, w, h};
    SDL_RenderDrawRect(impl->renderer, &rect);
}

static void sdl_renderer_draw_rect_filled(RendererPort* port, int x, int y, int w, int h, Color color) {
    SDLRendererImpl* impl = port ? (SDLRendererImpl*)port->impl : NULL;
    if (!impl || w <= 0 || h <= 0) return;
    SDL_SetRenderDrawColor(impl->renderer, color.r, color.g, color.b, color.a);
    SDL_Rect rect = {x, y, w, h};
    SDL_RenderFillRect(impl->renderer, &rect);
}

static void sdl_renderer_draw_line(RendererPort* port, int x1, int y1, int x2, int y2, Color color) {
    SDLRendererImpl* impl = port ? (SDLRendererImpl*)port->impl : NULL;
    if (!impl) return;
    SDL_SetRenderDrawColor(impl->renderer, color.r, color.g, color.b, color.a);
    SDL_RenderDrawLine(impl->renderer, x1, y1, x2, y2);
}

static void sdl_renderer_draw_text(RendererPort* port, int x, int y, const char* text, Color color) {
    (void)port;
    (void)x;
    (void)y;
    (void)text;
    (void)color;
}

static void sdl_renderer_get_size(const RendererPort* port, int* width, int* height) {
    if (!width || !height) return;
    SDLRendererImpl* impl = port ? (SDLRendererImpl*)port->impl : NULL;
    if (!impl) {
        *width = 0;
        *height = 0;
        return;
    }
    *width = impl->width;
    *height = impl->height;
}

static bool sdl_renderer_should_close(const RendererPort* port) {
    SDLRendererImpl* impl = port ? (SDLRendererImpl*)port->impl : NULL;
    return impl ? impl->should_close : true;
}

static SDLInputImpl* g_input_impl = NULL;
static SDLRendererImpl* g_renderer_impl = NULL;

static void sdl_input_poll_events(InputPort* port) {
    if (!port) return;
    SDLInputImpl* impl = (SDLInputImpl*)port->impl;
    if (!impl) return;
    SDLRendererImpl* rend_impl = g_renderer_impl;
    
    memcpy(impl->keys_previous, impl->keys_current, sizeof(impl->keys_current));
    memcpy(impl->mouse_previous, impl->mouse_current, sizeof(impl->mouse_current));
    impl->mouse_wheel = 0;
    
    SDL_Event event;
    while (SDL_PollEvent(&event)) {
        switch (event.type) {
            case SDL_QUIT:
                break;
            case SDL_WINDOWEVENT:
                if (event.window.event == SDL_WINDOWEVENT_SIZE_CHANGED) {
                    if (rend_impl) {
                        rend_impl->width = event.window.data1;
                        rend_impl->height = event.window.data2;
                    }
                }
                break;
            case SDL_KEYDOWN: {
                KeyCode kc = sdl_scancode_to_keycode(event.key.keysym.scancode);
                if (kc != KEY_UNKNOWN) {
                    impl->keys_current[kc] = true;
                }
                break;
            }
            case SDL_KEYUP: {
                KeyCode kc = sdl_scancode_to_keycode(event.key.keysym.scancode);
                if (kc != KEY_UNKNOWN) {
                    impl->keys_current[kc] = false;
                }
                break;
            }
            case SDL_MOUSEBUTTONDOWN:
                impl->mouse_x = event.button.x;
                impl->mouse_y = event.button.y;
                if (event.button.button == SDL_BUTTON_LEFT) impl->mouse_current[MOUSE_LEFT] = true;
                if (event.button.button == SDL_BUTTON_RIGHT) impl->mouse_current[MOUSE_RIGHT] = true;
                if (event.button.button == SDL_BUTTON_MIDDLE) impl->mouse_current[MOUSE_MIDDLE] = true;
                break;
            case SDL_MOUSEBUTTONUP:
                impl->mouse_x = event.button.x;
                impl->mouse_y = event.button.y;
                if (event.button.button == SDL_BUTTON_LEFT) impl->mouse_current[MOUSE_LEFT] = false;
                if (event.button.button == SDL_BUTTON_RIGHT) impl->mouse_current[MOUSE_RIGHT] = false;
                if (event.button.button == SDL_BUTTON_MIDDLE) impl->mouse_current[MOUSE_MIDDLE] = false;
                break;
            case SDL_MOUSEMOTION:
                impl->mouse_x = event.motion.x;
                impl->mouse_y = event.motion.y;
                break;
            case SDL_MOUSEWHEEL:
                impl->mouse_wheel = event.wheel.y;
                break;
        }
    }
    (void)rend_impl;
}

static bool sdl_input_is_key_down(const InputPort* port, KeyCode key) {
    SDLInputImpl* impl = port ? (SDLInputImpl*)port->impl : NULL;
    if (!impl || key < 0 || key >= KEY_COUNT) return false;
    return impl->keys_current[key];
}

static bool sdl_input_is_key_pressed(const InputPort* port, KeyCode key) {
    SDLInputImpl* impl = port ? (SDLInputImpl*)port->impl : NULL;
    if (!impl || key < 0 || key >= KEY_COUNT) return false;
    return impl->keys_current[key] && !impl->keys_previous[key];
}

static bool sdl_input_is_key_released(const InputPort* port, KeyCode key) {
    SDLInputImpl* impl = port ? (SDLInputImpl*)port->impl : NULL;
    if (!impl || key < 0 || key >= KEY_COUNT) return false;
    return !impl->keys_current[key] && impl->keys_previous[key];
}

static bool sdl_input_is_mouse_down(const InputPort* port, MouseButton button) {
    SDLInputImpl* impl = port ? (SDLInputImpl*)port->impl : NULL;
    if (!impl || button < 0 || button > MOUSE_MIDDLE) return false;
    return impl->mouse_current[button];
}

static bool sdl_input_is_mouse_clicked(const InputPort* port, MouseButton button) {
    SDLInputImpl* impl = port ? (SDLInputImpl*)port->impl : NULL;
    if (!impl || button < 0 || button > MOUSE_MIDDLE) return false;
    return impl->mouse_current[button] && !impl->mouse_previous[button];
}

static void sdl_input_get_mouse_position(const InputPort* port, int* x, int* y) {
    if (!x || !y) return;
    SDLInputImpl* impl = port ? (SDLInputImpl*)port->impl : NULL;
    if (!impl) {
        *x = 0;
        *y = 0;
        return;
    }
    *x = impl->mouse_x;
    *y = impl->mouse_y;
}

static int sdl_input_get_mouse_wheel(const InputPort* port) {
    SDLInputImpl* impl = port ? (SDLInputImpl*)port->impl : NULL;
    return impl ? impl->mouse_wheel : 0;
}

SDLPlatform* sdl_platform_create(void) {
    SDLPlatform* platform = (SDLPlatform*)malloc(sizeof(SDLPlatform));
    
    platform->renderer = (RendererPort*)malloc(sizeof(RendererPort));
    SDLRendererImpl* rend_impl = (SDLRendererImpl*)malloc(sizeof(SDLRendererImpl));
    memset(rend_impl, 0, sizeof(SDLRendererImpl));
    
    platform->renderer->impl = rend_impl;
    platform->renderer->initialize = sdl_renderer_initialize;
    platform->renderer->shutdown = sdl_renderer_shutdown;
    platform->renderer->begin_frame = sdl_renderer_begin_frame;
    platform->renderer->end_frame = sdl_renderer_end_frame;
    platform->renderer->clear = sdl_renderer_clear;
    platform->renderer->draw_rect = sdl_renderer_draw_rect;
    platform->renderer->draw_rect_filled = sdl_renderer_draw_rect_filled;
    platform->renderer->draw_line = sdl_renderer_draw_line;
    platform->renderer->draw_text = sdl_renderer_draw_text;
    platform->renderer->get_size = sdl_renderer_get_size;
    platform->renderer->should_close = sdl_renderer_should_close;
    
    platform->input = (InputPort*)malloc(sizeof(InputPort));
    SDLInputImpl* input_impl = (SDLInputImpl*)malloc(sizeof(SDLInputImpl));
    memset(input_impl, 0, sizeof(SDLInputImpl));
    g_input_impl = input_impl;
    
    platform->input->impl = input_impl;
    platform->input->poll_events = sdl_input_poll_events;
    platform->input->is_key_down = sdl_input_is_key_down;
    platform->input->is_key_pressed = sdl_input_is_key_pressed;
    platform->input->is_key_released = sdl_input_is_key_released;
    platform->input->is_mouse_down = sdl_input_is_mouse_down;
    platform->input->is_mouse_clicked = sdl_input_is_mouse_clicked;
    platform->input->get_mouse_position = sdl_input_get_mouse_position;
    platform->input->get_mouse_wheel = sdl_input_get_mouse_wheel;
    
    return platform;
}

void sdl_platform_destroy(SDLPlatform* platform) {
    if (platform) {
        if (platform->renderer) {
            sdl_renderer_shutdown(platform->renderer);
            free(platform->renderer->impl);
            free(platform->renderer);
        }
        if (platform->input) {
            free(platform->input->impl);
            free(platform->input);
        }
        free(platform);
    }
    g_input_impl = NULL;
}
