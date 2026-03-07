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
  eventRng_.seed(0x10293847u + static_cast<uint32_t>(preset_) * 0x1f1f1f1fu);
  loop_ = loop;
  finished_ = false;
  elapsedSeconds_ = 0.0f;
  gustTimer_ = randomRange(0.2f, 0.8f);
  dropletEnvelope_ = 0.0f;
  rainDropTimer_ = randomRange(0.02f, 0.12f);
  noiseLow_.reset();
  noiseMid_.reset();
  noiseHigh_.reset();
  noiseGust_.reset();
  noiseDrop_.reset();
  noiseThunder_.reset();
  noiseFire_.reset();
  noiseInsect_.reset();
  noiseOceanFoam_.reset();
  noiseThunderBolt_.reset();
  noiseFireDetail_.reset();
  noiseInsect2_.reset();
  thunderRumblePhase_ = 0.0f;
  thunderCrackEnvelope_ = 0.0f;
  crackleEnvelope_ = 0.0f;
  chirpPhase_ = 0.0f;
  chirpEnvelope_ = 0.0f;
  windWhistlePhase_ = 0.0f;
  oceanCrashEnvelope_ = 0.0f;
  oceanCrashTimer_ = randomRange(0.6f, 1.5f);
  thunderBoltEnvelope_ = 0.0f;
  thunderBoltTimer_ = randomRange(0.4f, 1.4f);
  thunderBoltDecayRate_ = 0.9990f;
  thunderDropEnvelope_ = 0.0f;
  thunderDropTimer_ = randomRange(0.02f, 0.10f);
  fireSnapEnvelope_ = 0.0f;
  firePopEnvelope_ = 0.0f;
  fireEmberPhase_ = 0.0f;
  fireCrackleTimer_ = randomRange(0.3f, 1.0f);
  firePopTimer_ = randomRange(0.08f, 0.30f);
  fireSnapTimer_ = randomRange(0.02f, 0.10f);
  cricket2Phase_ = 0.0f;
  cricket2Envelope_ = 0.0f;
  cricket3Phase_ = 0.0f;
  frogEnvelope_ = 0.0f;
  frogTimer_ = randomRange(2.0f, 4.5f);
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
  if (normalized.find("thunder") != std::string::npos || normalized.find("lightning") != std::string::npos) {
    return Preset::Thunder;
  }
  if (normalized.find("fire") != std::string::npos || normalized.find("hearth") != std::string::npos ||
      normalized.find("campfire") != std::string::npos) {
    return Preset::Fireplace;
  }
  if (normalized.find("insect") != std::string::npos || normalized.find("cricket") != std::string::npos ||
      normalized.find("cicada") != std::string::npos || normalized.find("night") != std::string::npos) {
    return Preset::NightInsects;
  }
  return Preset::Wind;
}

float LoomAmbienceRenderer::nextRandom01() {
  return eventDist_(eventRng_);
}

float LoomAmbienceRenderer::randomRange(float minValue, float maxValue) {
  const float lo = std::min(minValue, maxValue);
  const float hi = std::max(minValue, maxValue);
  return lo + (hi - lo) * nextRandom01();
}

