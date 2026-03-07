#import "macos_platform.h"
#import <Cocoa/Cocoa.h>
#import <QuartzCore/QuartzCore.h>
#include <stdlib.h>
#include <string.h>

typedef struct {
    NSWindow* window;
    NSView* view;
    CALayer* layer;
    CGContextRef bitmap_context;
    uint8_t* framebuffer;
    int width;
    int height;
    bool should_close;
} MacOSRendererImpl;

typedef struct {
    bool keys_current[KEY_COUNT];
    bool keys_previous[KEY_COUNT];
    bool mouse_current[3];
    bool mouse_previous[3];
    int mouse_x;
    int mouse_y;
    int mouse_wheel;
    bool quit_requested;
} MacOSInputImpl;

static MacOSInputImpl* g_input = NULL;
static MacOSRendererImpl* g_renderer = NULL;

@interface BeezView : NSView
@end

@implementation BeezView

- (BOOL)acceptsFirstResponder {
    return YES;
}

- (void)drawRect:(NSRect)dirtyRect {
    (void)dirtyRect;
    if (g_renderer && g_renderer->bitmap_context) {
        CGContextRef ctx = [[NSGraphicsContext currentContext] CGContext];
        CGImageRef image = CGBitmapContextCreateImage(g_renderer->bitmap_context);
        if (image) {
            CGContextDrawImage(ctx, CGRectMake(0, 0, g_renderer->width, g_renderer->height), image);
            CGImageRelease(image);
        }
    }
}

- (void)setFrameSize:(NSSize)newSize {
    [super setFrameSize:newSize];
    if (g_renderer) {
        int w = (int)newSize.width;
        int h = (int)newSize.height;
        if (w > 0 && h > 0 && (w != g_renderer->width || h != g_renderer->height)) {
            if (g_renderer->bitmap_context) {
                CGContextRelease(g_renderer->bitmap_context);
                g_renderer->bitmap_context = NULL;
            }
            free(g_renderer->framebuffer);
            g_renderer->framebuffer = (uint8_t*)calloc((size_t)w * (size_t)h * 4, 1);
            if (g_renderer->framebuffer) {
                CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
                if (colorSpace) {
                    g_renderer->bitmap_context = CGBitmapContextCreate(
                        g_renderer->framebuffer,
                        w, h, 8, w * 4,
                        colorSpace,
                        kCGImageAlphaPremultipliedLast
                    );
                    CGColorSpaceRelease(colorSpace);
                }
            }
            g_renderer->width = w;
            g_renderer->height = h;
        }
    }
}

