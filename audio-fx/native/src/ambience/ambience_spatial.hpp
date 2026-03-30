#pragma once

#include <array>
#include <cmath>

#include "ambience/ambience_common.hpp"
#include "ambience/ambience_filters.hpp"
#include "ambience/ambience_modulation.hpp"

namespace jvn::audiofx::detail {

struct StereoFrame {
  float left = 0.0f;
  float right = 0.0f;
};

template <int kCapacity>
class DiffuseAllpass {
public:
  void configure(int delaySamples, float feedback) {
    delaySamples_ = std::clamp(delaySamples, 1, kCapacity - 1);
    feedback_ = std::clamp(feedback, -0.82f, 0.82f);
    reset();
  }

  void reset() {
    buffer_.fill(0.0f);
    index_ = 0;
  }

  float process(float input) {
    const float delayed = buffer_[index_];
    const float output = delayed - feedback_ * input;
    buffer_[index_] = input + delayed * feedback_;
    index_++;
    if (index_ >= delaySamples_) {
      index_ = 0;
    }
    return output;
  }

private:
  std::array<float, kCapacity> buffer_{};
  int delaySamples_ = 1;
  int index_ = 0;
  float feedback_ = 0.58f;
};

class StereoFieldState {
public:
  void setSampleRate(float sampleRate) {
    sampleRate_ = std::isfinite(sampleRate) && sampleRate > 1.0f ? sampleRate : 44100.0f;
    widthDrift_.setSampleRate(sampleRate_);
    microDrift_.setSampleRate(sampleRate_);
    configureDiffusers();
    retune(controls_);
  }

  void retune(const RenderControls& controls) {
    controls_ = controls;
    const float sr = sampleRate_;
    centerLowPass_.setCoefficients(
        BiquadFilter::Type::LowPass,
        190.0f + controls.intensity * 80.0f + controls.accent * 35.0f,
        0.82f,
        sr);
    sideHighPass_.setCoefficients(
        BiquadFilter::Type::HighPass,
        300.0f + controls.intensity * 90.0f + controls.motion * 40.0f,
        0.72f,
        sr);
    sideLowPass_.setCoefficients(
        BiquadFilter::Type::LowPass,
        5400.0f + controls.detail * 2800.0f + controls.accent * 500.0f,
        0.68f,
        sr);
    diffuseGlowLowPass_.setCoefficients(
        BiquadFilter::Type::LowPass,
        1800.0f + controls.detail * 900.0f + controls.motion * 250.0f,
        0.74f,
        sr);
    widthDrift_.setRate(0.018f + controls.motion * 0.040f);
    microDrift_.setRate(0.13f + controls.motion * 0.32f);
  }

  void reset() {
    centerLowPass_.reset();
    sideHighPass_.reset();
    sideLowPass_.reset();
    diffuseGlowLowPass_.reset();
    leftDiffuseA_.reset();
    leftDiffuseB_.reset();
    leftDiffuseC_.reset();
    rightDiffuseA_.reset();
    rightDiffuseB_.reset();
    rightDiffuseC_.reset();
    widthDrift_.reset();
    microDrift_.reset();
    crossfeedState_ = 0.0f;
    previousSideInput_ = 0.0f;
    inputEnvelope_ = 0.0f;
    leftEnvelope_ = 0.0f;
    rightEnvelope_ = 0.0f;
    transientFast_ = 0.0f;
    transientSlow_ = 0.0f;
  }

