#pragma once

#include "../../ports/audio_port.h"

#ifdef __cplusplus
extern "C" {
#endif

AudioPort* miniaudio_adapter_create(void);
void miniaudio_adapter_destroy(AudioPort* port);

#ifdef __cplusplus
}
#endif