float LoomAmbienceRenderer::sampleEventInterval(float rateHz, float minimumSeconds) {
  const float safeRate = std::max(0.01f, rateHz);
  const float u = std::max(1.0e-5f, 1.0f - nextRandom01());
  return minimumSeconds + (-std::log(u) / safeRate);
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

  thunderRumbleLowPass_.setCoefficients(BiquadFilter::Type::LowPass, 80.0f + intensity_ * 60.0f + accent_ * 30.0f, 0.9f, sr);
  thunderCrackBandPass_.setCoefficients(BiquadFilter::Type::BandPass, 1800.0f + detail_ * 2400.0f + accent_ * 600.0f, 1.5f + accent_ * 1.0f, sr);
  thunderRainHighPass_.setCoefficients(BiquadFilter::Type::HighPass, 2800.0f + detail_ * 1800.0f, 0.6f, sr);

  fireCrackleBandPass_.setCoefficients(BiquadFilter::Type::BandPass, 900.0f + detail_ * 1200.0f + accent_ * 400.0f, 1.8f + detail_ * 0.8f, sr);
  fireBaseLowPass_.setCoefficients(BiquadFilter::Type::LowPass, 220.0f + intensity_ * 80.0f, 0.85f, sr);
  fireHissHighPass_.setCoefficients(BiquadFilter::Type::HighPass, 3600.0f + detail_ * 2400.0f, 0.55f, sr);

  insectChirpBandPass_.setCoefficients(BiquadFilter::Type::BandPass, 3200.0f + detail_ * 2800.0f + accent_ * 800.0f, 3.0f + accent_ * 2.0f, sr);
  insectBedLowPass_.setCoefficients(BiquadFilter::Type::LowPass, 280.0f + intensity_ * 120.0f, 0.7f, sr);
  insectDetailHighPass_.setCoefficients(BiquadFilter::Type::HighPass, 1600.0f + detail_ * 1400.0f, 0.65f, sr);

  // Ocean enhanced
  oceanUndertowLowPass_.setCoefficients(BiquadFilter::Type::LowPass, 55.0f + intensity_ * 25.0f, 0.85f, sr);
  oceanCrashBandPass_.setCoefficients(BiquadFilter::Type::BandPass, 600.0f + detail_ * 400.0f + accent_ * 200.0f, 1.0f + accent_ * 0.5f, sr);
  oceanSprayHighPass_.setCoefficients(BiquadFilter::Type::HighPass, 4000.0f + detail_ * 2000.0f, 0.6f, sr);

  // Thunder enhanced
  thunderSubBassLowPass_.setCoefficients(BiquadFilter::Type::LowPass, 45.0f + intensity_ * 20.0f, 0.9f, sr);
  thunderDropBandPass_.setCoefficients(BiquadFilter::Type::BandPass, 1800.0f + detail_ * 1200.0f + accent_ * 400.0f, 1.2f + accent_ * 0.6f, sr);

  // Fireplace enhanced
  fireSnapBandPass_.setCoefficients(BiquadFilter::Type::BandPass, 2500.0f + detail_ * 1500.0f + accent_ * 500.0f, 2.0f + detail_ * 1.0f, sr);
  fireEmberLowPass_.setCoefficients(BiquadFilter::Type::LowPass, 120.0f + intensity_ * 60.0f, 0.9f, sr);

  // Night insects enhanced
  cricket2BandPass_.setCoefficients(BiquadFilter::Type::BandPass, 5200.0f + detail_ * 1800.0f + accent_ * 600.0f, 3.5f + accent_ * 1.5f, sr);
  frogBandPass_.setCoefficients(BiquadFilter::Type::BandPass, 320.0f + accent_ * 180.0f, 2.0f + accent_ * 1.0f, sr);
}

void LoomAmbienceRenderer::maybeTriggerWindGust(float dt) {
  if (gust_.isActive()) return;
  gustTimer_ -= dt;
  if (gustTimer_ <= 0.0f) {
    const float gustEnergy =
        (0.22f + intensity_ * 0.60f + accent_ * 0.28f + motion_ * 0.18f)
        * randomRange(0.88f, 1.15f);
    gust_.trigger(gustEnergy);
    const float gustRateHz = 0.08f + motion_ * 0.11f + intensity_ * 0.08f + accent_ * 0.04f;
    gustTimer_ = sampleEventInterval(gustRateHz, 0.7f);
  }
}

