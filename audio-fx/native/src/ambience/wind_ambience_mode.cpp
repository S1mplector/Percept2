#include "ambience/wind_ambience_mode.hpp"

namespace jvn::audiofx::detail {

WindAmbienceMode::WindAmbienceMode(int sampleRate) : BaseAmbienceMode(sampleRate, 0x10293847u) {
  const float sr = static_cast<float>(sampleRate);
  gust_.setSampleRate(sr);
  slowLfo_.setSampleRate(sr);
  mediumLfo_.setSampleRate(sr);
  fastLfo_.setSampleRate(sr);
}

void WindAmbienceMode::configure(const RenderControls& controls) {
  setControls(controls);
  gustTimer_ = randomRange(0.2f, 0.8f);
  whistlePhase_ = 0.0f;
  whistleOvertonePhase_ = 0.0f;
  gust_.reset();
  noiseLow_.reset();
  noiseMid_.reset();
  noiseHigh_.reset();
  noiseGust_.reset();
  slowLfo_.reset();
  mediumLfo_.reset();
  fastLfo_.reset();
  lowPassLow_.reset();
  lowPassMid_.reset();
  highPassMid_.reset();
  highPassHigh_.reset();
  lowPassHigh_.reset();
  gustFilter_.reset();
  updateFilters();
}

void WindAmbienceMode::updateFilters() {
  const float sr = static_cast<float>(sampleRate());
  const float detailBrightness = 0.8f + controls().detail * 0.65f;
  const float intensityMod = 0.8f + controls().intensity * 0.45f;

  lowPassLow_.setCoefficients(
      BiquadFilter::Type::LowPass,
      180.0f * intensityMod * (0.9f + controls().detail * 0.2f),
      0.7f,
      sr);
  lowPassMid_.setCoefficients(
      BiquadFilter::Type::LowPass,
      700.0f * intensityMod * detailBrightness,
      0.5f,
      sr);
  highPassMid_.setCoefficients(BiquadFilter::Type::HighPass, 150.0f, 0.5f, sr);
  highPassHigh_.setCoefficients(
      BiquadFilter::Type::HighPass,
      1700.0f + controls().detail * 1600.0f + controls().intensity * 900.0f,
      0.6f,
      sr);
  lowPassHigh_.setCoefficients(
      BiquadFilter::Type::LowPass,
      6200.0f + controls().detail * 3800.0f,
      0.45f,
      sr);
  gustFilter_.setCoefficients(
      BiquadFilter::Type::BandPass,
      320.0f + controls().accent * 220.0f,
      1.2f + controls().accent * 0.8f,
      sr);
}

void WindAmbienceMode::maybeTriggerGust(float dt) {
  if (gust_.isActive()) return;
  gustTimer_ -= dt;
  if (gustTimer_ <= 0.0f) {
    const float gustEnergy =
        (0.22f + controls().intensity * 0.60f + controls().accent * 0.28f + controls().motion * 0.18f)
        * randomRange(0.88f, 1.15f);
    gust_.trigger(gustEnergy);
    const float gustRateHz =
        0.08f + controls().motion * 0.11f + controls().intensity * 0.08f + controls().accent * 0.04f;
    gustTimer_ = sampleEventInterval(gustRateHz, 0.7f);
  }
}

float WindAmbienceMode::sample(float /*elapsedSeconds*/) {
  const float dt = 1.0f / sampleRate();
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;
  const float medMod = mediumLfo_.triangle() * 0.5f + 0.5f;
  const float fastMod = fastLfo_.sine() * 0.2f + 0.8f;
  const float randomMod = slowLfo_.smoothRandom() * 0.15f + 0.85f;
  slowLfo_.advance();
  mediumLfo_.advance();
  fastLfo_.advance();

  maybeTriggerGust(dt);
  const float gustEnvelope = gust_.generate();
  gust_.update();

  const float shapedIntensity = std::sqrt(std::max(0.0f, controls().intensity * slowMod * randomMod));
  const float detailGain = 0.8f + controls().detail * 0.5f;

  const float lowLayer =
      lowPassLow_.process(noiseLow_.brown()) * 0.5f * shapedIntensity * 0.6f;

  float midNoise = lowPassMid_.process(noiseMid_.pink());
  midNoise = highPassMid_.process(midNoise);
  const float midLayer = midNoise * 0.6f * shapedIntensity * medMod * (1.0f + shapedIntensity * 0.3f)
      * 0.85f * detailGain;

  float highNoise = highPassHigh_.process(noiseHigh_.pink());
  highNoise = lowPassHigh_.process(highNoise);
  const float highLayer =
      highNoise * 0.4f * std::pow(shapedIntensity, 2.4f) * fastMod * (0.12f + controls().detail * 0.22f);

  const float gustNoise = gustFilter_.process(noiseGust_.pink());
  const float gustLayer = gustNoise * gustEnvelope * (0.35f + controls().accent * 0.45f);

  float speedWhoosh = 0.0f;
  if (controls().intensity > 0.35f) {
    const float whooshNoise = noiseMid_.filtered(
        0.06f + controls().intensity * 0.18f + controls().detail * 0.06f,
        0.2f + controls().accent * 0.2f);
    const float whooshIntensity = std::sqrt((controls().intensity - 0.35f) / 0.65f);
    speedWhoosh = whooshNoise * whooshIntensity * (0.24f + controls().motion * 0.10f) * medMod;
  }

  const float whistleDrive = std::max(
      0.0f,
      gustEnvelope * (0.9f + controls().accent * 0.45f)
          + std::max(0.0f, speedWhoosh) * (0.6f + controls().detail * 0.35f)
          - 0.10f);
  const float whistleFreq =
      620.0f + controls().detail * 1050.0f + controls().accent * 650.0f + medMod * 240.0f
      + gustEnvelope * 360.0f;
  whistlePhase_ += whistleFreq * dt;
  if (whistlePhase_ > 1.0f) whistlePhase_ -= std::floor(whistlePhase_);
  whistleOvertonePhase_ += whistleFreq * (1.92f + medMod * 0.06f) * dt;
  if (whistleOvertonePhase_ > 1.0f) whistleOvertonePhase_ -= std::floor(whistleOvertonePhase_);

  const float whistle = std::sin(whistlePhase_ * 2.0f * kPi)
      * whistleDrive * whistleDrive
      * (0.015f + controls().detail * 0.055f + controls().accent * 0.040f);
  const float overtone = std::sin(whistleOvertonePhase_ * 2.0f * kPi)
      * whistleDrive * whistleDrive * whistleDrive
      * (0.003f + controls().detail * 0.015f + controls().accent * 0.010f);
  const float edgeFlutter = lowPassHigh_.process(highPassHigh_.process(noiseGust_.white()))
      * whistleDrive * (0.010f + controls().detail * 0.020f);

  return std::tanh(
      (lowLayer + midLayer + highLayer + gustLayer + speedWhoosh + whistle + overtone + edgeFlutter)
      * 0.60f);
}

}  // namespace jvn::audiofx::detail