- (void)keyDown:(NSEvent*)event {
    if (g_input) {
        unsigned short keyCode = [event keyCode];
        KeyCode kc = KEY_UNKNOWN;
        
        switch (keyCode) {
            case 0: kc = KEY_A; break;
            case 11: kc = KEY_B; break;
            case 8: kc = KEY_C; break;
            case 2: kc = KEY_D; break;
            case 14: kc = KEY_E; break;
            case 3: kc = KEY_F; break;
            case 5: kc = KEY_G; break;
            case 4: kc = KEY_H; break;
            case 34: kc = KEY_I; break;
            case 38: kc = KEY_J; break;
            case 40: kc = KEY_K; break;
            case 37: kc = KEY_L; break;
            case 46: kc = KEY_M; break;
            case 45: kc = KEY_N; break;
            case 31: kc = KEY_O; break;
            case 35: kc = KEY_P; break;
            case 12: kc = KEY_Q; break;
            case 15: kc = KEY_R; break;
            case 1: kc = KEY_S; break;
            case 17: kc = KEY_T; break;
            case 32: kc = KEY_U; break;
            case 9: kc = KEY_V; break;
            case 13: kc = KEY_W; break;
            case 7: kc = KEY_X; break;
            case 16: kc = KEY_Y; break;
            case 6: kc = KEY_Z; break;
            case 29: kc = KEY_0; break;
            case 18: kc = KEY_1; break;
            case 19: kc = KEY_2; break;
            case 20: kc = KEY_3; break;
            case 21: kc = KEY_4; break;
            case 23: kc = KEY_5; break;
            case 22: kc = KEY_6; break;
            case 26: kc = KEY_7; break;
            case 28: kc = KEY_8; break;
            case 25: kc = KEY_9; break;
            case 126: kc = KEY_UP; break;
            case 125: kc = KEY_DOWN; break;
            case 123: kc = KEY_LEFT; break;
            case 124: kc = KEY_RIGHT; break;
            case 49: kc = KEY_SPACE; break;
            case 36: kc = KEY_ENTER; break;
            case 53: kc = KEY_ESCAPE; break;
            case 48: kc = KEY_TAB; break;
            case 51: kc = KEY_BACKSPACE; break;
            case 56: kc = KEY_SHIFT; break;
            case 60: kc = KEY_SHIFT; break;
            case 59: kc = KEY_CTRL; break;
            case 62: kc = KEY_CTRL; break;
            case 58: kc = KEY_ALT; break;
            case 61: kc = KEY_ALT; break;
            case 122: kc = KEY_F1; break;
            case 120: kc = KEY_F2; break;
            case 99: kc = KEY_F3; break;
            case 118: kc = KEY_F4; break;
            case 96: kc = KEY_F5; break;
            case 97: kc = KEY_F6; break;
            case 98: kc = KEY_F7; break;
            case 100: kc = KEY_F8; break;
            case 101: kc = KEY_F9; break;
            case 109: kc = KEY_F10; break;
            case 103: kc = KEY_F11; break;
            case 111: kc = KEY_F12; break;
        }
        
        if (kc != KEY_UNKNOWN) {
            g_input->keys_current[kc] = true;
        }
    }
}

- (void)keyUp:(NSEvent*)event {
    if (g_input) {
        unsigned short keyCode = [event keyCode];
        KeyCode kc = KEY_UNKNOWN;
        
        switch (keyCode) {
            case 0: kc = KEY_A; break;
            case 11: kc = KEY_B; break;
            case 8: kc = KEY_C; break;
            case 2: kc = KEY_D; break;
            case 14: kc = KEY_E; break;
            case 3: kc = KEY_F; break;
            case 5: kc = KEY_G; break;
            case 4: kc = KEY_H; break;
            case 34: kc = KEY_I; break;
            case 38: kc = KEY_J; break;
            case 40: kc = KEY_K; break;
            case 37: kc = KEY_L; break;
            case 46: kc = KEY_M; break;
            case 45: kc = KEY_N; break;
            case 31: kc = KEY_O; break;
            case 35: kc = KEY_P; break;
            case 12: kc = KEY_Q; break;
            case 15: kc = KEY_R; break;
            case 1: kc = KEY_S; break;
            case 17: kc = KEY_T; break;
            case 32: kc = KEY_U; break;
            case 9: kc = KEY_V; break;
            case 13: kc = KEY_W; break;
            case 7: kc = KEY_X; break;
            case 16: kc = KEY_Y; break;
            case 6: kc = KEY_Z; break;
            case 29: kc = KEY_0; break;
            case 18: kc = KEY_1; break;
            case 19: kc = KEY_2; break;
            case 20: kc = KEY_3; break;
            case 21: kc = KEY_4; break;
            case 23: kc = KEY_5; break;
            case 22: kc = KEY_6; break;
            case 26: kc = KEY_7; break;
            case 28: kc = KEY_8; break;
            case 25: kc = KEY_9; break;
            case 126: kc = KEY_UP; break;
            case 125: kc = KEY_DOWN; break;
            case 123: kc = KEY_LEFT; break;
            case 124: kc = KEY_RIGHT; break;
            case 49: kc = KEY_SPACE; break;
            case 36: kc = KEY_ENTER; break;
            case 53: kc = KEY_ESCAPE; break;
            case 48: kc = KEY_TAB; break;
            case 51: kc = KEY_BACKSPACE; break;
            case 56: kc = KEY_SHIFT; break;
            case 60: kc = KEY_SHIFT; break;
            case 59: kc = KEY_CTRL; break;
            case 62: kc = KEY_CTRL; break;
            case 58: kc = KEY_ALT; break;
            case 61: kc = KEY_ALT; break;
            case 122: kc = KEY_F1; break;
            case 120: kc = KEY_F2; break;
            case 99: kc = KEY_F3; break;
            case 118: kc = KEY_F4; break;
            case 96: kc = KEY_F5; break;
            case 97: kc = KEY_F6; break;
            case 98: kc = KEY_F7; break;
            case 100: kc = KEY_F8; break;
            case 101: kc = KEY_F9; break;
            case 109: kc = KEY_F10; break;
            case 103: kc = KEY_F11; break;
            case 111: kc = KEY_F12; break;
        }
        
        if (kc != KEY_UNKNOWN) {
            g_input->keys_current[kc] = false;
        }
    }
}

