/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * Standalone CLI profiler for Simjot.
 * Measures CPU and RAM usage per component with detailed reporting.
 * 
 * Usage:
 *   ./profiler_cli [options]
 *   
 * Options:
 *   -p, --pid <pid>      Profile a running process by PID
 *   -i, --interval <ms>  Sampling interval in milliseconds (default: 500)
 *   -d, --duration <s>   Duration to profile in seconds (default: 10)
 *   -c, --continuous     Run continuously until Ctrl+C
 *   -h, --help           Show this help
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <time.h>
#include <unistd.h>

#ifndef _WIN32
#include <sys/resource.h>
#include <sys/time.h>
#if defined(__APPLE__)
#include <sys/sysctl.h>
#include <mach/mach.h>
#include <mach/mach_host.h>
#include <libproc.h>
#else
#include <sys/sysinfo.h>
#endif
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * CONFIGURATION
 * ═══════════════════════════════════════════════════════════════════════════ */

#define MAX_SAMPLES 1000
#define HIGH_CPU_THRESHOLD 15.0
#define HIGH_MEM_MB 500  /* 500 MB - reasonable for Java apps */

typedef struct {
    int64_t timestamp_ms;
    int64_t rss_bytes;
    int64_t vmem_bytes;
    double cpu_percent;
    int64_t cpu_user_ns;
    int64_t cpu_sys_ns;
} Sample;

typedef struct {
    pid_t pid;
    int interval_ms;
    int duration_s;
    int continuous;
    
    Sample samples[MAX_SAMPLES];
    int sample_count;
    int sample_index;
    
    int64_t start_time_ms;
    int64_t last_cpu_ns;
    int64_t last_time_ns;
    int cpu_count;
    
    /* Peak values */
    int64_t peak_rss;
    double peak_cpu;
    
    /* Running averages */
    double avg_cpu;
    int64_t avg_rss;
} ProfilerState;

static volatile int g_running = 1;
static ProfilerState g_state = {0};

/* ═══════════════════════════════════════════════════════════════════════════
 * UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

static int64_t get_time_ms(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (int64_t)tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

static int64_t get_time_ns(void) {
#ifdef CLOCK_MONOTONIC
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) == 0) {
        return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
    }
#endif
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (int64_t)tv.tv_sec * 1000000000LL + (int64_t)tv.tv_usec * 1000LL;
}

static const char* format_bytes(int64_t bytes, char* buf, size_t buflen) {
    if (bytes < 1024) {
        snprintf(buf, buflen, "%lld B", (long long)bytes);
    } else if (bytes < 1024 * 1024) {
        snprintf(buf, buflen, "%.1f KB", bytes / 1024.0);
    } else if (bytes < 1024LL * 1024 * 1024) {
        snprintf(buf, buflen, "%.1f MB", bytes / (1024.0 * 1024));
    } else {
        snprintf(buf, buflen, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
    return buf;
}

static void signal_handler(int sig) {
    (void)sig;
    g_running = 0;
    printf("\n[Profiler] Stopping...\n");
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PROCESS METRICS (macOS)
 * ═══════════════════════════════════════════════════════════════════════════ */

#if defined(__APPLE__)

static int get_process_memory(pid_t pid, int64_t* rss, int64_t* vmem) {
    if (rss) *rss = 0;
    if (vmem) *vmem = 0;
    
    struct proc_taskinfo pti;
    int size = proc_pidinfo(pid, PROC_PIDTASKINFO, 0, &pti, sizeof(pti));
    if (size <= 0) return 0;
    
    if (rss) *rss = (int64_t)pti.pti_resident_size;
    if (vmem) *vmem = (int64_t)pti.pti_virtual_size;
    return 1;
}

static int get_process_cpu(pid_t pid, int64_t* user_ns, int64_t* sys_ns) {
    if (user_ns) *user_ns = 0;
    if (sys_ns) *sys_ns = 0;
    
    struct proc_taskinfo pti;
    int size = proc_pidinfo(pid, PROC_PIDTASKINFO, 0, &pti, sizeof(pti));
    if (size <= 0) return 0;
    
    /* pti_total_user and pti_total_system are in nanoseconds */
    if (user_ns) *user_ns = (int64_t)pti.pti_total_user;
    if (sys_ns) *sys_ns = (int64_t)pti.pti_total_system;
    return 1;
}

