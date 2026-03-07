#pragma once

#include "ambience/ambience_mode.hpp"

namespace jvn::audiofx::detail {

class WindAmbienceMode final : public BaseAmbienceMode {
public:
  explicit WindAmbienceMode(int sampleRate);

  void configure(const RenderControls& controls) override;
  float sample(float elapsedSeconds) override;

private:
  void updateFilters();
  void maybeTriggerGust(float dt);

  float gustTimer_ = 0.0f;
  float whistlePhase_ = 0.0f;
  float whistleOvertonePhase_ = 0.0f;
  GustGenerator gust_{44100.0f, 0xABCDEF01u};
  Lfo slowLfo_{0.15f, 0.0f, 0xDEADBEEFu};
  Lfo mediumLfo_{0.6f, 0.25f, 0xC0FFEE11u};
  Lfo fastLfo_{2.5f, 0.5f, 0xFACE1234u};
  NoiseGenerator noiseLow_{0x11111111u};
  NoiseGenerator noiseMid_{0x22222222u};
  NoiseGenerator noiseHigh_{0x33333333u};
  NoiseGenerator noiseGust_{0x44444444u};
  BiquadFilter lowPassLow_;
  BiquadFilter lowPassMid_;
  BiquadFilter highPassMid_;
  BiquadFilter highPassHigh_;
  BiquadFilter lowPassHigh_;
  BiquadFilter gustFilter_;
};

}  // namespace jvn::audiofx::detail