float LoomAmbienceRenderer::synthesizeWindSample() {
  const float dt = 1.0f / sampleRate_;
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
    speedWhoosh = whooshNoise * whooshIntensity * (0.24f + motion_ * 0.10f) * medMod;
  }

  const float whistleDrive = std::max(
      0.0f,
      gustEnvelope * (0.9f + accent_ * 0.45f)
          + std::max(0.0f, speedWhoosh) * (0.6f + detail_ * 0.35f)
          - 0.10f);
  const float whistleFreq =
      620.0f + detail_ * 1050.0f + accent_ * 650.0f + medMod * 240.0f + gustEnvelope * 360.0f;
  windWhistlePhase_ += whistleFreq * dt;
  if (windWhistlePhase_ > 1.0f) windWhistlePhase_ -= std::floor(windWhistlePhase_);
  const float whistle = std::sin(windWhistlePhase_ * 2.0f * kPi)
      * whistleDrive * whistleDrive
      * (0.015f + detail_ * 0.055f + accent_ * 0.040f);

  const float edgeFlutter =
      lowPassHigh_.process(highPassHigh_.process(noiseGust_.white()))
      * whistleDrive * (0.010f + detail_ * 0.020f);

  const float mix = std::tanh(
      (lowLayer + midLayer + highLayer + gustLayer + speedWhoosh + whistle + edgeFlutter) * 0.60f);
  return mix;
}

float LoomAmbienceRenderer::synthesizeRainSample() {
  const float dt = 1.0f / sampleRate_;
  slowLfo_.advance();
  mediumLfo_.advance();
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;
  const float density = 0.20f + intensity_ * 0.65f + accent_ * 0.25f;

  float bed = rainBedLowPass_.process(noiseMid_.pink());
  bed = rainBedHighPass_.process(bed);
  bed *= (0.22f + density * 0.38f) * (0.85f + detail_ * 0.35f);

  float hiss = rainHissHighPass_.process(noiseHigh_.white());
  hiss *= 0.04f + intensity_ * 0.12f + detail_ * 0.12f;

  rainDropTimer_ -= dt;
  if (rainDropTimer_ <= 0.0f) {
    dropletEnvelope_ = std::max(
        dropletEnvelope_,
        (0.14f + intensity_ * 0.22f + accent_ * 0.30f) * randomRange(0.85f, 1.20f));
    const float dropRateHz = 8.0f + intensity_ * 18.0f + accent_ * 3.0f + motion_ * 5.0f;
    rainDropTimer_ = sampleEventInterval(dropRateHz, 0.008f);
  }
  dropletEnvelope_ *= 0.9935f - intensity_ * 0.0015f - motion_ * 0.0008f;
  float drop = rainDropBandPass_.process(noiseDrop_.white()) * dropletEnvelope_;
  drop *= 0.36f + slowMod * 0.16f + accent_ * 0.10f;
  float splash = rainHissHighPass_.process(noiseDrop_.white());
  splash *= dropletEnvelope_ * (0.10f + detail_ * 0.10f + accent_ * 0.12f);

  const float body = std::tanh((bed + hiss + drop + splash) * (0.72f + density * 0.28f));
  return body;
}

