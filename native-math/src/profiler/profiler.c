/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "simjot_native.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#ifndef _WIN32
#include <unistd.h>
#include <pthread.h>
#include <sys/resource.h>
#include <sys/time.h>
#if defined(__APPLE__)
#include <sys/sysctl.h>
#include <mach/mach.h>
#include <mach/mach_host.h>
#include <mach/thread_act.h>
#include <mach/task.h>
#include <mach/task_info.h>
#else
#include <sys/sysinfo.h>
#endif
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * CONSTANTS AND DATA STRUCTURES
 * ═══════════════════════════════════════════════════════════════════════════ */

#define PROFILER_MAX_COMPONENTS 128
#define PROFILER_MAX_NAME_LEN 64
#define PROFILER_MAX_THREADS_PER_COMPONENT 32
#define PROFILER_HISTORY_SIZE 120

typedef struct {
    char name[PROFILER_MAX_NAME_LEN];
    int32_t active;
    
    /* Thread tracking */
    uint64_t thread_ids[PROFILER_MAX_THREADS_PER_COMPONENT];
    int32_t thread_count;
    
    /* Memory tracking */
    int64_t allocated_bytes;
    int64_t peak_memory;
    int64_t current_memory;
    
    /* CPU tracking */
    double cpu_samples[PROFILER_HISTORY_SIZE];
    int32_t sample_index;
    int32_t sample_count;
    double peak_cpu;
    double total_cpu_ns;
    
    /* Timing */
    uint64_t last_cpu_time_ns;
    uint64_t last_sample_time_ns;
} ComponentProfile;

typedef struct {
    ComponentProfile components[PROFILER_MAX_COMPONENTS];
    int32_t component_count;
    int32_t running;
    uint64_t start_time_ns;
    uint64_t sample_count;
    int32_t sample_interval_ms;
    
    /* Global metrics */
    int64_t total_rss_bytes;
    int64_t total_vmem_bytes;
    double total_cpu_percent;
    int32_t cpu_count;
} ProfilerState;

static ProfilerState g_profiler = {0};
static pthread_mutex_t g_profiler_lock = PTHREAD_MUTEX_INITIALIZER;

/* ═══════════════════════════════════════════════════════════════════════════
 * INTERNAL HELPERS
 * ═══════════════════════════════════════════════════════════════════════════ */

static uint64_t get_monotonic_ns(void) {
#ifdef CLOCK_MONOTONIC
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) == 0) {
        return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
    }
#endif
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (uint64_t)tv.tv_sec * 1000000000ULL + (uint64_t)tv.tv_usec * 1000ULL;
}

static uint64_t get_thread_cpu_time_ns(uint64_t thread_id) {
#if defined(__APPLE__)
    mach_port_t thread_port = pthread_mach_thread_np((pthread_t)thread_id);
    if (thread_port == MACH_PORT_NULL) return 0;
    
    thread_basic_info_data_t info;
    mach_msg_type_number_t count = THREAD_BASIC_INFO_COUNT;
    if (thread_info(thread_port, THREAD_BASIC_INFO, (thread_info_t)&info, &count) == KERN_SUCCESS) {
        return (uint64_t)info.user_time.seconds * 1000000000ULL +
               (uint64_t)info.user_time.microseconds * 1000ULL +
               (uint64_t)info.system_time.seconds * 1000000000ULL +
               (uint64_t)info.system_time.microseconds * 1000ULL;
    }
#else
    /* Linux: read from /proc/self/task/<tid>/stat */
    char path[64];
    snprintf(path, sizeof(path), "/proc/self/task/%lu/stat", (unsigned long)thread_id);
    FILE* f = fopen(path, "r");
    if (f) {
        char buf[512];
        if (fgets(buf, sizeof(buf), f)) {
            /* Skip to utime and stime fields (14th and 15th) */
            char* p = buf;
            int field = 0;
            unsigned long utime = 0, stime = 0;
            while (*p && field < 15) {
                if (*p == ' ') {
                    field++;
                    if (field == 13) sscanf(p+1, "%lu", &utime);
                    if (field == 14) sscanf(p+1, "%lu", &stime);
                }
                p++;
            }
            fclose(f);
            long ticks_per_sec = sysconf(_SC_CLK_TCK);
            if (ticks_per_sec > 0) {
                return ((utime + stime) * 1000000000ULL) / ticks_per_sec;
            }
        }
        fclose(f);
    }
#endif
    return 0;
}

