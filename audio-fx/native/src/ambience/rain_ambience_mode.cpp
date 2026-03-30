#include "ambience/rain_ambience_mode.hpp"

#include <algorithm>
#include <cmath>

namespace jvn::audiofx::detail {

namespace {

float clampPositive(float value, float fallback) {
  return std::isfinite(value) && value > 0.0f ? value : fallback;
}

}  // namespace

RainAmbienceMode::RainAmbienceMode(int sampleRate) : BaseAmbienceMode(sampleRate, 0x20293847u) {}

void RainAmbienceMode::configure(const RenderControls& controls) {
  setControls(controls);
  resetRandomState();
  roofTimer_ = randomRange(0.003f, 0.015f);
  leafTimer_ = randomRange(0.010f, 0.050f);
  puddleTimer_ = randomRange(0.020f, 0.090f);
  drainTimer_ = randomRange(0.045f, 0.180f);
  densityTimer_ = randomRange(0.10f, 0.35f);
  densityTarget_ = randomRange(0.25f, 0.70f);
  densityState_ = densityTarget_;

  noiseRoof_.reset();
  noiseLeaf_.reset();
  noiseWater_.reset();
  noiseDrain_.reset();
  noiseMist_.reset();
  mistHighPass_.reset();
  mistLowPass_.reset();
  distantBodyBandPass_.reset();
  distantRoofBandPass_.reset();

  for (auto& voice : roofVoices_) {
    voice.reset();
  }
  for (auto& voice : leafVoices_) {
    voice.reset();
  }
  for (auto& voice : puddleVoices_) {
    voice.reset();
  }
  for (auto& voice : drainVoices_) {
    voice.reset();
  }

  updateFilters();
}

void RainAmbienceMode::retune(const RenderControls& controls) {
  setControls(controls);
  updateFilters();
}

void RainAmbienceMode::updateFilters() {
  const float sr = static_cast<float>(sampleRate());
  mistHighPass_.setCoefficients(
      BiquadFilter::Type::HighPass,
      5400.0f + controls().detail * 2200.0f + controls().accent * 500.0f,
      0.72f,
      sr);
  mistLowPass_.setCoefficients(
      BiquadFilter::Type::LowPass,
      10500.0f + controls().detail * 4200.0f,
      0.64f,
      sr);
  distantBodyBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      380.0f + controls().intensity * 260.0f + controls().accent * 70.0f,
      0.85f,
      sr);
  distantRoofBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      1800.0f + controls().detail * 1200.0f + controls().motion * 350.0f,
      1.10f,
      sr);
}

float RainAmbienceMode::sampleDropDiameterMm(float scaleBias) {
  const float baseScale = 0.28f + controls().intensity * 0.72f + controls().accent * 0.24f + scaleBias;
  const float u = std::max(1.0e-5f, 1.0f - nextRandom01());
  const float diameter = 0.35f + (-std::log(u)) * clampPositive(baseScale, 0.45f);
  return std::clamp(diameter, 0.35f, 6.0f);
}

float RainAmbienceMode::terminalVelocityMetersPerSecond(float diameterMm) const {
  const float safeDiameter = std::clamp(diameterMm, 0.35f, 6.0f);
  // Common raindrop fit: v_t ~= 9.65 - 10.3 * exp(-0.6 D), D in mm.
  return 9.65f - 10.3f * std::exp(-0.6f * safeDiameter);
}

float RainAmbienceMode::normalizedImpactEnergy(
    float diameterMm,
    float velocityMetersPerSecond,
    float surfaceGain) const {
  const float normalizedDiameter = std::pow(std::clamp(diameterMm / 3.2f, 0.08f, 1.8f), 1.30f);
  const float normalizedVelocity =
      std::pow(std::clamp(velocityMetersPerSecond / 8.0f, 0.18f, 1.4f), 1.10f);
  const float detailGain = 0.88f + controls().detail * 0.22f;
  return std::clamp(normalizedDiameter * normalizedVelocity * surfaceGain * detailGain, 0.015f, 1.6f);
}

float RainAmbienceMode::impactBurstDecay(
    float impactDurationSeconds,
    float minimumSeconds,
    float maximumSeconds) const {
  const float targetSeconds = std::clamp(impactDurationSeconds * 10.0f, minimumSeconds, maximumSeconds);
  return std::exp(-1.0f / (targetSeconds * sampleRate()));
}