float LoomAmbienceRenderer::synthesizeOceanSample() {
  slowLfo_.advance();
  mediumLfo_.advance();
  fastLfo_.advance();
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;

  // Dual-period wave cycle — primary (8-14s) + secondary (4-7s)
  const float primaryRate = 0.08f + intensity_ * 0.04f + motion_ * 0.03f;
  const float secondaryRate = 0.18f + intensity_ * 0.06f + motion_ * 0.04f;
  const float primaryWave = std::sin(elapsedSeconds_ * primaryRate * 2.0f * kPi);
  const float secondaryWave = std::sin(elapsedSeconds_ * secondaryRate * 2.0f * kPi);

  // Asymmetric blend — sharper crests, gentler troughs
  const float rawCycle = 0.65f * primaryWave + 0.35f * secondaryWave;
  const float shaped = rawCycle > 0.0f
      ? std::pow(rawCycle, 0.7f)
      : -std::pow(-rawCycle, 1.4f) * 0.6f;
  const float waveCycle = shaped * 0.5f + 0.5f;

  // 1) Undertow bass (20-80 Hz) — rumble between waves
  float undertow = oceanUndertowLowPass_.process(noiseLow_.brown());
  undertow *= (0.12f + intensity_ * 0.16f + accent_ * 0.08f) * (1.0f - waveCycle * 0.4f);

  // 2) Swell body (100-300 Hz) — follows wave cycle
  float swell = oceanSwellLowPass_.process(noiseLow_.brown());
  swell *= (0.14f + intensity_ * 0.22f + accent_ * 0.10f) * (0.25f + 0.75f * waveCycle);

  // 3) Wash mid (300-1200 Hz) — breaking wave body
  float wash = oceanWashBandPass_.process(noiseMid_.pink());
  const float washGain = std::max(0.0f, waveCycle - 0.2f) / 0.8f;
  wash *= (0.15f + intensity_ * 0.28f + detail_ * 0.14f) * washGain
        * (0.85f + mediumLfo_.triangle() * 0.15f);

  // 4) Foam spray (2000+ Hz) — crests only
  const float crest = std::max(0.0f, (waveCycle - 0.5f) / 0.5f);
  float foam = oceanFoamHighPass_.process(noiseHigh_.white());
  float spray = oceanSprayHighPass_.process(noiseOceanFoam_.white());
  foam *= crest * crest * (0.07f + intensity_ * 0.14f + detail_ * 0.11f);
  spray *= crest * crest * crest * (0.03f + detail_ * 0.07f);

  // 5) Crash events — large wave impacts
  oceanCrashTimer_ -= 1.0f / sampleRate_;
  if (oceanCrashTimer_ <= 0.0f && crest > randomRange(0.32f, 0.68f)) {
    oceanCrashEnvelope_ =
        (0.34f + intensity_ * 0.34f + accent_ * 0.34f) * randomRange(0.88f, 1.18f);
    const float crashRateHz = 0.06f + intensity_ * 0.16f + accent_ * 0.04f;
    oceanCrashTimer_ = sampleEventInterval(crashRateHz, 0.65f);
  }
  oceanCrashEnvelope_ *= 0.9975f - motion_ * 0.0006f;
  float crash = oceanCrashBandPass_.process(noiseOceanFoam_.pink());
  crash *= oceanCrashEnvelope_ * (0.26f + detail_ * 0.20f + accent_ * 0.18f);
  float crashSpray = oceanSprayHighPass_.process(noiseOceanFoam_.white());
  crashSpray *= oceanCrashEnvelope_ * oceanCrashEnvelope_ * (0.05f + detail_ * 0.08f + accent_ * 0.06f);

  // 6) Distant continuous roar
  float roar = noiseMid_.filtered(0.04f + intensity_ * 0.06f, 0.1f + accent_ * 0.1f);
  roar *= (0.05f + intensity_ * 0.07f) * slowMod;

  return std::tanh((undertow + swell + wash + foam + spray + crash + crashSpray + roar) * 0.78f);
}

