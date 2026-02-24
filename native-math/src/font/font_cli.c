/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/*
 * font_cli.c - Test utility for custom font pipeline
 * 
 * Build: gcc -o font_cli font_cli.c font_strokes.cpp font_raster.cpp 
 *            font_serialize.cpp font_metrics.cpp -lstdc++ -lm
 * 
 * Usage:
 *   ./font_cli create <name> <output.sjf>     - Create empty font
 *   ./font_cli info <input.sjf>               - Show font info
 *   ./font_cli roundtrip <input.sjf> <output.sjf> - Load and save
 *   ./font_cli raster <input.sjf> <char> <size> - Rasterize a glyph
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "font_types.h"

/* External function declarations */
extern sjf_font_t* sjf_font_create(const char* name, const char* author);
extern void sjf_font_free(sjf_font_t* font);
extern sjf_font_t* sjf_font_load(const char* path);
extern int32_t sjf_font_save(const sjf_font_t* font, const char* path);
extern int32_t sjf_font_get_name(const sjf_font_t* font, char* out, int32_t out_len);
extern int32_t sjf_font_get_author(const sjf_font_t* font, char* out, int32_t out_len);
extern int32_t sjf_font_glyph_count(const sjf_font_t* font);
extern int32_t sjf_font_defined_glyph_count(const sjf_font_t* font);
extern sjf_glyph_t* sjf_font_add_glyph(sjf_font_t* font, uint32_t codepoint);
extern int32_t sjf_glyph_add_stroke(sjf_glyph_t* glyph, const sjf_stroke_t* stroke);
extern sjf_stroke_t* sjf_stroke_create(int32_t initial_capacity);
extern void sjf_stroke_free(sjf_stroke_t* stroke);
extern int32_t sjf_stroke_add_point(sjf_stroke_t* stroke, float x, float y, float pressure, float timestamp);
extern int32_t sjf_stroke_smooth(sjf_stroke_t* stroke, int32_t iterations, float tension,
                                 float resample_distance, int32_t preserve_corners);
extern sjf_bitmap_t* sjf_raster_glyph(const sjf_glyph_t* glyph, const sjf_raster_opts_t* opts, float em_size);
extern void sjf_bitmap_free(sjf_bitmap_t* bmp);
extern int32_t sjf_glyph_compute_metrics(sjf_glyph_t* glyph, float em_size);
extern int32_t sjf_font_pack(const sjf_font_t* font, uint8_t* buffer, int32_t buffer_len);
extern sjf_font_t* sjf_font_unpack(const uint8_t* buffer, int32_t buffer_len);

static void print_usage(const char* prog) {
    printf("Usage:\n");
    printf("  %s create <name> <output.sjf>           - Create empty font\n", prog);
    printf("  %s info <input.sjf>                     - Show font info\n", prog);
    printf("  %s roundtrip <input.sjf> <output.sjf>   - Load and save\n", prog);
    printf("  %s raster <input.sjf> <char> <size>     - Rasterize a glyph\n", prog);
    printf("  %s test                                 - Run basic tests\n", prog);
}

static int cmd_create(const char* name, const char* output_path) {
    printf("Creating font '%s'...\n", name);
    
    sjf_font_t* font = sjf_font_create(name, "Simjot User");
    if (!font) {
        fprintf(stderr, "Error: Failed to create font\n");
        return 1;
    }
    
    int32_t result = sjf_font_save(font, output_path);
    sjf_font_free(font);
    
    if (result != SJF_OK) {
        fprintf(stderr, "Error: Failed to save font (code %d)\n", result);
        return 1;
    }
    
    printf("Created: %s\n", output_path);
    return 0;
}

