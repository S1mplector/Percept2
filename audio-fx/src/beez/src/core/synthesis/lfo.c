#include "lfo.h"
#include <math.h>
#include <stdlib.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

void lfo_init(LFO* lfo) {
    if (!lfo) return;
    lfo->waveform = LFO_SINE;
    lfo->target = LFO_TARGET_NONE;
    lfo->rate = 5.0f;
    lfo->depth = 0.5f;
    lfo->phase = 0.0f;
    lfo->delay = 0.0f;
    lfo->delay_counter = 0.0f;
    lfo->last_random = 0.0f;
    lfo->enabled = false;
    lfo->synced = false;
}

void lfo_set_waveform(LFO* lfo, LFOWaveform waveform) {
    if (!lfo) return;
    lfo->waveform = waveform;
}

void lfo_set_target(LFO* lfo, LFOTarget target) {
    if (!lfo) return;
    lfo->target = target;
}

void lfo_set_rate(LFO* lfo, float rate) {
    if (!lfo) return;
    if (rate < 0.01f) rate = 0.01f;
    if (rate > 50.0f) rate = 50.0f;
    lfo->rate = rate;
}

void lfo_set_depth(LFO* lfo, float depth) {
    if (!lfo) return;
    if (depth < 0.0f) depth = 0.0f;
    if (depth > 1.0f) depth = 1.0f;
    lfo->depth = depth;
}

void lfo_set_delay(LFO* lfo, float delay) {
    if (!lfo) return;
    if (delay < 0.0f) delay = 0.0f;
    lfo->delay = delay;
}

void lfo_enable(LFO* lfo, bool enabled) {
    if (!lfo) return;
    lfo->enabled = enabled;
    if (enabled && lfo->synced) {
        lfo->phase = 0.0f;
        lfo->delay_counter = 0.0f;
    }
}

void lfo_sync(LFO* lfo) {
    if (!lfo) return;
    lfo->phase = 0.0f;
    lfo->delay_counter = 0.0f;
}

float lfo_process(LFO* lfo, float sample_rate) {
    if (!lfo || sample_rate <= 1.0f) {
        return 0.0f;
    }
    if (!lfo->enabled) {
        return 0.0f;
    }
    
    if (lfo->delay_counter < lfo->delay) {
        lfo->delay_counter += 1.0f / sample_rate;
        return 0.0f;
    }
    
    float phase_inc = lfo->rate / sample_rate;
    lfo->phase += phase_inc;
    
    if (lfo->phase >= 1.0f) {
        lfo->phase -= 1.0f;
        if (lfo->waveform == LFO_RANDOM) {
            lfo->last_random = ((float)rand() / (float)RAND_MAX) * 2.0f - 1.0f;
        }
    }
    
    return lfo_get_value(lfo);
}

float lfo_get_value(const LFO* lfo) {
    if (!lfo || !lfo->enabled) {
        return 0.0f;
    }
    
    if (lfo->delay_counter < lfo->delay) {
        return 0.0f;
    }
    
    float value = 0.0f;
    
    switch (lfo->waveform) {
        case LFO_SINE:
            value = sinf(lfo->phase * 2.0f * (float)M_PI);
            break;
            
        case LFO_TRIANGLE:
            if (lfo->phase < 0.25f) {
                value = lfo->phase * 4.0f;
            } else if (lfo->phase < 0.75f) {
                value = 1.0f - (lfo->phase - 0.25f) * 4.0f;
            } else {
                value = -1.0f + (lfo->phase - 0.75f) * 4.0f;
            }
            break;
            
        case LFO_SQUARE:
            value = lfo->phase < 0.5f ? 1.0f : -1.0f;
            break;
            
        case LFO_SAWTOOTH:
            value = 2.0f * lfo->phase - 1.0f;
            break;
            
        case LFO_SAWTOOTH_DOWN:
            value = 1.0f - 2.0f * lfo->phase;
            break;
            
        case LFO_RANDOM:
            value = lfo->last_random;
            break;
    }
    
    return value * lfo->depth;
}

void lfo_reset(LFO* lfo) {
    if (!lfo) return;
    lfo->phase = 0.0f;
    lfo->delay_counter = 0.0f;
    lfo->last_random = 0.0f;
}