- (void)mouseDown:(NSEvent*)event {
    if (g_input) {
        g_input->mouse_current[MOUSE_LEFT] = true;
        if (g_renderer) {
            NSPoint loc = [event locationInWindow];
            g_input->mouse_x = (int)loc.x;
            g_input->mouse_y = g_renderer->height - (int)loc.y;
        }
    }
}

- (void)mouseUp:(NSEvent*)event {
    if (g_input) {
        g_input->mouse_current[MOUSE_LEFT] = false;
        if (g_renderer) {
            NSPoint loc = [event locationInWindow];
            g_input->mouse_x = (int)loc.x;
            g_input->mouse_y = g_renderer->height - (int)loc.y;
        }
    }
}

- (void)rightMouseDown:(NSEvent*)event {
    if (g_input) {
        g_input->mouse_current[MOUSE_RIGHT] = true;
        if (g_renderer) {
            NSPoint loc = [event locationInWindow];
            g_input->mouse_x = (int)loc.x;
            g_input->mouse_y = g_renderer->height - (int)loc.y;
        }
    }
}

- (void)rightMouseUp:(NSEvent*)event {
    if (g_input) {
        g_input->mouse_current[MOUSE_RIGHT] = false;
        if (g_renderer) {
            NSPoint loc = [event locationInWindow];
            g_input->mouse_x = (int)loc.x;
            g_input->mouse_y = g_renderer->height - (int)loc.y;
        }
    }
}

- (void)mouseMoved:(NSEvent*)event {
    if (g_input && g_renderer) {
        NSPoint loc = [event locationInWindow];
        g_input->mouse_x = (int)loc.x;
        g_input->mouse_y = g_renderer->height - (int)loc.y;
    }
}

- (void)mouseDragged:(NSEvent*)event {
    [self mouseMoved:event];
}

- (void)scrollWheel:(NSEvent*)event {
    if (g_input) {
        g_input->mouse_wheel = (int)[event deltaY];
    }
}

@end

@interface BeezWindowDelegate : NSObject <NSWindowDelegate>
@end

@implementation BeezWindowDelegate

- (BOOL)windowShouldClose:(id)sender {
    (void)sender;
    if (g_renderer) g_renderer->should_close = true;
    if (g_input) g_input->quit_requested = true;
    return NO;
}

@end

