/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * Native performance and binary health utilities.
 */

#include "simjot_native.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <sys/stat.h>

#ifndef _WIN32
#include <unistd.h>
#include <sys/resource.h>
#include <sys/time.h>
#if defined(__APPLE__)
#include <sys/sysctl.h>
#include <mach/mach.h>
#include <mach/mach_host.h>
#else
#include <sys/sysinfo.h>
#endif
#endif

static int write_u32(uint8_t* out, int32_t out_len, int32_t* offset, uint32_t value) {
    if (!out || !offset) return 0;
    if (*offset + 4 > out_len) return 0;
    memcpy(out + *offset, &value, 4);
    *offset += 4;
    return 1;
}

static int write_u64(uint8_t* out, int32_t out_len, int32_t* offset, uint64_t value) {
    if (!out || !offset) return 0;
    if (*offset + 8 > out_len) return 0;
    memcpy(out + *offset, &value, 8);
    *offset += 8;
    return 1;
}

#ifndef _WIN32
static uint64_t timeval_to_ns(struct timeval tv) {
    return (uint64_t)tv.tv_sec * 1000000000ull + (uint64_t)tv.tv_usec * 1000ull;
}

static uint64_t monotonic_ns(void) {
#ifdef CLOCK_MONOTONIC
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) == 0) {
        return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
    }
#endif
    return 0;
}

static void read_process_memory(uint64_t* rss_bytes, uint64_t* vmem_bytes) {
    if (rss_bytes) *rss_bytes = 0;
    if (vmem_bytes) *vmem_bytes = 0;
#if defined(__APPLE__)
    mach_task_basic_info_data_t info;
    mach_msg_type_number_t count = MACH_TASK_BASIC_INFO_COUNT;
    if (task_info(mach_task_self(), MACH_TASK_BASIC_INFO, (task_info_t)&info, &count) == KERN_SUCCESS) {
        if (rss_bytes) *rss_bytes = (uint64_t)info.resident_size;
        if (vmem_bytes) *vmem_bytes = (uint64_t)info.virtual_size;
    }
#else
    FILE* f = fopen("/proc/self/statm", "r");
    if (f) {
        unsigned long size = 0;
        unsigned long resident = 0;
        if (fscanf(f, "%lu %lu", &size, &resident) == 2) {
            long page = sysconf(_SC_PAGESIZE);
            if (page > 0) {
                if (rss_bytes) *rss_bytes = (uint64_t)resident * (uint64_t)page;
                if (vmem_bytes) *vmem_bytes = (uint64_t)size * (uint64_t)page;
            }
        }
        fclose(f);
    }
    if (rss_bytes && *rss_bytes == 0) {
        struct rusage ru;
        if (getrusage(RUSAGE_SELF, &ru) == 0) {
            *rss_bytes = (uint64_t)ru.ru_maxrss * 1024ull;
        }
    }
#endif
}

static void read_system_memory(uint64_t* total_bytes, uint64_t* avail_bytes) {
    if (total_bytes) *total_bytes = 0;
    if (avail_bytes) *avail_bytes = 0;
#if defined(__APPLE__)
    uint64_t total = 0;
    size_t len = sizeof(total);
    if (sysctlbyname("hw.memsize", &total, &len, NULL, 0) == 0) {
        if (total_bytes) *total_bytes = total;
    }

    vm_statistics64_data_t vmstat;
    mach_msg_type_number_t count = HOST_VM_INFO64_COUNT;
    if (host_statistics64(mach_host_self(), HOST_VM_INFO64, (host_info64_t)&vmstat, &count) == KERN_SUCCESS) {
        vm_size_t page_size = 0;
        if (host_page_size(mach_host_self(), &page_size) == KERN_SUCCESS) {
            uint64_t free_pages = (uint64_t)vmstat.free_count
                                  + (uint64_t)vmstat.inactive_count
                                  + (uint64_t)vmstat.speculative_count;
            if (avail_bytes) *avail_bytes = free_pages * (uint64_t)page_size;
        }
    }
#else
    uint64_t total_kb = 0;
    uint64_t avail_kb = 0;
    FILE* f = fopen("/proc/meminfo", "r");
    if (f) {
        char line[256];
        while (fgets(line, sizeof(line), f)) {
            unsigned long value = 0;
            if (sscanf(line, "MemTotal: %lu kB", &value) == 1) {
                total_kb = value;
            } else if (sscanf(line, "MemAvailable: %lu kB", &value) == 1) {
                avail_kb = value;
            }
        }
        fclose(f);
    }
    if (total_kb > 0) {
        if (total_bytes) *total_bytes = total_kb * 1024ull;
        if (avail_bytes && avail_kb > 0) *avail_bytes = avail_kb * 1024ull;
    }
    if (total_bytes && *total_bytes == 0) {
        struct sysinfo info;
        if (sysinfo(&info) == 0) {
            uint64_t unit = (uint64_t)info.mem_unit;
            *total_bytes = (uint64_t)info.totalram * unit;
            if (avail_bytes) {
                *avail_bytes = ((uint64_t)info.freeram + (uint64_t)info.bufferram) * unit;
            }
        }
    }
#endif
}
#endif

