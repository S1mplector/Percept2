/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file task_queue.cpp
 * @brief Concurrent Task Queue and Parallel Processing for Simjot
 * 
 * C++ implementation of thread-safe task queues, parallel processing
 * utilities, and lock-free data structures.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <deque>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {

/* ═══════════════════════════════════════════════════════════════════════════
 * THREAD-SAFE RING BUFFER
 * ═══════════════════════════════════════════════════════════════════════════ */

template<typename T, size_t Capacity>
class RingBuffer {
public:
    bool push(const T& item) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (size_ >= Capacity) return false;
        buffer_[(head_ + size_) % Capacity] = item;
        size_++;
        return true;
    }
    
    bool pop(T& item) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (size_ == 0) return false;
        item = buffer_[head_];
        head_ = (head_ + 1) % Capacity;
        size_--;
        return true;
    }
    
    size_t size() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return size_;
    }
    
    bool empty() const { return size() == 0; }
    bool full() const { return size() >= Capacity; }
    
private:
    mutable std::mutex mutex_;
    T buffer_[Capacity];
    size_t head_ = 0;
    size_t size_ = 0;
};

/* ═══════════════════════════════════════════════════════════════════════════
 * THREAD-SAFE DEQUE (for task queue)
 * ═══════════════════════════════════════════════════════════════════════════ */

template<typename T>
class ThreadSafeDeque {
public:
    void push_back(T item) {
        std::lock_guard<std::mutex> lock(mutex_);
        deque_.push_back(std::move(item));
        cv_.notify_one();
    }
    
    void push_front(T item) {
        std::lock_guard<std::mutex> lock(mutex_);
        deque_.push_front(std::move(item));
        cv_.notify_one();
    }
    
    bool try_pop_front(T& item) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (deque_.empty()) return false;
        item = std::move(deque_.front());
        deque_.pop_front();
        return true;
    }
    
    bool pop_front_wait(T& item, int timeout_ms) {
        std::unique_lock<std::mutex> lock(mutex_);
        if (!cv_.wait_for(lock, std::chrono::milliseconds(timeout_ms),
                          [this] { return !deque_.empty() || stopped_; })) {
            return false;
        }
        if (stopped_ && deque_.empty()) return false;
        item = std::move(deque_.front());
        deque_.pop_front();
        return true;
    }
    
    size_t size() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return deque_.size();
    }
    
    bool empty() const { return size() == 0; }
    
    void stop() {
        std::lock_guard<std::mutex> lock(mutex_);
        stopped_ = true;
        cv_.notify_all();
    }
    
    void clear() {
        std::lock_guard<std::mutex> lock(mutex_);
        deque_.clear();
    }
    
private:
    mutable std::mutex mutex_;
    std::condition_variable cv_;
    std::deque<T> deque_;
    bool stopped_ = false;
};

/* ═══════════════════════════════════════════════════════════════════════════
 * SIMPLE TASK STRUCTURE
 * ═══════════════════════════════════════════════════════════════════════════ */

struct Task {
    int32_t id;
    int32_t priority;
    int32_t status;  /* 0=pending, 1=running, 2=completed, 3=failed */
    std::string data;
    int64_t created_time;
    int64_t completed_time;
    std::string result;
};

/* Task queue */
static ThreadSafeDeque<Task> g_task_queue;
static std::vector<Task> g_completed_tasks;
static std::mutex g_completed_mutex;
static std::atomic<int32_t> g_next_task_id{1};
static std::atomic<bool> g_queue_running{false};

/* ═══════════════════════════════════════════════════════════════════════════
 * PARALLEL PROCESSING UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

static int32_t g_thread_count = 0;

void process_batch_parallel(const char** items, int32_t count,
                            void (*processor)(const char*, char*, int32_t),
                            char* results, int32_t result_size) {
    if (!items || count <= 0 || !processor || !results) return;
    
    int num_threads = g_thread_count > 0 ? g_thread_count : 
                      std::max(1u, std::thread::hardware_concurrency());
    
    if (num_threads > count) num_threads = count;
    
    std::vector<std::thread> threads;
    std::mutex result_mutex;
    int32_t items_per_thread = (count + num_threads - 1) / num_threads;
    
    int32_t result_offset = 0;
    
    for (int t = 0; t < num_threads; t++) {
        int32_t start = t * items_per_thread;
        int32_t end = std::min(start + items_per_thread, count);
        
        threads.emplace_back([&, start, end]() {
            char local_buf[4096];
            for (int32_t i = start; i < end; i++) {
                processor(items[i], local_buf, sizeof(local_buf));
                
                std::lock_guard<std::mutex> lock(result_mutex);
                int32_t len = static_cast<int32_t>(std::strlen(local_buf));
                if (result_offset + len + 1 < result_size) {
                    if (result_offset > 0) results[result_offset++] = '\n';
                    std::memcpy(results + result_offset, local_buf, len);
                    result_offset += len;
                }
            }
        });
    }
    
    for (auto& th : threads) {
        th.join();
    }
    
    results[result_offset] = '\0';
}

/* ═══════════════════════════════════════════════════════════════════════════
 * ATOMIC COUNTERS
 * ═══════════════════════════════════════════════════════════════════════════ */

