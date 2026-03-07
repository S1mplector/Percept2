#pragma once

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define BEEZ_PATTERN_ROWS 64
#define BEEZ_MAX_PATTERNS 256

typedef struct {
    uint8_t note;
    uint8_t instrument;
    uint8_t volume;
    uint8_t effect;
    uint8_t effect_param;
} PatternCell;

#define NOTE_NONE  0
#define NOTE_OFF   254
#define NOTE_C0    12

typedef struct {
    PatternCell cells[BEEZ_PATTERN_ROWS][8];
    int length;
    int id;
} Pattern;

void pattern_init(Pattern* pattern, int id);
void pattern_clear(Pattern* pattern);
void pattern_set_cell(Pattern* pattern, int row, int channel, const PatternCell* cell);
const PatternCell* pattern_get_cell(const Pattern* pattern, int row, int channel);
void pattern_copy_row(Pattern* pattern, int src_row, int dst_row);

#ifdef __cplusplus
}
#endif
