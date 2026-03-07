#include "instrument.h"
#include "channel.h"
#include <string.h>

void instrument_init(Instrument* inst) {
    if (!inst) return;
    memset(inst, 0, sizeof(Instrument));
    strcpy(inst->name, "Init");
    inst->waveform = OSC_SQUARE;
    inst->duty_cycle = 0.5f;
    inst->attack = 0.01f;
    inst->decay = 0.1f;
    inst->sustain = 0.7f;
    inst->release = 0.2f;
    inst->volume = 1.0f;
    inst->pan = 0.0f;
    inst->noise_mode = NOISE_MODE_WHITE;
    inst->noise_period = 1;
    inst->filter_enabled = false;
    inst->filter_type = FILTER_LOWPASS;
    inst->filter_cutoff = 8000.0f;
    inst->filter_resonance = 0.7f;
    inst->lfo_enabled = false;
    inst->lfo_waveform = LFO_SINE;
    inst->lfo_target = LFO_TARGET_NONE;
    inst->lfo_rate = 5.0f;
    inst->lfo_depth = 0.0f;
    inst->lfo_delay = 0.0f;
}

void instrument_set_preset(Instrument* inst, int preset) {
    if (!inst) return;
    instrument_init(inst);
    
    switch (preset) {
        case PRESET_PULSE_LEAD:
            strcpy(inst->name, "Pulse Lead");
            inst->waveform = OSC_SQUARE;
            inst->duty_cycle = 0.25f;
            inst->attack = 0.005f;
            inst->decay = 0.15f;
            inst->sustain = 0.6f;
            inst->release = 0.1f;
            inst->lfo_enabled = true;
            inst->lfo_waveform = LFO_TRIANGLE;
            inst->lfo_target = LFO_TARGET_DUTY_CYCLE;
            inst->lfo_rate = 4.0f;
            inst->lfo_depth = 0.35f;
            break;
            
        case PRESET_PULSE_BASS:
            strcpy(inst->name, "Pulse Bass");
            inst->waveform = OSC_SQUARE;
            inst->attack = 0.002f;
            inst->decay = 0.3f;
            inst->sustain = 0.4f;
            inst->release = 0.15f;
            inst->filter_enabled = true;
            inst->filter_type = FILTER_LOWPASS;
            inst->filter_cutoff = 1200.0f;
            inst->filter_resonance = 0.9f;
            break;
            
        case PRESET_TRIANGLE_BASS:
            strcpy(inst->name, "Tri Bass");
            inst->waveform = OSC_TRIANGLE;
            inst->attack = 0.001f;
            inst->decay = 0.4f;
            inst->sustain = 0.3f;
            inst->release = 0.2f;
            inst->filter_enabled = true;
            inst->filter_type = FILTER_LOWPASS;
            inst->filter_cutoff = 900.0f;
            inst->filter_resonance = 0.8f;
            break;
            
        case PRESET_TRIANGLE_LEAD:
            strcpy(inst->name, "Tri Lead");
            inst->waveform = OSC_TRIANGLE;
            inst->attack = 0.01f;
            inst->decay = 0.1f;
            inst->sustain = 0.8f;
            inst->release = 0.15f;
            inst->vibrato_enabled = true;
            inst->vibrato_depth = 0.3f;
            inst->vibrato_speed = 5.0f;
            inst->vibrato_delay = 0.2f;
            break;
            
        case PRESET_NOISE_DRUM:
            strcpy(inst->name, "Noise Kick");
            inst->waveform = OSC_NOISE_WHITE;
            inst->attack = 0.001f;
            inst->decay = 0.15f;
            inst->sustain = 0.0f;
            inst->release = 0.05f;
            inst->pitch_env_enabled = true;
            inst->pitch_env_amount = 200.0f;
            inst->pitch_env_speed = 15.0f;
            break;
            
        case PRESET_NOISE_HAT:
            strcpy(inst->name, "Hi-Hat");
            inst->waveform = OSC_NOISE_METALLIC;
            inst->attack = 0.001f;
            inst->decay = 0.08f;
            inst->sustain = 0.0f;
            inst->release = 0.03f;
            inst->noise_period = 2;
            break;
            
        case PRESET_ARP_CHORD:
            strcpy(inst->name, "Arp Chord");
            inst->waveform = OSC_PULSE_25;
            inst->attack = 0.005f;
            inst->decay = 0.2f;
            inst->sustain = 0.5f;
            inst->release = 0.1f;
            inst->arp_enabled = true;
            inst->arp_note1 = 4;
            inst->arp_note2 = 7;
            inst->arp_speed = 3;
            inst->lfo_enabled = true;
            inst->lfo_waveform = LFO_SINE;
            inst->lfo_target = LFO_TARGET_PAN;
            inst->lfo_rate = 2.0f;
            inst->lfo_depth = 0.4f;
            break;
            
        case PRESET_VIBRATO_LEAD:
            strcpy(inst->name, "Vib Lead");
            inst->waveform = OSC_SQUARE;
            inst->attack = 0.01f;
            inst->decay = 0.1f;
            inst->sustain = 0.7f;
            inst->release = 0.2f;
            inst->vibrato_enabled = true;
            inst->vibrato_depth = 0.5f;
            inst->vibrato_speed = 6.0f;
            break;
            
        case PRESET_PLUCK:
            strcpy(inst->name, "Pluck");
            inst->waveform = OSC_SAWTOOTH;
            inst->attack = 0.001f;
            inst->decay = 0.3f;
            inst->sustain = 0.0f;
            inst->release = 0.1f;
            inst->filter_enabled = true;
            inst->filter_type = FILTER_LOWPASS;
            inst->filter_cutoff = 2200.0f;
            inst->filter_resonance = 0.7f;
            break;
            
        case PRESET_PAD:
            strcpy(inst->name, "Pad");
            inst->waveform = OSC_TRIANGLE;
            inst->attack = 0.3f;
            inst->decay = 0.2f;
            inst->sustain = 0.8f;
            inst->release = 0.5f;
            inst->vibrato_enabled = true;
            inst->vibrato_depth = 0.2f;
            inst->vibrato_speed = 3.0f;
            inst->filter_enabled = true;
            inst->filter_type = FILTER_LOWPASS;
            inst->filter_cutoff = 2500.0f;
            inst->filter_resonance = 0.6f;
            inst->lfo_enabled = true;
            inst->lfo_waveform = LFO_SINE;
            inst->lfo_target = LFO_TARGET_PAN;
            inst->lfo_rate = 0.6f;
            inst->lfo_depth = 0.6f;
            break;
            
        case PRESET_BELL:
            strcpy(inst->name, "Bell");
            inst->waveform = OSC_WAVETABLE;
            inst->wavetable_preset = 3;
            inst->attack = 0.001f;
            inst->decay = 0.5f;
            inst->sustain = 0.2f;
            inst->release = 0.3f;
            inst->lfo_enabled = true;
            inst->lfo_waveform = LFO_SINE;
            inst->lfo_target = LFO_TARGET_VOLUME;
            inst->lfo_rate = 5.0f;
            inst->lfo_depth = 0.2f;
            break;
            
        case PRESET_ORGAN:
            strcpy(inst->name, "Organ");
            inst->waveform = OSC_WAVETABLE;
            inst->wavetable_preset = 1;
            inst->attack = 0.01f;
            inst->decay = 0.05f;
            inst->sustain = 0.9f;
            inst->release = 0.1f;
            inst->lfo_enabled = true;
            inst->lfo_waveform = LFO_TRIANGLE;
            inst->lfo_target = LFO_TARGET_VOLUME;
            inst->lfo_rate = 4.0f;
            inst->lfo_depth = 0.25f;
            break;
            
        default:
            break;
    }
}