static bool macos_renderer_initialize(RendererPort* port, int width, int height, const char* title) {
    if (!port || width <= 0 || height <= 0) return false;
    MacOSRendererImpl* impl = (MacOSRendererImpl*)port->impl;
    if (!impl) return false;
    
    @autoreleasepool {
        [NSApplication sharedApplication];
        [NSApp setActivationPolicy:NSApplicationActivationPolicyRegular];
        
        NSRect frame = NSMakeRect(100, 100, width, height);
        NSWindowStyleMask style = NSWindowStyleMaskTitled | NSWindowStyleMaskClosable | 
                                   NSWindowStyleMaskMiniaturizable | NSWindowStyleMaskResizable;
        
        impl->window = [[NSWindow alloc] initWithContentRect:frame
                                                   styleMask:style
                                                     backing:NSBackingStoreBuffered
                                                       defer:NO];
        
        [impl->window setTitle:[NSString stringWithUTF8String:title]];
        [impl->window setDelegate:[[BeezWindowDelegate alloc] init]];
        
        impl->view = [[BeezView alloc] initWithFrame:frame];
        [impl->window setContentView:impl->view];
        [impl->window setAcceptsMouseMovedEvents:YES];
        
        impl->width = width;
        impl->height = height;
        impl->should_close = false;
        
        impl->framebuffer = (uint8_t*)calloc((size_t)width * (size_t)height * 4, 1);
        if (!impl->framebuffer) {
            return false;
        }
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        if (!colorSpace) {
            free(impl->framebuffer);
            impl->framebuffer = NULL;
            return false;
        }
        impl->bitmap_context = CGBitmapContextCreate(
            impl->framebuffer,
            width, height, 8, width * 4,
            colorSpace,
            kCGImageAlphaPremultipliedLast
        );
        CGColorSpaceRelease(colorSpace);
        if (!impl->bitmap_context) {
            free(impl->framebuffer);
            impl->framebuffer = NULL;
            return false;
        }
        
        [impl->window makeKeyAndOrderFront:nil];
        [NSApp activateIgnoringOtherApps:YES];
        
        g_renderer = impl;
    }
    
    return true;
}

static void macos_renderer_shutdown(RendererPort* port) {
    if (!port) return;
    MacOSRendererImpl* impl = (MacOSRendererImpl*)port->impl;
    if (!impl) return;
    
    if (impl->bitmap_context) {
        CGContextRelease(impl->bitmap_context);
        impl->bitmap_context = NULL;
    }
    free(impl->framebuffer);
    impl->framebuffer = NULL;
    
    g_renderer = NULL;
}

static void macos_renderer_begin_frame(RendererPort* port) {
    (void)port;
}

static void macos_renderer_end_frame(RendererPort* port) {
    MacOSRendererImpl* impl = port ? (MacOSRendererImpl*)port->impl : NULL;
    if (!impl) return;
    @autoreleasepool {
        [impl->view setNeedsDisplay:YES];
    }
}

static void macos_renderer_clear(RendererPort* port, Color color) {
    MacOSRendererImpl* impl = port ? (MacOSRendererImpl*)port->impl : NULL;
    if (!impl || !impl->framebuffer) return;
    uint8_t* fb = impl->framebuffer;
    
    for (int i = 0; i < impl->width * impl->height; i++) {
        fb[i * 4 + 0] = color.r;
        fb[i * 4 + 1] = color.g;
        fb[i * 4 + 2] = color.b;
        fb[i * 4 + 3] = color.a;
    }
}

static void set_pixel(MacOSRendererImpl* impl, int x, int y, Color color) {
    if (!impl || !impl->framebuffer) return;
    if (x < 0 || x >= impl->width || y < 0 || y >= impl->height) return;
    int idx = (y * impl->width + x) * 4;
    impl->framebuffer[idx + 0] = color.r;
    impl->framebuffer[idx + 1] = color.g;
    impl->framebuffer[idx + 2] = color.b;
    impl->framebuffer[idx + 3] = color.a;
}

static void macos_renderer_draw_rect(RendererPort* port, int x, int y, int w, int h, Color color) {
    MacOSRendererImpl* impl = port ? (MacOSRendererImpl*)port->impl : NULL;
    if (!impl || w <= 0 || h <= 0) return;
    for (int i = x; i < x + w; i++) {
        set_pixel(impl, i, y, color);
        set_pixel(impl, i, y + h - 1, color);
    }
    for (int j = y; j < y + h; j++) {
        set_pixel(impl, x, j, color);
        set_pixel(impl, x + w - 1, j, color);
    }
}

