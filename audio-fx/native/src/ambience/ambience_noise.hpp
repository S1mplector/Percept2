#pragma once

#include <array>
#include <cstdint>
#include <random>

#include "ambience/ambience_common.hpp"

namespace jvn::audiofx::detail {

class NoiseGenerator {
public:
  explicit NoiseGenerator(uint32_t seed = 0x2468ACE0u);

  float white();
  float pink();
  float brown();
  float filtered(float cutoff, float resonance);
  void reset();

private:
  uint32_t seed_;
  std::mt19937 rng_;
  std::uniform_real_distribution<float> dist_{-1.0f, 1.0f};
  std::array<float, 7> pinkState_{};
  float brownValue_ = 0.0f;
  float filterState1_ = 0.0f;
  float filterState2_ = 0.0f;
};

}  // namespace jvn::audiofx::detail
