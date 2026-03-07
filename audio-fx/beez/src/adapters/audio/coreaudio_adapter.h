#pragma once

#include "../../ports/audio_port.h"

#ifdef __cplusplus
extern "C" {
#endif

AudioPort* coreaudio_adapter_create(void);
void coreaudio_adapter_destroy(AudioPort* port);

#ifdef __cplusplus
}
#endif
