/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file memory_pool.c
 * @brief Memory Pool Allocators for Simjot
 * 
 * High-performance memory allocation strategies for frequent allocations.
 * Supports fixed-size pools, arena allocators, and string interning.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

/* ═══════════════════════════════════════════════════════════════════════════
 * FIXED-SIZE BLOCK POOL
 * ═══════════════════════════════════════════════════════════════════════════ */

#define MAX_POOLS 16
#define POOL_BLOCK_COUNT 4096

typedef struct BlockPool {
    void* memory;
    void* free_list;
    int32_t block_size;
    int32_t total_blocks;
    int32_t used_blocks;
    bool initialized;
} BlockPool;

static BlockPool g_pools[MAX_POOLS];

/**
 * @brief Create a fixed-size block pool
 */
int32_t simjot_pool_create(int32_t block_size, int32_t initial_blocks) {
    if (block_size <= 0) return -1;
    if (initial_blocks <= 0) initial_blocks = POOL_BLOCK_COUNT;
    
    /* Find free pool slot */
    int32_t pool_id = -1;
    for (int i = 0; i < MAX_POOLS; i++) {
        if (!g_pools[i].initialized) {
            pool_id = i;
            break;
        }
    }
    if (pool_id < 0) return -1;
    
    BlockPool* pool = &g_pools[pool_id];
    
    /* Ensure block size can hold a pointer for free list */
    if (block_size < (int32_t)sizeof(void*)) {
        block_size = (int32_t)sizeof(void*);
    }
    
    /* Allocate pool memory */
    size_t total_size = (size_t)block_size * initial_blocks;
    pool->memory = malloc(total_size);
    if (!pool->memory) return -1;
    
    pool->block_size = block_size;
    pool->total_blocks = initial_blocks;
    pool->used_blocks = 0;
    
    /* Build free list */
    pool->free_list = pool->memory;
    uint8_t* current = (uint8_t*)pool->memory;
    for (int32_t i = 0; i < initial_blocks - 1; i++) {
        void** next_ptr = (void**)current;
        *next_ptr = current + block_size;
        current += block_size;
    }
    *((void**)current) = NULL;
    
    pool->initialized = true;
    return pool_id;
}

/**
 * @brief Allocate block from pool
 */
void* simjot_pool_alloc(int32_t pool_id) {
    if (pool_id < 0 || pool_id >= MAX_POOLS) return NULL;
    
    BlockPool* pool = &g_pools[pool_id];
    if (!pool->initialized || !pool->free_list) return NULL;
    
    void* block = pool->free_list;
    pool->free_list = *((void**)block);
    pool->used_blocks++;
    
    return block;
}

/**
 * @brief Free block back to pool
 */
void simjot_pool_free(int32_t pool_id, void* ptr) {
    if (pool_id < 0 || pool_id >= MAX_POOLS || !ptr) return;
    
    BlockPool* pool = &g_pools[pool_id];
    if (!pool->initialized) return;
    
    *((void**)ptr) = pool->free_list;
    pool->free_list = ptr;
    pool->used_blocks--;
}

/**
 * @brief Get pool statistics
 */
void simjot_pool_stats(int32_t pool_id, int32_t* total, int32_t* used, int32_t* block_size) {
    if (pool_id < 0 || pool_id >= MAX_POOLS) return;
    
    BlockPool* pool = &g_pools[pool_id];
    if (!pool->initialized) return;
    
    if (total) *total = pool->total_blocks;
    if (used) *used = pool->used_blocks;
    if (block_size) *block_size = pool->block_size;
}

/**
 * @brief Destroy pool
 */
void simjot_pool_destroy(int32_t pool_id) {
    if (pool_id < 0 || pool_id >= MAX_POOLS) return;
    
    BlockPool* pool = &g_pools[pool_id];
    if (pool->initialized && pool->memory) {
        free(pool->memory);
    }
    memset(pool, 0, sizeof(BlockPool));
}

/* ═══════════════════════════════════════════════════════════════════════════
 * ARENA ALLOCATOR
 * ═══════════════════════════════════════════════════════════════════════════ */

#define MAX_ARENAS 8
#define ARENA_CHUNK_SIZE (1024 * 1024)  /* 1MB chunks */

typedef struct ArenaChunk {
    uint8_t* memory;
    size_t size;
    size_t used;
    struct ArenaChunk* next;
} ArenaChunk;

