#pragma once

#include "../../ports/file_port.h"

#ifdef __cplusplus
extern "C" {
#endif

FilePort* std_file_adapter_create(void);
void std_file_adapter_destroy(FilePort* port);

#ifdef __cplusplus
}
#endif
