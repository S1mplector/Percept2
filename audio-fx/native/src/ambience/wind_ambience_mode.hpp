#pragma once

#include "ambience/ambience_mode.hpp"

namespace jvn::audiofx::detail {

class WindAmbienceMode final : public BaseAmbienceMode {
public:
  explicit WindAmbienceMode(int sampleRate);

  void configure(const RenderControls& controls) override;
  void retune(const RenderControls& controls) override;
  float sample(float elapsedSeconds) override;

private:
  void updateFilters();
  void updateGusts(float dt);

  // ── Stochastic control processes (replace periodic LFOs) ──────────
  //  macro: overall gustiness envelope     (10-60 s)
  //  meso:  mid-range flow drift per band  (1-8 s)
  //  micro: turbulence grain               (50-500 ms)
  DriftProcess macroEnergy_{30.0f, 0xDEADBEEFu};
  DriftProcess mesoLow_{4.0f,     0xC0FFEE11u};
  DriftProcess mesoMid_{3.0f,     0xFACE1234u};
  DriftProcess mesoHigh_{2.0f,    0xBAADF00Du};
  DriftProcess microTurb_{0.18f,  0x0BADC0DEu};

  // ── Multi-voice overlapping gusts (4 voices) ─────────────────────
  static constexpr int kGustVoices = 4;
  struct GustVoice {
    bool   active    = false;
    float  time      = 0.0f;
    float  duration  = 0.0f;
    float  intensity = 0.0f;
    float  attack    = 0.0f;   // attack fraction of duration
    float  skew      = 0.5f;   // peak position (0..1); <0.5 = fast attack
  };
  GustVoice gustVoices_[kGustVoices]{};
  float gustTimers_[kGustVoices]{};
  DriftProcess gustTriggerDrift_{8.0f, 0xA5A55A5Au};  // modulates trigger rate

  // ── Hysteretic whistle ────────────────────────────────────────────
  bool   whistleActive_ = false;
  float  whistlePhase_  = 0.0f;
  float  whistleOvertonePhase_ = 0.0f;
  float  whistleGain_   = 0.0f;     // smoothed on/off
  DriftProcess whistleFreqDrift_{1.5f, 0x12345678u};  // random walk Hz
  DriftProcess whistleVelocity_{0.6f,  0x87654321u};  // local velocity proxy

  // ── Spectral lag (brightness trails energy) ───────────────────────
  float laggedBrightness_ = 0.0f;   // one-pole follower

  // ── Noise sources (one per band, decoupled) ───────────────────────
  NoiseGenerator noiseLow_{0x11111111u};
  NoiseGenerator noiseMid_{0x22222222u};
  NoiseGenerator noiseHigh_{0x33333333u};
  NoiseGenerator noiseGust_{0x44444444u};
  NoiseGenerator noiseWhistle_{0x55555555u};

  // ── Filters ───────────────────────────────────────────────────────
  BiquadFilter lowPassLow_;
  BiquadFilter lowPassMid_;
  BiquadFilter highPassMid_;
  BiquadFilter highPassHigh_;
  BiquadFilter lowPassHigh_;
  BiquadFilter gustFilter_;
  BiquadFilter gustFilter2_;  // second gust band for overlap richness
};

}  // namespace jvn::audiofx::detail
