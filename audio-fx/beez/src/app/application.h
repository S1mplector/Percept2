#pragma once

#include "../core/engine/synth_engine.h"
#include "../core/sequencer/sequencer.h"
#include "../core/synthesis/instrument.h"
#include "../ports/audio_port.h"
#include "../ports/renderer_port.h"
#include "../ports/input_port.h"
#include "editor/editor.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    SynthEngine engine;
    Sequencer sequencer;
    InstrumentBank instruments;
    Editor editor;
    
    AudioPort* audio;
    RendererPort* renderer;
    InputPort* input;
    
    bool running;
    float sample_rate;
    int buffer_size;
} Application;

bool application_init(Application* app, AudioPort* audio, RendererPort* renderer, InputPort* input);
void application_run(Application* app);
void application_shutdown(Application* app);

#ifdef __cplusplus
}
#endif