static std::atomic<int64_t> g_atomic_counters[16];

} /* anonymous namespace */

extern "C" {

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - TASK QUEUE
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Create a new task and add to queue
 * @return Task ID
 */
int32_t simjot_task_create(const char* data, int32_t priority) {
    Task task;
    task.id = g_next_task_id++;
    task.priority = priority;
    task.status = 0;
    task.data = data ? data : "";
    task.created_time = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    task.completed_time = 0;
    
    g_task_queue.push_back(std::move(task));
    return task.id;
}

/**
 * @brief Get number of pending tasks
 */
int32_t simjot_task_pending_count(void) {
    return static_cast<int32_t>(g_task_queue.size());
}

/**
 * @brief Pop next task from queue
 * @return Task ID, or 0 if empty
 */
int32_t simjot_task_pop(char* data_out, int32_t data_len, int32_t timeout_ms) {
    Task task;
    if (!g_task_queue.pop_front_wait(task, timeout_ms)) {
        return 0;
    }
    
    if (data_out && data_len > 0) {
        std::strncpy(data_out, task.data.c_str(), data_len - 1);
        data_out[data_len - 1] = '\0';
    }
    
    return task.id;
}

/**
 * @brief Mark task as completed
 */
void simjot_task_complete(int32_t task_id, const char* result) {
    Task completed;
    completed.id = task_id;
    completed.status = 2;
    completed.result = result ? result : "";
    completed.completed_time = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    
    std::lock_guard<std::mutex> lock(g_completed_mutex);
    g_completed_tasks.push_back(std::move(completed));
}

/**
 * @brief Clear all tasks
 */
void simjot_task_clear(void) {
    g_task_queue.clear();
    std::lock_guard<std::mutex> lock(g_completed_mutex);
    g_completed_tasks.clear();
}

/**
 * @brief Stop task queue
 */
void simjot_task_stop(void) {
    g_queue_running = false;
    g_task_queue.stop();
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - PARALLEL PROCESSING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Set thread count for parallel operations
 */
void simjot_parallel_set_threads(int32_t count) {
    g_thread_count = count > 0 ? count : 0;
}

/**
 * @brief Get available hardware threads
 */
int32_t simjot_parallel_get_hw_threads(void) {
    return static_cast<int32_t>(std::thread::hardware_concurrency());
}

/**
 * @brief Sleep current thread
 */
void simjot_thread_sleep(int32_t milliseconds) {
    std::this_thread::sleep_for(std::chrono::milliseconds(milliseconds));
}

/**
 * @brief Get current thread ID (simple hash)
 */
int64_t simjot_thread_id(void) {
    return static_cast<int64_t>(std::hash<std::thread::id>{}(std::this_thread::get_id()));
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - ATOMIC OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Atomic increment
 */
int64_t simjot_atomic_inc(int32_t counter_id) {
    if (counter_id < 0 || counter_id >= 16) return 0;
    return ++g_atomic_counters[counter_id];
}

/**
 * @brief Atomic decrement
 */
int64_t simjot_atomic_dec(int32_t counter_id) {
    if (counter_id < 0 || counter_id >= 16) return 0;
    return --g_atomic_counters[counter_id];
}

/**
 * @brief Atomic get
 */
int64_t simjot_atomic_get(int32_t counter_id) {
    if (counter_id < 0 || counter_id >= 16) return 0;
    return g_atomic_counters[counter_id].load();
}

/**
 * @brief Atomic set
 */
void simjot_atomic_set(int32_t counter_id, int64_t value) {
    if (counter_id < 0 || counter_id >= 16) return;
    g_atomic_counters[counter_id] = value;
}

/**
 * @brief Atomic compare-and-swap
 */
int32_t simjot_atomic_cas(int32_t counter_id, int64_t expected, int64_t desired) {
    if (counter_id < 0 || counter_id >= 16) return 0;
    return g_atomic_counters[counter_id].compare_exchange_strong(expected, desired) ? 1 : 0;
}

/**
 * @brief Atomic add
 */
int64_t simjot_atomic_add(int32_t counter_id, int64_t value) {
    if (counter_id < 0 || counter_id >= 16) return 0;
    return g_atomic_counters[counter_id].fetch_add(value) + value;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - TIMING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Get high-resolution timestamp in nanoseconds
 */
int64_t simjot_hrtime_ns(void) {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::high_resolution_clock::now().time_since_epoch()).count();
}

/**
 * @brief Get monotonic timestamp in milliseconds
 */
int64_t simjot_monotonic_ms(void) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

} /* extern "C" */
