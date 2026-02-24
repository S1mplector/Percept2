/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

 /**
  * Simjot Native CLI Tests
  * Designed to validate the functionality of the Simjot native library.
  * Covers arithmetic, string manipulation, dictionary access, file hashing, directory listing, atomic writes
  * file copying, and disk space checking.
  * Depends on a dictionary file for some tests; set SIMJOT_DICT_PATH environment variable to enable those.
  * Uses temporary files and directories for testing file I/O operations.
  * Reports results to standard output, indicating pass/fail status for each test.
  * Exits with code 0 if all tests pass, or 1 if any tests fail.
  * 
  * @author S1mplector
  */

#include "simjot_native.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifndef _WIN32
#include <sys/statvfs.h>
#include <unistd.h>
#include <sys/stat.h>
#endif

static int failures = 0;

static void expect_int(const char* name, long long got, long long expected) {
    if (got != expected) {
        fprintf(stderr, "[FAIL] %s: got %lld, expected %lld\n", name, got, expected);
        failures++;
    } else {
        printf("[OK] %s\n", name);
    }
}

static void expect_str(const char* name, const char* got, const char* expected) {
    if (got == NULL || expected == NULL || strcmp(got, expected) != 0) {
        fprintf(stderr, "[FAIL] %s: got '%s', expected '%s'\n", name, got ? got : "(null)", expected ? expected : "(null)");
        failures++;
    } else {
        printf("[OK] %s\n", name);
    }
}

static int write_file(const char* path, const char* data) {
    FILE* f = fopen(path, "wb");
    if (!f) return 0;
    size_t len = strlen(data);
    size_t written = fwrite(data, 1, len, f);
    fclose(f);
    return written == len;
}

static void bytes_to_hex(const uint8_t* in, size_t len, char* out) {
    static const char* hex = "0123456789abcdef";
    for (size_t i = 0; i < len; i++) {
        out[i * 2] = hex[(in[i] >> 4) & 0xF];
        out[i * 2 + 1] = hex[in[i] & 0xF];
    }
    out[len * 2] = '\0';
}

static int read_u32(const uint8_t* data, int32_t len, int32_t* offset, uint32_t* out) {
    if (*offset + 4 > len) return 0;
    memcpy(out, data + *offset, sizeof(uint32_t));
    *offset += 4;
    return 1;
}

static int read_u64(const uint8_t* data, int32_t len, int32_t* offset, uint64_t* out) {
    if (*offset + 8 > len) return 0;
    memcpy(out, data + *offset, sizeof(uint64_t));
    *offset += 8;
    return 1;
}

static int skip_string(const uint8_t* data, int32_t len, int32_t* offset) {
    uint32_t slen = 0;
    if (!read_u32(data, len, offset, &slen)) return 0;
    if (slen > (uint32_t)(len - *offset)) return 0;
    *offset += (int32_t)slen;
    return 1;
}

static int list_contains(const uint8_t* data, int32_t len, const char* name, int expect_dir) {
    int32_t offset = 0;
    while (offset + 8 <= len) {
        uint32_t name_len = 0;
        memcpy(&name_len, data + offset, sizeof(uint32_t));
        offset += 4;
        int is_dir = data[offset++];
        int is_hidden = data[offset++];
        (void)is_hidden;
        offset += 2; // padding
        if (offset + (int32_t)name_len > len) return 0;
        if (name_len == strlen(name) && memcmp(data + offset, name, name_len) == 0) {
            return (is_dir ? 1 : 0) == (expect_dir ? 1 : 0);
        }
        offset += (int32_t)name_len;
    }
    return 0;
}

static int make_temp_path(char* out, size_t size) {
#ifdef _WIN32
    (void)size;
    return tmpnam(out) != NULL;
#else
    if (size < 1) return 0;
    snprintf(out, size, "/tmp/simjot_native_XXXXXX");
    int fd = mkstemp(out);
    if (fd < 0) return 0;
    close(fd);
    return 1;
#endif
}

