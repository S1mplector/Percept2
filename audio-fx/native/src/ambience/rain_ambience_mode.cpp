#include "ambience/rain_ambience_mode.hpp"

namespace jvn::audiofx::detail {

RainAmbienceMode::RainAmbienceMode(int sampleRate) : BaseAmbienceMode(sampleRate, 0x20293847u) {
  const float sr = static_cast<float>(sampleRate);
  slowLfo_.setSampleRate(sr);
  mediumLfo_.setSampleRate(sr);
}

void RainAmbienceMode::configure(const RenderControls& controls) {
  setControls(controls);
  microDropEnvelope_ = 0.0f;
  roofSplashEnvelope_ = 0.0f;
  impactEnvelope_ = 0.0f;
  microDropTimer_ = randomRange(0.006f, 0.035f);
  impactTimer_ = randomRange(0.055f, 0.180f);
  slowLfo_.reset();
  mediumLfo_.reset();
  noiseMid_.reset();
  noiseHigh_.reset();
  noiseDrop_.reset();
  roofTickBody_.reset();
  leafDripBody_.reset();
  impactBody_.reset();
  gutterBody_.reset();
  drainBody_.reset();
  bedLowPass_.reset();
  bedHighPass_.reset();
  bedPresenceBandPass_.reset();
  mistHighPass_.reset();
  splashHighPass_.reset();
  dropBandPass_.reset();
  updateFilters();
}

void RainAmbienceMode::updateFilters() {
  const float sr = static_cast<float>(sampleRate());
  bedLowPass_.setCoefficients(
      BiquadFilter::Type::LowPass,
      2200.0f + controls().detail * 1700.0f + controls().intensity * 700.0f,
      0.62f,
      sr);
  bedHighPass_.setCoefficients(
      BiquadFilter::Type::HighPass,
      110.0f + controls().intensity * 60.0f,
      0.70f,
      sr);
  bedPresenceBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      540.0f + controls().detail * 320.0f + controls().motion * 90.0f,
      0.85f,
      sr);
  mistHighPass_.setCoefficients(
      BiquadFilter::Type::HighPass,
      5600.0f + controls().detail * 1800.0f,
      0.76f,
      sr);
  splashHighPass_.setCoefficients(
      BiquadFilter::Type::HighPass,
      3400.0f + controls().detail * 2200.0f + controls().accent * 300.0f,
      0.86f,
      sr);
  dropBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      2400.0f + controls().detail * 2100.0f + controls().accent * 650.0f,
      1.15f + controls().detail * 0.60f,
      sr);
  impactNoiseBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      850.0f + controls().detail * 900.0f + controls().accent * 250.0f,
      1.25f + controls().detail * 0.75f,
      sr);
  roofTickBody_.setMode(
      3000.0f + controls().detail * 1800.0f + controls().accent * 250.0f,
      0.014f + controls().detail * 0.010f,
      sr);
  leafDripBody_.setMode(
      1600.0f + controls().detail * 1200.0f + controls().accent * 300.0f,
      0.026f + controls().accent * 0.016f,
      sr);
  impactBody_.setMode(
      760.0f + controls().detail * 460.0f + controls().accent * 180.0f,
      0.060f + controls().detail * 0.036f,
      sr);
  gutterBody_.setMode(
      230.0f + controls().detail * 180.0f + controls().motion * 120.0f,
      0.110f + controls().motion * 0.070f,
      sr);
  drainBody_.setMode(
      125.0f + controls().intensity * 75.0f + controls().motion * 55.0f,
      0.175f + controls().accent * 0.090f,
      sr);
}

