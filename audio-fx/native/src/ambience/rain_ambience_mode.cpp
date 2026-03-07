#include "ambience/rain_ambience_mode.hpp"

namespace jvn::audiofx::detail {

RainAmbienceMode::RainAmbienceMode(int sampleRate) : BaseAmbienceMode(sampleRate, 0x20293847u) {
  const float sr = static_cast<float>(sampleRate);
  slowLfo_.setSampleRate(sr);
  mediumLfo_.setSampleRate(sr);
}

void RainAmbienceMode::configure(const RenderControls& controls) {
  setControls(controls);
  dropletEnvelope_ = 0.0f;
  impactEnvelope_ = 0.0f;
  dropTimer_ = randomRange(0.02f, 0.12f);
  slowLfo_.reset();
  mediumLfo_.reset();
  noiseMid_.reset();
  noiseHigh_.reset();
  noiseDrop_.reset();
  impactBody_.reset();
  gutterBody_.reset();
  bedLowPass_.reset();
  bedHighPass_.reset();
  hissHighPass_.reset();
  dropBandPass_.reset();
  impactBandPass_.reset();
  updateFilters();
}

void RainAmbienceMode::updateFilters() {
  const float sr = static_cast<float>(sampleRate());
  bedLowPass_.setCoefficients(
      BiquadFilter::Type::LowPass,
      5200.0f + controls().detail * 4200.0f + controls().intensity * 1800.0f,
      0.55f,
      sr);
  bedHighPass_.setCoefficients(BiquadFilter::Type::HighPass, 350.0f, 0.55f, sr);
  hissHighPass_.setCoefficients(
      BiquadFilter::Type::HighPass,
      3200.0f + controls().detail * 2200.0f,
      0.7f,
      sr);
  dropBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      1400.0f + controls().detail * 1600.0f + controls().accent * 500.0f,
      1.0f + controls().accent * 0.7f,
      sr);
  impactBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      650.0f + controls().detail * 950.0f + controls().accent * 220.0f,
      1.4f + controls().detail * 0.8f,
      sr);
  impactBody_.setMode(
      920.0f + controls().detail * 720.0f + controls().accent * 180.0f,
      0.045f + controls().detail * 0.030f,
      sr);
  gutterBody_.setMode(
      280.0f + controls().detail * 260.0f + controls().motion * 90.0f,
      0.090f + controls().motion * 0.050f,
      sr);
}

float RainAmbienceMode::sample(float /*elapsedSeconds*/) {
  const float dt = 1.0f / sampleRate();
  slowLfo_.advance();
  mediumLfo_.advance();
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;
  const float density = 0.20f + controls().intensity * 0.65f + controls().accent * 0.25f;

  float bed = bedLowPass_.process(noiseMid_.pink());
  bed = bedHighPass_.process(bed);
  bed *= (0.22f + density * 0.38f) * (0.85f + controls().detail * 0.35f);

  float hiss = hissHighPass_.process(noiseHigh_.white());
  hiss *= 0.04f + controls().intensity * 0.12f + controls().detail * 0.12f;

  dropTimer_ -= dt;
  if (dropTimer_ <= 0.0f) {
    dropletEnvelope_ = std::max(
        dropletEnvelope_,
        (0.14f + controls().intensity * 0.22f + controls().accent * 0.30f) * randomRange(0.85f, 1.20f));
    if (nextRandom01() < (0.18f + controls().intensity * 0.42f + controls().accent * 0.14f)) {
      const float impactEnergy =
          (0.10f + controls().intensity * 0.18f + controls().accent * 0.12f) * randomRange(0.88f, 1.15f);
      impactEnvelope_ = std::max(impactEnvelope_, impactEnergy);
      const float impactResonance =
          randomRange(520.0f + controls().detail * 280.0f, 1350.0f + controls().detail * 850.0f);
      impactBody_.setMode(
          impactResonance,
          0.035f + controls().detail * 0.035f + controls().accent * 0.015f,
          sampleRate());
      gutterBody_.setMode(
          randomRange(220.0f + controls().motion * 80.0f, 480.0f + controls().detail * 220.0f),
          0.080f + controls().motion * 0.050f,
          sampleRate());
      impactBody_.excite(impactEnergy * (0.40f + controls().detail * 0.22f));
      gutterBody_.excite(impactEnergy * (0.14f + controls().motion * 0.10f));
    }
    const float dropRateHz =
        8.0f + controls().intensity * 18.0f + controls().accent * 3.0f + controls().motion * 5.0f;
    dropTimer_ = sampleEventInterval(dropRateHz, 0.008f);
  }
  dropletEnvelope_ *= 0.9935f - controls().intensity * 0.0015f - controls().motion * 0.0008f;
  impactEnvelope_ *= 0.9895f - controls().motion * 0.0012f;

  float drop = dropBandPass_.process(noiseDrop_.white()) * dropletEnvelope_;
  drop *= 0.36f + slowMod * 0.16f + controls().accent * 0.10f;
  float splash = hissHighPass_.process(noiseDrop_.white());
  splash *= dropletEnvelope_ * (0.10f + controls().detail * 0.10f + controls().accent * 0.12f);
  float impactRing = impactBandPass_.process(impactBody_.process());
  impactRing *= impactEnvelope_ * (0.48f + controls().detail * 0.14f + controls().accent * 0.10f);
  float gutter = impactBandPass_.process(noiseMid_.pink());
  gutter *= impactEnvelope_ * (0.08f + controls().intensity * 0.06f + controls().motion * 0.04f);
  const float gutterTone = gutterBody_.process()
      * impactEnvelope_ * (0.22f + controls().motion * 0.12f + controls().detail * 0.05f);

  return std::tanh(
      (bed + hiss + drop + splash + impactRing + gutter + gutterTone) * (0.72f + density * 0.28f));
}

}  // namespace jvn::audiofx::detail
