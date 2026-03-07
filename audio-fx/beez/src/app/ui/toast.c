#include "toast.h"
#include "font.h"
#include "theme.h"
#include <string.h>
#include <stdarg.h>
#include <stdio.h>

#define TOAST_PADDING_X 10
#define TOAST_PADDING_Y 6
#define TOAST_SPACING 6
#define TOAST_FADE_IN 0.15f
#define TOAST_FADE_OUT 0.2f

void toast_queue_init(ToastQueue* queue) {
    if (!queue) return;
    memset(queue, 0, sizeof(ToastQueue));
    queue->default_duration = 2.5f;
}

void toast_queue_clear(ToastQueue* queue) {
    if (!queue) return;
    for (int i = 0; i < TOAST_MAX; i++) {
        queue->items[i].active = false;
        queue->items[i].timer = 0.0f;
    }
}

static void toast_write(Toast* toast, const char* message, float duration) {
    if (!toast || !message) return;
    snprintf(toast->message, sizeof(toast->message), "%s", message);
    toast->duration = duration;
    toast->timer = duration;
    toast->active = true;
}

void toast_push(ToastQueue* queue, const char* message) {
    if (!queue || !message) return;
    int slot = -1;
    for (int i = 0; i < TOAST_MAX; i++) {
        if (!queue->items[i].active) {
            slot = i;
            break;
        }
    }
    if (slot < 0) {
        slot = 0;
    }
    toast_write(&queue->items[slot], message, queue->default_duration);
}

void toast_pushf(ToastQueue* queue, const char* fmt, ...) {
    if (!queue || !fmt) return;
    char buffer[128];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);
    toast_push(queue, buffer);
}

void toast_update(ToastQueue* queue, float dt) {
    if (!queue) return;
    if (dt < 0.0f) dt = 0.0f;
    for (int i = 0; i < TOAST_MAX; i++) {
        if (!queue->items[i].active) continue;
        queue->items[i].timer -= dt;
        if (queue->items[i].timer <= 0.0f) {
            queue->items[i].active = false;
        }
    }
}

static float toast_alpha(const Toast* toast) {
    if (!toast || !toast->active) return 0.0f;
    float elapsed = toast->duration - toast->timer;
    float alpha = 1.0f;
    if (elapsed < TOAST_FADE_IN) {
        alpha = elapsed / TOAST_FADE_IN;
    } else if (toast->timer < TOAST_FADE_OUT) {
        alpha = toast->timer / TOAST_FADE_OUT;
    }
    if (alpha < 0.0f) alpha = 0.0f;
    if (alpha > 1.0f) alpha = 1.0f;
    return alpha;
}

void toast_render(const ToastQueue* queue, RendererPort* renderer, int width, int height) {
    if (!queue || !renderer) return;
    int y = height - 20;
    for (int i = 0; i < TOAST_MAX; i++) {
        if (!queue->items[i].active) continue;
        const Toast* toast = &queue->items[i];
        float alpha = toast_alpha(toast);
        if (alpha <= 0.0f) continue;
        
        int text_len = (int)strlen(toast->message);
        int box_w = text_len * FONT_CHAR_WIDTH + TOAST_PADDING_X * 2;
        int box_h = FONT_CHAR_HEIGHT + TOAST_PADDING_Y * 2;
        int x = width - box_w - 12;
        y -= box_h;
        
        Color bg = g_theme.bg_dark;
        bg.a = (uint8_t)(bg.a * alpha);
        Color border = g_theme.panel_border;
        border.a = (uint8_t)(border.a * alpha);
        Color text = g_theme.text_primary;
        text.a = (uint8_t)(text.a * alpha);
        
        renderer->draw_rect_filled(renderer, x, y, box_w, box_h, bg);
        renderer->draw_rect(renderer, x, y, box_w, box_h, border);
        font_draw_string(renderer, x + TOAST_PADDING_X, y + TOAST_PADDING_Y, toast->message, text);
        y -= TOAST_SPACING;
    }
}
