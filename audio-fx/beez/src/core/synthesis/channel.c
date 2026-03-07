#include "channel.h"
#include <math.h>
#include <stddef.h>

float note_to_frequency(int note) {
    return 440.0f * powf(2.0f, (note - 69) / 12.0f);
}

int frequency_to_note(float freq) {
    if (freq <= 0.0f) return 0;
    return (int)(69.0f + 12.0f * log2f(freq / 440.0f) + 0.5f);
}

void channel_init(Channel* ch, OscillatorWaveform waveform) {
    if (!ch) return;
    oscillator_init(&ch->osc, waveform);
    envelope_init(&ch->env);
    effects_init(&ch->fx);
    effects_init(&ch->inst_fx);
    effects_reset(&ch->fx);
    effects_reset(&ch->inst_fx);
    filter_init(&ch->filter, 44100.0f);
    lfo_init(&ch->lfo);
    ch->volume = 0.8f;
    ch->base_volume = 1.0f;
    ch->pan = 0.0f;
    ch->pan_mod = 0.0f;
    ch->base_duty_cycle = ch->osc.duty_cycle;
    ch->base_filter_cutoff = ch->filter.cutoff;
    ch->base_filter_resonance = ch->filter.resonance;
    ch->base_frequency = 0.0f;
    ch->current_frequency = 0.0f;
    ch->enabled = true;
    ch->note = -1;
    ch->instrument = 0;
}

void channel_note_on(Channel* ch, int note, float velocity) {
    if (!ch || note < 0) return;
    if (velocity < 0.0f) velocity = 0.0f;
    if (velocity > 1.0f) velocity = 1.0f;
    ch->note = note;
    ch->base_frequency = note_to_frequency(note);
    ch->current_frequency = ch->base_frequency;
    oscillator_set_frequency(&ch->osc, ch->current_frequency);
    oscillator_reset(&ch->osc);
    ch->volume = velocity * ch->base_volume;
    envelope_gate_on(&ch->env);
    lfo_sync(&ch->lfo);
    effects_reset(&ch->fx);
}

void channel_note_off(Channel* ch) {
    if (!ch) return;
    envelope_gate_off(&ch->env);
}

void channel_set_volume(Channel* ch, float volume) {
    if (!ch) return;
    if (volume < 0.0f) volume = 0.0f;
    if (volume > 1.0f) volume = 1.0f;
    ch->volume = volume;
}

void channel_set_base_volume(Channel* ch, float volume) {
    if (!ch) return;
    if (volume < 0.0f) volume = 0.0f;
    if (volume > 1.0f) volume = 1.0f;
    float old_base = ch->base_volume;
    ch->base_volume = volume;
    if (old_base > 0.0f) {
        ch->volume *= (volume / old_base);
    } else {
        ch->volume = volume;
    }
}

void channel_set_pan(Channel* ch, float pan) {
    if (!ch) return;
    if (pan < -1.0f) pan = -1.0f;
    if (pan > 1.0f) pan = 1.0f;
    ch->pan = pan;
    ch->pan_mod = pan;
}

void channel_set_duty_cycle(Channel* ch, float duty) {
    if (!ch) return;
    if (duty < 0.05f) duty = 0.05f;
    if (duty > 0.95f) duty = 0.95f;
    ch->base_duty_cycle = duty;
    oscillator_set_duty_cycle(&ch->osc, duty);
}

