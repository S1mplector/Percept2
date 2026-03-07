#pragma once

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    FX_NONE = 0x00,
    FX_ARPEGGIO = 0x00,
    FX_PORTAMENTO_UP = 0x01,
    FX_PORTAMENTO_DOWN = 0x02,
    FX_TONE_PORTAMENTO = 0x03,
    FX_VIBRATO = 0x04,
    FX_VOLUME_SLIDE = 0x0A,
    FX_JUMP_TO_ORDER = 0x0B,
    FX_SET_VOLUME = 0x0C,
    FX_PATTERN_BREAK = 0x0D,
    FX_SET_SPEED = 0x0F,
    FX_SET_DUTY_CYCLE = 0x10,
    FX_RETRIGGER = 0x11,
    FX_NOTE_CUT = 0x12,
    FX_NOTE_DELAY = 0x13,
    FX_PITCH_ENVELOPE = 0x14
} EffectType;

typedef struct {
    bool arp_enabled;
    int arp_notes[3];
    int arp_index;
    int arp_speed;
    int arp_counter;
    
    bool vibrato_enabled;
    float vibrato_depth;
    float vibrato_speed;
    float vibrato_phase;
    
    bool portamento_enabled;
    float portamento_target;
    float portamento_speed;
    
    bool volume_slide_enabled;
    float volume_slide_speed;
    
    bool tremolo_enabled;
    float tremolo_depth;
    float tremolo_speed;
    float tremolo_phase;
    
    int retrigger_rate;
    int retrigger_counter;
    
    int note_cut_tick;
    int note_delay_tick;
    int current_tick;
    
    float pitch_envelope_amount;
    float pitch_envelope_speed;
    float pitch_envelope_current;
} ChannelEffects;

void effects_init(ChannelEffects* fx);
void effects_reset(ChannelEffects* fx);

void effects_set_arpeggio(ChannelEffects* fx, int note1, int note2, int speed);
void effects_set_vibrato(ChannelEffects* fx, float depth, float speed);
void effects_set_portamento(ChannelEffects* fx, float target_freq, float speed);
void effects_set_volume_slide(ChannelEffects* fx, float speed);
void effects_set_tremolo(ChannelEffects* fx, float depth, float speed);
void effects_set_retrigger(ChannelEffects* fx, int rate);
void effects_set_note_cut(ChannelEffects* fx, int tick);
void effects_set_note_delay(ChannelEffects* fx, int tick);
void effects_set_pitch_envelope(ChannelEffects* fx, float amount, float speed);

void effects_process_tick(ChannelEffects* fx);
float effects_get_frequency_offset(const ChannelEffects* fx, float base_freq);
float effects_get_volume_multiplier(const ChannelEffects* fx);
int effects_get_arpeggio_semitones(const ChannelEffects* fx);
bool effects_should_trigger(const ChannelEffects* fx);
bool effects_should_cut(const ChannelEffects* fx);

#ifdef __cplusplus
}
#endif
