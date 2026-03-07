#include "ambience/ambience_noise.hpp"

#include <algorithm>
#include <cmath>

namespace jvn::audiofx::detail {

NoiseGenerator::NoiseGenerator(uint32_t seed) : seed_(seed), rng_(seed) {
  pinkState_.fill(0.0f);
}

float NoiseGenerator::white() {
  return dist_(rng_);
}

float NoiseGenerator::pink() {
  const float whiteNoise = dist_(rng_);
  pinkState_[0] = 0.99886f * pinkState_[0] + whiteNoise * 0.0555179f;
  pinkState_[1] = 0.99332f * pinkState_[1] + whiteNoise * 0.0750759f;
  pinkState_[2] = 0.96900f * pinkState_[2] + whiteNoise * 0.1538520f;
  pinkState_[3] = 0.86650f * pinkState_[3] + whiteNoise * 0.3104856f;
  pinkState_[4] = 0.55000f * pinkState_[4] + whiteNoise * 0.5329522f;
  pinkState_[5] = -0.7616f * pinkState_[5] - whiteNoise * 0.0168980f;
  const float pink =
      pinkState_[0] + pinkState_[1] + pinkState_[2] + pinkState_[3] + pinkState_[4] + pinkState_[5]
      + pinkState_[6] + whiteNoise * 0.5362f;
  pinkState_[6] = whiteNoise * 0.115926f;
  return std::clamp(pink * 0.11f, -1.0f, 1.0f);
}

float NoiseGenerator::brown() {
  const float whiteNoise = dist_(rng_);
  brownValue_ = (brownValue_ + 0.03f * whiteNoise) / 1.01f;
  brownValue_ = std::clamp(brownValue_ * 1.015f, -1.0f, 1.0f);
  return brownValue_;
}

float NoiseGenerator::filtered(float cutoff, float resonance) {
  const float whiteNoise = dist_(rng_);
  const float safeCutoff = std::clamp(cutoff, 0.001f, 0.45f);
  const float safeResonance = std::clamp(resonance, -0.99f, 2.0f);
  const float pole = std::exp(-2.0f * kPi * safeCutoff);
  const float alpha = 1.0f - pole;
  filterState1_ += alpha * (whiteNoise - filterState1_);
  filterState2_ += alpha * (filterState1_ - filterState2_);
  const float band = filterState1_ - filterState2_;
  return std::clamp(filterState2_ + band * safeResonance, -1.5f, 1.5f);
}

void NoiseGenerator::reset() {
  rng_.seed(seed_);
  pinkState_.fill(0.0f);
  brownValue_ = 0.0f;
  filterState1_ = 0.0f;
  filterState2_ = 0.0f;
}

}  // namespace jvn::audiofx::detail
