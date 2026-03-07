#pragma once

#include "ambience/ambience_filters.hpp"

namespace jvn::audiofx::detail {

struct MasterState {
  DcBlocker dcBlocker{18.0f, 44100.0f};
  float limiterEnvelope = 0.0f;
  float limiterGain = 1.0f;

  void setSampleRate(float sampleRate);
  void reset();
  float process(float input);
};

}  // namespace jvn::audiofx::detail
