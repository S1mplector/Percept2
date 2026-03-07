#pragma once

#include "ambience/ambience_mode.hpp"

namespace jvn::audiofx::detail {

class RainAmbienceMode final : public BaseAmbienceMode {
public:
  explicit RainAmbienceMode(int sampleRate);

  void configure(const RenderControls& controls) override;
  float sample(float elapsedSeconds) override;

private:
  void updateFilters();

  float dropletEnvelope_ = 0.0f;
  float impactEnvelope_ = 0.0f;
  float impactPhase_ = 0.0f;
  float impactFrequency_ = 1200.0f;
  float dropTimer_ = 0.0f;
  Lfo slowLfo_{0.15f, 0.0f, 0xDEADBEEFu};
  Lfo mediumLfo_{0.6f, 0.25f, 0xC0FFEE11u};
  NoiseGenerator noiseMid_{0x22222222u};
  NoiseGenerator noiseHigh_{0x33333333u};
  NoiseGenerator noiseDrop_{0x55555555u};
  BiquadFilter bedLowPass_;
  BiquadFilter bedHighPass_;
  BiquadFilter hissHighPass_;
  BiquadFilter dropBandPass_;
  BiquadFilter impactBandPass_;
};

}  // namespace jvn::audiofx::detail