float LoomAmbienceRenderer::synthesizeThunderSample() {
  const float dt = 1.0f / sampleRate_;
  slowLfo_.advance();
  mediumLfo_.advance();
  fastLfo_.advance();
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;

  // 1) Deep sub-bass foundation (20-50 Hz) — atmospheric pressure
  float subBass = thunderSubBassLowPass_.process(noiseThunder_.brown());
  subBass *= (0.16f + intensity_ * 0.24f + accent_ * 0.10f) * (0.8f + slowMod * 0.2f);

  // 2) Rolling rumble (50-200 Hz) — continuous thunder body
  thunderRumblePhase_ += (0.025f + motion_ * 0.04f) * dt;
  if (thunderRumblePhase_ > 1.0f) thunderRumblePhase_ -= 1.0f;
  const float rumbleMod = 0.75f + 0.25f * std::sin(thunderRumblePhase_ * 2.0f * kPi);
  float rumble = thunderRumbleLowPass_.process(noiseThunder_.brown());
  rumble *= (0.20f + intensity_ * 0.30f + accent_ * 0.12f) * rumbleMod;

  // 3) Lightning bolt events — multi-stage (crack → rolling decay)
  thunderBoltTimer_ -= dt;
  if (thunderBoltTimer_ <= 0.0f && thunderBoltEnvelope_ < 0.08f) {
    thunderBoltEnvelope_ = 0.85f + intensity_ * 0.15f;
    thunderCrackEnvelope_ = 0.92f + detail_ * 0.12f + accent_ * 0.08f;
    // Randomize decay rate for distance variation
    const float r = nextRandom01();
    thunderBoltDecayRate_ = 0.9980f + r * 0.0016f;
    const float boltRateHz = 0.08f + intensity_ * 0.20f + accent_ * 0.04f + motion_ * 0.05f;
    thunderBoltTimer_ = sampleEventInterval(boltRateHz, 1.0f);
  }
  thunderBoltEnvelope_ *= thunderBoltDecayRate_;
  thunderCrackEnvelope_ *= 0.9865f - detail_ * 0.0025f;

  // Bolt bright crack — only at high envelope (initial strike)
  const float brightPhase = thunderCrackEnvelope_ * thunderCrackEnvelope_;
  float crack = thunderCrackBandPass_.process(noiseThunderBolt_.white());
  crack *= brightPhase * brightPhase * (0.50f + detail_ * 0.30f + accent_ * 0.38f);
  float crackAir = thunderRainHighPass_.process(noiseThunderBolt_.white());
  crackAir *= thunderCrackEnvelope_ * (0.02f + detail_ * 0.04f + accent_ * 0.10f);

  // Bolt rolling body — follows full envelope but shaped
  const float bodyPhase = thunderBoltEnvelope_ * (1.0f - std::min(0.55f, brightPhase * 0.35f));
  float boltRumble = thunderRumbleLowPass_.process(noiseThunderBolt_.brown());
  boltRumble *= bodyPhase * (0.4f + intensity_ * 0.3f);

  // 4) Rain bed — dense continuous rain
  float rain = thunderRainHighPass_.process(noiseMid_.pink());
  rain *= (0.10f + intensity_ * 0.16f + detail_ * 0.10f) * (0.85f + mediumLfo_.triangle() * 0.15f);

  // Rain droplet events — individual impacts
  thunderDropTimer_ -= dt;
  if (thunderDropTimer_ <= 0.0f) {
    thunderDropEnvelope_ = std::max(
        thunderDropEnvelope_,
        (0.16f + intensity_ * 0.20f + accent_ * 0.16f) * randomRange(0.85f, 1.18f));
    const float dropRateHz = 6.0f + intensity_ * 14.0f + accent_ * 2.0f;
    thunderDropTimer_ = sampleEventInterval(dropRateHz, 0.01f);
  }
  thunderDropEnvelope_ *= 0.9945f - motion_ * 0.001f;
  float drop = thunderDropBandPass_.process(noiseDrop_.white());
  drop *= thunderDropEnvelope_ * (0.25f + detail_ * 0.20f);

  // 5) Wind presence — storm winds
  float wind = noiseMid_.filtered(0.05f + intensity_ * 0.10f, 0.15f + motion_ * 0.1f);
  wind *= (0.06f + intensity_ * 0.10f + motion_ * 0.06f) * (0.8f + slowMod * 0.4f);

  return std::tanh((subBass + rumble + crack + crackAir + boltRumble + rain + drop + wind) * 0.72f);
}

