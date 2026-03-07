#include "ambience/ambience_dsp.hpp"

namespace jvn::audiofx::detail {

BiquadFilter::BiquadFilter() = default;

void BiquadFilter::setCoefficients(Type type, float frequency, float q, float sampleRate) {
  const float safeFrequency = std::max(20.0f, std::min(frequency, sampleRate * 0.45f));
  const float safeQ = std::max(0.05f, q);
  const float omega = 2.0f * kPi * safeFrequency / sampleRate;
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

  b0_ /= a0;
  b1_ /= a0;
  b2_ /= a0;
  a1_ /= a0;
  a2_ /= a0;
}

float BiquadFilter::process(float input) {
  const float output = b0_ * input + b1_ * x1_ + b2_ * x2_ - a1_ * y1_ - a2_ * y2_;
  x2_ = x1_;
  x1_ = input;
  y2_ = y1_;
  y1_ = std::abs(output) < 1.0e-15f ? 0.0f : output;
  if (std::abs(y2_) < 1.0e-15f) y2_ = 0.0f;
  return output;
}

void BiquadFilter::reset() {
  x1_ = x2_ = y1_ = y2_ = 0.0f;
}

Lfo::Lfo(float rate, float phase, uint32_t seed)
    : rate_(rate), phase_(phase), initialPhase_(phase), seed_(seed), rng_(seed) {}

void Lfo::setRate(float rate) {
  rate_ = rate;
}

void Lfo::setSampleRate(float sampleRate) {
  sampleRate_ = sampleRate > 1.0f ? sampleRate : 44100.0f;
}

void Lfo::reset() {
  phase_ = initialPhase_;
  smoothValue_ = 0.0f;
  targetValue_ = 0.0f;
  rng_.seed(seed_);
}

float Lfo::sine() {
  return std::sin(phase_ * 2.0f * kPi);
}

float Lfo::triangle() {
  const float t = std::fmod(phase_, 1.0f);
  return 4.0f * std::abs(t - 0.5f) - 1.0f;
}

float Lfo::smoothRandom() {
  static std::uniform_real_distribution<float> dist(-1.0f, 1.0f);
  const float cyclePos = std::fmod(phase_ * 4.0f, 1.0f);
  if (cyclePos < 0.01f) {
    targetValue_ = dist(rng_);
  }
  smoothValue_ += (targetValue_ - smoothValue_) * 0.001f;
  return smoothValue_;
}

void Lfo::advance() {
  phase_ += rate_ / sampleRate_;
  if (phase_ > 1000.0f) phase_ -= 1000.0f;
}

NoiseGenerator::NoiseGenerator(uint32_t seed) : seed_(seed), rng_(seed) {
  pinkRows_.fill(0.0f);
}

float NoiseGenerator::white() {
  return dist_(rng_);
}

float NoiseGenerator::pink() {
  const float whiteNoise = dist_(rng_);
  pinkIndex_ = (pinkIndex_ + 1) & 15;
  if (pinkIndex_ != 0) {
    int numZeros = 0;
    int n = pinkIndex_;
    while ((n & 1) == 0) {
      n >>= 1;
      ++numZeros;
    }
    pinkRunningSum_ -= pinkRows_[numZeros];
    const float newRandom = dist_(rng_);
    pinkRunningSum_ += newRandom;
    pinkRows_[numZeros] = newRandom;
  }
  return (pinkRunningSum_ + whiteNoise) / 5.0f;
}

float NoiseGenerator::brown() {
  brownValue_ += dist_(rng_) * 0.02f;
  brownValue_ *= 0.998f;
  brownValue_ = std::max(-1.0f, std::min(1.0f, brownValue_));
  return brownValue_;
}

float NoiseGenerator::filtered(float cutoff, float resonance) {
  const float whiteNoise = dist_(rng_);
  const float alpha = std::max(0.001f, std::min(0.95f, cutoff));
  filterState_ = filterState_ + alpha * (whiteNoise - filterState_);
  return filterState_ * (1.0f + resonance);
}

void NoiseGenerator::reset() {
  rng_.seed(seed_);
  pinkRows_.fill(0.0f);
  pinkIndex_ = 0;
  pinkRunningSum_ = 0.0f;
  brownValue_ = 0.0f;
  filterState_ = 0.0f;
}

GustGenerator::GustGenerator(float sampleRate, uint32_t seed)
    : sampleRate_(sampleRate), seed_(seed), rng_(seed) {}

void GustGenerator::setSampleRate(float sampleRate) {
  sampleRate_ = sampleRate > 1.0f ? sampleRate : 44100.0f;
}

void GustGenerator::reset() {
  active_ = false;
  phase_ = 0.0f;
  duration_ = 0.0f;
  intensity_ = 0.0f;
  attackTime_ = 0.0f;
  decayTime_ = 0.0f;
  rng_.seed(seed_);
}

void GustGenerator::trigger(float intensity) {
  if (active_) return;
  std::uniform_real_distribution<float> durDist(0.8f, 2.5f);
  std::uniform_real_distribution<float> attackDist(0.15f, 0.4f);
  active_ = true;
  phase_ = 0.0f;
  intensity_ = intensity;
  duration_ = durDist(rng_);
  attackTime_ = attackDist(rng_) * duration_;
  decayTime_ = std::max(0.1f, duration_ - attackTime_);
}

float GustGenerator::generate() {
  if (!active_) return 0.0f;
  const float time = phase_ / sampleRate_;
  float envelope = 0.0f;
  if (time < attackTime_) {
    const float t = time / std::max(0.001f, attackTime_);
    envelope = t * t * (3.0f - 2.0f * t);
    envelope *= 1.0f + 0.2f * std::sin(t * kPi);
  } else if (time < duration_) {
    const float t = (time - attackTime_) / decayTime_;
    envelope = 1.0f - t;
    envelope *= envelope;
    envelope *= 1.0f + 0.1f * std::sin(t * 12.0f) * (1.0f - t);
  } else {
    active_ = false;
    return 0.0f;
  }
  return envelope * intensity_;
}

void GustGenerator::update() {
  if (active_) phase_ += 1.0f;
}

bool GustGenerator::isActive() const noexcept {
  return active_;
}

DcBlocker::DcBlocker(float cutoffHz, float sampleRate)
    : cutoffHz_(cutoffHz), sampleRate_(sampleRate > 1.0f ? sampleRate : 44100.0f) {
  setSampleRate(sampleRate_);
}

void DcBlocker::setSampleRate(float sampleRate) {
  sampleRate_ = sampleRate > 1.0f ? sampleRate : 44100.0f;
  coefficient_ = std::exp(-2.0f * kPi * cutoffHz_ / sampleRate_);
}

void DcBlocker::reset() {
  x1_ = 0.0f;
  y1_ = 0.0f;
}

float DcBlocker::process(float input) {
  const float output = input - x1_ + coefficient_ * y1_;
  x1_ = input;
  y1_ = output;
  return output;
}

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
  return std::clamp(dcSafe * limiterGain, -1.0f, 1.0f);
}

}  // namespace jvn::audiofx::detail
