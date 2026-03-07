#pragma once

#include <cstdint>
#include <random>

#include "ambience/ambience_common.hpp"

namespace jvn::audiofx::detail {

class Lfo {
public:
  explicit Lfo(float rate = 1.0f, float phase = 0.0f, uint32_t seed = 0x13579BDFu);

  void setRate(float rate);
  void setSampleRate(float sampleRate);
  void reset();
  float sine();
  float triangle();
  float smoothRandom();
  void advance();

private:
  float rate_;
  float phase_;
  float initialPhase_;
  float sampleRate_ = 44100.0f;
  float smoothValue_ = 0.0f;
  float smoothStart_ = 0.0f;
  float smoothTarget_ = 0.0f;
  int smoothSegmentIndex_ = -1;
  uint32_t seed_;
  std::mt19937 rng_;
};

class GustGenerator {
public:
  explicit GustGenerator(float sampleRate = 44100.0f, uint32_t seed = 0xA5A55A5Au);

  void setSampleRate(float sampleRate);
  void reset();
  void trigger(float intensity = 1.0f);
  float generate();
  void update();
  bool isActive() const noexcept;

private:
  float sampleRate_;
  bool active_ = false;
  float phase_ = 0.0f;
  float duration_ = 0.0f;
  float intensity_ = 0.0f;
  float attackTime_ = 0.0f;
  float decayTime_ = 0.0f;
  uint32_t seed_;
  std::mt19937 rng_;
};

}  // namespace jvn::audiofx::detail
