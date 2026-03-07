#include "application.h"
#include "ui/theme.h"
#include <string.h>
#include <time.h>

static Application* g_app = NULL;

static void audio_callback(float* left, float* right, int num_samples, void* user_data) {
    Application* app = (Application*)user_data;
    if (!app || !left || !right || num_samples <= 0) return;
    
    if (midi_player_is_playing(&app->editor.midi_player)) {
        midi_player_process(&app->editor.midi_player, num_samples);
    } else {
        sequencer_process(&app->sequencer, num_samples, app->sample_rate);
    }
    synth_engine_generate_stereo(&app->engine, left, right, num_samples);
}

bool application_init(Application* app, AudioPort* audio, RendererPort* renderer, InputPort* input) {
    if (!app || !audio || !renderer || !input) return false;
    memset(app, 0, sizeof(Application));
    
    theme_init_dark();
    
    app->audio = audio;
    app->renderer = renderer;
    app->input = input;
    
    app->sample_rate = BEEZ_DEFAULT_SAMPLE_RATE;
    app->buffer_size = 512;
    
    synth_engine_init(&app->engine, app->sample_rate);
    instrument_bank_load_defaults(&app->instruments);
    sequencer_init(&app->sequencer, &app->engine);
    sequencer_set_instrument_bank(&app->sequencer, &app->instruments);
    
    if (!renderer->initialize || !renderer->shutdown || !renderer->begin_frame || !renderer->end_frame ||
        !renderer->clear || !renderer->draw_rect || !renderer->draw_rect_filled || !renderer->draw_line ||
        !renderer->get_size || !renderer->should_close) {
        return false;
    }
    if (!renderer->initialize(renderer, 1040, 640, "Beez v0.1.0-beta")) {
        return false;
    }
    
    if (!audio->initialize || !audio->shutdown || !audio->start || !audio->stop || !audio->set_callback) {
        renderer->shutdown(renderer);
        return false;
    }
    if (!audio->initialize(audio, app->sample_rate, app->buffer_size)) {
        renderer->shutdown(renderer);
        return false;
    }
    
    audio->set_callback(audio, audio_callback, app);
    
    editor_init(&app->editor, &app->sequencer, &app->engine, &app->instruments, renderer, input);
    
    g_app = app;
    app->running = true;
    
    return true;
}

void application_run(Application* app) {
    if (!app || !app->audio || !app->renderer || !app->input) return;
    app->audio->start(app->audio);

    struct timespec last = {0};
    clock_gettime(CLOCK_MONOTONIC, &last);
    while (app->running && !app->renderer->should_close(app->renderer)) {
        struct timespec now = {0};
        clock_gettime(CLOCK_MONOTONIC, &now);
        float dt = (float)(now.tv_sec - last.tv_sec) + (float)(now.tv_nsec - last.tv_nsec) / 1000000000.0f;
        if (dt < 0.0f) dt = 0.0f;
        if (dt > 0.1f) dt = 0.1f;
        last = now;

        app->input->poll_events(app->input);
        
        if (app->input->is_key_pressed(app->input, KEY_ESCAPE)) {
            app->running = false;
            break;
        }
        
        editor_handle_input(&app->editor);
        editor_update(&app->editor, dt);
        if (app->editor.request_quit) {
            app->running = false;
            break;
        }
        
        app->renderer->begin_frame(app->renderer);
        editor_render(&app->editor);
        app->renderer->end_frame(app->renderer);
    }
    
    app->audio->stop(app->audio);
}

void application_shutdown(Application* app) {
    if (!app) return;
    if (app->audio) {
        app->audio->shutdown(app->audio);
    }
    if (app->renderer) {
        app->renderer->shutdown(app->renderer);
    }
    synth_engine_reset(&app->engine);
    g_app = NULL;
}
