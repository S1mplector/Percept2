#pragma once

#include "ambience/ambience_common.hpp"

namespace jvn::audiofx::detail {

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

class ModalResonator {
public:
  explicit ModalResonator(float sampleRate = 44100.0f);

  void setMode(float frequency, float decaySeconds, float sampleRate);
  void reset();
  void excite(float amplitude);
  float process();
  bool isActive() const noexcept;

private:
  float sampleRate_ = 44100.0f;
  float a1_ = 0.0f;
  float a2_ = 0.0f;
  float excitationGain_ = 0.1f;
  float y1_ = 0.0f;
  float y2_ = 0.0f;
  float pendingExcitation_ = 0.0f;
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

}  // namespace jvn::audiofx::detail
