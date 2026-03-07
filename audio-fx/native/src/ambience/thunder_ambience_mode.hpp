#pragma once

#include "ambience/ambience_mode.hpp"

namespace jvn::audiofx::detail {

class ThunderAmbienceMode final : public BaseAmbienceMode {
public:
  explicit ThunderAmbienceMode(int sampleRate);

  void configure(const RenderControls& controls) override;
  float sample(float elapsedSeconds) override;

private:
  void updateFilters();

  float rumblePhase_ = 0.0f;
  float crackEnvelope_ = 0.0f;
  float boltEnvelope_ = 0.0f;
  float boltTimer_ = 0.0f;
  float boltDecayRate_ = 0.9990f;
  float rollDelaySeconds_ = 0.0f;
  float dropEnvelope_ = 0.0f;
  float dropTimer_ = 0.0f;
  Lfo slowLfo_{0.15f, 0.0f, 0xDEADBEEFu};
  Lfo mediumLfo_{0.6f, 0.25f, 0xC0FFEE11u};
  Lfo fastLfo_{2.5f, 0.5f, 0xFACE1234u};
  NoiseGenerator noiseThunder_{0x66666666u};
  NoiseGenerator noiseThunderBolt_{0xAAAAAAAAu};
  NoiseGenerator noiseMid_{0x22222222u};
  NoiseGenerator noiseDrop_{0x55555555u};
  BiquadFilter rumbleLowPass_;
  BiquadFilter crackBandPass_;
  BiquadFilter rainHighPass_;
  BiquadFilter subBassLowPass_;
  BiquadFilter dropBandPass_;
};

}  // namespace jvn::audiofx::detail
