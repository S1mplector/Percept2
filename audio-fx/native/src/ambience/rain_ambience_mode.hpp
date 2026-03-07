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

  float microDropEnvelope_ = 0.0f;
  float roofSplashEnvelope_ = 0.0f;
  float impactEnvelope_ = 0.0f;
  float microDropTimer_ = 0.0f;
  float impactTimer_ = 0.0f;
  Lfo slowLfo_{0.15f, 0.0f, 0xDEADBEEFu};
  Lfo mediumLfo_{0.6f, 0.25f, 0xC0FFEE11u};
  NoiseGenerator noiseMid_{0x22222222u};
  NoiseGenerator noiseHigh_{0x33333333u};
  NoiseGenerator noiseDrop_{0x55555555u};
  BiquadFilter bedLowPass_;
  BiquadFilter bedHighPass_;
  BiquadFilter bedPresenceBandPass_;
  BiquadFilter mistHighPass_;
  BiquadFilter splashHighPass_;
  BiquadFilter dropBandPass_;
  BiquadFilter impactNoiseBandPass_;
  ModalResonator roofTickBody_;
  ModalResonator leafDripBody_;
  ModalResonator impactBody_;
  ModalResonator gutterBody_;
  ModalResonator drainBody_;
};

}  // namespace jvn::audiofx::detail
