#include "ambience/fireplace_ambience_mode.hpp"

namespace jvn::audiofx::detail {

FireplaceAmbienceMode::FireplaceAmbienceMode(int sampleRate) : BaseAmbienceMode(sampleRate, 0x50293847u) {
  const float sr = static_cast<float>(sampleRate);
  slowLfo_.setSampleRate(sr);
  mediumLfo_.setSampleRate(sr);
  fastLfo_.setSampleRate(sr);
}

void FireplaceAmbienceMode::configure(const RenderControls& controls) {
  setControls(controls);
  crackleEnvelope_ = 0.0f;
  popEnvelope_ = 0.0f;
  snapEnvelope_ = 0.0f;
  emberPhase_ = 0.0f;
  crackleTimer_ = randomRange(0.3f, 1.0f);
  popTimer_ = randomRange(0.08f, 0.30f);
  snapTimer_ = randomRange(0.02f, 0.10f);
  slowLfo_.reset();
  mediumLfo_.reset();
  fastLfo_.reset();
  noiseFire_.reset();
  noiseFireDetail_.reset();
  noiseHigh_.reset();
  crackleBandPass_.reset();
  baseLowPass_.reset();
  hissHighPass_.reset();
  snapBandPass_.reset();
  emberLowPass_.reset();
  updateFilters();
}

void FireplaceAmbienceMode::updateFilters() {
  const float sr = static_cast<float>(sampleRate());
  crackleBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      900.0f + controls().detail * 1200.0f + controls().accent * 400.0f,
      1.8f + controls().detail * 0.8f,
      sr);
  baseLowPass_.setCoefficients(
      BiquadFilter::Type::LowPass,
      220.0f + controls().intensity * 80.0f,
      0.85f,
      sr);
  hissHighPass_.setCoefficients(
      BiquadFilter::Type::HighPass,
      3600.0f + controls().detail * 2400.0f,
      0.55f,
      sr);
  snapBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      2500.0f + controls().detail * 1500.0f + controls().accent * 500.0f,
      2.0f + controls().detail * 1.0f,
      sr);
  emberLowPass_.setCoefficients(
      BiquadFilter::Type::LowPass,
      120.0f + controls().intensity * 60.0f,
      0.9f,
      sr);
}

float FireplaceAmbienceMode::sample(float /*elapsedSeconds*/) {
  const float dt = 1.0f / sampleRate();
  slowLfo_.advance();
  mediumLfo_.advance();
  fastLfo_.advance();
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;
  const float medMod = mediumLfo_.triangle() * 0.5f + 0.5f;
  const float fastMod = fastLfo_.sine() * 0.5f + 0.5f;
  const float breathe = 0.78f + 0.22f * medMod * (0.8f + controls().motion * 0.4f);

  emberPhase_ += (0.08f + controls().motion * 0.06f) * dt;
  if (emberPhase_ > 1.0f) emberPhase_ -= 1.0f;
  const float emberGlow = 0.7f + 0.3f * std::sin(emberPhase_ * 2.0f * kPi);
  float ember = emberLowPass_.process(noiseFire_.brown());
  ember *= (0.14f + controls().intensity * 0.18f) * emberGlow;

  float base = baseLowPass_.process(noiseFire_.brown());
  base *= (0.18f + controls().intensity * 0.24f) * (0.85f + slowMod * 0.15f);

  crackleTimer_ -= dt;
  if (crackleTimer_ <= 0.0f && crackleEnvelope_ < 0.10f) {
    crackleEnvelope_ =
        (0.34f + controls().intensity * 0.34f + controls().accent * 0.22f) * randomRange(0.85f, 1.20f);
    const float crackleRateHz = 0.7f + controls().intensity * 1.6f + controls().accent * 1.2f;
    crackleTimer_ = sampleEventInterval(crackleRateHz, 0.12f);
  }
  crackleEnvelope_ *= 0.9972f - controls().motion * 0.0008f;
  float crackle = crackleBandPass_.process(noiseFire_.white());
  crackle *= crackleEnvelope_ * (0.50f + controls().detail * 0.30f + controls().accent * 0.15f);

  popTimer_ -= dt;
  if (popTimer_ <= 0.0f && popEnvelope_ < 0.10f) {
    popEnvelope_ =
        (0.18f + controls().intensity * 0.22f + controls().accent * 0.12f) * randomRange(0.85f, 1.18f);
    const float popRateHz =
        2.5f + controls().intensity * 5.0f + controls().accent * 2.5f + controls().detail * 1.5f;
    popTimer_ = sampleEventInterval(popRateHz, 0.05f);
  }
  popEnvelope_ *= 0.9952f - controls().motion * 0.0006f;
  float pop = crackleBandPass_.process(noiseFireDetail_.white());
  pop *= popEnvelope_ * (0.35f + controls().detail * 0.25f);

  snapTimer_ -= dt;
  if (snapTimer_ <= 0.0f && snapEnvelope_ < 0.10f) {
    snapEnvelope_ =
        (0.11f + controls().intensity * 0.15f + controls().detail * 0.10f) * randomRange(0.90f, 1.15f);
    const float snapRateHz = 5.5f + controls().intensity * 9.0f + controls().detail * 7.0f;
    snapTimer_ = sampleEventInterval(snapRateHz, 0.02f);
  }
  snapEnvelope_ *= 0.992f - controls().motion * 0.001f;
  float snap = snapBandPass_.process(noiseFireDetail_.white());
  snap *= snapEnvelope_ * (0.20f + controls().detail * 0.25f);

  float hiss = hissHighPass_.process(noiseHigh_.pink());
  hiss *= (0.03f + controls().intensity * 0.06f + controls().detail * 0.08f) * fastMod;

  return std::tanh((ember + base + crackle + pop + snap + hiss) * breathe * 0.76f);
}

}  // namespace jvn::audiofx::detail
