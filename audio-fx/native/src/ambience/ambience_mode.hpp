#pragma once

#include <random>

#include "ambience/ambience_dsp.hpp"

namespace jvn::audiofx::detail {

class AmbienceMode {
public:
  virtual ~AmbienceMode() = default;

  virtual void configure(const RenderControls& controls) = 0;
  virtual float sample(float elapsedSeconds) = 0;
};

class BaseAmbienceMode : public AmbienceMode {
public:
  BaseAmbienceMode(int sampleRate, uint32_t seed);
  ~BaseAmbienceMode() override = default;

protected:
  void setControls(const RenderControls& controls);
  const RenderControls& controls() const noexcept;
  int sampleRate() const noexcept;
  float nextRandom01();
  float randomRange(float minValue, float maxValue);
  float sampleEventInterval(float rateHz, float minimumSeconds);

private:
  int sampleRate_;
  RenderControls controls_{};
  std::mt19937 eventRng_;
  std::uniform_real_distribution<float> eventDist_{0.0f, 1.0f};
};

}  // namespace jvn::audiofx::detail
