#include "loom_ambience_renderer.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstring>

namespace jvn::audiofx {
namespace {

constexpr float kPi = 3.14159265358979323846f;
constexpr int kChannels = 2;
constexpr int kBytesPerSample = 2;
constexpr int kFrameBytes = kChannels * kBytesPerSample;
constexpr float kAutoStopSeconds = 15.0f;

short clampPcm16(float value) {
  const float clamped = std::max(-1.0f, std::min(1.0f, value));
  return static_cast<short>(std::lrintf(clamped * 32767.0f));
}

}  // namespace

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

Lfo::Lfo(float rate, float phase, uint32_t seed) : rate_(rate), phase_(phase), rng_(seed) {}

void Lfo::setRate(float rate) {
  rate_ = rate;
}

void Lfo::setSampleRate(float sampleRate) {
  sampleRate_ = sampleRate > 1.0f ? sampleRate : 44100.0f;
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

NoiseGenerator::NoiseGenerator(uint32_t seed) : rng_(seed) {
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
  pinkRows_.fill(0.0f);
  pinkIndex_ = 0;
  pinkRunningSum_ = 0.0f;
  brownValue_ = 0.0f;
  filterState_ = 0.0f;
}

GustGenerator::GustGenerator(float sampleRate, uint32_t seed) : sampleRate_(sampleRate), rng_(seed) {}

void GustGenerator::setSampleRate(float sampleRate) {
  sampleRate_ = sampleRate > 1.0f ? sampleRate : 44100.0f;
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

LoomAmbienceRenderer::LoomAmbienceRenderer(int sampleRate)
    : sampleRate_(sampleRate > 1 ? sampleRate : 44100), gust_(static_cast<float>(sampleRate_)) {
  slowLfo_.setSampleRate(static_cast<float>(sampleRate_));
  mediumLfo_.setSampleRate(static_cast<float>(sampleRate_));
  fastLfo_.setSampleRate(static_cast<float>(sampleRate_));
  panLfo_.setSampleRate(static_cast<float>(sampleRate_));
  gust_.setSampleRate(static_cast<float>(sampleRate_));
  updateFilters();
}

void LoomAmbienceRenderer::configure(
    const std::string& preset,
    float intensity,
    float volume,
    float detail,
    float motion,
    float spread,
    float accent,
    bool loop) {
  preset_ = presetFromToken(preset);
  intensity_ = clamp01(intensity);
  volume_ = clamp01(volume);
  detail_ = clamp01(detail);
  motion_ = clamp01(motion);
  spread_ = clamp01(spread);
  accent_ = clamp01(accent);
  loop_ = loop;
  finished_ = false;
  elapsedSeconds_ = 0.0f;
  gustTimer_ = 0.0f;
  dropletEnvelope_ = 0.0f;
  noiseLow_.reset();
  noiseMid_.reset();
  noiseHigh_.reset();
  noiseGust_.reset();
  noiseDrop_.reset();
  gust_.setSampleRate(static_cast<float>(sampleRate_));
  updateFilters();
}

int LoomAmbienceRenderer::render(uint8_t* pcm, int frames) {
  if (!pcm || frames <= 0) return 0;
  const int totalBytes = frames * kFrameBytes;
  std::memset(pcm, 0, totalBytes);
  if (finished_) return totalBytes;

  for (int i = 0; i < frames; ++i) {
    const float mono = nextMonoSample();
    const float pan = (0.06f + spread_ * 0.24f) * panLfo_.sine();
    panLfo_.advance();
    const short left = clampPcm16(mono * (1.0f - pan));
    const short right = clampPcm16(mono * (1.0f + pan));
    const int offset = i * kFrameBytes;
    pcm[offset] = static_cast<uint8_t>(left & 0xFF);
    pcm[offset + 1] = static_cast<uint8_t>((left >> 8) & 0xFF);
    pcm[offset + 2] = static_cast<uint8_t>(right & 0xFF);
    pcm[offset + 3] = static_cast<uint8_t>((right >> 8) & 0xFF);
  }

  return totalBytes;
}

void LoomAmbienceRenderer::stop() {
  finished_ = true;
}

void LoomAmbienceRenderer::setVolume(float volume) {
  volume_ = clamp01(volume);
}

bool LoomAmbienceRenderer::finished() const noexcept {
  return finished_;
}

float LoomAmbienceRenderer::clamp01(float value) {
  return std::max(0.0f, std::min(1.0f, value));
}

LoomAmbienceRenderer::Preset LoomAmbienceRenderer::presetFromToken(const std::string& token) {
  std::string normalized;
  normalized.reserve(token.size());
  for (unsigned char c : token) {
    if (std::isalnum(c)) normalized.push_back(static_cast<char>(std::tolower(c)));
  }
  if (normalized.find("rain") != std::string::npos || normalized.find("drizzle") != std::string::npos ||
      normalized.find("storm") != std::string::npos) {
    return Preset::Rain;
  }
  if (normalized.find("ocean") != std::string::npos || normalized.find("wave") != std::string::npos ||
      normalized.find("sea") != std::string::npos || normalized.find("surf") != std::string::npos) {
    return Preset::Ocean;
  }
  return Preset::Wind;
}

void LoomAmbienceRenderer::updateFilters() {
  const float sr = static_cast<float>(sampleRate_);
  const float detailBrightness = 0.8f + detail_ * 0.65f;
  const float intensityMod = 0.8f + intensity_ * 0.45f;
  lowPassLow_.setCoefficients(BiquadFilter::Type::LowPass, 180.0f * intensityMod * (0.9f + detail_ * 0.2f), 0.7f, sr);
  lowPassMid_.setCoefficients(BiquadFilter::Type::LowPass, 700.0f * intensityMod * detailBrightness, 0.5f, sr);
  highPassMid_.setCoefficients(BiquadFilter::Type::HighPass, 150.0f, 0.5f, sr);
  highPassHigh_.setCoefficients(BiquadFilter::Type::HighPass, 1700.0f + detail_ * 1600.0f + intensity_ * 900.0f, 0.6f, sr);
  lowPassHigh_.setCoefficients(BiquadFilter::Type::LowPass, 6200.0f + detail_ * 3800.0f, 0.45f, sr);
  gustFilter_.setCoefficients(BiquadFilter::Type::BandPass, 320.0f + accent_ * 220.0f, 1.2f + accent_ * 0.8f, sr);

  rainBedLowPass_.setCoefficients(BiquadFilter::Type::LowPass, 5200.0f + detail_ * 4200.0f + intensity_ * 1800.0f, 0.55f, sr);
  rainBedHighPass_.setCoefficients(BiquadFilter::Type::HighPass, 350.0f, 0.55f, sr);
  rainHissHighPass_.setCoefficients(BiquadFilter::Type::HighPass, 3200.0f + detail_ * 2200.0f, 0.7f, sr);
  rainDropBandPass_.setCoefficients(BiquadFilter::Type::BandPass, 1400.0f + detail_ * 1600.0f + accent_ * 500.0f, 1.0f + accent_ * 0.7f, sr);

  oceanSwellLowPass_.setCoefficients(BiquadFilter::Type::LowPass, 180.0f + intensity_ * 110.0f + accent_ * 70.0f, 0.8f, sr);
  oceanWashBandPass_.setCoefficients(BiquadFilter::Type::BandPass, 560.0f + detail_ * 580.0f + intensity_ * 280.0f, 0.7f, sr);
  oceanFoamHighPass_.setCoefficients(BiquadFilter::Type::HighPass, 2200.0f + detail_ * 1800.0f, 0.7f, sr);
}

void LoomAmbienceRenderer::maybeTriggerWindGust(float dt) {
  if (gust_.isActive()) return;
  gustTimer_ += dt;
  const float gustChance = (0.05f + motion_ * 0.08f) * (0.35f + intensity_ * 1.15f + accent_ * 0.35f);
  if (gustTimer_ > 1.0f / std::max(0.05f, gustChance)) {
    gust_.trigger(0.25f + intensity_ * 0.65f + accent_ * 0.35f);
    gustTimer_ = 0.0f;
  }
}

float LoomAmbienceRenderer::synthesizeWindSample() {
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;
  const float medMod = mediumLfo_.triangle() * 0.5f + 0.5f;
  const float fastMod = fastLfo_.sine() * 0.2f + 0.8f;
  const float randomMod = slowLfo_.smoothRandom() * 0.15f + 0.85f;
  slowLfo_.advance();
  mediumLfo_.advance();
  fastLfo_.advance();

  maybeTriggerWindGust(1.0f / sampleRate_);
  const float gustEnvelope = gust_.generate();
  gust_.update();

  const float shapedIntensity = std::sqrt(std::max(0.0f, intensity_ * slowMod * randomMod));
  const float detailGain = 0.8f + detail_ * 0.5f;

  float lowLayer = lowPassLow_.process(noiseLow_.brown()) * 0.5f * shapedIntensity * 0.6f;

  float midNoise = lowPassMid_.process(noiseMid_.pink());
  midNoise = highPassMid_.process(midNoise);
  const float midLayer = midNoise * 0.6f * shapedIntensity * medMod * (1.0f + shapedIntensity * 0.3f) * 0.85f * detailGain;

  float highNoise = highPassHigh_.process(noiseHigh_.pink());
  highNoise = lowPassHigh_.process(highNoise);
  const float highLayer = highNoise * 0.4f * std::pow(shapedIntensity, 2.4f) * fastMod * (0.12f + detail_ * 0.22f);

  float gustNoise = gustFilter_.process(noiseGust_.pink());
  const float gustLayer = gustNoise * gustEnvelope * (0.35f + accent_ * 0.45f);

  float speedWhoosh = 0.0f;
  if (intensity_ > 0.35f) {
    const float whooshNoise = noiseMid_.filtered(0.06f + intensity_ * 0.18f + detail_ * 0.06f, 0.2f + accent_ * 0.2f);
    const float whooshIntensity = std::sqrt((intensity_ - 0.35f) / 0.65f);
    speedWhoosh = whooshNoise * whooshIntensity * 0.30f * medMod;
  }

  const float mix = std::tanh((lowLayer + midLayer + highLayer + gustLayer + speedWhoosh) * 0.60f);
  return mix;
}

float LoomAmbienceRenderer::synthesizeRainSample() {
  slowLfo_.advance();
  mediumLfo_.advance();
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;
  const float density = 0.20f + intensity_ * 0.65f + accent_ * 0.25f;

  float bed = rainBedLowPass_.process(noiseMid_.pink());
  bed = rainBedHighPass_.process(bed);
  bed *= (0.22f + density * 0.38f) * (0.85f + detail_ * 0.35f);

  float hiss = rainHissHighPass_.process(noiseHigh_.white());
  hiss *= 0.04f + intensity_ * 0.12f + detail_ * 0.12f;

  const float triggerThreshold = 0.989f - intensity_ * 0.07f - accent_ * 0.05f;
  if (noiseDrop_.white() > triggerThreshold) {
    dropletEnvelope_ = 0.28f + intensity_ * 0.45f + accent_ * 0.25f;
  }
  dropletEnvelope_ *= 0.9935f - intensity_ * 0.0015f - motion_ * 0.0008f;
  float drop = rainDropBandPass_.process(noiseDrop_.white()) * dropletEnvelope_;
  drop *= 0.55f + slowMod * 0.25f;

  const float body = std::tanh((bed + hiss + drop) * (0.72f + density * 0.28f));
  return body;
}

float LoomAmbienceRenderer::synthesizeOceanSample() {
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;
  const float medMod = mediumLfo_.triangle() * 0.5f + 0.5f;
  slowLfo_.advance();
  mediumLfo_.advance();
  const float waveCycle = 0.5f * (1.0f + std::sin(elapsedSeconds_ * (0.08f + intensity_ * 0.05f + motion_ * 0.07f) * 2.0f * kPi));

  float swell = oceanSwellLowPass_.process(noiseLow_.brown());
  swell *= 0.16f + intensity_ * 0.18f + accent_ * 0.10f;

  float wash = oceanWashBandPass_.process(noiseMid_.pink());
  wash *= (0.12f + intensity_ * 0.24f + detail_ * 0.10f) * (0.30f + 0.70f * waveCycle) * (0.85f + medMod * 0.15f);

  float foam = oceanFoamHighPass_.process(noiseHigh_.white());
  const float crest = std::max(0.0f, (waveCycle - 0.58f) / 0.42f);
  foam *= crest * crest * (0.05f + intensity_ * 0.12f + detail_ * 0.10f) * (0.8f + slowMod * 0.2f);

  return std::tanh((swell + wash + foam) * 0.92f);
}

float LoomAmbienceRenderer::nextMonoSample() {
  if (finished_) return 0.0f;
  const float sample = [this]() {
    switch (preset_) {
      case Preset::Wind:
        return synthesizeWindSample();
      case Preset::Rain:
        return synthesizeRainSample();
      case Preset::Ocean:
        return synthesizeOceanSample();
    }
    return 0.0f;
  }();

  elapsedSeconds_ += 1.0f / sampleRate_;
  if (!loop_ && elapsedSeconds_ >= kAutoStopSeconds) {
    finished_ = true;
  }
  return sample * volume_ * (0.18f + 0.82f * intensity_);
}

}  // namespace jvn::audiofx