static void get_process_memory(int64_t* rss, int64_t* vmem) {
    if (rss) *rss = 0;
    if (vmem) *vmem = 0;
    
#if defined(__APPLE__)
    mach_task_basic_info_data_t info;
    mach_msg_type_number_t count = MACH_TASK_BASIC_INFO_COUNT;
    if (task_info(mach_task_self(), MACH_TASK_BASIC_INFO, (task_info_t)&info, &count) == KERN_SUCCESS) {
        if (rss) *rss = (int64_t)info.resident_size;
        if (vmem) *vmem = (int64_t)info.virtual_size;
    }
#else
    FILE* f = fopen("/proc/self/statm", "r");
    if (f) {
        unsigned long size = 0, resident = 0;
        if (fscanf(f, "%lu %lu", &size, &resident) == 2) {
            long page = sysconf(_SC_PAGESIZE);
            if (page > 0) {
                if (rss) *rss = (int64_t)resident * page;
                if (vmem) *vmem = (int64_t)size * page;
            }
        }
        fclose(f);
    }
#endif
}

static int find_component(const char* name) {
    for (int i = 0; i < g_profiler.component_count; i++) {
        if (g_profiler.components[i].active && 
            strcmp(g_profiler.components[i].name, name) == 0) {
            return i;
        }
    }
    return -1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - INITIALIZATION
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_profiler_init(int32_t sample_interval_ms) {
    pthread_mutex_lock(&g_profiler_lock);
    
    memset(&g_profiler, 0, sizeof(g_profiler));
    g_profiler.sample_interval_ms = sample_interval_ms > 0 ? sample_interval_ms : 500;
    g_profiler.start_time_ns = get_monotonic_ns();
    
    long cores = sysconf(_SC_NPROCESSORS_ONLN);
    g_profiler.cpu_count = cores > 0 ? (int32_t)cores : 1;
    
    pthread_mutex_unlock(&g_profiler_lock);
    return 1;
}

int32_t simjot_profiler_start(void) {
    pthread_mutex_lock(&g_profiler_lock);
    g_profiler.running = 1;
    g_profiler.start_time_ns = get_monotonic_ns();
    pthread_mutex_unlock(&g_profiler_lock);
    return 1;
}

int32_t simjot_profiler_stop(void) {
    pthread_mutex_lock(&g_profiler_lock);
    g_profiler.running = 0;
    pthread_mutex_unlock(&g_profiler_lock);
    return 1;
}

void simjot_profiler_reset(void) {
    pthread_mutex_lock(&g_profiler_lock);
    for (int i = 0; i < g_profiler.component_count; i++) {
        ComponentProfile* cp = &g_profiler.components[i];
        cp->allocated_bytes = 0;
        cp->peak_memory = 0;
        cp->current_memory = 0;
        cp->peak_cpu = 0;
        cp->total_cpu_ns = 0;
        cp->sample_index = 0;
        cp->sample_count = 0;
        memset(cp->cpu_samples, 0, sizeof(cp->cpu_samples));
    }
    g_profiler.sample_count = 0;
    g_profiler.start_time_ns = get_monotonic_ns();
    pthread_mutex_unlock(&g_profiler_lock);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - COMPONENT REGISTRATION
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_profiler_register_component(const char* name) {
    if (!name || strlen(name) >= PROFILER_MAX_NAME_LEN) return -1;
    
    pthread_mutex_lock(&g_profiler_lock);
    
    /* Check if already registered */
    int existing = find_component(name);
    if (existing >= 0) {
        pthread_mutex_unlock(&g_profiler_lock);
        return existing;
    }
    
    /* Find empty slot */
    if (g_profiler.component_count >= PROFILER_MAX_COMPONENTS) {
        pthread_mutex_unlock(&g_profiler_lock);
        return -1;
    }
    
    int idx = g_profiler.component_count++;
    ComponentProfile* cp = &g_profiler.components[idx];
    memset(cp, 0, sizeof(*cp));
    strncpy(cp->name, name, PROFILER_MAX_NAME_LEN - 1);
    cp->active = 1;
    
    pthread_mutex_unlock(&g_profiler_lock);
    return idx;
}

int32_t simjot_profiler_register_thread(const char* component_name, uint64_t thread_id) {
    if (!component_name) return 0;
    
    pthread_mutex_lock(&g_profiler_lock);
    
    int idx = find_component(component_name);
    if (idx < 0) {
        pthread_mutex_unlock(&g_profiler_lock);
        return 0;
    }
    
    ComponentProfile* cp = &g_profiler.components[idx];
    if (cp->thread_count >= PROFILER_MAX_THREADS_PER_COMPONENT) {
        pthread_mutex_unlock(&g_profiler_lock);
        return 0;
    }
    
    /* Check if already registered */
    for (int i = 0; i < cp->thread_count; i++) {
        if (cp->thread_ids[i] == thread_id) {
            pthread_mutex_unlock(&g_profiler_lock);
            return 1;
        }
    }
    
    cp->thread_ids[cp->thread_count++] = thread_id;
    pthread_mutex_unlock(&g_profiler_lock);
    return 1;
}

int32_t simjot_profiler_unregister_thread(const char* component_name, uint64_t thread_id) {
    if (!component_name) return 0;
    
    pthread_mutex_lock(&g_profiler_lock);
    
    int idx = find_component(component_name);
    if (idx < 0) {
        pthread_mutex_unlock(&g_profiler_lock);
        return 0;
    }
    
    ComponentProfile* cp = &g_profiler.components[idx];
    for (int i = 0; i < cp->thread_count; i++) {
        if (cp->thread_ids[i] == thread_id) {
            /* Remove by shifting */
            for (int j = i; j < cp->thread_count - 1; j++) {
                cp->thread_ids[j] = cp->thread_ids[j + 1];
            }
            cp->thread_count--;
            pthread_mutex_unlock(&g_profiler_lock);
            return 1;
        }
    }
    
    pthread_mutex_unlock(&g_profiler_lock);
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - MEMORY TRACKING
 * ═══════════════════════════════════════════════════════════════════════════ */

void simjot_profiler_track_alloc(const char* component_name, int64_t bytes) {
    if (!component_name || bytes <= 0) return;
    
    pthread_mutex_lock(&g_profiler_lock);
    
    int idx = find_component(component_name);
    if (idx >= 0) {
        ComponentProfile* cp = &g_profiler.components[idx];
        cp->allocated_bytes += bytes;
        cp->current_memory += bytes;
        if (cp->current_memory > cp->peak_memory) {
            cp->peak_memory = cp->current_memory;
        }
    }
    
    pthread_mutex_unlock(&g_profiler_lock);
}

void simjot_profiler_track_free(const char* component_name, int64_t bytes) {
    if (!component_name || bytes <= 0) return;
    
    pthread_mutex_lock(&g_profiler_lock);
    
    int idx = find_component(component_name);
    if (idx >= 0) {
        ComponentProfile* cp = &g_profiler.components[idx];
        cp->current_memory -= bytes;
        if (cp->current_memory < 0) cp->current_memory = 0;
    }
    
    pthread_mutex_unlock(&g_profiler_lock);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - SAMPLING
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_profiler_sample(void) {
    pthread_mutex_lock(&g_profiler_lock);
    
    if (!g_profiler.running) {
        pthread_mutex_unlock(&g_profiler_lock);
        return 0;
    }
    
    uint64_t now = get_monotonic_ns();
    get_process_memory(&g_profiler.total_rss_bytes, &g_profiler.total_vmem_bytes);
    
    double total_cpu = 0.0;
    
    for (int i = 0; i < g_profiler.component_count; i++) {
        ComponentProfile* cp = &g_profiler.components[i];
        if (!cp->active) continue;
        
        uint64_t total_thread_cpu = 0;
        for (int t = 0; t < cp->thread_count; t++) {
            total_thread_cpu += get_thread_cpu_time_ns(cp->thread_ids[t]);
        }
        
        if (cp->last_sample_time_ns > 0) {
            uint64_t elapsed = now - cp->last_sample_time_ns;
            if (elapsed > 0) {
                uint64_t cpu_delta = total_thread_cpu - cp->last_cpu_time_ns;
                double cpu_percent = (cpu_delta * 100.0) / (elapsed * g_profiler.cpu_count);
                if (cpu_percent > 100.0) cpu_percent = 100.0;
                if (cpu_percent < 0.0) cpu_percent = 0.0;
                
                cp->cpu_samples[cp->sample_index] = cpu_percent;
                cp->sample_index = (cp->sample_index + 1) % PROFILER_HISTORY_SIZE;
                if (cp->sample_count < PROFILER_HISTORY_SIZE) cp->sample_count++;
                
                if (cpu_percent > cp->peak_cpu) cp->peak_cpu = cpu_percent;
                total_cpu += cpu_percent;
            }
        }
        
        cp->last_cpu_time_ns = total_thread_cpu;
        cp->last_sample_time_ns = now;
    }
    
    g_profiler.total_cpu_percent = total_cpu;
    g_profiler.sample_count++;
    
    pthread_mutex_unlock(&g_profiler_lock);
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - SNAPSHOT / REPORTING
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_profiler_component_count(void) {
    pthread_mutex_lock(&g_profiler_lock);
    int32_t count = g_profiler.component_count;
    pthread_mutex_unlock(&g_profiler_lock);
    return count;
}

int32_t simjot_profiler_get_component_snapshot(int32_t index, uint8_t* out, int32_t out_len) {
    /* Output format (binary):
     * 4 bytes: name_len
     * N bytes: name (UTF-8)
     * 8 bytes: current_memory
     * 8 bytes: peak_memory
     * 8 bytes: allocated_bytes
     * 8 bytes: current_cpu (double)
     * 8 bytes: avg_cpu (double)
     * 8 bytes: peak_cpu (double)
     * 4 bytes: thread_count
     * 4 bytes: sample_count
     * 1 byte: high_memory_flag
     * 1 byte: high_cpu_flag
     */
    const int32_t MIN_SIZE = 4 + PROFILER_MAX_NAME_LEN + 8*6 + 4*2 + 2;
    if (!out || out_len < MIN_SIZE) return -MIN_SIZE;
    
    pthread_mutex_lock(&g_profiler_lock);
    
    if (index < 0 || index >= g_profiler.component_count) {
        pthread_mutex_unlock(&g_profiler_lock);
        return 0;
    }
    
    ComponentProfile* cp = &g_profiler.components[index];
    
    /* Calculate avg CPU */
    double avg_cpu = 0.0;
    double current_cpu = 0.0;
    if (cp->sample_count > 0) {
        double sum = 0.0;
        for (int i = 0; i < cp->sample_count; i++) {
            sum += cp->cpu_samples[i];
        }
        avg_cpu = sum / cp->sample_count;
        current_cpu = cp->cpu_samples[(cp->sample_index - 1 + PROFILER_HISTORY_SIZE) % PROFILER_HISTORY_SIZE];
    }
    
    int32_t offset = 0;
    int32_t name_len = (int32_t)strlen(cp->name);
    
    memcpy(out + offset, &name_len, 4); offset += 4;
    memcpy(out + offset, cp->name, name_len); offset += name_len;
    memcpy(out + offset, &cp->current_memory, 8); offset += 8;
    memcpy(out + offset, &cp->peak_memory, 8); offset += 8;
    memcpy(out + offset, &cp->allocated_bytes, 8); offset += 8;
    memcpy(out + offset, &current_cpu, 8); offset += 8;
    memcpy(out + offset, &avg_cpu, 8); offset += 8;
    memcpy(out + offset, &cp->peak_cpu, 8); offset += 8;
    memcpy(out + offset, &cp->thread_count, 4); offset += 4;
    memcpy(out + offset, &cp->sample_count, 4); offset += 4;
    
    uint8_t high_mem = (cp->current_memory > 50 * 1024 * 1024) ? 1 : 0; /* >50MB */
    uint8_t high_cpu = (current_cpu > 15.0) ? 1 : 0; /* >15% */
    out[offset++] = high_mem;
    out[offset++] = high_cpu;
    
    pthread_mutex_unlock(&g_profiler_lock);
    return offset;
}

int32_t simjot_profiler_get_summary(uint8_t* out, int32_t out_len) {
    /* Output format:
     * 8 bytes: total_rss_bytes
     * 8 bytes: total_vmem_bytes
     * 8 bytes: total_cpu_percent (double)
     * 4 bytes: component_count
     * 4 bytes: cpu_count
     * 8 bytes: sample_count
     * 8 bytes: runtime_ns
     */
    const int32_t required = 48;
    if (!out || out_len < required) return -required;
    
    pthread_mutex_lock(&g_profiler_lock);
    
    uint64_t runtime = get_monotonic_ns() - g_profiler.start_time_ns;
    
    int32_t offset = 0;
    memcpy(out + offset, &g_profiler.total_rss_bytes, 8); offset += 8;
    memcpy(out + offset, &g_profiler.total_vmem_bytes, 8); offset += 8;
    memcpy(out + offset, &g_profiler.total_cpu_percent, 8); offset += 8;
    memcpy(out + offset, &g_profiler.component_count, 4); offset += 4;
    memcpy(out + offset, &g_profiler.cpu_count, 4); offset += 4;
    memcpy(out + offset, &g_profiler.sample_count, 8); offset += 8;
    memcpy(out + offset, &runtime, 8); offset += 8;
    
    pthread_mutex_unlock(&g_profiler_lock);
    return offset;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * CLI REPORTING - Text output for console
 * ═══════════════════════════════════════════════════════════════════════════ */

static const char* format_bytes(int64_t bytes, char* buf, int buf_len) {
    if (bytes < 1024) {
        snprintf(buf, buf_len, "%lld B", (long long)bytes);
    } else if (bytes < 1024 * 1024) {
        snprintf(buf, buf_len, "%.1f KB", bytes / 1024.0);
    } else if (bytes < 1024 * 1024 * 1024) {
        snprintf(buf, buf_len, "%.1f MB", bytes / (1024.0 * 1024));
    } else {
        snprintf(buf, buf_len, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
    return buf;
}

int32_t simjot_profiler_print_report(char* out, int32_t out_len) {
    if (!out || out_len < 256) return 0;
    
    pthread_mutex_lock(&g_profiler_lock);
    
    char buf1[32], buf2[32], buf3[32];
    int32_t written = 0;
    
    /* Header */
    written += snprintf(out + written, out_len - written,
        "\n╔══════════════════════════════════════════════════════════════════════════════╗\n"
        "║                         SIMJOT COMPONENT PROFILER                            ║\n"
        "╠══════════════════════════════════════════════════════════════════════════════╣\n");
    
    /* Summary */
    uint64_t runtime_ms = (get_monotonic_ns() - g_profiler.start_time_ns) / 1000000;
    written += snprintf(out + written, out_len - written,
        "║ Runtime: %llu.%llus │ Samples: %llu │ CPUs: %d │ RSS: %s │ VMEM: %s\n",
        (unsigned long long)(runtime_ms / 1000),
        (unsigned long long)(runtime_ms % 1000) / 100,
        (unsigned long long)g_profiler.sample_count,
        g_profiler.cpu_count,
        format_bytes(g_profiler.total_rss_bytes, buf1, sizeof(buf1)),
        format_bytes(g_profiler.total_vmem_bytes, buf2, sizeof(buf2)));
    
    written += snprintf(out + written, out_len - written,
        "╠══════════════════════════════════════════════════════════════════════════════╣\n"
        "║ %-30s │ %10s │ %10s │ %8s │ %8s │ FLAGS\n",
        "COMPONENT", "MEMORY", "PEAK MEM", "CPU%", "PEAK%");
    written += snprintf(out + written, out_len - written,
        "╠══════════════════════════════════════════════════════════════════════════════╣\n");
    
    /* Sort by memory usage (descending) */
    int indices[PROFILER_MAX_COMPONENTS];
    for (int i = 0; i < g_profiler.component_count; i++) indices[i] = i;
    
    for (int i = 0; i < g_profiler.component_count - 1; i++) {
        for (int j = i + 1; j < g_profiler.component_count; j++) {
            if (g_profiler.components[indices[j]].current_memory > 
                g_profiler.components[indices[i]].current_memory) {
                int tmp = indices[i];
                indices[i] = indices[j];
                indices[j] = tmp;
            }
        }
    }
    
    /* Components */
    for (int i = 0; i < g_profiler.component_count && written < out_len - 128; i++) {
        ComponentProfile* cp = &g_profiler.components[indices[i]];
        if (!cp->active) continue;
        
        double avg_cpu = 0.0, current_cpu = 0.0;
        if (cp->sample_count > 0) {
            double sum = 0.0;
            for (int s = 0; s < cp->sample_count; s++) sum += cp->cpu_samples[s];
            avg_cpu = sum / cp->sample_count;
            current_cpu = cp->cpu_samples[(cp->sample_index - 1 + PROFILER_HISTORY_SIZE) % PROFILER_HISTORY_SIZE];
        }
        
        int high_mem = cp->current_memory > 50 * 1024 * 1024;
        int high_cpu = current_cpu > 15.0;
        
        char flags[16] = "";
        if (high_mem) strcat(flags, "⚠MEM ");
        if (high_cpu) strcat(flags, "⚠CPU");
        
        written += snprintf(out + written, out_len - written,
            "║ %-30.30s │ %10s │ %10s │ %7.1f%% │ %7.1f%% │ %s\n",
            cp->name,
            format_bytes(cp->current_memory, buf1, sizeof(buf1)),
            format_bytes(cp->peak_memory, buf2, sizeof(buf2)),
            current_cpu,
            cp->peak_cpu,
            flags);
    }
    
    written += snprintf(out + written, out_len - written,
        "╚══════════════════════════════════════════════════════════════════════════════╝\n");
    
    /* High usage summary */
    int high_count = 0;
    for (int i = 0; i < g_profiler.component_count; i++) {
        ComponentProfile* cp = &g_profiler.components[i];
        if (!cp->active) continue;
        
        double current_cpu = 0.0;
        if (cp->sample_count > 0) {
            current_cpu = cp->cpu_samples[(cp->sample_index - 1 + PROFILER_HISTORY_SIZE) % PROFILER_HISTORY_SIZE];
        }
        
        int high_mem = cp->current_memory > 50 * 1024 * 1024;
        int high_cpu_flag = current_cpu > 15.0;
        
        if (high_mem || high_cpu_flag) {
            if (high_count == 0) {
                written += snprintf(out + written, out_len - written,
                    "\n⚠️  HIGH USAGE COMPONENTS:\n");
            }
            written += snprintf(out + written, out_len - written,
                "   • %s: ", cp->name);
            if (high_mem) {
                written += snprintf(out + written, out_len - written,
                    "Memory %s (>50MB) ", format_bytes(cp->current_memory, buf1, sizeof(buf1)));
            }
            if (high_cpu_flag) {
                written += snprintf(out + written, out_len - written,
                    "CPU %.1f%% (>15%%)", current_cpu);
            }
            written += snprintf(out + written, out_len - written, "\n");
            high_count++;
        }
    }
    
    if (high_count == 0) {
        written += snprintf(out + written, out_len - written,
            "\n✅ No high usage components detected.\n");
    }
    
    pthread_mutex_unlock(&g_profiler_lock);
    return written;
}

/* Simple single-line status for continuous monitoring */
int32_t simjot_profiler_status_line(char* out, int32_t out_len) {
    if (!out || out_len < 128) return 0;
    
    pthread_mutex_lock(&g_profiler_lock);
    
    char buf[32];
    int32_t written = snprintf(out, out_len,
        "[PROF] RSS:%s CPU:%.1f%% Components:%d Samples:%llu",
        format_bytes(g_profiler.total_rss_bytes, buf, sizeof(buf)),
        g_profiler.total_cpu_percent,
        g_profiler.component_count,
        (unsigned long long)g_profiler.sample_count);
    
    pthread_mutex_unlock(&g_profiler_lock);
    return written;
}
