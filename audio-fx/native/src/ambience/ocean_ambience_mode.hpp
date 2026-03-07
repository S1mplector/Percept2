#pragma once

#include "ambience/ambience_mode.hpp"

namespace jvn::audiofx::detail {

class OceanAmbienceMode final : public BaseAmbienceMode {
public:
  explicit OceanAmbienceMode(int sampleRate);

  void configure(const RenderControls& controls) override;
  float sample(float elapsedSeconds) override;

private:
  void updateFilters();

  float crashEnvelope_ = 0.0f;
  float backwashEnvelope_ = 0.0f;
  float crashTimer_ = 0.0f;
  Lfo slowLfo_{0.15f, 0.0f, 0xDEADBEEFu};
  Lfo mediumLfo_{0.6f, 0.25f, 0xC0FFEE11u};
  Lfo fastLfo_{2.5f, 0.5f, 0xFACE1234u};
  NoiseGenerator noiseLow_{0x11111111u};
  NoiseGenerator noiseMid_{0x22222222u};
  NoiseGenerator noiseHigh_{0x33333333u};
  NoiseGenerator noiseOceanFoam_{0x99999999u};
  BiquadFilter swellLowPass_;
  BiquadFilter washBandPass_;
  BiquadFilter foamHighPass_;
  BiquadFilter undertowLowPass_;
  BiquadFilter crashBandPass_;
  BiquadFilter sprayHighPass_;
  BiquadFilter backwashBandPass_;
  ModalResonator crashBody_;
  ModalResonator backwashBody_;
};

}  // namespace jvn::audiofx::detail
