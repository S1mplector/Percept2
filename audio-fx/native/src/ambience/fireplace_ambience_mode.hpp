#pragma once

#include "ambience/ambience_mode.hpp"

namespace jvn::audiofx::detail {

class FireplaceAmbienceMode final : public BaseAmbienceMode {
public:
  explicit FireplaceAmbienceMode(int sampleRate);

  void configure(const RenderControls& controls) override;
  void retune(const RenderControls& controls) override;
  float sample(float elapsedSeconds) override;

private:
  void updateFilters();

  float crackleEnvelope_ = 0.0f;
  float popEnvelope_ = 0.0f;
  float snapEnvelope_ = 0.0f;
  float emberPhase_ = 0.0f;
  float crackleTimer_ = 0.0f;
  float popTimer_ = 0.0f;
  float snapTimer_ = 0.0f;
  Lfo slowLfo_{0.15f, 0.0f, 0xDEADBEEFu};
  Lfo mediumLfo_{0.6f, 0.25f, 0xC0FFEE11u};
  Lfo fastLfo_{2.5f, 0.5f, 0xFACE1234u};
  NoiseGenerator noiseFire_{0x77777777u};
  NoiseGenerator noiseFireDetail_{0xBBBBBBBBu};
  NoiseGenerator noiseHigh_{0x33333333u};
  BiquadFilter crackleBandPass_;
  BiquadFilter baseLowPass_;
  BiquadFilter hissHighPass_;
  BiquadFilter snapBandPass_;
  BiquadFilter emberLowPass_;
};

}  // namespace jvn::audiofx::detail