static int get_system_memory(int64_t* total, int64_t* available) {
    if (total) {
        uint64_t mem = 0;
        size_t len = sizeof(mem);
        if (sysctlbyname("hw.memsize", &mem, &len, NULL, 0) == 0) {
            *total = (int64_t)mem;
        }
    }
    
    if (available) {
        vm_statistics64_data_t vmstat;
        mach_msg_type_number_t count = HOST_VM_INFO64_COUNT;
        if (host_statistics64(mach_host_self(), HOST_VM_INFO64, (host_info64_t)&vmstat, &count) == KERN_SUCCESS) {
            vm_size_t page_size = 0;
            if (host_page_size(mach_host_self(), &page_size) == KERN_SUCCESS) {
                uint64_t free_pages = (uint64_t)vmstat.free_count + 
                                      (uint64_t)vmstat.inactive_count + 
                                      (uint64_t)vmstat.speculative_count;
                *available = (int64_t)(free_pages * page_size);
            }
        }
    }
    return 1;
}

static int get_cpu_count(void) {
    int count = 1;
    size_t len = sizeof(count);
    sysctlbyname("hw.ncpu", &count, &len, NULL, 0);
    return count > 0 ? count : 1;
}

#else /* Linux */

static int get_process_memory(pid_t pid, int64_t* rss, int64_t* vmem) {
    if (rss) *rss = 0;
    if (vmem) *vmem = 0;
    
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/statm", pid);
    FILE* f = fopen(path, "r");
    if (!f) return 0;
    
    unsigned long size = 0, resident = 0;
    if (fscanf(f, "%lu %lu", &size, &resident) == 2) {
        long page = sysconf(_SC_PAGESIZE);
        if (page > 0) {
            if (rss) *rss = (int64_t)resident * page;
            if (vmem) *vmem = (int64_t)size * page;
        }
    }
    fclose(f);
    return 1;
}

static int get_process_cpu(pid_t pid, int64_t* user_ns, int64_t* sys_ns) {
    if (user_ns) *user_ns = 0;
    if (sys_ns) *sys_ns = 0;
    
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/stat", pid);
    FILE* f = fopen(path, "r");
    if (!f) return 0;
    
    char buf[1024];
    if (!fgets(buf, sizeof(buf), f)) {
        fclose(f);
        return 0;
    }
    fclose(f);
    
    /* Parse utime and stime (fields 14 and 15, 1-indexed) */
    char* p = buf;
    int field = 0;
    unsigned long utime = 0, stime = 0;
    
    /* Skip past comm field (in parentheses) */
    p = strchr(p, ')');
    if (!p) return 0;
    p++;
    field = 2;
    
    while (*p && field < 15) {
        if (*p == ' ') {
            field++;
            if (field == 14) utime = strtoul(p + 1, NULL, 10);
            if (field == 15) stime = strtoul(p + 1, NULL, 10);
        }
        p++;
    }
    
    long ticks_per_sec = sysconf(_SC_CLK_TCK);
    if (ticks_per_sec > 0) {
        if (user_ns) *user_ns = (int64_t)(utime * 1000000000LL / ticks_per_sec);
        if (sys_ns) *sys_ns = (int64_t)(stime * 1000000000LL / ticks_per_sec);
    }
    return 1;
}

static int get_system_memory(int64_t* total, int64_t* available) {
    if (total) *total = 0;
    if (available) *available = 0;
    
    FILE* f = fopen("/proc/meminfo", "r");
    if (!f) return 0;
    
    char line[256];
    while (fgets(line, sizeof(line), f)) {
        unsigned long value = 0;
        if (total && sscanf(line, "MemTotal: %lu kB", &value) == 1) {
            *total = (int64_t)value * 1024;
        } else if (available && sscanf(line, "MemAvailable: %lu kB", &value) == 1) {
            *available = (int64_t)value * 1024;
        }
    }
    fclose(f);
    return 1;
}