void instrument_apply_to_channel(const Instrument* inst, void* channel) {
    if (!inst || !channel) return;
    
    Channel* ch = (Channel*)channel;
    
    ch->osc.waveform = inst->waveform;
    channel_set_duty_cycle(ch, inst->duty_cycle);
    
    if (inst->waveform == OSC_WAVETABLE) {
        oscillator_load_preset_wavetable(&ch->osc, inst->wavetable_preset);
    }
    
    if (inst->waveform == OSC_NOISE_WHITE ||
        inst->waveform == OSC_NOISE_PERIODIC ||
        inst->waveform == OSC_NOISE_METALLIC) {
        oscillator_set_noise_mode(&ch->osc, (NoiseMode)inst->noise_mode, inst->noise_period);
    }
    
    envelope_set_adsr(&ch->env, inst->attack, inst->decay, inst->sustain, inst->release);
    channel_set_base_volume(ch, inst->volume);
    channel_set_pan(ch, inst->pan);
    
    channel_set_filter_type(ch, inst->filter_type);
    channel_set_filter(ch, inst->filter_cutoff, inst->filter_resonance);
    channel_enable_filter(ch, inst->filter_enabled);
    
    effects_reset(&ch->inst_fx);
    if (inst->arp_enabled) {
        effects_set_arpeggio(&ch->inst_fx, inst->arp_note1, inst->arp_note2, inst->arp_speed);
    }
    if (inst->vibrato_enabled) {
        effects_set_vibrato(&ch->inst_fx, inst->vibrato_depth, inst->vibrato_speed);
    }
    if (inst->pitch_env_enabled) {
        effects_set_pitch_envelope(&ch->inst_fx, inst->pitch_env_amount, inst->pitch_env_speed);
    }
    
    lfo_init(&ch->lfo);
    lfo_set_waveform(&ch->lfo, inst->lfo_waveform);
    lfo_set_target(&ch->lfo, inst->lfo_target);
    lfo_set_rate(&ch->lfo, inst->lfo_rate);
    lfo_set_depth(&ch->lfo, inst->lfo_depth);
    lfo_set_delay(&ch->lfo, inst->lfo_delay);
    lfo_enable(&ch->lfo, inst->lfo_enabled);
}

void instrument_bank_init(InstrumentBank* bank) {
    if (!bank) return;
    memset(bank, 0, sizeof(InstrumentBank));
    bank->count = 0;
}

void instrument_bank_load_defaults(InstrumentBank* bank) {
    if (!bank) return;
    instrument_bank_init(bank);
    
    for (int i = 0; i < PRESET_COUNT && bank->count < MAX_INSTRUMENTS; i++) {
        Instrument inst;
        instrument_set_preset(&inst, i);
        instrument_bank_add(bank, &inst);
    }
}

int instrument_bank_add(InstrumentBank* bank, const Instrument* inst) {
    if (!bank || !inst) return -1;
    if (bank->count >= MAX_INSTRUMENTS) return -1;
    bank->instruments[bank->count] = *inst;
    return bank->count++;
}

Instrument* instrument_bank_get(InstrumentBank* bank, int index) {
    if (!bank || index < 0 || index >= bank->count) return NULL;
    return &bank->instruments[index];
}