float RainAmbienceMode::sample(float /*elapsedSeconds*/) {
  const float dt = 1.0f / sampleRate();
  slowLfo_.advance();
  mediumLfo_.advance();
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;
  const float density = 0.18f + controls().intensity * 0.58f + controls().accent * 0.20f;

  const float microDropRateHz =
      18.0f + controls().intensity * 52.0f + controls().motion * 16.0f + controls().accent * 4.0f;
  const float impactRateHz =
      1.1f + controls().intensity * 4.8f + controls().accent * 2.6f + controls().motion * 0.8f;

  float bed = bedLowPass_.process(noiseMid_.pink() * 0.72f + noiseMid_.brown() * 0.28f);
  bed = bedHighPass_.process(bed);
  bed *= (0.08f + density * 0.15f) * (0.82f + controls().detail * 0.12f);

  float roofPresence = bedPresenceBandPass_.process(noiseMid_.pink());
  roofPresence *= 0.03f + controls().intensity * 0.05f + controls().motion * 0.03f;

  float mist = mistHighPass_.process(noiseHigh_.white());
  mist *= 0.007f + controls().detail * 0.030f + controls().intensity * 0.012f;

  microDropTimer_ -= dt;
  if (microDropTimer_ <= 0.0f) {
    const float microEnergy =
        (0.055f + controls().intensity * 0.075f + controls().detail * 0.040f) * randomRange(0.75f, 1.30f);
    microDropEnvelope_ = std::max(microDropEnvelope_, microEnergy);
    roofSplashEnvelope_ = std::max(roofSplashEnvelope_, microEnergy * (0.28f + controls().detail * 0.12f));
    roofTickBody_.setMode(
        randomRange(2600.0f + controls().detail * 900.0f, 5200.0f + controls().detail * 1700.0f),
        randomRange(0.010f, 0.024f + controls().detail * 0.008f),
        sampleRate());
    roofTickBody_.excite(microEnergy * (0.46f + controls().detail * 0.18f));
    if (nextRandom01() < (0.32f + controls().accent * 0.20f)) {
      leafDripBody_.setMode(
          randomRange(1050.0f + controls().detail * 250.0f, 2300.0f + controls().detail * 900.0f),
          randomRange(0.018f, 0.040f + controls().accent * 0.016f),
          sampleRate());
      leafDripBody_.excite(microEnergy * (0.26f + controls().accent * 0.18f));
    }
    microDropTimer_ = sampleEventInterval(microDropRateHz, 0.0035f);
  }

  impactTimer_ -= dt;
  if (impactTimer_ <= 0.0f) {
    const float impactEnergy =
        (0.080f + controls().intensity * 0.120f + controls().accent * 0.120f) * randomRange(0.72f, 1.25f);
    impactEnvelope_ = std::max(impactEnvelope_, impactEnergy);
    roofSplashEnvelope_ = std::max(roofSplashEnvelope_, impactEnergy * (0.30f + controls().detail * 0.14f));
    impactBody_.setMode(
        randomRange(520.0f + controls().detail * 180.0f, 1180.0f + controls().detail * 620.0f),
        randomRange(0.050f, 0.090f + controls().detail * 0.022f),
        sampleRate());
    gutterBody_.setMode(
        randomRange(190.0f + controls().motion * 55.0f, 430.0f + controls().detail * 160.0f),
        randomRange(0.090f, 0.160f + controls().motion * 0.030f),
        sampleRate());
    drainBody_.setMode(
        randomRange(110.0f + controls().motion * 35.0f, 240.0f + controls().accent * 65.0f),
        randomRange(0.150f, 0.240f + controls().accent * 0.050f),
        sampleRate());
    impactBody_.excite(impactEnergy * (0.44f + controls().detail * 0.16f));
    gutterBody_.excite(impactEnergy * (0.24f + controls().motion * 0.16f));
    drainBody_.excite(impactEnergy * (0.18f + controls().accent * 0.10f));
    impactTimer_ = sampleEventInterval(impactRateHz, 0.030f);
  }

  const float microDecay = std::exp(-dt / (0.010f + controls().detail * 0.008f));
  const float splashDecay = std::exp(-dt / (0.018f + controls().detail * 0.014f + controls().motion * 0.010f));
  const float impactDecay = std::exp(-dt / (0.060f + controls().motion * 0.040f + controls().accent * 0.020f));
  microDropEnvelope_ *= microDecay;
  roofSplashEnvelope_ *= splashDecay;
  impactEnvelope_ *= impactDecay;

  float tickNoise = dropBandPass_.process(noiseDrop_.white()) * microDropEnvelope_;
  tickNoise *= 0.11f + slowMod * 0.05f + controls().detail * 0.06f;
  const float tickTone = roofTickBody_.process() * (0.58f + controls().detail * 0.16f);
  const float leafTone = leafDripBody_.process() * (0.26f + controls().accent * 0.14f);
  float splash = splashHighPass_.process(noiseDrop_.white());
  splash *= roofSplashEnvelope_ * (0.030f + controls().detail * 0.035f + controls().accent * 0.035f);
  float impactNoise = impactNoiseBandPass_.process(noiseDrop_.pink());
  impactNoise *= impactEnvelope_ * (0.040f + controls().intensity * 0.028f);
  const float impactTone = impactBody_.process() * (0.56f + controls().detail * 0.18f + controls().accent * 0.08f);
  const float gutterTone = gutterBody_.process() * (0.30f + controls().motion * 0.16f);
  const float drainTone = drainBody_.process() * (0.24f + controls().accent * 0.12f);

  return std::tanh(
      (bed + roofPresence + mist + tickNoise + tickTone + leafTone + splash + impactNoise + impactTone + gutterTone
          + drainTone)
          * (0.86f + density * 0.18f));
}

}  // namespace jvn::audiofx::detail