float channel_generate(Channel* ch, float sample_rate) {
    if (!ch || sample_rate <= 1.0f) return 0.0f;
    ch->pan_mod = ch->pan;

    if (!ch->enabled || !envelope_is_active(&ch->env)) {
        return 0.0f;
    }
    
    if (effects_should_cut(&ch->fx) || effects_should_cut(&ch->inst_fx)) {
        envelope_gate_off(&ch->env);
        return 0.0f;
    }
    
    int arp_semitones = effects_get_arpeggio_semitones(&ch->fx);
    if (arp_semitones == 0) {
        arp_semitones = effects_get_arpeggio_semitones(&ch->inst_fx);
    }
    float freq = ch->current_frequency;
    if (arp_semitones != 0) {
        freq = ch->base_frequency * powf(2.0f, arp_semitones / 12.0f);
    }
    
    freq += effects_get_frequency_offset(&ch->fx, ch->base_frequency);
    freq += effects_get_frequency_offset(&ch->inst_fx, ch->base_frequency);
    
    float lfo_value = lfo_process(&ch->lfo, sample_rate);
    float volume_mod = 1.0f;
    float duty = ch->base_duty_cycle;
    
    if (ch->lfo.enabled && ch->lfo.target != LFO_TARGET_NONE) {
        switch (ch->lfo.target) {
            case LFO_TARGET_PITCH:
                freq *= powf(2.0f, lfo_value / 12.0f);
                break;
            case LFO_TARGET_VOLUME:
                volume_mod = 1.0f + lfo_value * 0.5f;
                if (volume_mod < 0.0f) volume_mod = 0.0f;
                break;
            case LFO_TARGET_PAN: {
                float pan = ch->pan + lfo_value;
                if (pan < -1.0f) pan = -1.0f;
                if (pan > 1.0f) pan = 1.0f;
                ch->pan_mod = pan;
                break;
            }
            case LFO_TARGET_FILTER_CUTOFF: {
                float cutoff = ch->base_filter_cutoff * (1.0f + lfo_value * 0.8f);
                if (fabsf(cutoff - ch->filter.cutoff) > 1.0f) {
                    filter_set_cutoff(&ch->filter, cutoff);
                }
                break;
            }
            case LFO_TARGET_FILTER_RESONANCE: {
                float res = ch->base_filter_resonance + lfo_value * 4.0f;
                if (fabsf(res - ch->filter.resonance) > 0.01f) {
                    filter_set_resonance(&ch->filter, res);
                }
                break;
            }
            case LFO_TARGET_DUTY_CYCLE:
                duty = ch->base_duty_cycle + lfo_value * 0.4f;
                if (duty < 0.05f) duty = 0.05f;
                if (duty > 0.95f) duty = 0.95f;
                break;
            case LFO_TARGET_NONE:
                break;
        }
    }

    if (freq < 20.0f) freq = 20.0f;
    if (freq > 20000.0f) freq = 20000.0f;
    
    oscillator_set_frequency(&ch->osc, freq);
    oscillator_set_duty_cycle(&ch->osc, duty);
    
    float osc_out = oscillator_generate(&ch->osc, sample_rate);
    float filtered = filter_process(&ch->filter, osc_out);
    float env_out = envelope_process(&ch->env, sample_rate);
    float fx_vol = effects_get_volume_multiplier(&ch->fx) * effects_get_volume_multiplier(&ch->inst_fx);
    
    return filtered * env_out * ch->volume * fx_vol * volume_mod;
}

void channel_reset(Channel* ch) {
    if (!ch) return;
    oscillator_reset(&ch->osc);
    envelope_reset(&ch->env);
    effects_reset(&ch->fx);
    effects_reset(&ch->inst_fx);
    filter_reset(&ch->filter);
    lfo_reset(&ch->lfo);
    ch->note = -1;
    ch->base_frequency = 0.0f;
    ch->current_frequency = 0.0f;
    ch->pan_mod = ch->pan;
    ch->base_duty_cycle = ch->osc.duty_cycle;
    ch->base_filter_cutoff = ch->filter.cutoff;
    ch->base_filter_resonance = ch->filter.resonance;
}

void channel_process_tick(Channel* ch) {
    if (!ch) return;
    effects_process_tick(&ch->fx);
    effects_process_tick(&ch->inst_fx);
    
    if (ch->fx.portamento_enabled && ch->current_frequency != ch->fx.portamento_target) {
        if (ch->current_frequency < ch->fx.portamento_target) {
            ch->current_frequency += ch->fx.portamento_speed;
            if (ch->current_frequency > ch->fx.portamento_target) {
                ch->current_frequency = ch->fx.portamento_target;
            }
        } else {
            ch->current_frequency -= ch->fx.portamento_speed;
            if (ch->current_frequency < ch->fx.portamento_target) {
                ch->current_frequency = ch->fx.portamento_target;
            }
        }
    }
}

void channel_apply_portamento(Channel* ch, float target_freq, float speed) {
    if (!ch) return;
    effects_set_portamento(&ch->fx, target_freq, speed);
}

void channel_set_arpeggio(Channel* ch, int note1, int note2, int speed) {
    if (!ch) return;
    effects_set_arpeggio(&ch->fx, note1, note2, speed);
}

void channel_set_vibrato(Channel* ch, float depth, float speed) {
    if (!ch) return;
    effects_set_vibrato(&ch->fx, depth, speed);
}

void channel_set_pitch_slide(Channel* ch, float amount) {
    if (!ch) return;
    ch->current_frequency += amount;
    if (ch->current_frequency < 20.0f) ch->current_frequency = 20.0f;
    if (ch->current_frequency > 20000.0f) ch->current_frequency = 20000.0f;
    ch->base_frequency = ch->current_frequency;
}

ChannelEffects* channel_get_effects(Channel* ch) {
    return ch ? &ch->fx : NULL;
}

void channel_set_filter(Channel* ch, float cutoff, float resonance) {
    if (!ch) return;
    filter_set_params(&ch->filter, cutoff, resonance);
    ch->base_filter_cutoff = ch->filter.cutoff;
    ch->base_filter_resonance = ch->filter.resonance;
}

void channel_enable_filter(Channel* ch, bool enabled) {
    if (!ch) return;
    filter_enable(&ch->filter, enabled);
}

void channel_set_filter_type(Channel* ch, FilterType type) {
    if (!ch) return;
    filter_set_type(&ch->filter, type);
}
