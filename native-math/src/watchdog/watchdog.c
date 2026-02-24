/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "simjot_native.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <time.h>

#ifdef _WIN32
    #include <windows.h>
    #include <process.h>
    #define THREAD_RET unsigned __stdcall
    #define THREAD_CALL __stdcall
#else
    #include <pthread.h>
    #include <unistd.h>
    #include <signal.h>
    #include <sys/types.h>
    #define THREAD_RET void*
    #define THREAD_CALL
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * CONSTANTS & STATE
 * ═══════════════════════════════════════════════════════════════════════════ */

#define MAX_WATCHDOGS 8
#define WATCHDOG_MAGIC 0x57444F47  /* "WDOG" */

typedef enum {
    WD_STATE_INACTIVE = 0,
    WD_STATE_RUNNING = 1,
    WD_STATE_TRIGGERED = 2,
    WD_STATE_CANCELLED = 3
} WatchdogState;

typedef enum {
    WD_ACTION_NONE = 0,
    WD_ACTION_CALLBACK = 1,
    WD_ACTION_EXIT = 2,
    WD_ACTION_HALT = 3
} WatchdogAction;

typedef struct {
    uint32_t magic;
    int32_t id;
    WatchdogState state;
    WatchdogAction action;
    int64_t timeout_ms;
    int64_t start_time_ms;
    const char* name;
    void (*callback)(int32_t id, void* user_data);
    void* user_data;
#ifdef _WIN32
    HANDLE thread_handle;
    HANDLE cancel_event;
#else
    pthread_t thread;
    volatile int cancel_flag;
#endif
} Watchdog;

static Watchdog watchdogs[MAX_WATCHDOGS];
static int watchdog_initialized = 0;

#ifdef _WIN32
static CRITICAL_SECTION watchdog_lock;
#else
static pthread_mutex_t watchdog_lock = PTHREAD_MUTEX_INITIALIZER;
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * HELPER FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

static int64_t current_time_ms(void) {
#ifdef _WIN32
    FILETIME ft;
    GetSystemTimeAsFileTime(&ft);
    uint64_t t = ((uint64_t)ft.dwHighDateTime << 32) | ft.dwLowDateTime;
    return (int64_t)(t / 10000 - 11644473600000LL);
#else
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
#endif
}

static void lock_watchdogs(void) {
#ifdef _WIN32
    EnterCriticalSection(&watchdog_lock);
#else
    pthread_mutex_lock(&watchdog_lock);
#endif
}

static void unlock_watchdogs(void) {
#ifdef _WIN32
    LeaveCriticalSection(&watchdog_lock);
#else
    pthread_mutex_unlock(&watchdog_lock);
#endif
}

