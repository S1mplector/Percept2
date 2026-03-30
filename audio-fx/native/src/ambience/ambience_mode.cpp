#include "ambience/ambience_mode.hpp"

#include <algorithm>
#include <cmath>

namespace jvn::audiofx::detail {

BaseAmbienceMode::BaseAmbienceMode(int sampleRate, uint32_t seed)
    : sampleRate_(sampleRate > 1 ? sampleRate : 44100), seed_(seed), eventRng_(seed) {}

void BaseAmbienceMode::setControls(const RenderControls& controls) {
  controls_.intensity = clamp01(controls.intensity);
  controls_.volume = clamp01(controls.volume);
  controls_.detail = clamp01(controls.detail);
  controls_.motion = clamp01(controls.motion);
  controls_.spread = clamp01(controls.spread);
  controls_.accent = clamp01(controls.accent);
  controls_.loop = controls.loop;
}

void BaseAmbienceMode::resetRandomState() {
  eventRng_.seed(seed_);
}

const RenderControls& BaseAmbienceMode::controls() const noexcept {
  return controls_;
}

int BaseAmbienceMode::sampleRate() const noexcept {
  return sampleRate_;
}

float BaseAmbienceMode::nextRandom01() {
  return eventDist_(eventRng_);
}

float BaseAmbienceMode::randomRange(float minValue, float maxValue) {
  const float lo = std::min(minValue, maxValue);
  const float hi = std::max(minValue, maxValue);
  return lo + (hi - lo) * nextRandom01();
}

float BaseAmbienceMode::sampleEventInterval(float rateHz, float minimumSeconds) {
  const float safeRate = std::max(0.01f, rateHz);
  const float u = std::max(1.0e-5f, 1.0f - nextRandom01());
  return minimumSeconds + (-std::log(u) / safeRate);
}

}  // namespace jvn::audiofx::detail
