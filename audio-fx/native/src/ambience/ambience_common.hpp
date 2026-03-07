#pragma once

#include <algorithm>
#include <cmath>

namespace jvn::audiofx::detail {

inline constexpr float kPi = 3.14159265358979323846f;

inline float clamp01(float value) {
  return std::max(0.0f, std::min(1.0f, value));
}

struct RenderControls {
  float intensity = 0.65f;
  float volume = 0.45f;
  float detail = 0.5f;
  float motion = 0.5f;
  float spread = 0.5f;
  float accent = 0.5f;
  bool loop = true;
};

}  // namespace jvn::audiofx::detail
