#pragma once

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdbool.h>

#define TEST_EPSILON 0.0001f

static int g_tests_run = 0;
static int g_tests_passed = 0;
static int g_tests_failed = 0;
static const char* g_current_suite = NULL;

#define TEST_SUITE(name) \
    do { \
        g_current_suite = name; \
        printf("\n=== %s ===\n", name); \
    } while(0)

#define TEST_CASE(name) \
    printf("  [TEST] %s... ", name);

#define TEST_PASS() \
    do { \
        g_tests_run++; \
        g_tests_passed++; \
        printf("PASS\n"); \
    } while(0)

#define TEST_FAIL(msg) \
    do { \
        g_tests_run++; \
        g_tests_failed++; \
        printf("FAIL: %s\n", msg); \
    } while(0)

#define ASSERT_TRUE(condition) \
    do { \
        if (!(condition)) { \
            TEST_FAIL(#condition " is false"); \
            return; \
        } \
    } while(0)

#define ASSERT_FALSE(condition) \
    do { \
        if (condition) { \
            TEST_FAIL(#condition " is true"); \
            return; \
        } \
    } while(0)

#define ASSERT_EQ(expected, actual) \
    do { \
        if ((expected) != (actual)) { \
            char buf[256]; \
            snprintf(buf, sizeof(buf), "Expected %d, got %d", (int)(expected), (int)(actual)); \
            TEST_FAIL(buf); \
            return; \
        } \
    } while(0)

#define ASSERT_FLOAT_EQ(expected, actual) \
    do { \
        float diff = fabsf((expected) - (actual)); \
        if (diff > TEST_EPSILON) { \
            char buf[256]; \
            snprintf(buf, sizeof(buf), "Expected %.6f, got %.6f (diff: %.6f)", \
                     (expected), (actual), diff); \
            TEST_FAIL(buf); \
            return; \
        } \
    } while(0)

#define ASSERT_FLOAT_NEAR(expected, actual, epsilon) \
    do { \
        float diff = fabsf((expected) - (actual)); \
        if (diff > (epsilon)) { \
            char buf[256]; \
            snprintf(buf, sizeof(buf), "Expected %.6f +/- %.6f, got %.6f", \
                     (expected), (epsilon), (actual)); \
            TEST_FAIL(buf); \
            return; \
        } \
    } while(0)

#define ASSERT_NOT_NULL(ptr) \
    do { \
        if ((ptr) == NULL) { \
            TEST_FAIL(#ptr " is NULL"); \
            return; \
        } \
    } while(0)

#define ASSERT_NULL(ptr) \
    do { \
        if ((ptr) != NULL) { \
            TEST_FAIL(#ptr " is not NULL"); \
            return; \
        } \
    } while(0)

#define ASSERT_STR_EQ(expected, actual) \
    do { \
        if (strcmp((expected), (actual)) != 0) { \
            char buf[256]; \
            snprintf(buf, sizeof(buf), "Expected \"%s\", got \"%s\"", (expected), (actual)); \
            TEST_FAIL(buf); \
            return; \
        } \
    } while(0)

#define ASSERT_IN_RANGE(value, min, max) \
    do { \
        if ((value) < (min) || (value) > (max)) { \
            char buf[256]; \
            snprintf(buf, sizeof(buf), "Value %.6f not in range [%.6f, %.6f]", \
                     (double)(value), (double)(min), (double)(max)); \
            TEST_FAIL(buf); \
            return; \
        } \
    } while(0)

static inline void test_print_summary(void) {
    printf("\n========================================\n");
    printf("Tests run: %d, Passed: %d, Failed: %d\n", 
           g_tests_run, g_tests_passed, g_tests_failed);
    printf("========================================\n");
    
    if (g_tests_failed > 0) {
        printf("SOME TESTS FAILED!\n");
    } else {
        printf("ALL TESTS PASSED!\n");
    }
}

static inline int test_get_exit_code(void) {
    return g_tests_failed > 0 ? 1 : 0;
}
