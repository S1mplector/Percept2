#include "ambience/ambience_mastering.hpp"

#include <algorithm>
#include <cmath>

namespace jvn::audiofx::detail {

void MasterState::setSampleRate(float sampleRate) {
  dcBlocker.setSampleRate(sampleRate);
}

void MasterState::reset() {
  dcBlocker.reset();
  limiterEnvelope = 0.0f;
  limiterGain = 1.0f;
}

float MasterState::process(float input) {
  const float dcSafe = dcBlocker.process(input);
  const float absolute = std::abs(dcSafe);
  limiterEnvelope = std::max(absolute, limiterEnvelope * 0.9996f);
  const float targetGain = limiterEnvelope > 0.92f ? (0.92f / limiterEnvelope) : 1.0f;
  const float slew = targetGain < limiterGain ? 0.035f : 0.0015f;
  limiterGain += (targetGain - limiterGain) * slew;
  const float gained = dcSafe * limiterGain;
  const float softClipped = std::tanh(gained * 1.08f) / std::tanh(1.08f);
  return std::clamp(softClipped, -1.0f, 1.0f);
}

}  // namespace jvn::audiofx::detail
