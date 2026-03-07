#include "ambience/ambience_modulation.hpp"

#include <algorithm>
#include <cmath>

namespace jvn::audiofx::detail {

namespace {

float sanitizeSampleRate(float sampleRate) {
  return std::isfinite(sampleRate) && sampleRate > 1.0f ? sampleRate : 44100.0f;
}

float smoothstep(float t) {
  const float clamped = std::clamp(t, 0.0f, 1.0f);
  return clamped * clamped * (3.0f - 2.0f * clamped);
}

}  // namespace

Lfo::Lfo(float rate, float phase, uint32_t seed)
    : rate_(rate), phase_(phase), initialPhase_(phase), seed_(seed), rng_(seed) {}

void Lfo::setRate(float rate) {
  rate_ = std::isfinite(rate) ? rate : 0.0f;
}

void Lfo::setSampleRate(float sampleRate) {
  sampleRate_ = sanitizeSampleRate(sampleRate);
}

void Lfo::reset() {
  phase_ = initialPhase_ - std::floor(initialPhase_);
  smoothValue_ = 0.0f;
  smoothStart_ = 0.0f;
  smoothTarget_ = 0.0f;
  smoothSegmentIndex_ = -1;
  rng_.seed(seed_);
}

float Lfo::sine() {
  return std::sin(phase_ * 2.0f * kPi);
}

float Lfo::triangle() {
  const float t = phase_ - std::floor(phase_);
  return 1.0f - 4.0f * std::abs(t - 0.5f);
}

float Lfo::smoothRandom() {
  static std::uniform_real_distribution<float> dist(-1.0f, 1.0f);
  constexpr float kSegments = 8.0f;
  const float wrapped = phase_ - std::floor(phase_);
  const float scaled = wrapped * kSegments;
  const int segmentIndex = static_cast<int>(scaled);
  if (segmentIndex != smoothSegmentIndex_) {
    smoothSegmentIndex_ = segmentIndex;
    smoothStart_ = smoothValue_;
    smoothTarget_ = dist(rng_);
  }
  const float t = scaled - static_cast<float>(segmentIndex);
  smoothValue_ = smoothStart_ + (smoothTarget_ - smoothStart_) * smoothstep(t);
  return smoothValue_;
}

void Lfo::advance() {
  phase_ += rate_ / sampleRate_;
  phase_ -= std::floor(phase_);
}

// ─── DriftProcess (Ornstein-Uhlenbeck) ──────────────────────────────

DriftProcess::DriftProcess(float timescaleSeconds, uint32_t seed)
    : timescale_(std::max(0.01f, timescaleSeconds)), seed_(seed), rng_(seed) {
  updateCoefficients();
}

void DriftProcess::setTimescale(float seconds) {
  timescale_ = std::max(0.01f, seconds);
  updateCoefficients();
}

void DriftProcess::setSampleRate(float sampleRate) {
  sampleRate_ = sanitizeSampleRate(sampleRate);
  updateCoefficients();
}

void DriftProcess::reset() {
  state_ = 0.0f;
  rng_.seed(seed_);
  updateCoefficients();
}

float DriftProcess::next() {
  // Exact discretization of dx = -x/tau * dt + sigma * dW
  // where sigma = sqrt(2/tau) gives unit stationary variance
  const float noise = dist_(rng_);
  state_ = state_ * decay_ + diffusion_ * noise;
  // Soft-clip to [-1, 1] with tanh to avoid hard edges
  state_ = std::tanh(state_);
  return state_;
}

float DriftProcess::current() const {
  return state_;
}

void DriftProcess::updateCoefficients() {
  const float dt = 1.0f / sampleRate_;
  const float theta = 1.0f / timescale_;  // mean reversion rate
  decay_ = std::exp(-theta * dt);
  // diffusion chosen so stationary variance ≈ 1.0
  // Var = sigma^2 / (2*theta), sigma^2 * (1 - exp(-2*theta*dt)) / (2*theta)
  // We want Var ≈ 1, so sigma = sqrt(2*theta)
  const float sigma = std::sqrt(2.0f * theta);
  diffusion_ = sigma * std::sqrt((1.0f - decay_ * decay_) / (2.0f * theta));
}

// ─── GustGenerator ──────────────────────────────────────────────────

GustGenerator::GustGenerator(float sampleRate, uint32_t seed)
    : sampleRate_(sanitizeSampleRate(sampleRate)), seed_(seed), rng_(seed) {}

void GustGenerator::setSampleRate(float sampleRate) {
  sampleRate_ = sanitizeSampleRate(sampleRate);
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
  intensity_ = std::max(0.0f, intensity);
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
    envelope = smoothstep(t);
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

}  // namespace jvn::audiofx::detail