float RainAmbienceMode::processVoice(RainVoice& voice, NoiseGenerator& noise) {
  if (!voice.active) {
    return 0.0f;
  }

  const float burst = voice.burstFilter.process(noise.white()) * voice.burstEnvelope * voice.burstGain;
  const float tail = voice.tailFilter.process(noise.pink()) * voice.tailEnvelope * voice.tailGain;
  voice.burstEnvelope *= voice.burstDecay;
  voice.tailEnvelope *= voice.tailDecay;
  const float tonal = (voice.modeA.process() + voice.modeB.process() + voice.modeC.process()) * voice.tonalMix;

  if (voice.burstEnvelope < 1.0e-4f && voice.tailEnvelope < 1.0e-4f && !voice.modeA.isActive() && !voice.modeB.isActive()
      && !voice.modeC.isActive()) {
    voice.active = false;
  }

  return burst + tail + tonal;
}

void RainAmbienceMode::spawnRoofTick() {
  RainVoice& voice = selectVoice(roofVoices_);
  const float sr = static_cast<float>(sampleRate());
  const float diameterMm = sampleDropDiameterMm(-0.05f);
  const float velocity = terminalVelocityMetersPerSecond(diameterMm);
  const float impactDuration = std::clamp((diameterMm * 0.001f) / velocity, 0.00007f, 0.00075f);
  const float energy = normalizedImpactEnergy(diameterMm, velocity, 0.78f + controls().detail * 0.12f);
  const float shimmer = 0.75f + controls().detail * 0.55f + controls().motion * 0.18f;

  voice.active = true;
  voice.burstEnvelope = 0.75f + energy * 0.50f;
  voice.burstDecay = impactBurstDecay(impactDuration, 0.0018f, 0.010f + controls().detail * 0.006f);
  voice.burstGain = 0.052f + energy * 0.055f;
  voice.burstFilter.setCoefficients(
      BiquadFilter::Type::BandPass,
      randomRange(2200.0f, 5200.0f + controls().detail * 1800.0f),
      0.65f + controls().detail * 0.35f,
      sr);
  voice.tailEnvelope = 0.55f + energy * 0.30f;
  voice.tailDecay = impactBurstDecay(impactDuration, 0.005f, 0.020f + controls().detail * 0.008f);
  voice.tailGain = 0.030f + energy * 0.026f;
  voice.tailFilter.setCoefficients(
      BiquadFilter::Type::BandPass,
      randomRange(1000.0f, 2600.0f + controls().detail * 600.0f),
      0.55f + controls().detail * 0.20f,
      sr);
  voice.tonalMix = 0.10f + controls().detail * 0.04f;

  const float plateMode = randomRange(620.0f, 1080.0f + controls().detail * 180.0f);
  const float tickMode = randomRange(1200.0f, 2100.0f + controls().detail * 450.0f);
  const float bodyMode = randomRange(260.0f, 520.0f + controls().motion * 120.0f);
  voice.modeA.setMode(plateMode, randomRange(0.010f, 0.020f + controls().detail * 0.004f), sr);
  voice.modeB.setMode(tickMode, randomRange(0.006f, 0.012f + controls().detail * 0.004f), sr);
  voice.modeC.setMode(bodyMode, randomRange(0.016f, 0.030f + controls().motion * 0.008f), sr);
  voice.modeA.excite(energy * 0.030f * shimmer);
  voice.modeB.excite(energy * 0.018f * shimmer);
  voice.modeC.excite(energy * 0.015f * shimmer);
}

void RainAmbienceMode::spawnLeafImpact() {
  RainVoice& voice = selectVoice(leafVoices_);
  const float sr = static_cast<float>(sampleRate());
  const float diameterMm = sampleDropDiameterMm(0.02f);
  const float velocity = terminalVelocityMetersPerSecond(diameterMm) * randomRange(0.82f, 0.96f);
  const float impactDuration = std::clamp((diameterMm * 0.001f) / velocity, 0.00010f, 0.00110f);
  const float energy = normalizedImpactEnergy(diameterMm, velocity, 0.70f + controls().motion * 0.18f);
  const float softness = 0.78f + controls().detail * 0.24f;

  voice.active = true;
  voice.burstEnvelope = 0.82f + energy * 0.42f;
  voice.burstDecay = impactBurstDecay(impactDuration, 0.0035f, 0.018f + controls().motion * 0.010f);
  voice.burstGain = 0.040f + energy * 0.040f;
  voice.burstFilter.setCoefficients(
      BiquadFilter::Type::BandPass,
      randomRange(900.0f, 2200.0f + controls().detail * 900.0f),
      0.55f + controls().detail * 0.20f,
      sr);
  voice.tailEnvelope = 0.70f + energy * 0.22f;
  voice.tailDecay = impactBurstDecay(impactDuration, 0.010f, 0.040f + controls().motion * 0.014f);
  voice.tailGain = 0.018f + energy * 0.018f;
  voice.tailFilter.setCoefficients(
      BiquadFilter::Type::BandPass,
      randomRange(550.0f, 1500.0f + controls().detail * 650.0f),
      0.45f + controls().detail * 0.15f,
      sr);
  voice.tonalMix = 0.06f + controls().detail * 0.03f;

  voice.modeA.setMode(
      randomRange(480.0f, 980.0f + controls().detail * 250.0f),
      randomRange(0.012f, 0.024f + controls().motion * 0.010f),
      sr);
  voice.modeB.setMode(
      randomRange(980.0f, 1800.0f + controls().detail * 450.0f),
      randomRange(0.008f, 0.016f + controls().detail * 0.006f),
      sr);
  voice.modeC.setMode(
      randomRange(220.0f, 420.0f + controls().motion * 100.0f),
      randomRange(0.015f, 0.032f + controls().motion * 0.012f),
      sr);
  voice.modeA.excite(energy * 0.020f * softness);
  voice.modeB.excite(energy * 0.014f * softness);
  voice.modeC.excite(energy * 0.010f * softness);
}

