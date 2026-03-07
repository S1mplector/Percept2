#include "app/application.h"
#include "app/editor/editor.h"
#include "adapters/audio/coreaudio_adapter.h"
#include "adapters/platform/macos_platform.h"
#include <stdio.h>

int main(int argc, char* argv[]) {
    const char* midi_file = NULL;
    
    for (int i = 1; i < argc; i++) {
        if (argv[i][0] != '-') {
            midi_file = argv[i];
        }
    }
    
    printf("Beez Chiptune Tracker v0.1.0\n");
    printf("============================\n");
    printf("Built from scratch - no external dependencies!\n\n");
    
    AudioPort* audio = coreaudio_adapter_create();
    if (!audio) {
        fprintf(stderr, "Failed to create Core Audio adapter\n");
        return 1;
    }
    
    MacOSPlatform* platform = macos_platform_create();
    if (!platform) {
        fprintf(stderr, "Failed to create macOS platform\n");
        coreaudio_adapter_destroy(audio);
        return 1;
    }
    
    Application app;
    if (!application_init(&app, audio, platform->renderer, platform->input)) {
        fprintf(stderr, "Failed to initialize application\n");
        macos_platform_destroy(platform);
        coreaudio_adapter_destroy(audio);
        return 1;
    }
    
    if (midi_file) {
        printf("Loading MIDI: %s\n", midi_file);
        if (editor_load_midi(&app.editor, midi_file)) {
            printf("MIDI loaded successfully!\n\n");
        } else {
            printf("Failed to load MIDI file.\n\n");
        }
    }
    
    printf("Controls:\n");
    printf("  Z-M keys: Play notes (piano layout)\n");
    printf("  F1/F2: Octave down/up\n");
    printf("  F3: Toggle Pattern/Piano Roll view\n");
    printf("  F5: Toggle loop\n");
    printf("  1-8: Select channel\n");
    printf("  Arrow keys: Navigate\n");
    printf("  TAB: Toggle edit mode\n");
    printf("  SPACE: Play/Pause\n");
    printf("  ENTER: Stop\n");
    printf("  ESC: Quit\n\n");
    
    application_run(&app);
    application_shutdown(&app);
    
    macos_platform_destroy(platform);
    coreaudio_adapter_destroy(audio);
    
    printf("Goodbye!\n");
    return 0;
}