typedef struct Arena {
    ArenaChunk* head;
    ArenaChunk* current;
    size_t total_allocated;
    size_t total_used;
    bool initialized;
} Arena;

static Arena g_arenas[MAX_ARENAS];

static ArenaChunk* arena_new_chunk(size_t min_size) {
    size_t size = min_size > ARENA_CHUNK_SIZE ? min_size : ARENA_CHUNK_SIZE;
    
    ArenaChunk* chunk = (ArenaChunk*)malloc(sizeof(ArenaChunk));
    if (!chunk) return NULL;
    
    chunk->memory = (uint8_t*)malloc(size);
    if (!chunk->memory) {
        free(chunk);
        return NULL;
    }
    
    chunk->size = size;
    chunk->used = 0;
    chunk->next = NULL;
    
    return chunk;
}

/**
 * @brief Create arena allocator
 */
int32_t simjot_arena_create(void) {
    int32_t arena_id = -1;
    for (int i = 0; i < MAX_ARENAS; i++) {
        if (!g_arenas[i].initialized) {
            arena_id = i;
            break;
        }
    }
    if (arena_id < 0) return -1;
    
    Arena* arena = &g_arenas[arena_id];
    
    arena->head = arena_new_chunk(ARENA_CHUNK_SIZE);
    if (!arena->head) return -1;
    
    arena->current = arena->head;
    arena->total_allocated = arena->head->size;
    arena->total_used = 0;
    arena->initialized = true;
    
    return arena_id;
}

/**
 * @brief Allocate from arena (bump allocator, no individual free)
 */
void* simjot_arena_alloc(int32_t arena_id, int32_t size, int32_t alignment) {
    if (arena_id < 0 || arena_id >= MAX_ARENAS || size <= 0) return NULL;
    
    Arena* arena = &g_arenas[arena_id];
    if (!arena->initialized) return NULL;
    
    if (alignment <= 0) alignment = 8;
    
    /* Find chunk with enough space */
    ArenaChunk* chunk = arena->current;
    
    /* Align current position */
    size_t aligned_used = (chunk->used + alignment - 1) & ~(size_t)(alignment - 1);
    
    if (aligned_used + size > chunk->size) {
        /* Need new chunk */
        ArenaChunk* new_chunk = arena_new_chunk((size_t)size);
        if (!new_chunk) return NULL;
        
        chunk->next = new_chunk;
        arena->current = new_chunk;
        arena->total_allocated += new_chunk->size;
        chunk = new_chunk;
        aligned_used = 0;
    }
    
    void* ptr = chunk->memory + aligned_used;
    chunk->used = aligned_used + size;
    arena->total_used += size;
    
    return ptr;
}

/**
 * @brief Allocate and copy string into arena
 */
const char* simjot_arena_strdup(int32_t arena_id, const char* str) {
    if (!str) return NULL;
    
    size_t len = strlen(str) + 1;
    char* copy = (char*)simjot_arena_alloc(arena_id, (int32_t)len, 1);
    if (copy) {
        memcpy(copy, str, len);
    }
    return copy;
}

/**
 * @brief Reset arena (keep memory, reset position)
 */
void simjot_arena_reset(int32_t arena_id) {
    if (arena_id < 0 || arena_id >= MAX_ARENAS) return;
    
    Arena* arena = &g_arenas[arena_id];
    if (!arena->initialized) return;
    
    /* Reset all chunks */
    for (ArenaChunk* chunk = arena->head; chunk; chunk = chunk->next) {
        chunk->used = 0;
    }
    arena->current = arena->head;
    arena->total_used = 0;
}

/**
 * @brief Destroy arena
 */
void simjot_arena_destroy(int32_t arena_id) {
    if (arena_id < 0 || arena_id >= MAX_ARENAS) return;
    
    Arena* arena = &g_arenas[arena_id];
    if (!arena->initialized) return;
    
    ArenaChunk* chunk = arena->head;
    while (chunk) {
        ArenaChunk* next = chunk->next;
        free(chunk->memory);
        free(chunk);
        chunk = next;
    }
    
    memset(arena, 0, sizeof(Arena));
}

/**
 * @brief Get arena statistics
 */