static void macos_renderer_draw_rect_filled(RendererPort* port, int x, int y, int w, int h, Color color) {
    MacOSRendererImpl* impl = port ? (MacOSRendererImpl*)port->impl : NULL;
    if (!impl || w <= 0 || h <= 0) return;
    for (int j = y; j < y + h; j++) {
        for (int i = x; i < x + w; i++) {
            set_pixel(impl, i, j, color);
        }
    }
}

static void macos_renderer_draw_line(RendererPort* port, int x1, int y1, int x2, int y2, Color color) {
    MacOSRendererImpl* impl = port ? (MacOSRendererImpl*)port->impl : NULL;
    if (!impl) return;
    
    int dx = abs(x2 - x1);
    int dy = abs(y2 - y1);
    int sx = x1 < x2 ? 1 : -1;
    int sy = y1 < y2 ? 1 : -1;
    int err = dx - dy;
    
    while (1) {
        set_pixel(impl, x1, y1, color);
        if (x1 == x2 && y1 == y2) break;
        int e2 = 2 * err;
        if (e2 > -dy) { err -= dy; x1 += sx; }
        if (e2 < dx) { err += dx; y1 += sy; }
    }
}

static void macos_renderer_draw_text(RendererPort* port, int x, int y, const char* text, Color color) {
    (void)port; (void)x; (void)y; (void)text; (void)color;
}

static void macos_renderer_get_size(const RendererPort* port, int* width, int* height) {
    if (!width || !height) return;
    MacOSRendererImpl* impl = port ? (MacOSRendererImpl*)port->impl : NULL;
    if (!impl) {
        *width = 0;
        *height = 0;
        return;
    }
    *width = impl->width;
    *height = impl->height;
}

static bool macos_renderer_should_close(const RendererPort* port) {
    MacOSRendererImpl* impl = port ? (MacOSRendererImpl*)port->impl : NULL;
    return impl ? impl->should_close : true;
}

static void macos_input_poll_events(InputPort* port) {
    if (!port) return;
    MacOSInputImpl* impl = (MacOSInputImpl*)port->impl;
    if (!impl) return;
    
    memcpy(impl->keys_previous, impl->keys_current, sizeof(impl->keys_current));
    memcpy(impl->mouse_previous, impl->mouse_current, sizeof(impl->mouse_current));
    impl->mouse_wheel = 0;
    
    @autoreleasepool {
        NSEvent* event;
        while ((event = [NSApp nextEventMatchingMask:NSEventMaskAny
                                           untilDate:nil
                                              inMode:NSDefaultRunLoopMode
                                             dequeue:YES])) {
            [NSApp sendEvent:event];
        }
    }
}

static bool macos_input_is_key_down(const InputPort* port, KeyCode key) {
    MacOSInputImpl* impl = port ? (MacOSInputImpl*)port->impl : NULL;
    if (!impl || key < 0 || key >= KEY_COUNT) return false;
    return impl->keys_current[key];
}

static bool macos_input_is_key_pressed(const InputPort* port, KeyCode key) {
    MacOSInputImpl* impl = port ? (MacOSInputImpl*)port->impl : NULL;
    if (!impl || key < 0 || key >= KEY_COUNT) return false;
    return impl->keys_current[key] && !impl->keys_previous[key];
}

static bool macos_input_is_key_released(const InputPort* port, KeyCode key) {
    MacOSInputImpl* impl = port ? (MacOSInputImpl*)port->impl : NULL;
    if (!impl || key < 0 || key >= KEY_COUNT) return false;
    return !impl->keys_current[key] && impl->keys_previous[key];
}

static bool macos_input_is_mouse_down(const InputPort* port, MouseButton button) {
    MacOSInputImpl* impl = port ? (MacOSInputImpl*)port->impl : NULL;
    if (!impl || button < 0 || button > MOUSE_MIDDLE) return false;
    return impl->mouse_current[button];
}

static bool macos_input_is_mouse_clicked(const InputPort* port, MouseButton button) {
    MacOSInputImpl* impl = port ? (MacOSInputImpl*)port->impl : NULL;
    if (!impl || button < 0 || button > MOUSE_MIDDLE) return false;
    return impl->mouse_current[button] && !impl->mouse_previous[button];
}