static int cmd_info(const char* input_path) {
    printf("Loading font from '%s'...\n", input_path);
    
    sjf_font_t* font = sjf_font_load(input_path);
    if (!font) {
        fprintf(stderr, "Error: Failed to load font\n");
        return 1;
    }
    
    char name[SJF_MAX_NAME_LEN];
    char author[SJF_MAX_AUTHOR_LEN];
    
    sjf_font_get_name(font, name, sizeof(name));
    sjf_font_get_author(font, author, sizeof(author));
    
    printf("\n=== Font Info ===\n");
    printf("Name:           %s\n", name);
    printf("Author:         %s\n", author);
    printf("Version:        %u\n", font->header.version);
    printf("Em Size:        %.1f\n", font->header.em_size);
    printf("Ascender:       %.1f\n", font->header.ascender);
    printf("Descender:      %.1f\n", font->header.descender);
    printf("Line Gap:       %.1f\n", font->header.line_gap);
    printf("Thickness:      %.1f\n", font->header.default_thickness);
    printf("Total Glyphs:   %d\n", sjf_font_glyph_count(font));
    printf("Defined Glyphs: %d\n", sjf_font_defined_glyph_count(font));
    
    sjf_font_free(font);
    return 0;
}

static int cmd_roundtrip(const char* input_path, const char* output_path) {
    printf("Roundtrip: %s -> %s\n", input_path, output_path);
    
    sjf_font_t* font = sjf_font_load(input_path);
    if (!font) {
        fprintf(stderr, "Error: Failed to load font\n");
        return 1;
    }
    
    int32_t result = sjf_font_save(font, output_path);
    sjf_font_free(font);
    
    if (result != SJF_OK) {
        fprintf(stderr, "Error: Failed to save font (code %d)\n", result);
        return 1;
    }
    
    printf("Success!\n");
    return 0;
}

static int cmd_raster(const char* input_path, const char* char_str, int size) {
    sjf_font_t* font = sjf_font_load(input_path);
    if (!font) {
        fprintf(stderr, "Error: Failed to load font\n");
        return 1;
    }
    
    uint32_t codepoint = (uint8_t)char_str[0];
    printf("Rasterizing '%c' (U+%04X) at size %d...\n", (char)codepoint, codepoint, size);
    
    sjf_glyph_t* glyph = NULL;
    for (int32_t i = 0; i < font->glyph_count; i++) {
        if (font->glyphs[i].codepoint == codepoint) {
            glyph = &font->glyphs[i];
            break;
        }
    }
    
    if (!glyph || !glyph->defined) {
        fprintf(stderr, "Error: Glyph not found or not defined\n");
        sjf_font_free(font);
        return 1;
    }
    
    sjf_raster_opts_t opts = { size, 4, 1.0f, 0, 0 };
    sjf_bitmap_t* bmp = sjf_raster_glyph(glyph, &opts, font->header.em_size);
    
    if (!bmp) {
        fprintf(stderr, "Error: Failed to rasterize glyph\n");
        sjf_font_free(font);
        return 1;
    }
    
    printf("Bitmap: %dx%d (stride=%d)\n", bmp->width, bmp->height, bmp->stride);
    printf("Origin: (%.1f, %.1f)\n\n", bmp->origin_x, bmp->origin_y);
    
    /* ASCII art preview */
    const char* shades = " .:-=+*#%@";
    int num_shades = 10;
    
    for (int32_t y = 0; y < bmp->height && y < 40; y++) {
        for (int32_t x = 0; x < bmp->width && x < 80; x++) {
            uint8_t val = bmp->pixels[y * bmp->stride + x];
            int shade_idx = val * (num_shades - 1) / 255;
            putchar(shades[shade_idx]);
        }
        putchar('\n');
    }
    
    sjf_bitmap_free(bmp);
    sjf_font_free(font);
    return 0;
}

