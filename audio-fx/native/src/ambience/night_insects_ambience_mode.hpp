#pragma once

#include "ambience/ambience_mode.hpp"

namespace jvn::audiofx::detail {

class NightInsectsAmbienceMode final : public BaseAmbienceMode {
public:
  explicit NightInsectsAmbienceMode(int sampleRate);

  void configure(const RenderControls& controls) override;
  void retune(const RenderControls& controls) override;
  float sample(float elapsedSeconds) override;

private:
  void updateFilters();

  float chirpPhase_ = 0.0f;
  float chirpEnvelope_ = 0.0f;
  float cricket2Phase_ = 0.0f;
  float cricket2Envelope_ = 0.0f;
  float cricket3Phase_ = 0.0f;
  float frogEnvelope_ = 0.0f;
  float frogTimer_ = 0.0f;
  Lfo slowLfo_{0.15f, 0.0f, 0xDEADBEEFu};
  Lfo mediumLfo_{0.6f, 0.25f, 0xC0FFEE11u};
  Lfo fastLfo_{2.5f, 0.5f, 0xFACE1234u};
  NoiseGenerator noiseInsect_{0x88888888u};
  NoiseGenerator noiseInsect2_{0xCCCCCCCCu};
  NoiseGenerator noiseMid_{0x22222222u};
  NoiseGenerator noiseHigh_{0x33333333u};
  BiquadFilter chirpBandPass_;
  BiquadFilter bedLowPass_;
  BiquadFilter detailHighPass_;
  BiquadFilter cricket2BandPass_;
  BiquadFilter frogBandPass_;
};

}  // namespace jvn::audiofx::detail