float LoomAmbienceRenderer::synthesizeFireplaceSample() {
  const float dt = 1.0f / sampleRate_;
  slowLfo_.advance();
  mediumLfo_.advance();
  fastLfo_.advance();
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;
  const float medMod = mediumLfo_.triangle() * 0.5f + 0.5f;
  const float fastMod = fastLfo_.sine() * 0.5f + 0.5f;

  // Draft breathing — slow modulation simulating air currents
  const float breathe = 0.78f + 0.22f * medMod * (0.8f + motion_ * 0.4f);

  // 1) Ember warmth (40-180 Hz) — oscillating warm glow
  fireEmberPhase_ += (0.08f + motion_ * 0.06f) * dt;
  if (fireEmberPhase_ > 1.0f) fireEmberPhase_ -= 1.0f;
  const float emberGlow = 0.7f + 0.3f * std::sin(fireEmberPhase_ * 2.0f * kPi);
  float ember = fireEmberLowPass_.process(noiseFire_.brown());
  ember *= (0.14f + intensity_ * 0.18f) * emberGlow;

  // 2) Base warmth (120-300 Hz) — fire body
  float base = fireBaseLowPass_.process(noiseFire_.brown());
  base *= (0.18f + intensity_ * 0.24f) * (0.85f + slowMod * 0.15f);

  // 3) Large cracks — infrequent, loud, long decay
  fireCrackleTimer_ -= dt;
  if (fireCrackleTimer_ <= 0.0f && crackleEnvelope_ < 0.10f) {
    crackleEnvelope_ =
        (0.34f + intensity_ * 0.34f + accent_ * 0.22f) * randomRange(0.85f, 1.20f);
    const float crackleRateHz = 0.7f + intensity_ * 1.6f + accent_ * 1.2f;
    fireCrackleTimer_ = sampleEventInterval(crackleRateHz, 0.12f);
  }
  crackleEnvelope_ *= 0.9972f - motion_ * 0.0008f;
  float crackle = fireCrackleBandPass_.process(noiseFire_.white());
  crackle *= crackleEnvelope_ * (0.50f + detail_ * 0.30f + accent_ * 0.15f);

  // 4) Medium pops — moderately frequent, mid energy
  firePopTimer_ -= dt;
  if (firePopTimer_ <= 0.0f && firePopEnvelope_ < 0.10f) {
    firePopEnvelope_ =
        (0.18f + intensity_ * 0.22f + accent_ * 0.12f) * randomRange(0.85f, 1.18f);
    const float popRateHz = 2.5f + intensity_ * 5.0f + accent_ * 2.5f + detail_ * 1.5f;
    firePopTimer_ = sampleEventInterval(popRateHz, 0.05f);
  }
  firePopEnvelope_ *= 0.9952f - motion_ * 0.0006f;
  float pop = fireCrackleBandPass_.process(noiseFireDetail_.white());
  pop *= firePopEnvelope_ * (0.35f + detail_ * 0.25f);

  // 5) Small snaps — frequent, quiet, short
  fireSnapTimer_ -= dt;
  if (fireSnapTimer_ <= 0.0f && fireSnapEnvelope_ < 0.10f) {
    fireSnapEnvelope_ =
        (0.11f + intensity_ * 0.15f + detail_ * 0.10f) * randomRange(0.90f, 1.15f);
    const float snapRateHz = 5.5f + intensity_ * 9.0f + detail_ * 7.0f;
    fireSnapTimer_ = sampleEventInterval(snapRateHz, 0.02f);
  }
  fireSnapEnvelope_ *= 0.992f - motion_ * 0.001f;
  float snap = fireSnapBandPass_.process(noiseFireDetail_.white());
  snap *= fireSnapEnvelope_ * (0.20f + detail_ * 0.25f);

  // 6) Sizzle/hiss — high frequency continuous texture
  float hiss = fireHissHighPass_.process(noiseHigh_.pink());
  hiss *= (0.03f + intensity_ * 0.06f + detail_ * 0.08f) * fastMod;

  return std::tanh((ember + base + crackle + pop + snap + hiss) * breathe * 0.76f);
}

