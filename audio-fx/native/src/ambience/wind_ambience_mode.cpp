#include "ambience/wind_ambience_mode.hpp"

namespace jvn::audiofx::detail {

// ═══════════════════════════════════════════════════════════════════════
// Construction / Configuration
// ═══════════════════════════════════════════════════════════════════════

WindAmbienceMode::WindAmbienceMode(int sampleRate) : BaseAmbienceMode(sampleRate, 0x10293847u) {
  const float sr = static_cast<float>(sampleRate);
  macroEnergy_.setSampleRate(sr);
  mesoLow_.setSampleRate(sr);
  mesoMid_.setSampleRate(sr);
  mesoHigh_.setSampleRate(sr);
  microTurb_.setSampleRate(sr);
  gustTriggerDrift_.setSampleRate(sr);
  whistleFreqDrift_.setSampleRate(sr);
  whistleVelocity_.setSampleRate(sr);
}

void WindAmbienceMode::configure(const RenderControls& controls) {
  setControls(controls);
  resetRandomState();

  // Stochastic controls — timescales adapt to motion parameter
  const float motionScale = 0.6f + controls.motion * 0.8f;
  macroEnergy_.setTimescale(30.0f / motionScale);
  mesoLow_.setTimescale(5.0f / motionScale);
  mesoMid_.setTimescale(3.5f / motionScale);
  mesoHigh_.setTimescale(2.0f / motionScale);
  microTurb_.setTimescale(0.18f / motionScale);
  gustTriggerDrift_.setTimescale(8.0f / motionScale);
  whistleFreqDrift_.setTimescale(1.5f);
  whistleVelocity_.setTimescale(0.6f);

  macroEnergy_.reset();
  mesoLow_.reset();
  mesoMid_.reset();
  mesoHigh_.reset();
  microTurb_.reset();
  gustTriggerDrift_.reset();
  whistleFreqDrift_.reset();
  whistleVelocity_.reset();

  // Multi-voice gusts — stagger initial timers
  for (int i = 0; i < kGustVoices; ++i) {
    gustVoices_[i] = GustVoice{};
    gustTimers_[i] = randomRange(0.4f + i * 0.6f, 1.2f + i * 1.0f);
  }

  whistleActive_ = false;
  whistlePhase_ = 0.0f;
  whistleOvertonePhase_ = 0.0f;
  whistleGain_ = 0.0f;
  laggedBrightness_ = 0.0f;

  noiseLow_.reset();
  noiseMid_.reset();
  noiseHigh_.reset();
  noiseGust_.reset();
  noiseWhistle_.reset();

  lowPassLow_.reset();
  lowPassMid_.reset();
  highPassMid_.reset();
  highPassHigh_.reset();
  lowPassHigh_.reset();
  gustFilter_.reset();
  gustFilter2_.reset();

  updateFilters();
}

void WindAmbienceMode::retune(const RenderControls& controls) {
  setControls(controls);

  const float motionScale = 0.6f + controls.motion * 0.8f;
  macroEnergy_.setTimescale(30.0f / motionScale);
  mesoLow_.setTimescale(5.0f / motionScale);
  mesoMid_.setTimescale(3.5f / motionScale);
  mesoHigh_.setTimescale(2.0f / motionScale);
  microTurb_.setTimescale(0.18f / motionScale);
  gustTriggerDrift_.setTimescale(8.0f / motionScale);
  whistleFreqDrift_.setTimescale(1.5f);
  whistleVelocity_.setTimescale(0.6f);
  updateFilters();
}

// ═══════════════════════════════════════════════════════════════════════
// Filters
// ═══════════════════════════════════════════════════════════════════════

void WindAmbienceMode::updateFilters() {
  const float sr = static_cast<float>(sampleRate());
  const float bright = 0.8f + controls().detail * 0.65f;
  const float intMod = 0.8f + controls().intensity * 0.45f;

  lowPassLow_.setCoefficients(
      BiquadFilter::Type::LowPass,
      180.0f * intMod * (0.9f + controls().detail * 0.2f), 0.7f, sr);
  lowPassMid_.setCoefficients(
      BiquadFilter::Type::LowPass,
      700.0f * intMod * bright, 0.5f, sr);
  highPassMid_.setCoefficients(
      BiquadFilter::Type::HighPass, 150.0f, 0.5f, sr);
  highPassHigh_.setCoefficients(
      BiquadFilter::Type::HighPass,
      1700.0f + controls().detail * 1600.0f + controls().intensity * 900.0f, 0.6f, sr);
  lowPassHigh_.setCoefficients(
      BiquadFilter::Type::LowPass,
      6200.0f + controls().detail * 3800.0f, 0.45f, sr);
  gustFilter_.setCoefficients(
      BiquadFilter::Type::BandPass,
      280.0f + controls().accent * 200.0f, 1.2f + controls().accent * 0.8f, sr);
  gustFilter2_.setCoefficients(
      BiquadFilter::Type::BandPass,
      520.0f + controls().accent * 350.0f, 0.9f + controls().accent * 0.6f, sr);
}

// ═══════════════════════════════════════════════════════════════════════
// Multi-voice overlapping gusts
// ═══════════════════════════════════════════════════════════════════════

void WindAmbienceMode::updateGusts(float dt) {
  const float triggerMod = gustTriggerDrift_.current() * 0.5f + 0.5f;  // [0..1]
  const float baseRate = 0.06f + controls().motion * 0.12f + controls().intensity * 0.08f;

  for (int i = 0; i < kGustVoices; ++i) {
    GustVoice& g = gustVoices_[i];
    if (g.active) {
      g.time += dt;
      if (g.time >= g.duration) g.active = false;
    } else {
      gustTimers_[i] -= dt;
      if (gustTimers_[i] <= 0.0f) {
        // Trigger a new gust
        g.active = true;
        g.time = 0.0f;
        g.duration = randomRange(0.8f, 3.5f + controls().motion * 2.0f);
        g.intensity =
            (0.18f + controls().intensity * 0.55f + controls().accent * 0.25f)
            * randomRange(0.7f, 1.3f);
        g.attack = randomRange(0.15f, 0.45f);
        g.skew = randomRange(0.25f, 0.60f);
        // Next gust timer — modulated by drift process for aperiodic spacing
        const float modRate = baseRate * (0.5f + triggerMod);
        gustTimers_[i] = sampleEventInterval(modRate, 0.5f);
      }
    }
  }
}

// ═══════════════════════════════════════════════════════════════════════
// Sample — main synthesis
// ═══════════════════════════════════════════════════════════════════════

float WindAmbienceMode::sample(float /*elapsedSeconds*/) {
  const float dt = 1.0f / static_cast<float>(sampleRate());

  // ── Advance stochastic control processes ──────────────────────────
  const float macro  = macroEnergy_.next() * 0.5f + 0.5f;   // [0..1]
  const float mLow   = mesoLow_.next();                      // [-1..1]
  const float mMid   = mesoMid_.next();                      // [-1..1]
  const float mHigh  = mesoHigh_.next();                     // [-1..1]
  const float turb   = microTurb_.next() * 0.5f + 0.5f;     // [0..1]
  gustTriggerDrift_.next();

  // ── Derived energy (shaped intensity, no periodic component) ──────
  const float energy = std::sqrt(std::max(0.0f, controls().intensity * macro));
  const float detailGain = 0.8f + controls().detail * 0.5f;

  // ── Spectral lag: brightness follows energy with ~300ms delay ─────
  const float lagAlpha = 1.0f - std::exp(-dt / 0.3f);
  laggedBrightness_ += lagAlpha * (energy - laggedBrightness_);

  // ── Multi-voice gusts ─────────────────────────────────────────────
  updateGusts(dt);
  float totalGustEnv = 0.0f;
  for (int i = 0; i < kGustVoices; ++i) {
    const GustVoice& g = gustVoices_[i];
    if (!g.active) continue;
    const float t = g.time / std::max(0.001f, g.duration);
    const float peakT = g.skew;
    float env;
    if (t < peakT) {
      // Attack phase — asymmetric rise
      const float a = t / peakT;
      env = a * a * (3.0f - 2.0f * a);  // smoothstep
    } else {
      // Decay phase — longer tail
      const float d = (t - peakT) / (1.0f - peakT);
      env = 1.0f - d;
      env *= env;
    }
    totalGustEnv += env * g.intensity;
  }
  // Soft-limit so overlapping gusts compress naturally
  totalGustEnv = std::tanh(totalGustEnv);

  // ── Low band (brown noise, independent meso modulation) ───────────
  const float lowMod = 0.85f + mLow * 0.15f;  // subtle drift
  const float lowLayer =
      lowPassLow_.process(noiseLow_.brown())
      * 0.30f * energy * lowMod
      * (1.0f - controls().motion * 0.12f);

  // ── Mid band (pink noise, its own meso process, NOT locked to low) ─
  float midNoise = lowPassMid_.process(noiseMid_.pink());
  midNoise = highPassMid_.process(midNoise);
  const float midMod = 0.75f + mMid * 0.25f;
  const float midLayer =
      midNoise * 0.45f * energy * midMod * detailGain
      * (1.0f + energy * 0.2f);

  // ── High band (pink→bandpass, lagged brightness, separate meso) ───
  float highNoise = highPassHigh_.process(noiseHigh_.pink());
  highNoise = lowPassHigh_.process(highNoise);
  const float highMod = 0.7f + mHigh * 0.3f;
  const float highLayer =
      highNoise * 0.35f
      * std::pow(laggedBrightness_, 2.0f)  // brightness trails energy
      * highMod * turb
      * (0.12f + controls().detail * 0.25f);

  // ── Gust layer (two bandpass filters for richer overlap) ──────────
  const float gustNoise1 = gustFilter_.process(noiseGust_.pink());
  const float gustNoise2 = gustFilter2_.process(noiseGust_.white() * 0.7f);
  const float gustMotion = 1.0f + controls().motion * 0.4f * turb;
  const float gustLayer =
      (gustNoise1 * 0.6f + gustNoise2 * 0.4f)
      * totalGustEnv * gustMotion
      * (0.35f + controls().accent * 0.45f);

  // ── Speed whoosh (high-intensity broadband) ───────────────────────
  float speedWhoosh = 0.0f;
  if (controls().intensity > 0.30f) {
    const float whooshNoise = noiseMid_.filtered(
        0.06f + controls().intensity * 0.18f + controls().detail * 0.06f,
        0.2f + controls().accent * 0.2f);
    const float whooshStrength = std::sqrt((controls().intensity - 0.30f) / 0.70f);
    speedWhoosh = whooshNoise * whooshStrength * gustMotion
        * (0.20f + controls().motion * 0.16f)
        * (0.8f + mMid * 0.2f);  // meso drift, NOT periodic
  }

  // ── Hysteretic whistle (thresholded aeolian tone) ─────────────────
  // Local velocity proxy: independent drift, not slaved to gust envelope
  const float velocity = whistleVelocity_.next() * 0.5f + 0.5f;
  const float combinedVelocity =
      velocity * 0.6f + totalGustEnv * 0.3f + energy * 0.1f;

  // Hysteresis: activate at 0.55, deactivate at 0.38
  constexpr float kWhistleOnThresh  = 0.55f;
  constexpr float kWhistleOffThresh = 0.38f;
  if (!whistleActive_ && combinedVelocity > kWhistleOnThresh) {
    whistleActive_ = true;
  } else if (whistleActive_ && combinedVelocity < kWhistleOffThresh) {
    whistleActive_ = false;
  }

  // Smooth gain transition (50ms attack, 120ms release)
  const float whistleTarget = whistleActive_ ? 1.0f : 0.0f;
  const float whistleTau = whistleActive_ ? 0.05f : 0.12f;
  const float wAlpha = 1.0f - std::exp(-dt / whistleTau);
  whistleGain_ += wAlpha * (whistleTarget - whistleGain_);

  // Whistle frequency: random walk, NOT derived from periodic LFO
  const float freqDrift = whistleFreqDrift_.next();
  const float whistleFreq =
      580.0f + controls().detail * 1000.0f + controls().accent * 600.0f
      + freqDrift * 180.0f          // stochastic wander
      + totalGustEnv * 120.0f;      // mild gust influence (not dominant)

  whistlePhase_ += whistleFreq * dt;
  if (whistlePhase_ > 1.0f) whistlePhase_ -= std::floor(whistlePhase_);
  const float overtoneRatio = 1.92f + freqDrift * 0.04f;
  whistleOvertonePhase_ += whistleFreq * overtoneRatio * dt;
  if (whistleOvertonePhase_ > 1.0f) whistleOvertonePhase_ -= std::floor(whistleOvertonePhase_);

  const float wDrive = whistleGain_ * whistleGain_;
  const float whistle = std::sin(whistlePhase_ * 2.0f * kPi)
      * wDrive
      * (0.015f + controls().detail * 0.050f + controls().accent * 0.035f);
  const float overtone = std::sin(whistleOvertonePhase_ * 2.0f * kPi)
      * wDrive * whistleGain_
      * (0.003f + controls().detail * 0.012f + controls().accent * 0.008f);

  // Edge flutter: noise burst modulated by whistle gain
  const float edgeFlutter =
      lowPassHigh_.process(highPassHigh_.process(noiseWhistle_.white()))
      * wDrive * (0.010f + controls().detail * 0.018f);

  // ── Mix and soft-clip ─────────────────────────────────────────────
  return std::tanh(
      (lowLayer + midLayer + highLayer + gustLayer + speedWhoosh
       + whistle + overtone + edgeFlutter)
      * 0.58f);
}

}  // namespace jvn::audiofx::detail
