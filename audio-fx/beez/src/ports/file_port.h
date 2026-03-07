#pragma once

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct FilePort FilePort;

struct FilePort {
    bool (*read_binary)(FilePort* port, const char* path, uint8_t** data, size_t* size);
    bool (*write_binary)(FilePort* port, const char* path, const uint8_t* data, size_t size);
    bool (*read_text)(FilePort* port, const char* path, char** text);
    bool (*write_text)(FilePort* port, const char* path, const char* text);
    bool (*exists)(FilePort* port, const char* path);
    void (*free_data)(FilePort* port, void* data);
    void* impl;
};

#ifdef __cplusplus
}
#endif