float LoomAmbienceRenderer::synthesizeNightInsectsSample() {
  const float dt = 1.0f / sampleRate_;
  slowLfo_.advance();
  mediumLfo_.advance();
  fastLfo_.advance();
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;

  // Density chorus — insects synchronize and desynchronize
  const float chorusMod = 0.55f + 0.45f * slowMod * (0.6f + motion_ * 0.8f);

  // 1) Night bed (50-300 Hz) — warm darkness
  float bed = insectBedLowPass_.process(noiseInsect_.brown());
  bed *= (0.10f + intensity_ * 0.14f) * (0.85f + slowMod * 0.15f);

  // 2) Cricket species 1 (3000-5000 Hz) — rhythmic chirps with burst pattern
  chirpPhase_ += (3.0f + accent_ * 4.5f + motion_ * 2.5f) * dt;
  if (chirpPhase_ > 1.0f) chirpPhase_ -= 1.0f;
  // Burst pattern: chirp-chirp-pause (3 quick pulses then gap)
  const float burstPhase = std::fmod(chirpPhase_ * 3.0f, 1.0f);
  const float chirpPulse = burstPhase < 0.6f
      ? std::max(0.0f, std::sin(burstPhase / 0.6f * kPi))
      : 0.0f;
  const float chirpGate = chirpPulse * chirpPulse;

  const float chirp1Trigger = 0.003f + intensity_ * 0.006f + accent_ * 0.005f;
  if (noiseInsect_.white() > (1.0f - chirp1Trigger) && chirpEnvelope_ < 0.1f) {
    chirpEnvelope_ = 0.28f + intensity_ * 0.35f + accent_ * 0.25f;
  }
  chirpEnvelope_ *= 0.9960f - motion_ * 0.0008f;
  float chirp1 = insectChirpBandPass_.process(noiseInsect_.white());
  chirp1 *= chirpGate * chirpEnvelope_ * (0.35f + detail_ * 0.40f) * chorusMod;

  // 3) Cricket species 2 (4500-7000 Hz) — different rhythm, higher pitch
  cricket2Phase_ += (4.5f + accent_ * 3.0f + motion_ * 1.8f) * dt;
  if (cricket2Phase_ > 1.0f) cricket2Phase_ -= 1.0f;
  const float chirp2Pulse = std::max(0.0f, std::sin(cricket2Phase_ * 2.0f * kPi));
  const float chirp2Gate = chirp2Pulse * chirp2Pulse * chirp2Pulse;

  const float chirp2Trigger = 0.002f + intensity_ * 0.005f + accent_ * 0.003f;
  if (noiseInsect2_.white() > (1.0f - chirp2Trigger) && cricket2Envelope_ < 0.1f) {
    cricket2Envelope_ = 0.20f + intensity_ * 0.28f + accent_ * 0.18f;
  }
  cricket2Envelope_ *= 0.9955f - motion_ * 0.0007f;
  float chirp2 = cricket2BandPass_.process(noiseInsect2_.white());
  chirp2 *= chirp2Gate * cricket2Envelope_ * (0.22f + detail_ * 0.30f) * chorusMod;

  // 4) Cicada drone (1500-3000 Hz) — sustained buzzing, accent-dependent
  cricket3Phase_ += (6.0f + motion_ * 3.0f) * dt;
  if (cricket3Phase_ > 1.0f) cricket3Phase_ -= 1.0f;
  const float cicadaMod = 0.5f + 0.5f * std::sin(cricket3Phase_ * 2.0f * kPi);
  const float cicadaPresence = std::max(0.0f, accent_ - 0.25f) / 0.75f;
  float cicada = insectDetailHighPass_.process(noiseMid_.pink());
  cicada *= cicadaPresence * cicadaMod * (0.06f + intensity_ * 0.10f + detail_ * 0.06f);

  // 5) Frog croaks (200-500 Hz) — occasional low-frequency bursts
  frogTimer_ -= dt;
  if (frogTimer_ <= 0.0f && frogEnvelope_ < 0.08f) {
    frogEnvelope_ =
        (0.22f + intensity_ * 0.20f + accent_ * 0.18f) * randomRange(0.90f, 1.15f);
    const float frogRateHz = 0.04f + intensity_ * 0.10f + accent_ * 0.12f;
    frogTimer_ = sampleEventInterval(frogRateHz, 0.9f);
  }
  frogEnvelope_ *= 0.9985f - motion_ * 0.0003f;
  float frog = frogBandPass_.process(noiseInsect2_.white());
  frog *= frogEnvelope_ * (0.15f + accent_ * 0.20f);

  // 6) Leaf/grass rustle — detail texture
  float rustle = insectDetailHighPass_.process(noiseHigh_.pink());
  rustle *= (0.03f + detail_ * 0.06f + intensity_ * 0.03f) * (0.7f + mediumLfo_.triangle() * 0.3f);

  return std::tanh((bed + chirp1 + chirp2 + cicada + frog + rustle) * 0.82f);
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
      case Preset::Thunder:
        return synthesizeThunderSample();
      case Preset::Fireplace:
        return synthesizeFireplaceSample();
      case Preset::NightInsects:
        return synthesizeNightInsectsSample();
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