static void macos_input_get_mouse_position(const InputPort* port, int* x, int* y) {
    if (!x || !y) return;
    MacOSInputImpl* impl = port ? (MacOSInputImpl*)port->impl : NULL;
    if (!impl) {
        *x = 0;
        *y = 0;
        return;
    }
    *x = impl->mouse_x;
    *y = impl->mouse_y;
}

static int macos_input_get_mouse_wheel(const InputPort* port) {
    MacOSInputImpl* impl = port ? (MacOSInputImpl*)port->impl : NULL;
    return impl ? impl->mouse_wheel : 0;
}

MacOSPlatform* macos_platform_create(void) {
    MacOSPlatform* platform = (MacOSPlatform*)calloc(1, sizeof(MacOSPlatform));
    if (!platform) return NULL;
    
    platform->renderer = (RendererPort*)calloc(1, sizeof(RendererPort));
    MacOSRendererImpl* rend_impl = (MacOSRendererImpl*)calloc(1, sizeof(MacOSRendererImpl));
    
    platform->renderer->impl = rend_impl;
    platform->renderer->initialize = macos_renderer_initialize;
    platform->renderer->shutdown = macos_renderer_shutdown;
    platform->renderer->begin_frame = macos_renderer_begin_frame;
    platform->renderer->end_frame = macos_renderer_end_frame;
    platform->renderer->clear = macos_renderer_clear;
    platform->renderer->draw_rect = macos_renderer_draw_rect;
    platform->renderer->draw_rect_filled = macos_renderer_draw_rect_filled;
    platform->renderer->draw_line = macos_renderer_draw_line;
    platform->renderer->draw_text = macos_renderer_draw_text;
    platform->renderer->get_size = macos_renderer_get_size;
    platform->renderer->should_close = macos_renderer_should_close;
    
    platform->input = (InputPort*)calloc(1, sizeof(InputPort));
    MacOSInputImpl* input_impl = (MacOSInputImpl*)calloc(1, sizeof(MacOSInputImpl));
    g_input = input_impl;
    
    platform->input->impl = input_impl;
    platform->input->poll_events = macos_input_poll_events;
    platform->input->is_key_down = macos_input_is_key_down;
    platform->input->is_key_pressed = macos_input_is_key_pressed;
    platform->input->is_key_released = macos_input_is_key_released;
    platform->input->is_mouse_down = macos_input_is_mouse_down;
    platform->input->is_mouse_clicked = macos_input_is_mouse_clicked;
    platform->input->get_mouse_position = macos_input_get_mouse_position;
    platform->input->get_mouse_wheel = macos_input_get_mouse_wheel;
    
    return platform;
}

void macos_platform_destroy(MacOSPlatform* platform) {
    if (platform) {
        if (platform->renderer) {
            macos_renderer_shutdown(platform->renderer);
            free(platform->renderer->impl);
            free(platform->renderer);
        }
        if (platform->input) {
            free(platform->input->impl);
            free(platform->input);
        }
        g_input = NULL;
        free(platform);
    }
}

char* macos_open_midi_file_dialog(void) {
    @autoreleasepool {
        NSOpenPanel* panel = [NSOpenPanel openPanel];
        [panel setCanChooseFiles:YES];
        [panel setCanChooseDirectories:NO];
        [panel setAllowsMultipleSelection:NO];
        [panel setAllowedFileTypes:@[@"mid", @"midi"]];
        NSInteger result = [panel runModal];
        if (result == NSModalResponseOK) {
            NSURL* url = [[panel URLs] firstObject];
            if (!url) return NULL;
            const char* path = [[url path] UTF8String];
            if (!path) return NULL;
            size_t len = strlen(path);
            char* out = (char*)malloc(len + 1);
            if (!out) return NULL;
            memcpy(out, path, len + 1);
            return out;
        }
    }
    return NULL;
}