int32_t simjot_perf_snapshot(uint8_t* out, int32_t out_len) {
    const int32_t required = 64;
    if (!out) return 0;
    if (out_len < required) return -required;
#ifndef _WIN32
    uint32_t version = 1;
    uint32_t cpu_count = 1;
    long cores = sysconf(_SC_NPROCESSORS_ONLN);
    if (cores > 0) cpu_count = (uint32_t)cores;

    uint64_t timestamp_ns = monotonic_ns();
    uint64_t cpu_user_ns = 0;
    uint64_t cpu_system_ns = 0;
    struct rusage ru;
    if (getrusage(RUSAGE_SELF, &ru) == 0) {
        cpu_user_ns = timeval_to_ns(ru.ru_utime);
        cpu_system_ns = timeval_to_ns(ru.ru_stime);
    }

    uint64_t rss_bytes = 0;
    uint64_t vmem_bytes = 0;
    read_process_memory(&rss_bytes, &vmem_bytes);

    uint64_t sys_total_bytes = 0;
    uint64_t sys_avail_bytes = 0;
    read_system_memory(&sys_total_bytes, &sys_avail_bytes);

    int32_t offset = 0;
    if (!write_u32(out, out_len, &offset, version)) return 0;
    if (!write_u32(out, out_len, &offset, cpu_count)) return 0;
    if (!write_u64(out, out_len, &offset, timestamp_ns)) return 0;
    if (!write_u64(out, out_len, &offset, cpu_user_ns)) return 0;
    if (!write_u64(out, out_len, &offset, cpu_system_ns)) return 0;
    if (!write_u64(out, out_len, &offset, rss_bytes)) return 0;
    if (!write_u64(out, out_len, &offset, vmem_bytes)) return 0;
    if (!write_u64(out, out_len, &offset, sys_total_bytes)) return 0;
    if (!write_u64(out, out_len, &offset, sys_avail_bytes)) return 0;
    return offset;
#else
    (void)out;
    (void)out_len;
    return 0;
#endif
}

int32_t simjot_binary_health(const char* path, uint8_t* out, int32_t out_len) {
    const int32_t required = 56;
    if (!path || !out) return 0;
    if (out_len < required) return -required;
#ifndef _WIN32
    struct stat st;
    if (stat(path, &st) != 0) return 0;
    uint8_t hash[32];
    if (!simjot_sha256_file(path, hash)) return 0;

    uint32_t version = 1;
    uint32_t flags = 1;
    uint64_t size = (uint64_t)st.st_size;
    uint64_t mtime = (uint64_t)st.st_mtime;
    int32_t offset = 0;
    if (!write_u32(out, out_len, &offset, version)) return 0;
    if (!write_u32(out, out_len, &offset, flags)) return 0;
    if (!write_u64(out, out_len, &offset, size)) return 0;
    if (!write_u64(out, out_len, &offset, mtime)) return 0;
    if (offset + 32 > out_len) return 0;
    memcpy(out + offset, hash, 32);
    offset += 32;
    return offset;
#else
    (void)path;
    (void)out;
    (void)out_len;
    return 0;
#endif
}
