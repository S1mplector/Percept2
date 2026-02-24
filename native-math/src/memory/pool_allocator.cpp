/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/*
 * SIMJOT - High-Performance Memory Pool Allocator
 * Zero-fragmentation allocator for frequent small allocations
 */

#include "simjot_native.h"
#include <cstdlib>
#include <cstring>
#include <atomic>
#include <new>

extern "C" {

/* ═══════════════════════════════════════════════════════════════════════════
 * FIXED-SIZE BLOCK POOL
 * ═══════════════════════════════════════════════════════════════════════════ */

struct PoolBlock {
    PoolBlock* next;
};

struct MemoryPool {
    void* memory;
    PoolBlock* free_list;
    size_t block_size;
    size_t block_count;
    size_t allocated;
    std::atomic<int32_t> ref_count;
};

/**
 * Create a memory pool for fixed-size allocations (handle-based API).
 * NOTE: Renamed to avoid ABI conflict with memory/memory_pool.c (int32 ID-based API).
 * @param block_size Size of each block (min 8 bytes)
 * @param block_count Number of blocks to pre-allocate
 * @return Pool handle or NULL on failure
 */
void* simjot_pool_create_ex(int32_t block_size, int32_t block_count) {
    if (block_size < 8) block_size = 8;
    if (block_count <= 0) block_count = 1024;
    
    // Align to 8 bytes
    block_size = (block_size + 7) & ~7;
    
    MemoryPool* pool = new(std::nothrow) MemoryPool();
    if (!pool) return nullptr;
    
    pool->memory = aligned_alloc(64, (size_t)block_size * block_count);
    if (!pool->memory) {
        delete pool;
        return nullptr;
    }
    
    pool->block_size = block_size;
    pool->block_count = block_count;
    pool->allocated = 0;
    pool->ref_count = 1;
    
    // Build free list
    pool->free_list = nullptr;
    char* ptr = (char*)pool->memory;
    for (int32_t i = block_count - 1; i >= 0; i--) {
        PoolBlock* block = (PoolBlock*)(ptr + i * block_size);
        block->next = pool->free_list;
        pool->free_list = block;
    }
    
    return pool;
}

/**
 * Allocate a block from the pool.
 * @return Pointer to block or NULL if pool exhausted
 */
void* simjot_pool_alloc_ex(void* pool_handle) {
    if (!pool_handle) return nullptr;
    
    MemoryPool* pool = (MemoryPool*)pool_handle;
    
    if (!pool->free_list) return nullptr;
    
    PoolBlock* block = pool->free_list;
    pool->free_list = block->next;
    pool->allocated++;
    
    return block;
}

/**
 * Return a block to the pool.
 */
void simjot_pool_free_ex(void* pool_handle, void* ptr) {
    if (!pool_handle || !ptr) return;
    
    MemoryPool* pool = (MemoryPool*)pool_handle;
    
    PoolBlock* block = (PoolBlock*)ptr;
    block->next = pool->free_list;
    pool->free_list = block;
    pool->allocated--;
}

/**
 * Get pool statistics.
 * @param out_total Total blocks
 * @param out_allocated Currently allocated
 * @param out_block_size Size of each block
 */
void simjot_pool_stats_ex(void* pool_handle, int32_t* out_total, 
                          int32_t* out_allocated, int32_t* out_block_size) {
    if (!pool_handle) return;
    
    MemoryPool* pool = (MemoryPool*)pool_handle;
    
    if (out_total) *out_total = (int32_t)pool->block_count;
    if (out_allocated) *out_allocated = (int32_t)pool->allocated;
    if (out_block_size) *out_block_size = (int32_t)pool->block_size;
}

/**
 * Reset pool (free all allocations).
 */
void simjot_pool_reset_ex(void* pool_handle) {
    if (!pool_handle) return;
    
    MemoryPool* pool = (MemoryPool*)pool_handle;
    
    // Rebuild free list
    pool->free_list = nullptr;
    char* ptr = (char*)pool->memory;
    for (size_t i = pool->block_count; i > 0; i--) {
        PoolBlock* block = (PoolBlock*)(ptr + (i - 1) * pool->block_size);
        block->next = pool->free_list;
        pool->free_list = block;
    }
    pool->allocated = 0;
}

/**
 * Destroy pool and free all memory.
 */
void simjot_pool_destroy_ex(void* pool_handle) {
    if (!pool_handle) return;
    
    MemoryPool* pool = (MemoryPool*)pool_handle;
    
    if (--pool->ref_count == 0) {
        free(pool->memory);
        delete pool;
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * ARENA ALLOCATOR (Bump Allocator)
 * ═══════════════════════════════════════════════════════════════════════════ */

struct Arena {
    char* memory;
    size_t capacity;
    size_t used;
    Arena* next;  // For chained arenas
};

/**
 * Create an arena allocator.
 * @param initial_size Initial arena size (default 64KB)
 * @return Arena handle
 */
void* simjot_arena_create_ex(int32_t initial_size) {
    if (initial_size <= 0) initial_size = 65536;
    
    Arena* arena = new(std::nothrow) Arena();
    if (!arena) return nullptr;
    
    arena->memory = (char*)malloc(initial_size);
    if (!arena->memory) {
        delete arena;
        return nullptr;
    }
    
    arena->capacity = initial_size;
    arena->used = 0;
    arena->next = nullptr;
    
    return arena;
}

/**
 * Allocate from arena (bump pointer).
 * @param size Bytes to allocate
 * @param align Alignment (must be power of 2)
 * @return Pointer or NULL if failed
 */
void* simjot_arena_alloc_ex(void* arena_handle, int32_t size, int32_t align) {
    if (!arena_handle || size <= 0) return nullptr;
    if (align <= 0) align = 8;
    
    Arena* arena = (Arena*)arena_handle;
    
    // Find arena with space
    while (arena) {
        size_t aligned_used = (arena->used + align - 1) & ~(align - 1);
        
        if (aligned_used + size <= arena->capacity) {
            void* ptr = arena->memory + aligned_used;
            arena->used = aligned_used + size;
            return ptr;
        }
        
        // Try next arena or create new one
        if (!arena->next) {
            size_t new_size = arena->capacity * 2;
            if ((size_t)size > new_size) new_size = size * 2;
            
            Arena* new_arena = new(std::nothrow) Arena();
            if (!new_arena) return nullptr;
            
            new_arena->memory = (char*)malloc(new_size);
            if (!new_arena->memory) {
                delete new_arena;
                return nullptr;
            }
            
            new_arena->capacity = new_size;
            new_arena->used = 0;
            new_arena->next = nullptr;
            arena->next = new_arena;
        }
        
        arena = arena->next;
    }
    
    return nullptr;
}

/**
 * Allocate and zero-initialize from arena.
 */
void* simjot_arena_calloc_ex(void* arena_handle, int32_t count, int32_t size) {
    void* ptr = simjot_arena_alloc_ex(arena_handle, count * size, 8);
    if (ptr) {
        memset(ptr, 0, count * size);
    }
    return ptr;
}

/**
 * Duplicate string in arena.
 */
char* simjot_arena_strdup_ex(void* arena_handle, const char* str) {
    if (!str) return nullptr;
    
    size_t len = strlen(str) + 1;
    char* copy = (char*)simjot_arena_alloc_ex(arena_handle, (int32_t)len, 1);
    if (copy) {
        memcpy(copy, str, len);
    }
    return copy;
}

/**
 * Get arena usage statistics.
 */
void simjot_arena_stats_ex(void* arena_handle, int64_t* out_used, int64_t* out_capacity) {
    if (!arena_handle) return;
    
    Arena* arena = (Arena*)arena_handle;
    int64_t used = 0, capacity = 0;
    
    while (arena) {
        used += arena->used;
        capacity += arena->capacity;
        arena = arena->next;
    }
    
    if (out_used) *out_used = used;
    if (out_capacity) *out_capacity = capacity;
}

/**
 * Reset arena (keep memory, reset usage).
 */
void simjot_arena_reset_ex(void* arena_handle) {
    if (!arena_handle) return;
    
    Arena* arena = (Arena*)arena_handle;
    while (arena) {
        arena->used = 0;
        arena = arena->next;
    }
}

/**
 * Destroy arena and free all memory.
 */
void simjot_arena_destroy_ex(void* arena_handle) {
    if (!arena_handle) return;
    
    Arena* arena = (Arena*)arena_handle;
    while (arena) {
        Arena* next = arena->next;
        free(arena->memory);
        delete arena;
        arena = next;
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * SLAB ALLOCATOR (Multiple Size Classes)
 * ═══════════════════════════════════════════════════════════════════════════ */

#define SLAB_SIZE_CLASSES 8
static const int32_t SLAB_SIZES[SLAB_SIZE_CLASSES] = {16, 32, 64, 128, 256, 512, 1024, 2048};

struct SlabAllocator {
    void* pools[SLAB_SIZE_CLASSES];
};

/**
 * Create slab allocator with multiple size classes.
 */
void* simjot_slab_create(void) {
    SlabAllocator* slab = new(std::nothrow) SlabAllocator();
    if (!slab) return nullptr;
    
    for (int i = 0; i < SLAB_SIZE_CLASSES; i++) {
        slab->pools[i] = simjot_pool_create_ex(SLAB_SIZES[i], 256);
        if (!slab->pools[i]) {
            // Cleanup on failure
            for (int j = 0; j < i; j++) {
                simjot_pool_destroy_ex(slab->pools[j]);
            }
            delete slab;
            return nullptr;
        }
    }
    
    return slab;
}

/**
 * Allocate from slab (picks appropriate size class).
 */
void* simjot_slab_alloc(void* slab_handle, int32_t size) {
    if (!slab_handle || size <= 0) return nullptr;
    
    SlabAllocator* slab = (SlabAllocator*)slab_handle;
    
    // Find appropriate size class
    for (int i = 0; i < SLAB_SIZE_CLASSES; i++) {
        if (size <= SLAB_SIZES[i]) {
            void* ptr = simjot_pool_alloc_ex(slab->pools[i]);
            if (ptr) return ptr;
            // Pool exhausted, try next size
        }
    }
    
    // Too large for slab, use malloc
    return malloc(size);
}

/**
 * Free slab allocation.
 * @param size Original allocation size (needed to find pool)
 */
void simjot_slab_free(void* slab_handle, void* ptr, int32_t size) {
    if (!slab_handle || !ptr) return;
    
    SlabAllocator* slab = (SlabAllocator*)slab_handle;
    
    for (int i = 0; i < SLAB_SIZE_CLASSES; i++) {
        if (size <= SLAB_SIZES[i]) {
            simjot_pool_free_ex(slab->pools[i], ptr);
            return;
        }
    }
    
    // Was malloc'd
    free(ptr);
}

/**
 * Destroy slab allocator.
 */
void simjot_slab_destroy(void* slab_handle) {
    if (!slab_handle) return;
    
    SlabAllocator* slab = (SlabAllocator*)slab_handle;
    
    for (int i = 0; i < SLAB_SIZE_CLASSES; i++) {
        simjot_pool_destroy_ex(slab->pools[i]);
    }
    
    delete slab;
}

} // extern "C"
