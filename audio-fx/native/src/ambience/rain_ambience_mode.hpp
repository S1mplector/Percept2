#pragma once

#include <array>

#include "ambience/ambience_mode.hpp"

namespace jvn::audiofx::detail {

class RainAmbienceMode final : public BaseAmbienceMode {
public:
  explicit RainAmbienceMode(int sampleRate);

  void configure(const RenderControls& controls) override;
  float sample(float elapsedSeconds) override;

private:
  struct RainVoice {
    bool active = false;
    float burstEnvelope = 0.0f;
    float burstDecay = 0.0f;
    float burstGain = 0.0f;
    float tailEnvelope = 0.0f;
    float tailDecay = 0.0f;
    float tailGain = 0.0f;
    float tonalMix = 0.0f;
    BiquadFilter burstFilter;
    BiquadFilter tailFilter;
    ModalResonator modeA{44100.0f};
    ModalResonator modeB{44100.0f};
    ModalResonator modeC{44100.0f};

    void reset() {
      active = false;
      burstEnvelope = 0.0f;
      burstDecay = 0.0f;
      burstGain = 0.0f;
      tailEnvelope = 0.0f;
      tailDecay = 0.0f;
      tailGain = 0.0f;
      tonalMix = 0.0f;
      burstFilter.reset();
      tailFilter.reset();
      modeA.reset();
      modeB.reset();
      modeC.reset();
    }
  };

  template <size_t N>
  RainVoice& selectVoice(std::array<RainVoice, N>& voices) {
    RainVoice* candidate = &voices.front();
    float quietest = 1.0e9f;
    for (auto& voice : voices) {
      if (!voice.active) {
        voice.reset();
        return voice;
      }
      const float score = voice.burstEnvelope
          + voice.tailEnvelope
          + (voice.modeA.isActive() ? 1.0f : 0.0f)
          + (voice.modeB.isActive() ? 1.0f : 0.0f)
          + (voice.modeC.isActive() ? 1.0f : 0.0f);
      if (score < quietest) {
        quietest = score;
        candidate = &voice;
      }
    }
    candidate->reset();
    return *candidate;
  }

  void updateFilters();
  float sampleDropDiameterMm(float scaleBias);
  float terminalVelocityMetersPerSecond(float diameterMm) const;
  float normalizedImpactEnergy(float diameterMm, float velocityMetersPerSecond, float surfaceGain) const;
  float impactBurstDecay(float impactDurationSeconds, float minimumSeconds, float maximumSeconds) const;
  float processVoice(RainVoice& voice, NoiseGenerator& noise);
  void spawnRoofTick();
  void spawnLeafImpact();
  void spawnPuddleImpact();
  void spawnDrainImpact();

  float roofTimer_ = 0.0f;
  float leafTimer_ = 0.0f;
  float puddleTimer_ = 0.0f;
  float drainTimer_ = 0.0f;
  float densityTimer_ = 0.0f;
  float densityTarget_ = 0.0f;
  float densityState_ = 0.0f;

  NoiseGenerator noiseRoof_{0x22222222u};
  NoiseGenerator noiseLeaf_{0x33333333u};
  NoiseGenerator noiseWater_{0x55555555u};
  NoiseGenerator noiseDrain_{0x77777777u};
  NoiseGenerator noiseMist_{0x99999999u};

  BiquadFilter mistHighPass_;
  BiquadFilter mistLowPass_;
  BiquadFilter distantBodyBandPass_;
  BiquadFilter distantRoofBandPass_;

  std::array<RainVoice, 18> roofVoices_{};
  std::array<RainVoice, 10> leafVoices_{};
  std::array<RainVoice, 8> puddleVoices_{};
  std::array<RainVoice, 6> drainVoices_{};
};

}  // namespace jvn::audiofx::detail