static int make_temp_dir(char* out, size_t size) {
#ifdef _WIN32
    (void)size;
    return tmpnam(out) != NULL;
#else
    if (size < 1) return 0;
    snprintf(out, size, "/tmp/simjot_native_dir_XXXXXX");
    return mkdtemp(out) != NULL;
#endif
}

int main(void) {
    printf("=== Simjot Native CLI Tests ===\n");

    expect_int("simjot_add", simjot_add(17, 25), 42);
    expect_int("simjot_strlen", simjot_strlen("Hello"), 5);

    int32_t nums[] = {1,2,3,4,5,6,7,8,9,10};
    expect_int("simjot_sum_array", simjot_sum_array(nums, 10), 55);
    expect_int("simjot_fib", simjot_fib(20), 6765);

    expect_int("simjot_count_syllables(hello)", simjot_count_syllables("hello"), 2);
    expect_int("simjot_count_syllables(poetry)", simjot_count_syllables("poetry"), 3);
    expect_int("simjot_count_syllables(wanted)", simjot_count_syllables("wanted"), 2);
    expect_int("simjot_count_syllables(the)", simjot_count_syllables("the"), 1);
    expect_int("simjot_count_syllables(a)", simjot_count_syllables("a"), 1);

    char rhyme[32] = {0};
    if (simjot_rhyme_key("day", rhyme, (int32_t)sizeof(rhyme)) > 0) {
        expect_str("simjot_rhyme_key(day)", rhyme, "ay");
    } else {
        fprintf(stderr, "[FAIL] simjot_rhyme_key(day): call failed\n");
        failures++;
    }
    if (simjot_rhyme_key("hear", rhyme, (int32_t)sizeof(rhyme)) > 0) {
        expect_str("simjot_rhyme_key(hear)", rhyme, "ar");
    } else {
        fprintf(stderr, "[FAIL] simjot_rhyme_key(hear): call failed\n");
        failures++;
    }
    if (simjot_near_rhyme_key("thing", rhyme, (int32_t)sizeof(rhyme)) > 0) {
        expect_str("simjot_near_rhyme_key(thing)", rhyme, "ing");
    } else {
        fprintf(stderr, "[FAIL] simjot_near_rhyme_key(thing): call failed\n");
        failures++;
    }

    const char* dict_path = getenv("SIMJOT_DICT_PATH");
    if (dict_path && *dict_path) {
        if (!simjot_dict_set_base_path(dict_path)) {
            fprintf(stderr, "[FAIL] simjot_dict_set_base_path\n");
            failures++;
        } else {
            int32_t dict_size = simjot_dict_size();
            if (dict_size > 0) {
                printf("[OK] simjot_dict_size\n");
            } else {
                fprintf(stderr, "[FAIL] simjot_dict_size: %d\n", dict_size);
                failures++;
            }
            expect_int("simjot_dict_contains(love)", simjot_dict_contains("love"), 1);
            expect_int("simjot_dict_contains(qzxw)", simjot_dict_contains("qzxw"), 0);

            int32_t needed = simjot_dict_lookup("love", NULL, 0);
            if (needed < 0) {
                needed = -needed;
            }
            if (needed <= 0) {
                fprintf(stderr, "[FAIL] simjot_dict_lookup(love): size\n");
                failures++;
            } else {
                uint8_t* data = (uint8_t*)malloc((size_t)needed);
                if (!data) {
                    fprintf(stderr, "[FAIL] simjot_dict_lookup(love): alloc\n");
                    failures++;
                } else {
                    int32_t wrote = simjot_dict_lookup("love", data, needed);
                    if (wrote > 0) {
                        int32_t off = 0;
                        uint32_t pos_count = 0, syn_count = 0, ant_count = 0;
                        if (read_u32(data, wrote, &off, &pos_count) &&
                            read_u32(data, wrote, &off, &syn_count) &&
                            read_u32(data, wrote, &off, &ant_count)) {
                            int ok = 1;
                            for (uint32_t i = 0; i < pos_count; i++) ok &= skip_string(data, wrote, &off);
                            for (uint32_t i = 0; i < syn_count; i++) ok &= skip_string(data, wrote, &off);
                            for (uint32_t i = 0; i < ant_count; i++) ok &= skip_string(data, wrote, &off);
                            if (ok && pos_count > 0) {
                                printf("[OK] simjot_dict_lookup(love)\n");
                            } else {
                                fprintf(stderr, "[FAIL] simjot_dict_lookup(love): parse\n");
                                failures++;
                            }
                        } else {
                            fprintf(stderr, "[FAIL] simjot_dict_lookup(love): header\n");
                            failures++;
                        }
                    } else {
                        fprintf(stderr, "[FAIL] simjot_dict_lookup(love): call\n");
                        failures++;
                    }
                    free(data);
                }
            }

            int32_t rhyme_need = simjot_dict_rhymes_for("night", 5, NULL, 0);
            if (rhyme_need < 0) rhyme_need = -rhyme_need;
            if (rhyme_need <= 0) {
                fprintf(stderr, "[FAIL] simjot_dict_rhymes_for(night): size\n");
                failures++;
            } else {
                uint8_t* data = (uint8_t*)malloc((size_t)rhyme_need);
                if (!data) {
                    fprintf(stderr, "[FAIL] simjot_dict_rhymes_for(night): alloc\n");
                    failures++;
                } else {
                    int32_t wrote = simjot_dict_rhymes_for("night", 5, data, rhyme_need);
                    if (wrote > 0) {
                        int32_t off = 0;
                        uint32_t count = 0;
                        if (read_u32(data, wrote, &off, &count) && count > 0) {
                            printf("[OK] simjot_dict_rhymes_for(night)\n");
                        } else {
                            fprintf(stderr, "[FAIL] simjot_dict_rhymes_for(night): parse\n");
                            failures++;
                        }
                    } else {
                        fprintf(stderr, "[FAIL] simjot_dict_rhymes_for(night): call\n");
                        failures++;
                    }
                    free(data);
                }
            }
        }
    } else {
        printf("[SKIP] simjot_dict_* (set SIMJOT_DICT_PATH to enable)\n");
    }

    char tmp_path[L_tmpnam];
    if (make_temp_path(tmp_path, sizeof(tmp_path))) {
        if (write_file(tmp_path, "abc")) {
            uint8_t hash[32];
            if (simjot_sha256_file(tmp_path, hash)) {
                char hex[65];
                bytes_to_hex(hash, 32, hex);
                expect_str("simjot_sha256_file", hex, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
            } else {
                fprintf(stderr, "[FAIL] simjot_sha256_file: call failed\n");
                failures++;
            }

            uint8_t health[64];
            int32_t hlen = simjot_binary_health(tmp_path, health, (int32_t)sizeof(health));
            if (hlen > 0) {
                int32_t off = 0;
                uint32_t version = 0;
                uint32_t flags = 0;
                uint64_t size = 0;
                uint64_t mtime = 0;
                if (read_u32(health, hlen, &off, &version) &&
                    read_u32(health, hlen, &off, &flags) &&
                    read_u64(health, hlen, &off, &size) &&
                    read_u64(health, hlen, &off, &mtime) &&
                    off + 32 <= hlen) {
                    (void)mtime;
                    if (version == 1 && flags == 1 && size == 3) {
                        char hex[65];
                        bytes_to_hex(health + off, 32, hex);
                        expect_str("simjot_binary_health", hex, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
                    } else {
                        fprintf(stderr, "[FAIL] simjot_binary_health: unexpected metadata\n");
                        failures++;
                    }
                } else {
                    fprintf(stderr, "[FAIL] simjot_binary_health: parse failed\n");
                    failures++;
                }
            } else {
                fprintf(stderr, "[FAIL] simjot_binary_health: call failed\n");
                failures++;
            }
        } else {
            fprintf(stderr, "[FAIL] write temp file for sha256\n");
            failures++;
        }
        remove(tmp_path);
    } else {
        fprintf(stderr, "[FAIL] tmpnam failed for sha256 test\n");
        failures++;
    }

#ifndef _WIN32
    char dir_path[64];
    if (make_temp_dir(dir_path, sizeof(dir_path))) {
        char file_path[96];
        char subdir_path[96];
        snprintf(file_path, sizeof(file_path), "%s/%s", dir_path, "alpha.txt");
        snprintf(subdir_path, sizeof(subdir_path), "%s/%s", dir_path, "subdir");
        if (!write_file(file_path, "x")) {
            fprintf(stderr, "[FAIL] write temp file for list dir test\n");
            failures++;
        }
        if (mkdir(subdir_path, 0700) != 0) {
            fprintf(stderr, "[FAIL] mkdir for list dir test\n");
            failures++;
        }
        int size = simjot_list_dir_size(dir_path, 1);
        if (size > 0) {
            uint8_t* data = (uint8_t*)malloc((size_t)size);
            if (data) {
                int written = simjot_list_dir(dir_path, 1, data, size);
                if (written > 0) {
                    if (!list_contains(data, written, "alpha.txt", 0)) {
                        fprintf(stderr, "[FAIL] simjot_list_dir missing alpha.txt\n");
                        failures++;
                    } else {
                        printf("[OK] simjot_list_dir file\n");
                    }
                    if (!list_contains(data, written, "subdir", 1)) {
                        fprintf(stderr, "[FAIL] simjot_list_dir missing subdir\n");
                        failures++;
                    } else {
                        printf("[OK] simjot_list_dir dir\n");
                    }
                } else {
                    fprintf(stderr, "[FAIL] simjot_list_dir: call failed\n");
                    failures++;
                }
                free(data);
            } else {
                fprintf(stderr, "[FAIL] simjot_list_dir: alloc failed\n");
                failures++;
            }
        } else {
            fprintf(stderr, "[FAIL] simjot_list_dir_size: %d\n", size);
            failures++;
        }
        remove(file_path);
        rmdir(subdir_path);
        rmdir(dir_path);
    } else {
        fprintf(stderr, "[FAIL] mkdtemp failed for list dir test\n");
        failures++;
    }
#else
    printf("[SKIP] simjot_list_dir (Windows stub)\n");
#endif

#ifndef _WIN32
    uint8_t perf[96];
    int32_t plen = simjot_perf_snapshot(perf, (int32_t)sizeof(perf));
    if (plen > 0) {
        int32_t off = 0;
        uint32_t version = 0;
        uint32_t cpu_count = 0;
        uint64_t ts = 0, user = 0, sys = 0, rss = 0, vmem = 0, total = 0, avail = 0;
        if (read_u32(perf, plen, &off, &version) &&
            read_u32(perf, plen, &off, &cpu_count) &&
            read_u64(perf, plen, &off, &ts) &&
            read_u64(perf, plen, &off, &user) &&
            read_u64(perf, plen, &off, &sys) &&
            read_u64(perf, plen, &off, &rss) &&
            read_u64(perf, plen, &off, &vmem) &&
            read_u64(perf, plen, &off, &total) &&
            read_u64(perf, plen, &off, &avail)) {
            if (version == 1 && cpu_count >= 1 && total > 0) {
                printf("[OK] simjot_perf_snapshot\n");
            } else {
                fprintf(stderr, "[FAIL] simjot_perf_snapshot: unexpected metadata\n");
                failures++;
            }
            (void)ts; (void)user; (void)sys; (void)rss; (void)vmem; (void)avail;
        } else {
            fprintf(stderr, "[FAIL] simjot_perf_snapshot: parse failed\n");
            failures++;
        }
    } else {
        fprintf(stderr, "[FAIL] simjot_perf_snapshot: call failed\n");
        failures++;
    }
#else
    printf("[SKIP] simjot_perf_snapshot (Windows stub)\n");
#endif

#ifndef _WIN32
    if (make_temp_path(tmp_path, sizeof(tmp_path))) {
        const char* payload = "native io";
        if (simjot_atomic_write(tmp_path, (const uint8_t*)payload, (int32_t)strlen(payload), 1, 1)) {
            FILE* f = fopen(tmp_path, "rb");
            if (f) {
                char buf[32] = {0};
                size_t n = fread(buf, 1, sizeof(buf) - 1, f);
                fclose(f);
                buf[n] = '\0';
                expect_str("simjot_atomic_write", buf, payload);
            } else {
                fprintf(stderr, "[FAIL] simjot_atomic_write: read back failed\n");
                failures++;
            }
        } else {
            fprintf(stderr, "[FAIL] simjot_atomic_write: call failed\n");
            failures++;
        }
        remove(tmp_path);
    } else {
        fprintf(stderr, "[FAIL] tmpnam failed for atomic write test\n");
        failures++;
    }

    char copy_src[L_tmpnam];
    char copy_dst[L_tmpnam];
    if (make_temp_path(copy_src, sizeof(copy_src)) && make_temp_path(copy_dst, sizeof(copy_dst))) {
        const char* payload = "copy fast path";
        if (write_file(copy_src, payload)) {
            if (simjot_copy_file(copy_src, copy_dst, 1)) {
                FILE* f = fopen(copy_dst, "rb");
                if (f) {
                    char buf[64] = {0};
                    size_t n = fread(buf, 1, sizeof(buf) - 1, f);
                    fclose(f);
                    buf[n] = '\0';
                    expect_str("simjot_copy_file", buf, payload);
                } else {
                    fprintf(stderr, "[FAIL] simjot_copy_file: read back failed\n");
                    failures++;
                }
            } else {
                fprintf(stderr, "[FAIL] simjot_copy_file: call failed\n");
                failures++;
            }
        } else {
            fprintf(stderr, "[FAIL] write temp file for copy test\n");
            failures++;
        }
        remove(copy_src);
        remove(copy_dst);
    } else {
        fprintf(stderr, "[FAIL] tmpnam failed for copy test\n");
        failures++;
    }

    int space_ok = simjot_ensure_space(".", 1);
    if (space_ok == 1) {
        printf("[OK] simjot_ensure_space small\n");
    } else {
        fprintf(stderr, "[FAIL] simjot_ensure_space small: %d\n", space_ok);
        failures++;
    }

    int space_fail = simjot_ensure_space(".", UINT64_MAX);
    if (space_fail == 0) {
        printf("[OK] simjot_ensure_space huge\n");
    } else {
        fprintf(stderr, "[FAIL] simjot_ensure_space huge: %d\n", space_fail);
        failures++;
    }
#else
    printf("[SKIP] simjot_atomic_write and simjot_ensure_space (Windows stub)\n");
#endif

    // ═══════════════════════════════════════════════════════════════════════════
    // HOTKEY MANAGER TESTS
    // ═══════════════════════════════════════════════════════════════════════════
    printf("\n=== Hotkey Manager Tests ===\n");
    
    // Test platform detection
    int32_t platform = simjot_hotkey_get_platform();
#if defined(__APPLE__) && defined(__MACH__)
    expect_int("simjot_hotkey_get_platform (macOS)", platform, SIMJOT_PLATFORM_MACOS);
#elif defined(_WIN32) || defined(_WIN64)
    expect_int("simjot_hotkey_get_platform (Windows)", platform, SIMJOT_PLATFORM_WINDOWS);
#elif defined(__linux__)
    expect_int("simjot_hotkey_get_platform (Linux)", platform, SIMJOT_PLATFORM_LINUX);
#else
    printf("[INFO] simjot_hotkey_get_platform: %d (unknown platform)\n", platform);
#endif

    // Test primary modifier
    int32_t primary_mod = simjot_hotkey_get_primary_modifier();
#if defined(__APPLE__) && defined(__MACH__)
    expect_int("simjot_hotkey_get_primary_modifier (macOS)", primary_mod, SIMJOT_MOD_META);
#else
    expect_int("simjot_hotkey_get_primary_modifier (non-macOS)", primary_mod, SIMJOT_MOD_CTRL);
#endif

    // Test hotkey check - Bold (Cmd/Ctrl + B)
    int32_t bold_action = simjot_hotkey_check('B', primary_mod);
    expect_int("simjot_hotkey_check(B, primary)", bold_action, SIMJOT_ACTION_BOLD);
    
    // Test hotkey check - lowercase should also work
    int32_t bold_lower = simjot_hotkey_check('b', primary_mod);
    expect_int("simjot_hotkey_check(b, primary)", bold_lower, SIMJOT_ACTION_BOLD);

    // Test hotkey check - Italic (Cmd/Ctrl + I)
    int32_t italic_action = simjot_hotkey_check('I', primary_mod);
    expect_int("simjot_hotkey_check(I, primary)", italic_action, SIMJOT_ACTION_ITALIC);

    // Test hotkey check - Underline (Cmd/Ctrl + U)
    int32_t underline_action = simjot_hotkey_check('U', primary_mod);
    expect_int("simjot_hotkey_check(U, primary)", underline_action, SIMJOT_ACTION_UNDERLINE);

    // Test hotkey check - Strikethrough (Cmd/Ctrl + Shift + S)
    int32_t strike_action = simjot_hotkey_check('S', primary_mod | SIMJOT_MOD_SHIFT);
    expect_int("simjot_hotkey_check(S, primary|shift)", strike_action, SIMJOT_ACTION_STRIKETHROUGH);

    // Test that S without shift doesn't match strikethrough
    int32_t s_no_shift = simjot_hotkey_check('S', primary_mod);
    expect_int("simjot_hotkey_check(S, primary only)", s_no_shift, SIMJOT_ACTION_NONE);

    // Test that unbound keys return NONE
    int32_t unbound = simjot_hotkey_check('X', primary_mod);
    expect_int("simjot_hotkey_check(X, primary)", unbound, SIMJOT_ACTION_NONE);

    // Test that wrong modifier returns NONE
    int32_t wrong_mod = simjot_hotkey_check('B', SIMJOT_MOD_ALT);
    expect_int("simjot_hotkey_check(B, alt only)", wrong_mod, SIMJOT_ACTION_NONE);

    // Test get binding
    int32_t key_code = 0, modifiers = 0;
    int32_t got_binding = simjot_hotkey_get_binding(SIMJOT_ACTION_BOLD, &key_code, &modifiers);
    expect_int("simjot_hotkey_get_binding(BOLD) success", got_binding, 1);
    expect_int("simjot_hotkey_get_binding(BOLD) key", key_code, 'B');
    expect_int("simjot_hotkey_get_binding(BOLD) mods", modifiers, primary_mod);

    got_binding = simjot_hotkey_get_binding(SIMJOT_ACTION_STRIKETHROUGH, &key_code, &modifiers);
    expect_int("simjot_hotkey_get_binding(STRIKE) success", got_binding, 1);
    expect_int("simjot_hotkey_get_binding(STRIKE) key", key_code, 'S');
    expect_int("simjot_hotkey_get_binding(STRIKE) mods", modifiers, primary_mod | SIMJOT_MOD_SHIFT);

    // Test display string
    char display[32] = {0};
    int32_t dlen = simjot_hotkey_get_display_string(SIMJOT_ACTION_BOLD, display, sizeof(display));
    if (dlen > 0) {
#if defined(__APPLE__) && defined(__MACH__)
        expect_str("simjot_hotkey_get_display_string(BOLD)", display, "⌘B");
#else
        expect_str("simjot_hotkey_get_display_string(BOLD)", display, "Ctrl+B");
#endif
    } else {
        fprintf(stderr, "[FAIL] simjot_hotkey_get_display_string(BOLD): returned %d\n", dlen);
        failures++;
    }

    dlen = simjot_hotkey_get_display_string(SIMJOT_ACTION_STRIKETHROUGH, display, sizeof(display));
    if (dlen > 0) {
#if defined(__APPLE__) && defined(__MACH__)
        expect_str("simjot_hotkey_get_display_string(STRIKE)", display, "⇧⌘S");
#else
        expect_str("simjot_hotkey_get_display_string(STRIKE)", display, "Ctrl+Shift+S");
#endif
    } else {
        fprintf(stderr, "[FAIL] simjot_hotkey_get_display_string(STRIKE): returned %d\n", dlen);
        failures++;
    }

    if (failures == 0) {
        printf("\nAll native tests passed.\n");
        return 0;
    }

    fprintf(stderr, "\nNative tests failed: %d\n", failures);
    return 1;
}
