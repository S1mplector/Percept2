#include "ambience/ocean_ambience_mode.hpp"

namespace jvn::audiofx::detail {

OceanAmbienceMode::OceanAmbienceMode(int sampleRate) : BaseAmbienceMode(sampleRate, 0x30293847u) {
  const float sr = static_cast<float>(sampleRate);
  slowLfo_.setSampleRate(sr);
  mediumLfo_.setSampleRate(sr);
  fastLfo_.setSampleRate(sr);
}

void OceanAmbienceMode::configure(const RenderControls& controls) {
  setControls(controls);
  crashEnvelope_ = 0.0f;
  backwashEnvelope_ = 0.0f;
  crashTimer_ = randomRange(0.6f, 1.5f);
  slowLfo_.reset();
  mediumLfo_.reset();
  fastLfo_.reset();
  noiseLow_.reset();
  noiseMid_.reset();
  noiseHigh_.reset();
  noiseOceanFoam_.reset();
  crashBody_.reset();
  backwashBody_.reset();
  swellLowPass_.reset();
  washBandPass_.reset();
  foamHighPass_.reset();
  undertowLowPass_.reset();
  crashBandPass_.reset();
  sprayHighPass_.reset();
  backwashBandPass_.reset();
  updateFilters();
}

void OceanAmbienceMode::updateFilters() {
  const float sr = static_cast<float>(sampleRate());
  swellLowPass_.setCoefficients(
      BiquadFilter::Type::LowPass,
      180.0f + controls().intensity * 110.0f + controls().accent * 70.0f,
      0.8f,
      sr);
  washBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      560.0f + controls().detail * 580.0f + controls().intensity * 280.0f,
      0.7f,
      sr);
  foamHighPass_.setCoefficients(
      BiquadFilter::Type::HighPass,
      2200.0f + controls().detail * 1800.0f,
      0.7f,
      sr);
  undertowLowPass_.setCoefficients(
      BiquadFilter::Type::LowPass,
      55.0f + controls().intensity * 25.0f,
      0.85f,
      sr);
  crashBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      600.0f + controls().detail * 400.0f + controls().accent * 200.0f,
      1.0f + controls().accent * 0.5f,
      sr);
  sprayHighPass_.setCoefficients(
      BiquadFilter::Type::HighPass,
      4000.0f + controls().detail * 2000.0f,
      0.6f,
      sr);
  backwashBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      240.0f + controls().detail * 280.0f + controls().motion * 90.0f,
      0.9f + controls().motion * 0.5f,
      sr);
  crashBody_.setMode(
      140.0f + controls().intensity * 55.0f + controls().accent * 35.0f,
      0.20f + controls().accent * 0.08f,
      sr);
  backwashBody_.setMode(
      220.0f + controls().detail * 140.0f + controls().motion * 60.0f,
      0.28f + controls().motion * 0.10f,
      sr);
}