void simjot_arena_stats(int32_t arena_id, int64_t* total_allocated, int64_t* total_used) {
    if (arena_id < 0 || arena_id >= MAX_ARENAS) return;
    
    Arena* arena = &g_arenas[arena_id];
    if (!arena->initialized) return;
    
    if (total_allocated) *total_allocated = (int64_t)arena->total_allocated;
    if (total_used) *total_used = (int64_t)arena->total_used;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STRING INTERNING
 * ═══════════════════════════════════════════════════════════════════════════ */

#define INTERN_TABLE_SIZE 4096
#define MAX_INTERNED_LEN 256

typedef struct InternEntry {
    const char* str;
    uint32_t hash;
    struct InternEntry* next;
} InternEntry;

static InternEntry* g_intern_table[INTERN_TABLE_SIZE];
static int32_t g_intern_arena = -1;
static int32_t g_intern_count = 0;

static uint32_t intern_hash(const char* str) {
    uint32_t hash = 5381;
    int c;
    while ((c = (unsigned char)*str++)) {
        hash = ((hash << 5) + hash) + c;
    }
    return hash;
}

/**
 * @brief Initialize string intern table
 */
int32_t simjot_intern_init(void) {
    if (g_intern_arena >= 0) return 0;
    
    g_intern_arena = simjot_arena_create();
    if (g_intern_arena < 0) return -1;
    
    memset(g_intern_table, 0, sizeof(g_intern_table));
    g_intern_count = 0;
    
    return 0;
}

/**
 * @brief Intern a string (returns canonical pointer)
 */
const char* simjot_intern(const char* str) {
    if (!str || g_intern_arena < 0) return str;
    
    size_t len = strlen(str);
    if (len > MAX_INTERNED_LEN) return str;  /* Don't intern long strings */
    
    uint32_t hash = intern_hash(str);
    uint32_t bucket = hash % INTERN_TABLE_SIZE;
    
    /* Search existing */
    for (InternEntry* e = g_intern_table[bucket]; e; e = e->next) {
        if (e->hash == hash && strcmp(e->str, str) == 0) {
            return e->str;
        }
    }
    
    /* Add new entry */
    InternEntry* entry = (InternEntry*)simjot_arena_alloc(g_intern_arena, sizeof(InternEntry), 8);
    if (!entry) return str;
    
    entry->str = simjot_arena_strdup(g_intern_arena, str);
    if (!entry->str) return str;
    
    entry->hash = hash;
    entry->next = g_intern_table[bucket];
    g_intern_table[bucket] = entry;
    g_intern_count++;
    
    return entry->str;
}

/**
 * @brief Check if string is interned
 */
int32_t simjot_intern_contains(const char* str) {
    if (!str || g_intern_arena < 0) return 0;
    
    uint32_t hash = intern_hash(str);
    uint32_t bucket = hash % INTERN_TABLE_SIZE;
    
    for (InternEntry* e = g_intern_table[bucket]; e; e = e->next) {
        if (e->hash == hash && strcmp(e->str, str) == 0) {
            return 1;
        }
    }
    return 0;
}

/**
 * @brief Get intern table count
 */
int32_t simjot_intern_count(void) {
    return g_intern_count;
}

/**
 * @brief Clear intern table
 */
void simjot_intern_clear(void) {
    if (g_intern_arena >= 0) {
        simjot_arena_destroy(g_intern_arena);
        g_intern_arena = -1;
    }
    memset(g_intern_table, 0, sizeof(g_intern_table));
    g_intern_count = 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * SCRATCH BUFFER
 * ═══════════════════════════════════════════════════════════════════════════ */

#define SCRATCH_SIZE (64 * 1024)  /* 64KB scratch buffer */

static uint8_t g_scratch_buffer[SCRATCH_SIZE];
static size_t g_scratch_used = 0;

/**
 * @brief Get temporary scratch memory (reset on each call)
 */
void* simjot_scratch_alloc(int32_t size) {
    if (size <= 0 || (size_t)size > SCRATCH_SIZE) return NULL;
    
    /* Always allocate from start for simplicity */
    g_scratch_used = (size_t)size;
    return g_scratch_buffer;
}

/**
 * @brief Reset scratch buffer
 */
void simjot_scratch_reset(void) {
    g_scratch_used = 0;
}

/**
 * @brief Get scratch buffer capacity
 */
int32_t simjot_scratch_capacity(void) {
    return SCRATCH_SIZE;
}