static int get_cpu_count(void) {
    long count = sysconf(_SC_NPROCESSORS_ONLN);
    return count > 0 ? (int)count : 1;
}

#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * PROFILER CORE
 * ═══════════════════════════════════════════════════════════════════════════ */

static int init_profiler(pid_t pid, int interval_ms) {
    memset(&g_state, 0, sizeof(g_state));
    g_state.pid = pid;
    g_state.interval_ms = interval_ms;
    g_state.start_time_ms = get_time_ms();
    g_state.cpu_count = get_cpu_count();
    g_state.last_time_ns = get_time_ns();
    
    /* Get initial CPU time */
    int64_t user_ns, sys_ns;
    if (get_process_cpu(pid, &user_ns, &sys_ns)) {
        g_state.last_cpu_ns = user_ns + sys_ns;
    }
    
    return 1;
}

static int take_sample(void) {
    if (g_state.sample_count >= MAX_SAMPLES) {
        /* Shift samples to make room */
        memmove(g_state.samples, g_state.samples + 1, 
                sizeof(Sample) * (MAX_SAMPLES - 1));
        g_state.sample_count = MAX_SAMPLES - 1;
    }
    
    Sample* s = &g_state.samples[g_state.sample_count];
    s->timestamp_ms = get_time_ms() - g_state.start_time_ms;
    
    /* Get memory */
    if (!get_process_memory(g_state.pid, &s->rss_bytes, &s->vmem_bytes)) {
        return 0;
    }
    
    /* Get CPU */
    int64_t now_ns = get_time_ns();
    int64_t user_ns, sys_ns;
    if (get_process_cpu(g_state.pid, &user_ns, &sys_ns)) {
        s->cpu_user_ns = user_ns;
        s->cpu_sys_ns = sys_ns;
        
        int64_t total_cpu = user_ns + sys_ns;
        int64_t elapsed_ns = now_ns - g_state.last_time_ns;
        
        if (elapsed_ns > 0 && g_state.last_cpu_ns > 0) {
            int64_t cpu_delta = total_cpu - g_state.last_cpu_ns;
            s->cpu_percent = (cpu_delta * 100.0) / (elapsed_ns * g_state.cpu_count);
            if (s->cpu_percent < 0) s->cpu_percent = 0;
            if (s->cpu_percent > 100) s->cpu_percent = 100;
        }
        
        g_state.last_cpu_ns = total_cpu;
        g_state.last_time_ns = now_ns;
    }
    
    /* Update peaks */
    if (s->rss_bytes > g_state.peak_rss) g_state.peak_rss = s->rss_bytes;
    if (s->cpu_percent > g_state.peak_cpu) g_state.peak_cpu = s->cpu_percent;
    
    /* Update averages */
    g_state.avg_cpu = ((g_state.avg_cpu * g_state.sample_count) + s->cpu_percent) / 
                      (g_state.sample_count + 1);
    g_state.avg_rss = ((g_state.avg_rss * g_state.sample_count) + s->rss_bytes) / 
                      (g_state.sample_count + 1);
    
    g_state.sample_count++;
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * OUTPUT
 * ═══════════════════════════════════════════════════════════════════════════ */

static void print_header(void) {
    printf("\n");
    printf("╔══════════════════════════════════════════════════════════════════════════════╗\n");
    printf("║                         SIMJOT PROCESS PROFILER                              ║\n");
    printf("╠══════════════════════════════════════════════════════════════════════════════╣\n");
    printf("║ PID: %-10d  │  Interval: %dms  │  CPUs: %d                              \n",
           g_state.pid, g_state.interval_ms, g_state.cpu_count);
    printf("╠══════════════════════════════════════════════════════════════════════════════╣\n");
    printf("║  TIME   │    RSS     │   VMEM     │  CPU%%  │  STATUS                        \n");
    printf("╠══════════════════════════════════════════════════════════════════════════════╣\n");
}

static void print_sample(Sample* s) {
    char rss_buf[32], vmem_buf[32];
    const char* status = "";
    
    if (s->rss_bytes > HIGH_MEM_MB * 1024 * 1024) status = "⚠️  HIGH MEM";
    else if (s->cpu_percent > HIGH_CPU_THRESHOLD) status = "⚠️  HIGH CPU";
    
    printf("║ %5.1fs │ %10s │ %10s │ %5.1f%% │ %s\n",
           s->timestamp_ms / 1000.0,
           format_bytes(s->rss_bytes, rss_buf, sizeof(rss_buf)),
           format_bytes(s->vmem_bytes, vmem_buf, sizeof(vmem_buf)),
           s->cpu_percent,
           status);
}

static void print_status_line(Sample* s) {
    char rss_buf[32];
    printf("\r[PROF] PID:%d  RSS:%s  CPU:%.1f%%  Samples:%d  ",
           g_state.pid,
           format_bytes(s->rss_bytes, rss_buf, sizeof(rss_buf)),
           s->cpu_percent,
           g_state.sample_count);
    fflush(stdout);
}

static void print_summary(void) {
    char peak_rss[32], avg_rss[32];
    int64_t sys_total = 0, sys_avail = 0;
    get_system_memory(&sys_total, &sys_avail);
    
    char sys_total_buf[32], sys_avail_buf[32];
    
    printf("╠══════════════════════════════════════════════════════════════════════════════╣\n");
    printf("║                                  SUMMARY                                     ║\n");
    printf("╠══════════════════════════════════════════════════════════════════════════════╣\n");
    printf("║ Duration:     %.1f seconds                                                   \n",
           (get_time_ms() - g_state.start_time_ms) / 1000.0);
    printf("║ Samples:      %d                                                             \n",
           g_state.sample_count);
    printf("║ Peak RSS:     %s                                                        \n",
           format_bytes(g_state.peak_rss, peak_rss, sizeof(peak_rss)));
    printf("║ Avg RSS:      %s                                                        \n",
           format_bytes(g_state.avg_rss, avg_rss, sizeof(avg_rss)));
    printf("║ Peak CPU:     %.1f%%                                                         \n",
           g_state.peak_cpu);
    printf("║ Avg CPU:      %.1f%%                                                         \n",
           g_state.avg_cpu);
    printf("║ System RAM:   %s / %s                                       \n",
           format_bytes(sys_total - sys_avail, sys_avail_buf, sizeof(sys_avail_buf)),
           format_bytes(sys_total, sys_total_buf, sizeof(sys_total_buf)));
    printf("╚══════════════════════════════════════════════════════════════════════════════╝\n");
    
    /* Warnings */
    if (g_state.peak_rss > HIGH_MEM_MB * 1024 * 1024) {
        printf("\n⚠️  HIGH MEMORY USAGE detected (peak: %s)\n", peak_rss);
        printf("    Consider investigating memory allocations.\n");
    }
    if (g_state.peak_cpu > HIGH_CPU_THRESHOLD) {
        printf("\n⚠️  HIGH CPU USAGE detected (peak: %.1f%%)\n", g_state.peak_cpu);
        printf("    Consider profiling CPU-intensive code paths.\n");
    }
    if (g_state.peak_rss <= HIGH_MEM_MB * 1024 * 1024 && g_state.peak_cpu <= HIGH_CPU_THRESHOLD) {
        printf("\n✅ No high usage detected. Process appears healthy.\n");
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * SELF PROFILING
 * ═══════════════════════════════════════════════════════════════════════════ */

static void profile_self(void) {
    printf("[Profiler] Self-profiling mode (profiling this process)\n");
    
    pid_t pid = getpid();
    if (!init_profiler(pid, g_state.interval_ms > 0 ? g_state.interval_ms : 500)) {
        fprintf(stderr, "Failed to initialize profiler\n");
        return;
    }
    
    print_header();
    
    int sample_num = 0;
    while (g_running && (g_state.continuous || sample_num < (g_state.duration_s * 1000 / g_state.interval_ms))) {
        if (take_sample()) {
            Sample* s = &g_state.samples[g_state.sample_count - 1];
            print_sample(s);
        }
        
        usleep(g_state.interval_ms * 1000);
        sample_num++;
    }
    
    print_summary();
}

/* ═══════════════════════════════════════════════════════════════════════════
 * MAIN
 * ═══════════════════════════════════════════════════════════════════════════ */

static void print_usage(const char* prog) {
    printf("Simjot Process Profiler - Measures CPU and RAM usage\n\n");
    printf("Usage: %s [options]\n\n", prog);
    printf("Options:\n");
    printf("  -p, --pid <pid>      Profile a running process by PID\n");
    printf("  -i, --interval <ms>  Sampling interval in milliseconds (default: 500)\n");
    printf("  -d, --duration <s>   Duration to profile in seconds (default: 10)\n");
    printf("  -c, --continuous     Run continuously until Ctrl+C\n");
    printf("  -q, --quiet          Only show status line (no table)\n");
    printf("  -s, --self           Profile this profiler process (for testing)\n");
    printf("  -h, --help           Show this help\n\n");
    printf("Examples:\n");
    printf("  %s -p 12345              # Profile process 12345 for 10 seconds\n", prog);
    printf("  %s -p 12345 -c           # Profile continuously until Ctrl+C\n", prog);
    printf("  %s -p 12345 -i 100 -d 60 # Sample every 100ms for 60 seconds\n", prog);
    printf("  %s -s                    # Self-profile for testing\n", prog);
}

int main(int argc, char** argv) {
    pid_t target_pid = 0;
    int interval_ms = 500;
    int duration_s = 10;
    int continuous = 0;
    int quiet = 0;
    int self_profile = 0;
    
    /* Parse arguments */
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "-p") == 0 || strcmp(argv[i], "--pid") == 0) {
            if (i + 1 < argc) target_pid = atoi(argv[++i]);
        } else if (strcmp(argv[i], "-i") == 0 || strcmp(argv[i], "--interval") == 0) {
            if (i + 1 < argc) interval_ms = atoi(argv[++i]);
        } else if (strcmp(argv[i], "-d") == 0 || strcmp(argv[i], "--duration") == 0) {
            if (i + 1 < argc) duration_s = atoi(argv[++i]);
        } else if (strcmp(argv[i], "-c") == 0 || strcmp(argv[i], "--continuous") == 0) {
            continuous = 1;
        } else if (strcmp(argv[i], "-q") == 0 || strcmp(argv[i], "--quiet") == 0) {
            quiet = 1;
        } else if (strcmp(argv[i], "-s") == 0 || strcmp(argv[i], "--self") == 0) {
            self_profile = 1;
        } else if (strcmp(argv[i], "-h") == 0 || strcmp(argv[i], "--help") == 0) {
            print_usage(argv[0]);
            return 0;
        }
    }
    
    /* Setup signal handler */
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);
    
    /* Self-profile mode */
    if (self_profile) {
        g_state.interval_ms = interval_ms;
        g_state.duration_s = duration_s;
        g_state.continuous = continuous;
        profile_self();
        return 0;
    }
    
    /* Validate PID */
    if (target_pid <= 0) {
        fprintf(stderr, "Error: No PID specified. Use -p <pid> or -s for self-profiling.\n");
        print_usage(argv[0]);
        return 1;
    }
    
    /* Check if process exists */
    if (kill(target_pid, 0) != 0) {
        fprintf(stderr, "Error: Process %d does not exist or is not accessible.\n", target_pid);
        return 1;
    }
    
    /* Initialize */
    if (!init_profiler(target_pid, interval_ms)) {
        fprintf(stderr, "Failed to initialize profiler for PID %d\n", target_pid);
        return 1;
    }
    g_state.duration_s = duration_s;
    g_state.continuous = continuous;
    
    if (!quiet) print_header();
    
    /* Main loop */
    int sample_num = 0;
    int max_samples = continuous ? 0x7FFFFFFF : (duration_s * 1000 / interval_ms);
    
    while (g_running && sample_num < max_samples) {
        if (take_sample()) {
            Sample* s = &g_state.samples[g_state.sample_count - 1];
            if (quiet) {
                print_status_line(s);
            } else {
                print_sample(s);
            }
        } else {
            fprintf(stderr, "\n[Profiler] Process %d terminated or inaccessible.\n", target_pid);
            break;
        }
        
        usleep(interval_ms * 1000);
        sample_num++;
    }
    
    if (quiet) printf("\n");
    print_summary();
    
    return 0;
}
