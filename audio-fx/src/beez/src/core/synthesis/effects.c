#include "effects.h"
#include <math.h>
#include <string.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

void effects_init(ChannelEffects* fx) {
    if (!fx) return;
    memset(fx, 0, sizeof(ChannelEffects));
    fx->arp_speed = 1;
}

void effects_reset(ChannelEffects* fx) {
    if (!fx) return;
    fx->arp_enabled = false;
    fx->vibrato_enabled = false;
    fx->portamento_enabled = false;
    fx->volume_slide_enabled = false;
    fx->tremolo_enabled = false;
    fx->retrigger_rate = 0;
    fx->note_cut_tick = -1;
    fx->note_delay_tick = -1;
    fx->current_tick = 0;
    fx->pitch_envelope_current = 0.0f;
}

void effects_set_arpeggio(ChannelEffects* fx, int note1, int note2, int speed) {
    if (!fx) return;
    fx->arp_enabled = (note1 != 0 || note2 != 0);
    fx->arp_notes[0] = 0;
    fx->arp_notes[1] = note1;
    fx->arp_notes[2] = note2;
    fx->arp_index = 0;
    fx->arp_speed = speed > 0 ? speed : 1;
    fx->arp_counter = 0;
}

void effects_set_vibrato(ChannelEffects* fx, float depth, float speed) {
    if (!fx) return;
    fx->vibrato_enabled = (depth > 0.0f);
    fx->vibrato_depth = depth;
    fx->vibrato_speed = speed;
    if (!fx->vibrato_enabled) {
        fx->vibrato_phase = 0.0f;
    }
}

void effects_set_portamento(ChannelEffects* fx, float target_freq, float speed) {
    if (!fx) return;
    fx->portamento_enabled = true;
    fx->portamento_target = target_freq;
    fx->portamento_speed = speed;
}

void effects_set_volume_slide(ChannelEffects* fx, float speed) {
    if (!fx) return;
    fx->volume_slide_enabled = (speed != 0.0f);
    fx->volume_slide_speed = speed;
}

void effects_set_tremolo(ChannelEffects* fx, float depth, float speed) {
    if (!fx) return;
    fx->tremolo_enabled = (depth > 0.0f);
    fx->tremolo_depth = depth;
    fx->tremolo_speed = speed;
}

void effects_set_retrigger(ChannelEffects* fx, int rate) {
    if (!fx) return;
    fx->retrigger_rate = rate;
    fx->retrigger_counter = 0;
}

void effects_set_note_cut(ChannelEffects* fx, int tick) {
    if (!fx) return;
    fx->note_cut_tick = tick;
}

void effects_set_note_delay(ChannelEffects* fx, int tick) {
    if (!fx) return;
    fx->note_delay_tick = tick;
}

void effects_set_pitch_envelope(ChannelEffects* fx, float amount, float speed) {
    if (!fx) return;
    fx->pitch_envelope_amount = amount;
    fx->pitch_envelope_speed = speed;
    fx->pitch_envelope_current = amount;
}

void effects_process_tick(ChannelEffects* fx) {
    if (!fx) return;
    fx->current_tick++;
    
    if (fx->arp_enabled) {
        fx->arp_counter++;
        if (fx->arp_counter >= fx->arp_speed) {
            fx->arp_counter = 0;
            fx->arp_index = (fx->arp_index + 1) % 3;
        }
    }
    
    if (fx->vibrato_enabled) {
        fx->vibrato_phase += fx->vibrato_speed * 0.01f;
        if (fx->vibrato_phase >= 1.0f) {
            fx->vibrato_phase -= 1.0f;
        }
    }
    
    if (fx->tremolo_enabled) {
        fx->tremolo_phase += fx->tremolo_speed * 0.01f;
        if (fx->tremolo_phase >= 1.0f) {
            fx->tremolo_phase -= 1.0f;
        }
    }
    
    if (fx->retrigger_rate > 0) {
        fx->retrigger_counter++;
    }
    
    if (fx->pitch_envelope_current != 0.0f) {
        if (fx->pitch_envelope_current > 0) {
            fx->pitch_envelope_current -= fx->pitch_envelope_speed;
            if (fx->pitch_envelope_current < 0) fx->pitch_envelope_current = 0;
        } else {
            fx->pitch_envelope_current += fx->pitch_envelope_speed;
            if (fx->pitch_envelope_current > 0) fx->pitch_envelope_current = 0;
        }
    }
}

float effects_get_frequency_offset(const ChannelEffects* fx, float base_freq) {
    float offset = 0.0f;
    
    if (fx && fx->vibrato_enabled) {
        float vib = sinf(fx->vibrato_phase * 2.0f * (float)M_PI);
        offset += base_freq * vib * fx->vibrato_depth * 0.01f;
    }
    
    if (fx) {
        offset += fx->pitch_envelope_current;
    }
    
    return offset;
}

float effects_get_volume_multiplier(const ChannelEffects* fx) {
    float mult = 1.0f;
    
    if (fx && fx->tremolo_enabled) {
        float trem = sinf(fx->tremolo_phase * 2.0f * (float)M_PI);
        mult *= 1.0f - (trem * 0.5f + 0.5f) * fx->tremolo_depth;
    }
    
    return mult;
}

int effects_get_arpeggio_semitones(const ChannelEffects* fx) {
    if (!fx || !fx->arp_enabled) return 0;
    return fx->arp_notes[fx->arp_index];
}

bool effects_should_trigger(const ChannelEffects* fx) {
    if (!fx) return false;
    if (fx->retrigger_rate > 0 && fx->retrigger_counter >= fx->retrigger_rate) {
        return true;
    }
    if (fx->note_delay_tick >= 0 && fx->current_tick == fx->note_delay_tick) {
        return true;
    }
    return false;
}

bool effects_should_cut(const ChannelEffects* fx) {
    if (!fx) return false;
    return fx->note_cut_tick >= 0 && fx->current_tick >= fx->note_cut_tick;
}
