#include "loom_ambience_synth.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstring>

namespace jvn::audiofx::detail {
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

void SharedLfoBank::setSampleRate(float sampleRate) {
  slow.setSampleRate(sampleRate);
  medium.setSampleRate(sampleRate);
  fast.setSampleRate(sampleRate);
  pan.setSampleRate(sampleRate);
}

void SharedLfoBank::reset() {
  slow.reset();
  medium.reset();
  fast.reset();
  pan.reset();
}

void SharedNoiseBank::reset() {
  low.reset();
  mid.reset();
  high.reset();
  gust.reset();
  drop.reset();
  thunder.reset();
  fire.reset();
  insect.reset();
  oceanFoam.reset();
  thunderBolt.reset();
  fireDetail.reset();
  insect2.reset();
}

void WindState::setSampleRate(float sampleRate) {
  gust.setSampleRate(sampleRate);
}

void WindState::reset(float initialGustTimer) {
  gustTimer = initialGustTimer;
  whistlePhase = 0.0f;
  whistleOvertonePhase = 0.0f;
  gust.reset();
  lowPassLow.reset();
  lowPassMid.reset();
  highPassMid.reset();
  highPassHigh.reset();
  lowPassHigh.reset();
  gustFilter.reset();
}

void RainState::reset(float initialDropTimer) {
  dropletEnvelope = 0.0f;
  impactEnvelope = 0.0f;
  impactPhase = 0.0f;
  impactFrequency = 1200.0f;
  dropTimer = initialDropTimer;
  bedLowPass.reset();
  bedHighPass.reset();
  hissHighPass.reset();
  dropBandPass.reset();
  impactBandPass.reset();
}

void OceanState::reset(float initialCrashTimer) {
  crashEnvelope = 0.0f;
  backwashEnvelope = 0.0f;
  crashTimer = initialCrashTimer;
  swellLowPass.reset();
  washBandPass.reset();
  foamHighPass.reset();
  undertowLowPass.reset();
  crashBandPass.reset();
  sprayHighPass.reset();
  backwashBandPass.reset();
}

void ThunderState::reset(float initialBoltTimer, float initialDropTimer) {
  rumblePhase = 0.0f;
  crackEnvelope = 0.0f;
  boltEnvelope = 0.0f;
  boltTimer = initialBoltTimer;
  boltDecayRate = 0.9990f;
  rollDelaySeconds = 0.0f;
  dropEnvelope = 0.0f;
  dropTimer = initialDropTimer;
  rumbleLowPass.reset();
  crackBandPass.reset();
  rainHighPass.reset();
  subBassLowPass.reset();
  dropBandPass.reset();
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

void FireplaceState::reset(float initialCrackleTimer, float initialPopTimer, float initialSnapTimer) {
  crackleEnvelope = 0.0f;
  popEnvelope = 0.0f;
  snapEnvelope = 0.0f;
  emberPhase = 0.0f;
  crackleTimer = initialCrackleTimer;
  popTimer = initialPopTimer;
  snapTimer = initialSnapTimer;
  crackleBandPass.reset();
  baseLowPass.reset();
  hissHighPass.reset();
  snapBandPass.reset();
  emberLowPass.reset();
}

void NightInsectsState::reset(float initialFrogTimer) {
  chirpPhase = 0.0f;
  chirpEnvelope = 0.0f;
  cricket2Phase = 0.0f;
  cricket2Envelope = 0.0f;
  cricket3Phase = 0.0f;
  frogEnvelope = 0.0f;
  frogTimer = initialFrogTimer;
  chirpBandPass.reset();
  bedLowPass.reset();
  detailHighPass.reset();
  cricket2BandPass.reset();
  frogBandPass.reset();
}

LoomAmbienceSynthCore::LoomAmbienceSynthCore(int sampleRate) : sampleRate_(sampleRate > 1 ? sampleRate : 44100) {
  const float sampleRateF = static_cast<float>(sampleRate_);
  lfos_.setSampleRate(sampleRateF);
  wind_.setSampleRate(sampleRateF);
  master_.setSampleRate(sampleRateF);
  updateFilters();
}

void LoomAmbienceSynthCore::configure(
    const std::string& preset,
    float intensity,
    float volume,
    float detail,
    float motion,
    float spread,
    float accent,
    bool loop) {
  preset_ = presetFromToken(preset);
  controls_.intensity = clamp01(intensity);
  controls_.volume = clamp01(volume);
  controls_.detail = clamp01(detail);
  controls_.motion = clamp01(motion);
  controls_.spread = clamp01(spread);
  controls_.accent = clamp01(accent);
  controls_.loop = loop;
  eventRng_.seed(0x10293847u + static_cast<uint32_t>(preset_) * 0x1f1f1f1fu);
  finished_ = false;
  elapsedSeconds_ = 0.0f;
  lfos_.reset();
  noise_.reset();
  resetDynamicState();
  master_.reset();
  updateFilters();
}

int LoomAmbienceSynthCore::render(uint8_t* pcm, int frames) {
  if (!pcm || frames <= 0) return 0;
  const int totalBytes = frames * kFrameBytes;
  std::memset(pcm, 0, totalBytes);
  if (finished_) return totalBytes;

  for (int i = 0; i < frames; ++i) {
    const float mono = nextMonoSample();
    const float pan = (0.06f + controls_.spread * 0.24f) * lfos_.pan.sine();
    lfos_.pan.advance();
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

void LoomAmbienceSynthCore::stop() {
  finished_ = true;
}

void LoomAmbienceSynthCore::setVolume(float volume) {
  controls_.volume = clamp01(volume);
}

bool LoomAmbienceSynthCore::finished() const noexcept {
  return finished_;
}

float LoomAmbienceSynthCore::clamp01(float value) {
  return std::max(0.0f, std::min(1.0f, value));
}

AmbiencePreset LoomAmbienceSynthCore::presetFromToken(const std::string& token) {
  std::string normalized;
  normalized.reserve(token.size());
  for (unsigned char c : token) {
    if (std::isalnum(c)) normalized.push_back(static_cast<char>(std::tolower(c)));
  }
  if (normalized.find("rain") != std::string::npos || normalized.find("drizzle") != std::string::npos ||
      normalized.find("storm") != std::string::npos) {
    return AmbiencePreset::Rain;
  }
  if (normalized.find("ocean") != std::string::npos || normalized.find("wave") != std::string::npos ||
      normalized.find("sea") != std::string::npos || normalized.find("surf") != std::string::npos) {
    return AmbiencePreset::Ocean;
  }
  if (normalized.find("thunder") != std::string::npos || normalized.find("lightning") != std::string::npos) {
    return AmbiencePreset::Thunder;
  }
  if (normalized.find("fire") != std::string::npos || normalized.find("hearth") != std::string::npos ||
      normalized.find("campfire") != std::string::npos) {
    return AmbiencePreset::Fireplace;
  }
  if (normalized.find("insect") != std::string::npos || normalized.find("cricket") != std::string::npos ||
      normalized.find("cicada") != std::string::npos || normalized.find("night") != std::string::npos) {
    return AmbiencePreset::NightInsects;
  }
  return AmbiencePreset::Wind;
}

float LoomAmbienceSynthCore::nextRandom01() {
  return eventDist_(eventRng_);
}

float LoomAmbienceSynthCore::randomRange(float minValue, float maxValue) {
  const float lo = std::min(minValue, maxValue);
  const float hi = std::max(minValue, maxValue);
  return lo + (hi - lo) * nextRandom01();
}

float LoomAmbienceSynthCore::sampleEventInterval(float rateHz, float minimumSeconds) {
  const float safeRate = std::max(0.01f, rateHz);
  const float u = std::max(1.0e-5f, 1.0f - nextRandom01());
  return minimumSeconds + (-std::log(u) / safeRate);
}

void LoomAmbienceSynthCore::resetDynamicState() {
  wind_.reset(randomRange(0.2f, 0.8f));
  rain_.reset(randomRange(0.02f, 0.12f));
  ocean_.reset(randomRange(0.6f, 1.5f));
  thunder_.reset(randomRange(0.4f, 1.4f), randomRange(0.02f, 0.10f));
  fireplace_.reset(
      randomRange(0.3f, 1.0f),
      randomRange(0.08f, 0.30f),
      randomRange(0.02f, 0.10f));
  insects_.reset(randomRange(2.0f, 4.5f));
}

void LoomAmbienceSynthCore::updateFilters() {
  const float sr = static_cast<float>(sampleRate_);
  const float detailBrightness = 0.8f + controls_.detail * 0.65f;
  const float intensityMod = 0.8f + controls_.intensity * 0.45f;

  wind_.lowPassLow.setCoefficients(
      BiquadFilter::Type::LowPass,
      180.0f * intensityMod * (0.9f + controls_.detail * 0.2f),
      0.7f,
      sr);
  wind_.lowPassMid.setCoefficients(
      BiquadFilter::Type::LowPass,
      700.0f * intensityMod * detailBrightness,
      0.5f,
      sr);
  wind_.highPassMid.setCoefficients(BiquadFilter::Type::HighPass, 150.0f, 0.5f, sr);
  wind_.highPassHigh.setCoefficients(
      BiquadFilter::Type::HighPass,
      1700.0f + controls_.detail * 1600.0f + controls_.intensity * 900.0f,
      0.6f,
      sr);
  wind_.lowPassHigh.setCoefficients(
      BiquadFilter::Type::LowPass,
      6200.0f + controls_.detail * 3800.0f,
      0.45f,
      sr);
  wind_.gustFilter.setCoefficients(
      BiquadFilter::Type::BandPass,
      320.0f + controls_.accent * 220.0f,
      1.2f + controls_.accent * 0.8f,
      sr);

  rain_.bedLowPass.setCoefficients(
      BiquadFilter::Type::LowPass,
      5200.0f + controls_.detail * 4200.0f + controls_.intensity * 1800.0f,
      0.55f,
      sr);
  rain_.bedHighPass.setCoefficients(BiquadFilter::Type::HighPass, 350.0f, 0.55f, sr);
  rain_.hissHighPass.setCoefficients(
      BiquadFilter::Type::HighPass,
      3200.0f + controls_.detail * 2200.0f,
      0.7f,
      sr);
  rain_.dropBandPass.setCoefficients(
      BiquadFilter::Type::BandPass,
      1400.0f + controls_.detail * 1600.0f + controls_.accent * 500.0f,
      1.0f + controls_.accent * 0.7f,
      sr);
  rain_.impactBandPass.setCoefficients(
      BiquadFilter::Type::BandPass,
      650.0f + controls_.detail * 950.0f + controls_.accent * 220.0f,
      1.4f + controls_.detail * 0.8f,
      sr);

  ocean_.swellLowPass.setCoefficients(
      BiquadFilter::Type::LowPass,
      180.0f + controls_.intensity * 110.0f + controls_.accent * 70.0f,
      0.8f,
      sr);
  ocean_.washBandPass.setCoefficients(
      BiquadFilter::Type::BandPass,
      560.0f + controls_.detail * 580.0f + controls_.intensity * 280.0f,
      0.7f,
      sr);
  ocean_.foamHighPass.setCoefficients(
      BiquadFilter::Type::HighPass,
      2200.0f + controls_.detail * 1800.0f,
      0.7f,
      sr);
  ocean_.undertowLowPass.setCoefficients(
      BiquadFilter::Type::LowPass,
      55.0f + controls_.intensity * 25.0f,
      0.85f,
      sr);
  ocean_.crashBandPass.setCoefficients(
      BiquadFilter::Type::BandPass,
      600.0f + controls_.detail * 400.0f + controls_.accent * 200.0f,
      1.0f + controls_.accent * 0.5f,
      sr);
  ocean_.sprayHighPass.setCoefficients(
      BiquadFilter::Type::HighPass,
      4000.0f + controls_.detail * 2000.0f,
      0.6f,
      sr);
  ocean_.backwashBandPass.setCoefficients(
      BiquadFilter::Type::BandPass,
      240.0f + controls_.detail * 280.0f + controls_.motion * 90.0f,
      0.9f + controls_.motion * 0.5f,
      sr);

  thunder_.rumbleLowPass.setCoefficients(
      BiquadFilter::Type::LowPass,
      80.0f + controls_.intensity * 60.0f + controls_.accent * 30.0f,
      0.9f,
      sr);
  thunder_.crackBandPass.setCoefficients(
      BiquadFilter::Type::BandPass,
      1800.0f + controls_.detail * 2400.0f + controls_.accent * 600.0f,
      1.5f + controls_.accent * 1.0f,
      sr);
  thunder_.rainHighPass.setCoefficients(
      BiquadFilter::Type::HighPass,
      2800.0f + controls_.detail * 1800.0f,
      0.6f,
      sr);
  thunder_.subBassLowPass.setCoefficients(
      BiquadFilter::Type::LowPass,
      45.0f + controls_.intensity * 20.0f,
      0.9f,
      sr);
  thunder_.dropBandPass.setCoefficients(
      BiquadFilter::Type::BandPass,
      1800.0f + controls_.detail * 1200.0f + controls_.accent * 400.0f,
      1.2f + controls_.accent * 0.6f,
      sr);

  fireplace_.crackleBandPass.setCoefficients(
      BiquadFilter::Type::BandPass,
      900.0f + controls_.detail * 1200.0f + controls_.accent * 400.0f,
      1.8f + controls_.detail * 0.8f,
      sr);
  fireplace_.baseLowPass.setCoefficients(
      BiquadFilter::Type::LowPass,
      220.0f + controls_.intensity * 80.0f,
      0.85f,
      sr);
  fireplace_.hissHighPass.setCoefficients(
      BiquadFilter::Type::HighPass,
      3600.0f + controls_.detail * 2400.0f,
      0.55f,
      sr);
  fireplace_.snapBandPass.setCoefficients(
      BiquadFilter::Type::BandPass,
      2500.0f + controls_.detail * 1500.0f + controls_.accent * 500.0f,
      2.0f + controls_.detail * 1.0f,
      sr);
  fireplace_.emberLowPass.setCoefficients(
      BiquadFilter::Type::LowPass,
      120.0f + controls_.intensity * 60.0f,
      0.9f,
      sr);

  insects_.chirpBandPass.setCoefficients(
      BiquadFilter::Type::BandPass,
      3200.0f + controls_.detail * 2800.0f + controls_.accent * 800.0f,
      3.0f + controls_.accent * 2.0f,
      sr);
  insects_.bedLowPass.setCoefficients(
      BiquadFilter::Type::LowPass,
      280.0f + controls_.intensity * 120.0f,
      0.7f,
      sr);
  insects_.detailHighPass.setCoefficients(
      BiquadFilter::Type::HighPass,
      1600.0f + controls_.detail * 1400.0f,
      0.65f,
      sr);
  insects_.cricket2BandPass.setCoefficients(
      BiquadFilter::Type::BandPass,
      5200.0f + controls_.detail * 1800.0f + controls_.accent * 600.0f,
      3.5f + controls_.accent * 1.5f,
      sr);
  insects_.frogBandPass.setCoefficients(
      BiquadFilter::Type::BandPass,
      320.0f + controls_.accent * 180.0f,
      2.0f + controls_.accent * 1.0f,
      sr);
}

void LoomAmbienceSynthCore::maybeTriggerWindGust(float dt) {
  if (wind_.gust.isActive()) return;
  wind_.gustTimer -= dt;
  if (wind_.gustTimer <= 0.0f) {
    const float gustEnergy =
        (0.22f + controls_.intensity * 0.60f + controls_.accent * 0.28f + controls_.motion * 0.18f)
        * randomRange(0.88f, 1.15f);
    wind_.gust.trigger(gustEnergy);
    const float gustRateHz =
        0.08f + controls_.motion * 0.11f + controls_.intensity * 0.08f + controls_.accent * 0.04f;
    wind_.gustTimer = sampleEventInterval(gustRateHz, 0.7f);
  }
}

float LoomAmbienceSynthCore::synthesizeWindSample() {
  const float dt = 1.0f / sampleRate_;
  const float slowMod = lfos_.slow.sine() * 0.5f + 0.5f;
  const float medMod = lfos_.medium.triangle() * 0.5f + 0.5f;
  const float fastMod = lfos_.fast.sine() * 0.2f + 0.8f;
  const float randomMod = lfos_.slow.smoothRandom() * 0.15f + 0.85f;
  lfos_.slow.advance();
  lfos_.medium.advance();
  lfos_.fast.advance();

  maybeTriggerWindGust(dt);
  const float gustEnvelope = wind_.gust.generate();
  wind_.gust.update();

  const float shapedIntensity = std::sqrt(std::max(0.0f, controls_.intensity * slowMod * randomMod));
  const float detailGain = 0.8f + controls_.detail * 0.5f;

  const float lowLayer =
      wind_.lowPassLow.process(noise_.low.brown()) * 0.5f * shapedIntensity * 0.6f;

  float midNoise = wind_.lowPassMid.process(noise_.mid.pink());
  midNoise = wind_.highPassMid.process(midNoise);
  const float midLayer = midNoise * 0.6f * shapedIntensity * medMod * (1.0f + shapedIntensity * 0.3f)
      * 0.85f * detailGain;

  float highNoise = wind_.highPassHigh.process(noise_.high.pink());
  highNoise = wind_.lowPassHigh.process(highNoise);
  const float highLayer =
      highNoise * 0.4f * std::pow(shapedIntensity, 2.4f) * fastMod * (0.12f + controls_.detail * 0.22f);

  const float gustNoise = wind_.gustFilter.process(noise_.gust.pink());
  const float gustLayer = gustNoise * gustEnvelope * (0.35f + controls_.accent * 0.45f);

  float speedWhoosh = 0.0f;
  if (controls_.intensity > 0.35f) {
    const float whooshNoise = noise_.mid.filtered(
        0.06f + controls_.intensity * 0.18f + controls_.detail * 0.06f,
        0.2f + controls_.accent * 0.2f);
    const float whooshIntensity = std::sqrt((controls_.intensity - 0.35f) / 0.65f);
    speedWhoosh = whooshNoise * whooshIntensity * (0.24f + controls_.motion * 0.10f) * medMod;
  }

  const float whistleDrive = std::max(
      0.0f,
      gustEnvelope * (0.9f + controls_.accent * 0.45f)
          + std::max(0.0f, speedWhoosh) * (0.6f + controls_.detail * 0.35f)
          - 0.10f);
  const float whistleFreq =
      620.0f + controls_.detail * 1050.0f + controls_.accent * 650.0f + medMod * 240.0f
      + gustEnvelope * 360.0f;
  wind_.whistlePhase += whistleFreq * dt;
  if (wind_.whistlePhase > 1.0f) wind_.whistlePhase -= std::floor(wind_.whistlePhase);
  wind_.whistleOvertonePhase += whistleFreq * (1.92f + medMod * 0.06f) * dt;
  if (wind_.whistleOvertonePhase > 1.0f) {
    wind_.whistleOvertonePhase -= std::floor(wind_.whistleOvertonePhase);
  }
  const float whistle = std::sin(wind_.whistlePhase * 2.0f * kPi)
      * whistleDrive * whistleDrive
      * (0.015f + controls_.detail * 0.055f + controls_.accent * 0.040f);
  const float overtone = std::sin(wind_.whistleOvertonePhase * 2.0f * kPi)
      * whistleDrive * whistleDrive * whistleDrive
      * (0.003f + controls_.detail * 0.015f + controls_.accent * 0.010f);

  const float edgeFlutter = wind_.lowPassHigh.process(wind_.highPassHigh.process(noise_.gust.white()))
      * whistleDrive * (0.010f + controls_.detail * 0.020f);

  return std::tanh(
      (lowLayer + midLayer + highLayer + gustLayer + speedWhoosh + whistle + overtone + edgeFlutter)
      * 0.60f);
}

float LoomAmbienceSynthCore::synthesizeRainSample() {
  const float dt = 1.0f / sampleRate_;
  lfos_.slow.advance();
  lfos_.medium.advance();
  const float slowMod = lfos_.slow.sine() * 0.5f + 0.5f;
  const float density = 0.20f + controls_.intensity * 0.65f + controls_.accent * 0.25f;

  float bed = rain_.bedLowPass.process(noise_.mid.pink());
  bed = rain_.bedHighPass.process(bed);
  bed *= (0.22f + density * 0.38f) * (0.85f + controls_.detail * 0.35f);

  float hiss = rain_.hissHighPass.process(noise_.high.white());
  hiss *= 0.04f + controls_.intensity * 0.12f + controls_.detail * 0.12f;

  rain_.dropTimer -= dt;
  if (rain_.dropTimer <= 0.0f) {
    rain_.dropletEnvelope = std::max(
        rain_.dropletEnvelope,
        (0.14f + controls_.intensity * 0.22f + controls_.accent * 0.30f) * randomRange(0.85f, 1.20f));
    if (nextRandom01() < (0.18f + controls_.intensity * 0.42f + controls_.accent * 0.14f)) {
      rain_.impactEnvelope = std::max(
          rain_.impactEnvelope,
          (0.10f + controls_.intensity * 0.18f + controls_.accent * 0.12f) * randomRange(0.88f, 1.15f));
      rain_.impactFrequency =
          randomRange(520.0f + controls_.detail * 280.0f, 1350.0f + controls_.detail * 850.0f);
    }
    const float dropRateHz =
        8.0f + controls_.intensity * 18.0f + controls_.accent * 3.0f + controls_.motion * 5.0f;
    rain_.dropTimer = sampleEventInterval(dropRateHz, 0.008f);
  }
  rain_.dropletEnvelope *= 0.9935f - controls_.intensity * 0.0015f - controls_.motion * 0.0008f;
  rain_.impactEnvelope *= 0.9895f - controls_.motion * 0.0012f;
  rain_.impactPhase += rain_.impactFrequency * dt;
  if (rain_.impactPhase > 1.0f) rain_.impactPhase -= std::floor(rain_.impactPhase);
  float drop = rain_.dropBandPass.process(noise_.drop.white()) * rain_.dropletEnvelope;
  drop *= 0.36f + slowMod * 0.16f + controls_.accent * 0.10f;
  float splash = rain_.hissHighPass.process(noise_.drop.white());
  splash *= rain_.dropletEnvelope * (0.10f + controls_.detail * 0.10f + controls_.accent * 0.12f);
  float impactRing = std::sin(rain_.impactPhase * 2.0f * kPi);
  impactRing = rain_.impactBandPass.process(impactRing);
  impactRing *= rain_.impactEnvelope * rain_.impactEnvelope
      * (0.14f + controls_.detail * 0.10f + controls_.accent * 0.08f);
  float gutter = rain_.impactBandPass.process(noise_.mid.pink());
  gutter *= rain_.impactEnvelope * (0.08f + controls_.intensity * 0.06f + controls_.motion * 0.04f);

  return std::tanh((bed + hiss + drop + splash + impactRing + gutter) * (0.72f + density * 0.28f));
}

float LoomAmbienceSynthCore::synthesizeOceanSample() {
  lfos_.slow.advance();
  lfos_.medium.advance();
  lfos_.fast.advance();

  const float primaryRate = 0.08f + controls_.intensity * 0.04f + controls_.motion * 0.03f;
  const float secondaryRate = 0.18f + controls_.intensity * 0.06f + controls_.motion * 0.04f;
  const float primaryWave = std::sin(elapsedSeconds_ * primaryRate * 2.0f * kPi);
  const float secondaryWave = std::sin(elapsedSeconds_ * secondaryRate * 2.0f * kPi);
  const float rawCycle = 0.65f * primaryWave + 0.35f * secondaryWave;
  const float shaped = rawCycle > 0.0f
      ? std::pow(rawCycle, 0.7f)
      : -std::pow(-rawCycle, 1.4f) * 0.6f;
  const float waveCycle = shaped * 0.5f + 0.5f;

  float undertow = ocean_.undertowLowPass.process(noise_.low.brown());
  undertow *= (0.12f + controls_.intensity * 0.16f + controls_.accent * 0.08f) * (1.0f - waveCycle * 0.4f);

  float swell = ocean_.swellLowPass.process(noise_.low.brown());
  swell *= (0.14f + controls_.intensity * 0.22f + controls_.accent * 0.10f) * (0.25f + 0.75f * waveCycle);

  float wash = ocean_.washBandPass.process(noise_.mid.pink());
  const float washGain = std::max(0.0f, waveCycle - 0.2f) / 0.8f;
  wash *= (0.15f + controls_.intensity * 0.28f + controls_.detail * 0.14f) * washGain
      * (0.85f + lfos_.medium.triangle() * 0.15f);

  const float crest = std::max(0.0f, (waveCycle - 0.5f) / 0.5f);
  float foam = ocean_.foamHighPass.process(noise_.high.white());
  float spray = ocean_.sprayHighPass.process(noise_.oceanFoam.white());
  foam *= crest * crest * (0.07f + controls_.intensity * 0.14f + controls_.detail * 0.11f);
  spray *= crest * crest * crest * (0.03f + controls_.detail * 0.07f);

  ocean_.crashTimer -= 1.0f / sampleRate_;
  if (ocean_.crashTimer <= 0.0f && crest > randomRange(0.32f, 0.68f)) {
    ocean_.crashEnvelope =
        (0.34f + controls_.intensity * 0.34f + controls_.accent * 0.34f) * randomRange(0.88f, 1.18f);
    ocean_.backwashEnvelope = std::max(
        ocean_.backwashEnvelope,
        ocean_.crashEnvelope * randomRange(0.28f, 0.48f));
    const float crashRateHz = 0.06f + controls_.intensity * 0.16f + controls_.accent * 0.04f;
    ocean_.crashTimer = sampleEventInterval(crashRateHz, 0.65f);
  }
  ocean_.crashEnvelope *= 0.9975f - controls_.motion * 0.0006f;
  const float backwashDrive = std::max(0.0f, (1.0f - crest) * (0.28f + ocean_.crashEnvelope * 0.45f));
  ocean_.backwashEnvelope += (backwashDrive - ocean_.backwashEnvelope) * 0.0012f;
  ocean_.backwashEnvelope *= 0.9990f - controls_.motion * 0.0004f;
  float crash = ocean_.crashBandPass.process(noise_.oceanFoam.pink());
  crash *= ocean_.crashEnvelope * (0.26f + controls_.detail * 0.20f + controls_.accent * 0.18f);
  float crashSpray = ocean_.sprayHighPass.process(noise_.oceanFoam.white());
  crashSpray *= ocean_.crashEnvelope * ocean_.crashEnvelope
      * (0.05f + controls_.detail * 0.08f + controls_.accent * 0.06f);
  float backwash = ocean_.backwashBandPass.process(noise_.mid.pink());
  backwash *= ocean_.backwashEnvelope * (0.10f + controls_.motion * 0.08f + controls_.detail * 0.04f);

  float roar = noise_.mid.filtered(0.04f + controls_.intensity * 0.06f, 0.1f + controls_.accent * 0.1f);
  roar *= (0.05f + controls_.intensity * 0.07f) * (lfos_.slow.sine() * 0.5f + 0.5f);

  return std::tanh((undertow + swell + wash + foam + spray + crash + crashSpray + backwash + roar) * 0.78f);
}

float LoomAmbienceSynthCore::synthesizeThunderSample() {
  const float dt = 1.0f / sampleRate_;
  lfos_.slow.advance();
  lfos_.medium.advance();
  lfos_.fast.advance();
  const float slowMod = lfos_.slow.sine() * 0.5f + 0.5f;

  float subBass = thunder_.subBassLowPass.process(noise_.thunder.brown());
  subBass *= (0.16f + controls_.intensity * 0.24f + controls_.accent * 0.10f) * (0.8f + slowMod * 0.2f);

  thunder_.rumblePhase += (0.025f + controls_.motion * 0.04f) * dt;
  if (thunder_.rumblePhase > 1.0f) thunder_.rumblePhase -= 1.0f;
  const float rumbleMod = 0.75f + 0.25f * std::sin(thunder_.rumblePhase * 2.0f * kPi);
  float rumble = thunder_.rumbleLowPass.process(noise_.thunder.brown());
  rumble *= (0.20f + controls_.intensity * 0.30f + controls_.accent * 0.12f) * rumbleMod;

  thunder_.boltTimer -= dt;
  if (thunder_.boltTimer <= 0.0f && thunder_.boltEnvelope < 0.08f) {
    thunder_.boltEnvelope = 0.85f + controls_.intensity * 0.15f;
    thunder_.crackEnvelope = 0.92f + controls_.detail * 0.12f + controls_.accent * 0.08f;
    thunder_.boltDecayRate = 0.9980f + nextRandom01() * 0.0016f;
    thunder_.rollDelaySeconds = randomRange(0.018f, 0.085f) + (1.0f - controls_.motion) * 0.025f;
    const float boltRateHz =
        0.08f + controls_.intensity * 0.20f + controls_.accent * 0.04f + controls_.motion * 0.05f;
    thunder_.boltTimer = sampleEventInterval(boltRateHz, 1.0f);
  }
  thunder_.boltEnvelope *= thunder_.boltDecayRate;
  thunder_.crackEnvelope *= 0.9865f - controls_.detail * 0.0025f;
  thunder_.rollDelaySeconds = std::max(0.0f, thunder_.rollDelaySeconds - dt);

  const float brightPhase = thunder_.crackEnvelope * thunder_.crackEnvelope;
  float crack = thunder_.crackBandPass.process(noise_.thunderBolt.white());
  crack *= brightPhase * brightPhase * (0.50f + controls_.detail * 0.30f + controls_.accent * 0.38f);
  float crackAir = thunder_.rainHighPass.process(noise_.thunderBolt.white());
  crackAir *= thunder_.crackEnvelope * (0.02f + controls_.detail * 0.04f + controls_.accent * 0.10f);

  const float bodyGate = thunder_.rollDelaySeconds <= 0.0f ? 1.0f : 0.0f;
  const float bodyPhase =
      bodyGate * thunder_.boltEnvelope * (1.0f - std::min(0.55f, brightPhase * 0.35f));
  float boltRumble = thunder_.rumbleLowPass.process(noise_.thunderBolt.brown());
  boltRumble *= bodyPhase * (0.4f + controls_.intensity * 0.3f);

  float rain = thunder_.rainHighPass.process(noise_.mid.pink());
  rain *= (0.10f + controls_.intensity * 0.16f + controls_.detail * 0.10f)
      * (0.85f + lfos_.medium.triangle() * 0.15f);

  thunder_.dropTimer -= dt;
  if (thunder_.dropTimer <= 0.0f) {
    thunder_.dropEnvelope = std::max(
        thunder_.dropEnvelope,
        (0.16f + controls_.intensity * 0.20f + controls_.accent * 0.16f) * randomRange(0.85f, 1.18f));
    const float dropRateHz = 6.0f + controls_.intensity * 14.0f + controls_.accent * 2.0f;
    thunder_.dropTimer = sampleEventInterval(dropRateHz, 0.01f);
  }
  thunder_.dropEnvelope *= 0.9945f - controls_.motion * 0.001f;
  float drop = thunder_.dropBandPass.process(noise_.drop.white());
  drop *= thunder_.dropEnvelope * (0.25f + controls_.detail * 0.20f);

  float wind = noise_.mid.filtered(0.05f + controls_.intensity * 0.10f, 0.15f + controls_.motion * 0.1f);
  wind *= (0.06f + controls_.intensity * 0.10f + controls_.motion * 0.06f) * (0.8f + slowMod * 0.4f);

  return std::tanh((subBass + rumble + crack + crackAir + boltRumble + rain + drop + wind) * 0.72f);
}

float LoomAmbienceSynthCore::synthesizeFireplaceSample() {
  const float dt = 1.0f / sampleRate_;
  lfos_.slow.advance();
  lfos_.medium.advance();
  lfos_.fast.advance();
  const float slowMod = lfos_.slow.sine() * 0.5f + 0.5f;
  const float medMod = lfos_.medium.triangle() * 0.5f + 0.5f;
  const float fastMod = lfos_.fast.sine() * 0.5f + 0.5f;
  const float breathe = 0.78f + 0.22f * medMod * (0.8f + controls_.motion * 0.4f);

  fireplace_.emberPhase += (0.08f + controls_.motion * 0.06f) * dt;
  if (fireplace_.emberPhase > 1.0f) fireplace_.emberPhase -= 1.0f;
  const float emberGlow = 0.7f + 0.3f * std::sin(fireplace_.emberPhase * 2.0f * kPi);
  float ember = fireplace_.emberLowPass.process(noise_.fire.brown());
  ember *= (0.14f + controls_.intensity * 0.18f) * emberGlow;

  float base = fireplace_.baseLowPass.process(noise_.fire.brown());
  base *= (0.18f + controls_.intensity * 0.24f) * (0.85f + slowMod * 0.15f);

  fireplace_.crackleTimer -= dt;
  if (fireplace_.crackleTimer <= 0.0f && fireplace_.crackleEnvelope < 0.10f) {
    fireplace_.crackleEnvelope =
        (0.34f + controls_.intensity * 0.34f + controls_.accent * 0.22f) * randomRange(0.85f, 1.20f);
    const float crackleRateHz = 0.7f + controls_.intensity * 1.6f + controls_.accent * 1.2f;
    fireplace_.crackleTimer = sampleEventInterval(crackleRateHz, 0.12f);
  }
  fireplace_.crackleEnvelope *= 0.9972f - controls_.motion * 0.0008f;
  float crackle = fireplace_.crackleBandPass.process(noise_.fire.white());
  crackle *= fireplace_.crackleEnvelope * (0.50f + controls_.detail * 0.30f + controls_.accent * 0.15f);

  fireplace_.popTimer -= dt;
  if (fireplace_.popTimer <= 0.0f && fireplace_.popEnvelope < 0.10f) {
    fireplace_.popEnvelope =
        (0.18f + controls_.intensity * 0.22f + controls_.accent * 0.12f) * randomRange(0.85f, 1.18f);
    const float popRateHz =
        2.5f + controls_.intensity * 5.0f + controls_.accent * 2.5f + controls_.detail * 1.5f;
    fireplace_.popTimer = sampleEventInterval(popRateHz, 0.05f);
  }
  fireplace_.popEnvelope *= 0.9952f - controls_.motion * 0.0006f;
  float pop = fireplace_.crackleBandPass.process(noise_.fireDetail.white());
  pop *= fireplace_.popEnvelope * (0.35f + controls_.detail * 0.25f);

  fireplace_.snapTimer -= dt;
  if (fireplace_.snapTimer <= 0.0f && fireplace_.snapEnvelope < 0.10f) {
    fireplace_.snapEnvelope =
        (0.11f + controls_.intensity * 0.15f + controls_.detail * 0.10f) * randomRange(0.90f, 1.15f);
    const float snapRateHz = 5.5f + controls_.intensity * 9.0f + controls_.detail * 7.0f;
    fireplace_.snapTimer = sampleEventInterval(snapRateHz, 0.02f);
  }
  fireplace_.snapEnvelope *= 0.992f - controls_.motion * 0.001f;
  float snap = fireplace_.snapBandPass.process(noise_.fireDetail.white());
  snap *= fireplace_.snapEnvelope * (0.20f + controls_.detail * 0.25f);

  float hiss = fireplace_.hissHighPass.process(noise_.high.pink());
  hiss *= (0.03f + controls_.intensity * 0.06f + controls_.detail * 0.08f) * fastMod;

  return std::tanh((ember + base + crackle + pop + snap + hiss) * breathe * 0.76f);
}

float LoomAmbienceSynthCore::synthesizeNightInsectsSample() {
  const float dt = 1.0f / sampleRate_;
  lfos_.slow.advance();
  lfos_.medium.advance();
  lfos_.fast.advance();
  const float slowMod = lfos_.slow.sine() * 0.5f + 0.5f;
  const float chorusMod = 0.55f + 0.45f * slowMod * (0.6f + controls_.motion * 0.8f);

  float bed = insects_.bedLowPass.process(noise_.insect.brown());
  bed *= (0.10f + controls_.intensity * 0.14f) * (0.85f + slowMod * 0.15f);

  insects_.chirpPhase += (3.0f + controls_.accent * 4.5f + controls_.motion * 2.5f) * dt;
  if (insects_.chirpPhase > 1.0f) insects_.chirpPhase -= 1.0f;
  const float burstPhase = std::fmod(insects_.chirpPhase * 3.0f, 1.0f);
  const float chirpPulse = burstPhase < 0.6f
      ? std::max(0.0f, std::sin(burstPhase / 0.6f * kPi))
      : 0.0f;
  const float chirpGate = chirpPulse * chirpPulse;

  const float chirp1Trigger = 0.003f + controls_.intensity * 0.006f + controls_.accent * 0.005f;
  if (noise_.insect.white() > (1.0f - chirp1Trigger) && insects_.chirpEnvelope < 0.1f) {
    insects_.chirpEnvelope = 0.28f + controls_.intensity * 0.35f + controls_.accent * 0.25f;
  }
  insects_.chirpEnvelope *= 0.9960f - controls_.motion * 0.0008f;
  float chirp1 = insects_.chirpBandPass.process(noise_.insect.white());
  chirp1 *= chirpGate * insects_.chirpEnvelope * (0.35f + controls_.detail * 0.40f) * chorusMod;

  insects_.cricket2Phase += (4.5f + controls_.accent * 3.0f + controls_.motion * 1.8f) * dt;
  if (insects_.cricket2Phase > 1.0f) insects_.cricket2Phase -= 1.0f;
  const float chirp2Pulse = std::max(0.0f, std::sin(insects_.cricket2Phase * 2.0f * kPi));
  const float chirp2Gate = chirp2Pulse * chirp2Pulse * chirp2Pulse;

  const float chirp2Trigger = 0.002f + controls_.intensity * 0.005f + controls_.accent * 0.003f;
  if (noise_.insect2.white() > (1.0f - chirp2Trigger) && insects_.cricket2Envelope < 0.1f) {
    insects_.cricket2Envelope = 0.20f + controls_.intensity * 0.28f + controls_.accent * 0.18f;
  }
  insects_.cricket2Envelope *= 0.9955f - controls_.motion * 0.0007f;
  float chirp2 = insects_.cricket2BandPass.process(noise_.insect2.white());
  chirp2 *= chirp2Gate * insects_.cricket2Envelope * (0.22f + controls_.detail * 0.30f) * chorusMod;

  insects_.cricket3Phase += (6.0f + controls_.motion * 3.0f) * dt;
  if (insects_.cricket3Phase > 1.0f) insects_.cricket3Phase -= 1.0f;
  const float cicadaMod = 0.5f + 0.5f * std::sin(insects_.cricket3Phase * 2.0f * kPi);
  const float cicadaPresence = std::max(0.0f, controls_.accent - 0.25f) / 0.75f;
  float cicada = insects_.detailHighPass.process(noise_.mid.pink());
  cicada *= cicadaPresence * cicadaMod * (0.06f + controls_.intensity * 0.10f + controls_.detail * 0.06f);

  insects_.frogTimer -= dt;
  if (insects_.frogTimer <= 0.0f && insects_.frogEnvelope < 0.08f) {
    insects_.frogEnvelope =
        (0.22f + controls_.intensity * 0.20f + controls_.accent * 0.18f) * randomRange(0.90f, 1.15f);
    const float frogRateHz = 0.04f + controls_.intensity * 0.10f + controls_.accent * 0.12f;
    insects_.frogTimer = sampleEventInterval(frogRateHz, 0.9f);
  }
  insects_.frogEnvelope *= 0.9985f - controls_.motion * 0.0003f;
  float frog = insects_.frogBandPass.process(noise_.insect2.white());
  frog *= insects_.frogEnvelope * (0.15f + controls_.accent * 0.20f);

  float rustle = insects_.detailHighPass.process(noise_.high.pink());
  rustle *= (0.03f + controls_.detail * 0.06f + controls_.intensity * 0.03f)
      * (0.7f + lfos_.medium.triangle() * 0.3f);

  return std::tanh((bed + chirp1 + chirp2 + cicada + frog + rustle) * 0.82f);
}

float LoomAmbienceSynthCore::nextMonoSample() {
  if (finished_) return 0.0f;
  const float sample = [this]() {
    switch (preset_) {
      case AmbiencePreset::Wind:
        return synthesizeWindSample();
      case AmbiencePreset::Rain:
        return synthesizeRainSample();
      case AmbiencePreset::Ocean:
        return synthesizeOceanSample();
      case AmbiencePreset::Thunder:
        return synthesizeThunderSample();
      case AmbiencePreset::Fireplace:
        return synthesizeFireplaceSample();
      case AmbiencePreset::NightInsects:
        return synthesizeNightInsectsSample();
    }
    return 0.0f;
  }();

  elapsedSeconds_ += 1.0f / sampleRate_;
  if (!controls_.loop && elapsedSeconds_ >= kAutoStopSeconds) {
    finished_ = true;
  }
  return master_.process(sample * controls_.volume * (0.18f + 0.82f * controls_.intensity));
}

}  // namespace jvn::audiofx::detail
