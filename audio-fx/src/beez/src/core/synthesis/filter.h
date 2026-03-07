#pragma once

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    FILTER_LOWPASS,
    FILTER_HIGHPASS,
    FILTER_BANDPASS,
    FILTER_NOTCH
} FilterType;

typedef struct {
    FilterType type;
    float cutoff;
    float resonance;
    float sample_rate;
    
    float b0, b1, b2;
    float a1, a2;
    float x1, x2;
    float y1, y2;
    
    bool enabled;
} Filter;

void filter_init(Filter* f, float sample_rate);
void filter_set_type(Filter* f, FilterType type);
void filter_set_cutoff(Filter* f, float cutoff);
void filter_set_resonance(Filter* f, float resonance);
void filter_set_params(Filter* f, float cutoff, float resonance);
void filter_enable(Filter* f, bool enabled);

float filter_process(Filter* f, float input);
void filter_reset(Filter* f);

void filter_calculate_coefficients(Filter* f);

#ifdef __cplusplus
}
#endif
