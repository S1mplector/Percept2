#include "pattern.h"
#include <string.h>

void pattern_init(Pattern* pattern, int id) {
    if (!pattern) return;
    pattern->id = id;
    pattern->length = BEEZ_PATTERN_ROWS;
    pattern_clear(pattern);
}

void pattern_clear(Pattern* pattern) {
    if (!pattern) return;
    memset(pattern->cells, 0, sizeof(pattern->cells));
}

void pattern_set_cell(Pattern* pattern, int row, int channel, const PatternCell* cell) {
    if (!pattern || !cell) return;
    if (row >= 0 && row < pattern->length && channel >= 0 && channel < 8) {
        pattern->cells[row][channel] = *cell;
    }
}

const PatternCell* pattern_get_cell(const Pattern* pattern, int row, int channel) {
    if (!pattern) return NULL;
    if (row >= 0 && row < pattern->length && channel >= 0 && channel < 8) {
        return &pattern->cells[row][channel];
    }
    return NULL;
}

void pattern_copy_row(Pattern* pattern, int src_row, int dst_row) {
    if (!pattern) return;
    if (src_row >= 0 && src_row < pattern->length && 
        dst_row >= 0 && dst_row < pattern->length) {
        memcpy(pattern->cells[dst_row], pattern->cells[src_row], sizeof(pattern->cells[0]));
    }
}