static void init_watchdog_system(void) {
    if (watchdog_initialized) return;
#ifdef _WIN32
    InitializeCriticalSection(&watchdog_lock);
#endif
    memset(watchdogs, 0, sizeof(watchdogs));
    watchdog_initialized = 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * WATCHDOG THREAD
 * ═══════════════════════════════════════════════════════════════════════════ */

#ifdef _WIN32
static THREAD_RET THREAD_CALL watchdog_thread_func(void* arg) {
    Watchdog* wd = (Watchdog*)arg;
    
    DWORD wait_ms = (DWORD)wd->timeout_ms;
    DWORD result = WaitForSingleObject(wd->cancel_event, wait_ms);
    
    lock_watchdogs();
    if (result == WAIT_TIMEOUT && wd->state == WD_STATE_RUNNING) {
        wd->state = WD_STATE_TRIGGERED;
        unlock_watchdogs();
        
        fprintf(stderr, "[Watchdog] '%s' triggered after %lld ms\n", 
                wd->name ? wd->name : "unnamed", (long long)wd->timeout_ms);
        
        switch (wd->action) {
            case WD_ACTION_CALLBACK:
                if (wd->callback) wd->callback(wd->id, wd->user_data);
                break;
            case WD_ACTION_EXIT:
                exit(1);
                break;
            case WD_ACTION_HALT:
                TerminateProcess(GetCurrentProcess(), 1);
                break;
            default:
                break;
        }
    } else {
        if (wd->state == WD_STATE_RUNNING) {
            wd->state = WD_STATE_CANCELLED;
        }
        unlock_watchdogs();
    }
    
    return 0;
}
#else
static THREAD_RET watchdog_thread_func(void* arg) {
    Watchdog* wd = (Watchdog*)arg;
    
    int64_t end_time = wd->start_time_ms + wd->timeout_ms;
    
    while (!wd->cancel_flag) {
        int64_t now = current_time_ms();
        if (now >= end_time) {
            lock_watchdogs();
            if (wd->state == WD_STATE_RUNNING && !wd->cancel_flag) {
                wd->state = WD_STATE_TRIGGERED;
                unlock_watchdogs();
                
                fprintf(stderr, "[Watchdog] '%s' triggered after %lld ms\n", 
                        wd->name ? wd->name : "unnamed", (long long)wd->timeout_ms);
                
                switch (wd->action) {
                    case WD_ACTION_CALLBACK:
                        if (wd->callback) wd->callback(wd->id, wd->user_data);
                        break;
                    case WD_ACTION_EXIT:
                        _exit(1);
                        break;
                    case WD_ACTION_HALT:
                        kill(getpid(), SIGKILL);
                        break;
                    default:
                        break;
                }
                return NULL;
            }
            unlock_watchdogs();
            return NULL;
        }
        
        /* Sleep in small increments to check cancel flag */
        int64_t remaining = end_time - now;
        int sleep_ms = remaining > 100 ? 100 : (int)remaining;
        usleep(sleep_ms * 1000);
    }
    
    lock_watchdogs();
    if (wd->state == WD_STATE_RUNNING) {
        wd->state = WD_STATE_CANCELLED;
    }
    unlock_watchdogs();
    
    return NULL;
}
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Start a watchdog timer that triggers after timeout_ms.
 * 
 * @param timeout_ms Timeout in milliseconds
 * @param action Action to take: 0=none, 1=callback, 2=exit, 3=halt
 * @param name Optional name for logging (can be NULL)
 * @return Watchdog ID (0-7), or -1 on error
 */
int32_t simjot_watchdog_start(int64_t timeout_ms, int32_t action, const char* name) {
    init_watchdog_system();
    
    if (timeout_ms <= 0) return -1;
    if (action < 0 || action > 3) action = WD_ACTION_HALT;
    
    lock_watchdogs();
    
    /* Find free slot */
    int slot = -1;
    for (int i = 0; i < MAX_WATCHDOGS; i++) {
        if (watchdogs[i].state == WD_STATE_INACTIVE || 
            watchdogs[i].state == WD_STATE_TRIGGERED ||
            watchdogs[i].state == WD_STATE_CANCELLED) {
            slot = i;
            break;
        }
    }
    
    if (slot < 0) {
        unlock_watchdogs();
        return -1;
    }
    
    Watchdog* wd = &watchdogs[slot];
    memset(wd, 0, sizeof(Watchdog));
    wd->magic = WATCHDOG_MAGIC;
    wd->id = slot;
    wd->state = WD_STATE_RUNNING;
    wd->action = (WatchdogAction)action;
    wd->timeout_ms = timeout_ms;
    wd->start_time_ms = current_time_ms();
    wd->name = name;
    
#ifdef _WIN32
    wd->cancel_event = CreateEvent(NULL, TRUE, FALSE, NULL);
    wd->thread_handle = (HANDLE)_beginthreadex(NULL, 0, watchdog_thread_func, wd, 0, NULL);
#else
    wd->cancel_flag = 0;
    pthread_create(&wd->thread, NULL, watchdog_thread_func, wd);
#endif
    
    unlock_watchdogs();
    return slot;
}

/**
 * Cancel a running watchdog.
 * 
 * @param id Watchdog ID returned from start
 * @return 1 if cancelled, 0 if not found or already triggered
 */
int32_t simjot_watchdog_cancel(int32_t id) {
    if (id < 0 || id >= MAX_WATCHDOGS) return 0;
    
    lock_watchdogs();
    
    Watchdog* wd = &watchdogs[id];
    if (wd->magic != WATCHDOG_MAGIC || wd->state != WD_STATE_RUNNING) {
        unlock_watchdogs();
        return 0;
    }
    
#ifdef _WIN32
    SetEvent(wd->cancel_event);
#else
    wd->cancel_flag = 1;
#endif
    
    unlock_watchdogs();
    
    /* Wait for thread to finish */
#ifdef _WIN32
    WaitForSingleObject(wd->thread_handle, 1000);
    CloseHandle(wd->thread_handle);
    CloseHandle(wd->cancel_event);
#else
    pthread_join(wd->thread, NULL);
#endif
    
    return 1;
}

/**
 * Reset a watchdog timer (restart countdown).
 * 
 * @param id Watchdog ID
 * @return 1 if reset, 0 if not found
 */
int32_t simjot_watchdog_reset(int32_t id) {
    if (id < 0 || id >= MAX_WATCHDOGS) return 0;
    
    lock_watchdogs();
    
    Watchdog* wd = &watchdogs[id];
    if (wd->magic != WATCHDOG_MAGIC || wd->state != WD_STATE_RUNNING) {
        unlock_watchdogs();
        return 0;
    }
    
    wd->start_time_ms = current_time_ms();
    unlock_watchdogs();
    return 1;
}

/**
 * Get watchdog state.
 * 
 * @param id Watchdog ID
 * @return State: 0=inactive, 1=running, 2=triggered, 3=cancelled, -1=invalid
 */
int32_t simjot_watchdog_state(int32_t id) {
    if (id < 0 || id >= MAX_WATCHDOGS) return -1;
    
    lock_watchdogs();
    Watchdog* wd = &watchdogs[id];
    int32_t state = (wd->magic == WATCHDOG_MAGIC) ? (int32_t)wd->state : -1;
    unlock_watchdogs();
    return state;
}

/**
 * Get remaining time for a watchdog.
 * 
 * @param id Watchdog ID
 * @return Remaining milliseconds, or -1 if not running
 */
int64_t simjot_watchdog_remaining(int32_t id) {
    if (id < 0 || id >= MAX_WATCHDOGS) return -1;
    
    lock_watchdogs();
    Watchdog* wd = &watchdogs[id];
    if (wd->magic != WATCHDOG_MAGIC || wd->state != WD_STATE_RUNNING) {
        unlock_watchdogs();
        return -1;
    }
    
    int64_t elapsed = current_time_ms() - wd->start_time_ms;
    int64_t remaining = wd->timeout_ms - elapsed;
    unlock_watchdogs();
    
    return remaining > 0 ? remaining : 0;
}

/**
 * Force immediate process halt (for emergency shutdown).
 */
void simjot_force_halt(void) {
#ifdef _WIN32
    TerminateProcess(GetCurrentProcess(), 1);
#else
    kill(getpid(), SIGKILL);
#endif
}

/**
 * Get current monotonic time in milliseconds.
 */
int64_t simjot_monotonic_time_ms(void) {
    return current_time_ms();
}