static int cmd_test(void) {
    printf("=== Running Font Pipeline Tests ===\n\n");
    int failures = 0;
    
    /* Test 1: Create font */
    printf("Test 1: Create font... ");
    sjf_font_t* font = sjf_font_create("Test Font", "Test Author");
    if (font) {
        printf("PASS\n");
    } else {
        printf("FAIL\n");
        failures++;
        return failures;
    }
    
    /* Test 2: Add glyph */
    printf("Test 2: Add glyph... ");
    sjf_glyph_t* glyph = sjf_font_add_glyph(font, 'A');
    if (glyph) {
        printf("PASS\n");
    } else {
        printf("FAIL\n");
        failures++;
    }
    
    /* Test 3: Create stroke */
    printf("Test 3: Create stroke... ");
    sjf_stroke_t* stroke = sjf_stroke_create(64);
    if (stroke) {
        printf("PASS\n");
    } else {
        printf("FAIL\n");
        failures++;
    }
    
    /* Test 4: Add points */
    printf("Test 4: Add points... ");
    int add_ok = 1;
    add_ok &= (sjf_stroke_add_point(stroke, 100, 100, 1.0f, 0) == SJF_OK);
    add_ok &= (sjf_stroke_add_point(stroke, 500, 100, 1.0f, 10) == SJF_OK);
    add_ok &= (sjf_stroke_add_point(stroke, 300, 800, 1.0f, 20) == SJF_OK);
    add_ok &= (sjf_stroke_add_point(stroke, 100, 100, 1.0f, 30) == SJF_OK);
    if (add_ok) {
        printf("PASS\n");
    } else {
        printf("FAIL\n");
        failures++;
    }
    
    /* Test 5: Smooth stroke */
    printf("Test 5: Smooth stroke... ");
    if (sjf_stroke_smooth(stroke, -1, 0.0f, 0.0f, 1) == SJF_OK) {
        printf("PASS (points: %d)\n", stroke->point_count);
    } else {
        printf("FAIL\n");
        failures++;
    }
    
    /* Test 6: Add stroke to glyph */
    printf("Test 6: Add stroke to glyph... ");
    if (sjf_glyph_add_stroke(glyph, stroke) == SJF_OK) {
        printf("PASS\n");
    } else {
        printf("FAIL\n");
        failures++;
    }
    
    /* Test 7: Compute metrics */
    printf("Test 7: Compute metrics... ");
    if (sjf_glyph_compute_metrics(glyph, font->header.em_size) == SJF_OK) {
        printf("PASS (advance: %.1f)\n", glyph->metrics.advance_width);
    } else {
        printf("FAIL\n");
        failures++;
    }
    
    /* Test 8: Pack to buffer */
    printf("Test 8: Pack to buffer... ");
    int32_t pack_size = sjf_font_pack(font, NULL, 0);
    uint8_t* buffer = (uint8_t*)malloc(pack_size);
    int32_t packed = sjf_font_pack(font, buffer, pack_size);
    if (packed > 0) {
        printf("PASS (size: %d bytes)\n", packed);
    } else {
        printf("FAIL\n");
        failures++;
    }
    
    /* Test 9: Unpack from buffer */
    printf("Test 9: Unpack from buffer... ");
    sjf_font_t* unpacked = sjf_font_unpack(buffer, packed);
    if (unpacked && unpacked->glyph_count == font->glyph_count) {
        printf("PASS\n");
    } else {
        printf("FAIL\n");
        failures++;
    }
    free(buffer);
    if (unpacked) sjf_font_free(unpacked);
    
    /* Test 10: Rasterize */
    printf("Test 10: Rasterize glyph... ");
    sjf_raster_opts_t opts = { 32, 2, 1.0f, 0, 0 };
    sjf_bitmap_t* bmp = sjf_raster_glyph(glyph, &opts, font->header.em_size);
    if (bmp && bmp->width > 0 && bmp->height > 0) {
        printf("PASS (%dx%d)\n", bmp->width, bmp->height);
        sjf_bitmap_free(bmp);
    } else {
        printf("FAIL\n");
        failures++;
    }
    
    /* Cleanup */
    sjf_stroke_free(stroke);
    sjf_font_free(font);
    
    printf("\n=== Results: %d failures ===\n", failures);
    return failures;
}

int main(int argc, char* argv[]) {
    if (argc < 2) {
        print_usage(argv[0]);
        return 1;
    }
    
    const char* cmd = argv[1];
    
    if (strcmp(cmd, "create") == 0 && argc >= 4) {
        return cmd_create(argv[2], argv[3]);
    }
    else if (strcmp(cmd, "info") == 0 && argc >= 3) {
        return cmd_info(argv[2]);
    }
    else if (strcmp(cmd, "roundtrip") == 0 && argc >= 4) {
        return cmd_roundtrip(argv[2], argv[3]);
    }
    else if (strcmp(cmd, "raster") == 0 && argc >= 5) {
        return cmd_raster(argv[2], argv[3], atoi(argv[4]));
    }
    else if (strcmp(cmd, "test") == 0) {
        return cmd_test();
    }
    else {
        print_usage(argv[0]);
        return 1;
    }
}
