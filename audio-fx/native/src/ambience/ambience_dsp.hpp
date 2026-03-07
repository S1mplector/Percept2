#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <random>

namespace jvn::audiofx::detail {

inline constexpr float kPi = 3.14159265358979323846f;

inline float clamp01(float value) {
  return std::max(0.0f, std::min(1.0f, value));
}

class BiquadFilter {
public:
  enum class Type { LowPass, HighPass, BandPass };

  BiquadFilter();

  void setCoefficients(Type type, float frequency, float q, float sampleRate);
  float process(float input);
  void reset();

private:
  float b0_ = 0.0f;
  float b1_ = 0.0f;
  float b2_ = 0.0f;
  float a1_ = 0.0f;
  float a2_ = 0.0f;
  float x1_ = 0.0f;
  float x2_ = 0.0f;
  float y1_ = 0.0f;
  float y2_ = 0.0f;
};

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
  float targetValue_ = 0.0f;
  uint32_t seed_;
  std::mt19937 rng_;
};

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
  std::array<float, 16> pinkRows_{};
  int pinkIndex_ = 0;
  float pinkRunningSum_ = 0.0f;
  float brownValue_ = 0.0f;
  float filterState_ = 0.0f;
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

class DcBlocker {
public:
  explicit DcBlocker(float cutoffHz = 18.0f, float sampleRate = 44100.0f);

  void setSampleRate(float sampleRate);
  void reset();
  float process(float input);

private:
  float cutoffHz_;
  float sampleRate_;
  float coefficient_ = 0.995f;
  float x1_ = 0.0f;
  float y1_ = 0.0f;
};

struct RenderControls {
  float intensity = 0.65f;
  float volume = 0.45f;
  float detail = 0.5f;
  float motion = 0.5f;
  float spread = 0.5f;
  float accent = 0.5f;
  bool loop = true;
};

struct MasterState {
  DcBlocker dcBlocker{18.0f, 44100.0f};
  float limiterEnvelope = 0.0f;
  float limiterGain = 1.0f;

  void setSampleRate(float sampleRate);
  void reset();
  float process(float input);
};

}  // namespace jvn::audiofx::detail