float OceanAmbienceMode::sample(float elapsedSeconds) {
  slowLfo_.advance();
  mediumLfo_.advance();
  fastLfo_.advance();

  const float primaryRate = 0.08f + controls().intensity * 0.04f + controls().motion * 0.03f;
  const float secondaryRate = 0.18f + controls().intensity * 0.06f + controls().motion * 0.04f;
  const float primaryWave = std::sin(elapsedSeconds * primaryRate * 2.0f * kPi);
  const float secondaryWave = std::sin(elapsedSeconds * secondaryRate * 2.0f * kPi);
  const float rawCycle = 0.65f * primaryWave + 0.35f * secondaryWave;
  const float shaped = rawCycle > 0.0f
      ? std::pow(rawCycle, 0.7f)
      : -std::pow(-rawCycle, 1.4f) * 0.6f;
  const float waveCycle = shaped * 0.5f + 0.5f;

  float undertow = undertowLowPass_.process(noiseLow_.brown());
  undertow *= (0.12f + controls().intensity * 0.16f + controls().accent * 0.08f) * (1.0f - waveCycle * 0.4f);

  float swell = swellLowPass_.process(noiseLow_.brown());
  swell *= (0.14f + controls().intensity * 0.22f + controls().accent * 0.10f) * (0.25f + 0.75f * waveCycle);

  float wash = washBandPass_.process(noiseMid_.pink());
  const float washGain = std::max(0.0f, waveCycle - 0.2f) / 0.8f;
  wash *= (0.15f + controls().intensity * 0.28f + controls().detail * 0.14f) * washGain
      * (0.85f + mediumLfo_.triangle() * 0.15f);

  const float crest = std::max(0.0f, (waveCycle - 0.5f) / 0.5f);
  float foam = foamHighPass_.process(noiseHigh_.white());
  float spray = sprayHighPass_.process(noiseOceanFoam_.white());
  foam *= crest * crest * (0.07f + controls().intensity * 0.14f + controls().detail * 0.11f);
  spray *= crest * crest * crest * (0.03f + controls().detail * 0.07f);

  crashTimer_ -= 1.0f / sampleRate();
  if (crashTimer_ <= 0.0f && crest > randomRange(0.32f, 0.68f)) {
    crashEnvelope_ =
        (0.34f + controls().intensity * 0.34f + controls().accent * 0.34f) * randomRange(0.88f, 1.18f);
    backwashEnvelope_ = std::max(backwashEnvelope_, crashEnvelope_ * randomRange(0.28f, 0.48f));
    crashBody_.setMode(
        randomRange(110.0f + controls().intensity * 45.0f, 210.0f + controls().accent * 55.0f),
        0.22f + controls().accent * 0.12f,
        sampleRate());
    backwashBody_.setMode(
        randomRange(180.0f + controls().motion * 50.0f, 360.0f + controls().detail * 120.0f),
        0.28f + controls().motion * 0.12f,
        sampleRate());
    crashBody_.excite(crashEnvelope_ * (0.26f + controls().accent * 0.14f));
    backwashBody_.excite(backwashEnvelope_ * (0.18f + controls().motion * 0.10f));
    const float crashRateHz = 0.06f + controls().intensity * 0.16f + controls().accent * 0.04f;
    crashTimer_ = sampleEventInterval(crashRateHz, 0.65f);
  }
  crashEnvelope_ *= 0.9975f - controls().motion * 0.0006f;
  const float backwashDrive = std::max(0.0f, (1.0f - crest) * (0.28f + crashEnvelope_ * 0.45f));
  backwashEnvelope_ += (backwashDrive - backwashEnvelope_) * 0.0012f;
  backwashEnvelope_ *= 0.9990f - controls().motion * 0.0004f;

  float crash = crashBandPass_.process(noiseOceanFoam_.pink());
  crash *= crashEnvelope_ * (0.26f + controls().detail * 0.20f + controls().accent * 0.18f);
  float crashSpray = sprayHighPass_.process(noiseOceanFoam_.white());
  crashSpray *= crashEnvelope_ * crashEnvelope_
      * (0.05f + controls().detail * 0.08f + controls().accent * 0.06f);
  const float crashBody = crashBody_.process()
      * (0.20f + controls().accent * 0.12f + controls().intensity * 0.08f);
  float backwash = backwashBandPass_.process(noiseMid_.pink());
  backwash *= backwashEnvelope_ * (0.10f + controls().motion * 0.08f + controls().detail * 0.04f);
  const float backwashBody = backwashBody_.process()
      * (0.12f + controls().motion * 0.10f + controls().detail * 0.05f);

  float roar = noiseMid_.filtered(0.04f + controls().intensity * 0.06f, 0.1f + controls().accent * 0.1f);
  roar *= (0.05f + controls().intensity * 0.07f) * (slowLfo_.sine() * 0.5f + 0.5f);

  return std::tanh(
      (undertow + swell + wash + foam + spray + crash + crashSpray + crashBody + backwash + backwashBody + roar)
      * 0.78f);
}

}  // namespace jvn::audiofx::detail
