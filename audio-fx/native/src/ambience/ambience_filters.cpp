#include "ambience/ambience_filters.hpp"

#include <algorithm>
#include <cmath>
#include <limits>

namespace jvn::audiofx::detail {

namespace {

float sanitizeSampleRate(float sampleRate) {
  return std::isfinite(sampleRate) && sampleRate > 2000.0f ? sampleRate : 44100.0f;
}

float flushDenormal(float value) {
  return std::abs(value) < 1.0e-15f ? 0.0f : value;
}

}  // namespace

BiquadFilter::BiquadFilter() = default;

void BiquadFilter::setCoefficients(Type type, float frequency, float q, float sampleRate) {
  const float safeSampleRate = sanitizeSampleRate(sampleRate);
  const float safeFrequency = std::clamp(
      std::isfinite(frequency) ? frequency : 1200.0f,
      20.0f,
      safeSampleRate * 0.45f);
  const float safeQ = std::clamp(std::isfinite(q) ? q : 0.707f, 0.05f, 12.0f);
  const float omega = 2.0f * kPi * safeFrequency / safeSampleRate;
  const float sinOmega = std::sin(omega);
  const float cosOmega = std::cos(omega);
  const float alpha = sinOmega / (2.0f * safeQ);
  float a0 = 1.0f;

  switch (type) {
    case Type::LowPass:
      b0_ = (1.0f - cosOmega) * 0.5f;
      b1_ = 1.0f - cosOmega;
      b2_ = (1.0f - cosOmega) * 0.5f;
      a0 = 1.0f + alpha;
      a1_ = -2.0f * cosOmega;
      a2_ = 1.0f - alpha;
      break;
    case Type::HighPass:
      b0_ = (1.0f + cosOmega) * 0.5f;
      b1_ = -(1.0f + cosOmega);
      b2_ = (1.0f + cosOmega) * 0.5f;
      a0 = 1.0f + alpha;
      a1_ = -2.0f * cosOmega;
      a2_ = 1.0f - alpha;
      break;
    case Type::BandPass:
      b0_ = alpha;
      b1_ = 0.0f;
      b2_ = -alpha;
      a0 = 1.0f + alpha;
      a1_ = -2.0f * cosOmega;
      a2_ = 1.0f - alpha;
      break;
  }

  if (!std::isfinite(a0) || std::abs(a0) < std::numeric_limits<float>::epsilon()) {
    reset();
    return;
  }

  b0_ /= a0;
  b1_ /= a0;
  b2_ /= a0;
  a1_ /= a0;
  a2_ /= a0;
}

float BiquadFilter::process(float input) {
  const float output = b0_ * input + b1_ * x1_ + b2_ * x2_ - a1_ * y1_ - a2_ * y2_;
  x2_ = flushDenormal(x1_);
  x1_ = flushDenormal(input);
  y2_ = flushDenormal(y1_);
  y1_ = flushDenormal(output);
  return output;
}

void BiquadFilter::reset() {
  b0_ = 0.0f;
  b1_ = 0.0f;
  b2_ = 0.0f;
  a1_ = 0.0f;
  a2_ = 0.0f;
  x1_ = 0.0f;
  x2_ = 0.0f;
  y1_ = 0.0f;
  y2_ = 0.0f;
}

ModalResonator::ModalResonator(float sampleRate) {
  setMode(880.0f, 0.08f, sampleRate);
  reset();
}

void ModalResonator::setMode(float frequency, float decaySeconds, float sampleRate) {
  sampleRate_ = sanitizeSampleRate(sampleRate);
  const float safeFrequency = std::clamp(
      std::isfinite(frequency) ? frequency : 880.0f,
      20.0f,
      sampleRate_ * 0.45f);
  const float safeDecay = std::clamp(std::isfinite(decaySeconds) ? decaySeconds : 0.08f, 0.004f, 8.0f);
  const float radius = std::exp(-1.0f / (safeDecay * sampleRate_));
  const float theta = 2.0f * kPi * safeFrequency / sampleRate_;
  a1_ = 2.0f * radius * std::cos(theta);
  a2_ = -(radius * radius);
  excitationGain_ = std::clamp((1.0f - radius) * 96.0f, 0.01f, 0.35f);
}

void ModalResonator::reset() {
  y1_ = 0.0f;
  y2_ = 0.0f;
  pendingExcitation_ = 0.0f;
}

void ModalResonator::excite(float amplitude) {
  pendingExcitation_ += amplitude * excitationGain_;
}

float ModalResonator::process() {
  const float output = pendingExcitation_ + a1_ * y1_ + a2_ * y2_;
  pendingExcitation_ = 0.0f;
  y2_ = flushDenormal(y1_);
  y1_ = flushDenormal(output);
  return output;
}

bool ModalResonator::isActive() const noexcept {
  return std::abs(y1_) > 1.0e-5f || std::abs(y2_) > 1.0e-5f || std::abs(pendingExcitation_) > 1.0e-5f;
}

DcBlocker::DcBlocker(float cutoffHz, float sampleRate)
    : cutoffHz_(cutoffHz), sampleRate_(sanitizeSampleRate(sampleRate)) {
  setSampleRate(sampleRate_);
}

void DcBlocker::setSampleRate(float sampleRate) {
  sampleRate_ = sanitizeSampleRate(sampleRate);
  const float safeCutoff = std::clamp(std::isfinite(cutoffHz_) ? cutoffHz_ : 18.0f, 1.0f, 80.0f);
  coefficient_ = std::exp(-2.0f * kPi * safeCutoff / sampleRate_);
}

void DcBlocker::reset() {
  x1_ = 0.0f;
  y1_ = 0.0f;
}

float DcBlocker::process(float input) {
  const float output = input - x1_ + coefficient_ * y1_;
  x1_ = flushDenormal(input);
  y1_ = flushDenormal(output);
  return output;
}

}  // namespace jvn::audiofx::detail