  StereoFrame process(float mono) {
    const float centeredLow = centerLowPass_.process(mono);
    float sideSource = sideHighPass_.process(mono - centeredLow);
    sideSource = sideLowPass_.process(sideSource);

    const float inputAbs = std::abs(sideSource);
    inputEnvelope_ += (inputAbs - inputEnvelope_) * (inputAbs > inputEnvelope_ ? 0.060f : 0.0011f);

    const float transientDelta = sideSource - previousSideInput_;
    previousSideInput_ = sideSource;
    const float transientAbs = std::abs(transientDelta);
    transientFast_ += (transientAbs - transientFast_) * (transientAbs > transientFast_ ? 0.22f : 0.02f);
    transientSlow_ += (transientFast_ - transientSlow_) * 0.0032f;
    const float transientSpread = clamp01((transientFast_ - transientSlow_) * (5.0f + controls_.accent * 6.5f));

    const float leftWet = shapeWetEnvelope(processLeftWet(sideSource));
    const float rightWet = shapeWetEnvelope(processRightWet(sideSource), false);

    const float widthMod =
        (widthDrift_.smoothRandom() * 0.68f + microDrift_.smoothRandom() * 0.32f)
        * (0.025f + controls_.motion * 0.055f);
    widthDrift_.advance();
    microDrift_.advance();

    const float width = 0.016f + controls_.spread * 0.24f + controls_.detail * 0.028f;
    const float lateBlend = 0.014f + controls_.spread * 0.040f + controls_.intensity * 0.020f;
    const float transientSide = transientDelta
        * transientSpread
        * (0.010f + controls_.spread * 0.030f + controls_.accent * 0.026f);

    const float leftWeight = 1.0f + widthMod;
    const float rightWeight = 1.0f - widthMod;
    const float side = (leftWet * leftWeight - rightWet * rightWeight) * 0.5f * width + transientSide;
    const float lateMid = diffuseGlowLowPass_.process((leftWet + rightWet) * 0.5f) * lateBlend;
    const float mid = mono + lateMid;

    StereoFrame frame;
    frame.left = softClip(mid + side);
    frame.right = softClip(mid - side);
    return frame;
  }

private:
  static float softClip(float sample) {
    constexpr float kDrive = 1.04f;
    return std::tanh(sample * kDrive) / std::tanh(kDrive);
  }

  void configureDiffusers() {
    leftDiffuseA_.configure(delaySamples(7.3f), 0.64f);
    leftDiffuseB_.configure(delaySamples(11.9f), 0.58f);
    leftDiffuseC_.configure(delaySamples(17.2f), 0.52f);
    rightDiffuseA_.configure(delaySamples(8.7f), 0.62f);
    rightDiffuseB_.configure(delaySamples(13.6f), 0.57f);
    rightDiffuseC_.configure(delaySamples(19.4f), 0.50f);
  }

  int delaySamples(float milliseconds) const {
    return std::max(1, static_cast<int>(std::lround(sampleRate_ * milliseconds * 0.001f)));
  }

  float processLeftWet(float sideSource) {
    const float diffuseInput = sideSource + crossfeedState_ * 0.08f;
    float wet = leftDiffuseA_.process(diffuseInput);
    wet = leftDiffuseB_.process(wet);
    wet = leftDiffuseC_.process(wet);
    crossfeedState_ = crossfeedState_ * 0.972f + wet * 0.014f;
    return wet;
  }

  float processRightWet(float sideSource) {
    const float diffuseInput = sideSource - crossfeedState_ * 0.08f;
    float wet = rightDiffuseA_.process(diffuseInput);
    wet = rightDiffuseB_.process(wet);
    wet = rightDiffuseC_.process(wet);
    crossfeedState_ -= wet * 0.014f;
    return wet;
  }

  float shapeWetEnvelope(float wet, bool left = true) {
    float& envelope = left ? leftEnvelope_ : rightEnvelope_;
    const float wetAbs = std::abs(wet);
    envelope += (wetAbs - envelope) * (wetAbs > envelope ? 0.075f : 0.0018f);
    const float shaper = std::clamp((inputEnvelope_ + 0.0012f) / (envelope + 0.0012f), 0.58f, 1.22f);
    return wet * shaper;
  }

  float sampleRate_ = 44100.0f;
  RenderControls controls_{};
  BiquadFilter centerLowPass_{};
  BiquadFilter sideHighPass_{};
  BiquadFilter sideLowPass_{};
  BiquadFilter diffuseGlowLowPass_{};
  DiffuseAllpass<1024> leftDiffuseA_{};
  DiffuseAllpass<1024> leftDiffuseB_{};
  DiffuseAllpass<1024> leftDiffuseC_{};
  DiffuseAllpass<1024> rightDiffuseA_{};
  DiffuseAllpass<1024> rightDiffuseB_{};
  DiffuseAllpass<1024> rightDiffuseC_{};
  Lfo widthDrift_{0.025f, 0.13f, 0x26A4321Fu};
  Lfo microDrift_{0.21f, 0.51f, 0x51D33F21u};
  float crossfeedState_ = 0.0f;
  float previousSideInput_ = 0.0f;
  float inputEnvelope_ = 0.0f;
  float leftEnvelope_ = 0.0f;
  float rightEnvelope_ = 0.0f;
  float transientFast_ = 0.0f;
  float transientSlow_ = 0.0f;
};

}  // namespace jvn::audiofx::detail