void RainAmbienceMode::spawnPuddleImpact() {
  RainVoice& voice = selectVoice(puddleVoices_);
  const float sr = static_cast<float>(sampleRate());
  const float diameterMm = sampleDropDiameterMm(0.22f);
  const float velocity = terminalVelocityMetersPerSecond(diameterMm);
  const float impactDuration = std::clamp((diameterMm * 0.001f) / velocity, 0.00009f, 0.00120f);
  const float energy = normalizedImpactEnergy(diameterMm, velocity, 0.86f + controls().accent * 0.20f);
  const float watery = 0.88f + controls().accent * 0.35f;

  voice.active = true;
  voice.burstEnvelope = 0.85f + energy * 0.48f;
  voice.burstDecay = impactBurstDecay(impactDuration, 0.0020f, 0.012f + controls().accent * 0.010f);
  voice.burstGain = 0.050f + energy * 0.050f;
  voice.burstFilter.setCoefficients(
      BiquadFilter::Type::BandPass,
      randomRange(1200.0f, 3000.0f + controls().detail * 1200.0f),
      0.60f + controls().detail * 0.22f,
      sr);
  voice.tailEnvelope = 0.72f + energy * 0.26f;
  voice.tailDecay = impactBurstDecay(impactDuration, 0.010f, 0.034f + controls().accent * 0.012f);
  voice.tailGain = 0.028f + energy * 0.022f;
  voice.tailFilter.setCoefficients(
      BiquadFilter::Type::BandPass,
      randomRange(500.0f, 1500.0f + controls().detail * 700.0f),
      0.48f + controls().detail * 0.18f,
      sr);
  voice.tonalMix = 0.08f + controls().accent * 0.04f;

  voice.modeA.setMode(
      randomRange(320.0f, 720.0f + controls().detail * 220.0f),
      randomRange(0.018f, 0.040f + controls().accent * 0.012f),
      sr);
  voice.modeB.setMode(
      randomRange(700.0f, 1350.0f + controls().detail * 350.0f),
      randomRange(0.010f, 0.020f + controls().detail * 0.006f),
      sr);
  voice.modeC.setMode(
      randomRange(150.0f, 360.0f + controls().accent * 120.0f),
      randomRange(0.030f, 0.060f + controls().accent * 0.018f),
      sr);
  voice.modeA.excite(energy * 0.028f * watery);
  voice.modeB.excite(energy * 0.015f * watery);
  voice.modeC.excite(energy * 0.014f * watery);
}

void RainAmbienceMode::spawnDrainImpact() {
  RainVoice& voice = selectVoice(drainVoices_);
  const float sr = static_cast<float>(sampleRate());
  const float diameterMm = sampleDropDiameterMm(0.34f);
  const float velocity = terminalVelocityMetersPerSecond(diameterMm) * randomRange(0.95f, 1.05f);
  const float impactDuration = std::clamp((diameterMm * 0.001f) / velocity, 0.00010f, 0.00140f);
  const float energy = normalizedImpactEnergy(diameterMm, velocity, 0.96f + controls().accent * 0.20f);
  const float cavity = 0.92f + controls().motion * 0.16f + controls().accent * 0.20f;

  voice.active = true;
  voice.burstEnvelope = 0.82f + energy * 0.42f;
  voice.burstDecay = impactBurstDecay(impactDuration, 0.0030f, 0.018f + controls().accent * 0.012f);
  voice.burstGain = 0.034f + energy * 0.044f;
  voice.burstFilter.setCoefficients(
      BiquadFilter::Type::BandPass,
      randomRange(520.0f, 1400.0f + controls().detail * 350.0f),
      0.55f + controls().detail * 0.18f,
      sr);
  voice.tailEnvelope = 0.75f + energy * 0.18f;
  voice.tailDecay = impactBurstDecay(impactDuration, 0.020f, 0.060f + controls().accent * 0.020f);
  voice.tailGain = 0.014f + energy * 0.016f;
  voice.tailFilter.setCoefficients(
      BiquadFilter::Type::BandPass,
      randomRange(220.0f, 680.0f + controls().detail * 220.0f),
      0.42f + controls().detail * 0.12f,
      sr);
  voice.tonalMix = 0.14f + controls().accent * 0.06f;

  voice.modeA.setMode(
      randomRange(120.0f, 240.0f + controls().motion * 80.0f),
      randomRange(0.10f, 0.18f + controls().accent * 0.04f),
      sr);
  voice.modeB.setMode(
      randomRange(260.0f, 620.0f + controls().detail * 180.0f),
      randomRange(0.05f, 0.10f + controls().motion * 0.03f),
      sr);
  voice.modeC.setMode(
      randomRange(520.0f, 980.0f + controls().detail * 250.0f),
      randomRange(0.018f, 0.032f + controls().accent * 0.010f),
      sr);
  voice.modeA.excite(energy * 0.070f * cavity);
  voice.modeB.excite(energy * 0.040f * cavity);
  voice.modeC.excite(energy * 0.015f * cavity);
}

