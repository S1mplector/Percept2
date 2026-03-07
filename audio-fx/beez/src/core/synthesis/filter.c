#include "filter.h"
#include <math.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

void filter_init(Filter* f, float sample_rate) {
    if (!f) return;
    f->type = FILTER_LOWPASS;
    f->cutoff = 10000.0f;
    f->resonance = 0.707f;
    f->sample_rate = sample_rate > 1.0f ? sample_rate : 44100.0f;
    f->enabled = false;
    
    f->b0 = 1.0f;
    f->b1 = 0.0f;
    f->b2 = 0.0f;
    f->a1 = 0.0f;
    f->a2 = 0.0f;
    
    f->x1 = 0.0f;
    f->x2 = 0.0f;
    f->y1 = 0.0f;
    f->y2 = 0.0f;
    
    filter_calculate_coefficients(f);
}

void filter_set_type(Filter* f, FilterType type) {
    if (!f) return;
    f->type = type;
    filter_calculate_coefficients(f);
}

void filter_set_cutoff(Filter* f, float cutoff) {
    if (!f) return;
    if (cutoff < 20.0f) cutoff = 20.0f;
    if (f->sample_rate <= 1.0f) f->sample_rate = 44100.0f;
    if (cutoff > f->sample_rate * 0.49f) cutoff = f->sample_rate * 0.49f;
    f->cutoff = cutoff;
    filter_calculate_coefficients(f);
}

void filter_set_resonance(Filter* f, float resonance) {
    if (!f) return;
    if (resonance < 0.1f) resonance = 0.1f;
    if (resonance > 20.0f) resonance = 20.0f;
    f->resonance = resonance;
    filter_calculate_coefficients(f);
}

void filter_set_params(Filter* f, float cutoff, float resonance) {
    if (!f) return;
    if (cutoff < 20.0f) cutoff = 20.0f;
    if (f->sample_rate <= 1.0f) f->sample_rate = 44100.0f;
    if (cutoff > f->sample_rate * 0.49f) cutoff = f->sample_rate * 0.49f;
    if (resonance < 0.1f) resonance = 0.1f;
    if (resonance > 20.0f) resonance = 20.0f;
    
    f->cutoff = cutoff;
    f->resonance = resonance;
    filter_calculate_coefficients(f);
}

void filter_enable(Filter* f, bool enabled) {
    if (!f) return;
    f->enabled = enabled;
    if (enabled) {
        filter_reset(f);
    }
}

void filter_calculate_coefficients(Filter* f) {
    if (!f) return;
    if (f->sample_rate <= 1.0f) f->sample_rate = 44100.0f;
    if (f->resonance <= 0.0f) f->resonance = 0.707f;
    float omega = 2.0f * (float)M_PI * f->cutoff / f->sample_rate;
    float sin_omega = sinf(omega);
    float cos_omega = cosf(omega);
    float alpha = sin_omega / (2.0f * f->resonance);
    
    float a0;
    
    switch (f->type) {
        case FILTER_LOWPASS:
            f->b0 = (1.0f - cos_omega) / 2.0f;
            f->b1 = 1.0f - cos_omega;
            f->b2 = (1.0f - cos_omega) / 2.0f;
            a0 = 1.0f + alpha;
            f->a1 = -2.0f * cos_omega;
            f->a2 = 1.0f - alpha;
            break;
            
        case FILTER_HIGHPASS:
            f->b0 = (1.0f + cos_omega) / 2.0f;
            f->b1 = -(1.0f + cos_omega);
            f->b2 = (1.0f + cos_omega) / 2.0f;
            a0 = 1.0f + alpha;
            f->a1 = -2.0f * cos_omega;
            f->a2 = 1.0f - alpha;
            break;
            
        case FILTER_BANDPASS:
            f->b0 = alpha;
            f->b1 = 0.0f;
            f->b2 = -alpha;
            a0 = 1.0f + alpha;
            f->a1 = -2.0f * cos_omega;
            f->a2 = 1.0f - alpha;
            break;
            
        case FILTER_NOTCH:
            f->b0 = 1.0f;
            f->b1 = -2.0f * cos_omega;
            f->b2 = 1.0f;
            a0 = 1.0f + alpha;
            f->a1 = -2.0f * cos_omega;
            f->a2 = 1.0f - alpha;
            break;
            
        default:
            f->b0 = 1.0f;
            f->b1 = 0.0f;
            f->b2 = 0.0f;
            a0 = 1.0f;
            f->a1 = 0.0f;
            f->a2 = 0.0f;
            break;
    }
    
    f->b0 /= a0;
    f->b1 /= a0;
    f->b2 /= a0;
    f->a1 /= a0;
    f->a2 /= a0;
}

float filter_process(Filter* f, float input) {
    if (!f) return input;
    if (!f->enabled) {
        return input;
    }
    
    float output = f->b0 * input + f->b1 * f->x1 + f->b2 * f->x2
                   - f->a1 * f->y1 - f->a2 * f->y2;
    
    f->x2 = f->x1;
    f->x1 = input;
    f->y2 = f->y1;
    f->y1 = output;
    
    if (output > 1.0f) output = 1.0f;
    if (output < -1.0f) output = -1.0f;
    
    return output;
}

void filter_reset(Filter* f) {
    if (!f) return;
    f->x1 = 0.0f;
    f->x2 = 0.0f;
    f->y1 = 0.0f;
    f->y2 = 0.0f;
}
