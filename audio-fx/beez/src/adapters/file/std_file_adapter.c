#include "std_file_adapter.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static bool std_read_binary(FilePort* port, const char* path, uint8_t** data, size_t* size) {
    (void)port;
    if (!path || !data || !size) return false;
    FILE* f = fopen(path, "rb");
    if (!f) return false;
    
    fseek(f, 0, SEEK_END);
    long len = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (len <= 0 || len > (long)SIZE_MAX) {
        fclose(f);
        return false;
    }
    
    *data = (uint8_t*)malloc((size_t)len);
    if (!*data) {
        fclose(f);
        return false;
    }
    
    *size = fread(*data, 1, (size_t)len, f);
    fclose(f);
    return true;
}

static bool std_write_binary(FilePort* port, const char* path, const uint8_t* data, size_t size) {
    (void)port;
    if (!path || (!data && size > 0)) return false;
    FILE* f = fopen(path, "wb");
    if (!f) return false;
    
    size_t written = fwrite(data, 1, size, f);
    fclose(f);
    return written == size;
}

static bool std_read_text(FilePort* port, const char* path, char** text) {
    uint8_t* data;
    size_t size;
    
    if (!path || !text) return false;
    if (!std_read_binary(port, path, &data, &size)) {
        return false;
    }
    
    *text = (char*)malloc(size + 1);
    if (!*text) {
        free(data);
        return false;
    }
    
    memcpy(*text, data, size);
    (*text)[size] = '\0';
    free(data);
    return true;
}

static bool std_write_text(FilePort* port, const char* path, const char* text) {
    if (!path || !text) return false;
    return std_write_binary(port, path, (const uint8_t*)text, strlen(text));
}

static bool std_exists(FilePort* port, const char* path) {
    (void)port;
    if (!path) return false;
    FILE* f = fopen(path, "r");
    if (f) {
        fclose(f);
        return true;
    }
    return false;
}

static void std_free_data(FilePort* port, void* data) {
    (void)port;
    free(data);
}

FilePort* std_file_adapter_create(void) {
    FilePort* port = (FilePort*)malloc(sizeof(FilePort));
    if (!port) return NULL;
    
    port->impl = NULL;
    port->read_binary = std_read_binary;
    port->write_binary = std_write_binary;
    port->read_text = std_read_text;
    port->write_text = std_write_text;
    port->exists = std_exists;
    port->free_data = std_free_data;
    
    return port;
}

void std_file_adapter_destroy(FilePort* port) {
    if (!port) return;
    free(port);
}