float RainAmbienceMode::sample(float /*elapsedSeconds*/) {
  const float dt = 1.0f / sampleRate();
  densityTimer_ -= dt;
  if (densityTimer_ <= 0.0f) {
    const float macroBase = 0.35f + controls().intensity * 0.45f + controls().motion * 0.08f;
    densityTarget_ = std::clamp(macroBase + randomRange(-0.22f, 0.22f), 0.10f, 1.0f);
    densityTimer_ = sampleEventInterval(0.65f + controls().motion * 0.55f, 0.20f);
  }

  const float densitySlew = 1.0f - std::exp(-dt / (0.22f + (1.0f - controls().motion) * 0.32f));
  densityState_ += (densityTarget_ - densityState_) * densitySlew;
  const float resolvedDensity = std::clamp(densityState_, 0.10f, 1.0f);

  const float roofRateHz =
      22.0f + controls().intensity * 125.0f + controls().detail * 34.0f + resolvedDensity * 38.0f;
  const float leafRateHz =
      4.0f + controls().intensity * 26.0f + controls().motion * 16.0f + resolvedDensity * 10.0f;
  const float puddleRateHz =
      1.6f + controls().intensity * 9.0f + controls().accent * 5.2f + resolvedDensity * 3.0f;
  const float drainRateHz =
      0.65f + controls().intensity * 3.2f + controls().accent * 4.0f + controls().motion * 1.2f;

  roofTimer_ -= dt;
  while (roofTimer_ <= 0.0f) {
    spawnRoofTick();
    roofTimer_ += sampleEventInterval(roofRateHz, 0.0018f);
  }

  leafTimer_ -= dt;
  while (leafTimer_ <= 0.0f) {
    spawnLeafImpact();
    leafTimer_ += sampleEventInterval(leafRateHz, 0.0070f);
  }

  puddleTimer_ -= dt;
  while (puddleTimer_ <= 0.0f) {
    spawnPuddleImpact();
    puddleTimer_ += sampleEventInterval(puddleRateHz, 0.018f);
  }

  drainTimer_ -= dt;
  while (drainTimer_ <= 0.0f) {
    spawnDrainImpact();
    drainTimer_ += sampleEventInterval(drainRateHz, 0.040f);
  }

  float rain = 0.0f;
  for (auto& voice : roofVoices_) {
    rain += processVoice(voice, noiseRoof_);
  }
  for (auto& voice : leafVoices_) {
    rain += processVoice(voice, noiseLeaf_);
  }
  for (auto& voice : puddleVoices_) {
    rain += processVoice(voice, noiseWater_);
  }
  for (auto& voice : drainVoices_) {
    rain += processVoice(voice, noiseDrain_);
  }

  float mist = mistHighPass_.process(noiseMist_.pink());
  mist = mistLowPass_.process(mist);
  mist *= (0.0012f + controls().detail * 0.0048f + controls().intensity * 0.0024f)
      * (0.45f + resolvedDensity * 0.55f);

  float distantBody = distantBodyBandPass_.process(noiseMist_.brown());
  distantBody *= (0.0012f + controls().intensity * 0.0042f + controls().accent * 0.0016f)
      * (0.55f + resolvedDensity * 0.45f);

  float distantRoof = distantRoofBandPass_.process(noiseMist_.pink());
  distantRoof *= (0.0010f + controls().detail * 0.0028f + controls().motion * 0.0014f)
      * (0.50f + resolvedDensity * 0.50f);

  const float combined = (rain * 0.82f) + mist + distantBody + distantRoof;
  return std::tanh(combined * (0.90f + controls().intensity * 0.18f + controls().volume * 0.10f));
}

}  // namespace jvn::audiofx::detail
