#include "ambience/thunder_ambience_mode.hpp"

namespace jvn::audiofx::detail {

ThunderAmbienceMode::ThunderAmbienceMode(int sampleRate) : BaseAmbienceMode(sampleRate, 0x40293847u) {
  const float sr = static_cast<float>(sampleRate);
  slowLfo_.setSampleRate(sr);
  mediumLfo_.setSampleRate(sr);
  fastLfo_.setSampleRate(sr);
}

void ThunderAmbienceMode::configure(const RenderControls& controls) {
  setControls(controls);
  rumblePhase_ = 0.0f;
  crackEnvelope_ = 0.0f;
  boltEnvelope_ = 0.0f;
  boltTimer_ = randomRange(0.4f, 1.4f);
  boltDecayRate_ = 0.9990f;
  rollDelaySeconds_ = 0.0f;
  dropEnvelope_ = 0.0f;
  dropTimer_ = randomRange(0.02f, 0.10f);
  slowLfo_.reset();
  mediumLfo_.reset();
  fastLfo_.reset();
  noiseThunder_.reset();
  noiseThunderBolt_.reset();
  noiseMid_.reset();
  noiseDrop_.reset();
  rumbleLowPass_.reset();
  crackBandPass_.reset();
  rainHighPass_.reset();
  subBassLowPass_.reset();
  dropBandPass_.reset();
  updateFilters();
}

void ThunderAmbienceMode::updateFilters() {
  const float sr = static_cast<float>(sampleRate());
  rumbleLowPass_.setCoefficients(
      BiquadFilter::Type::LowPass,
      80.0f + controls().intensity * 60.0f + controls().accent * 30.0f,
      0.9f,
      sr);
  crackBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      1800.0f + controls().detail * 2400.0f + controls().accent * 600.0f,
      1.5f + controls().accent * 1.0f,
      sr);
  rainHighPass_.setCoefficients(
      BiquadFilter::Type::HighPass,
      2800.0f + controls().detail * 1800.0f,
      0.6f,
      sr);
  subBassLowPass_.setCoefficients(
      BiquadFilter::Type::LowPass,
      45.0f + controls().intensity * 20.0f,
      0.9f,
      sr);
  dropBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      1800.0f + controls().detail * 1200.0f + controls().accent * 400.0f,
      1.2f + controls().accent * 0.6f,
      sr);
}

float ThunderAmbienceMode::sample(float /*elapsedSeconds*/) {
  const float dt = 1.0f / sampleRate();
  slowLfo_.advance();
  mediumLfo_.advance();
  fastLfo_.advance();
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;

  float subBass = subBassLowPass_.process(noiseThunder_.brown());
  subBass *= (0.16f + controls().intensity * 0.24f + controls().accent * 0.10f) * (0.8f + slowMod * 0.2f);

  rumblePhase_ += (0.025f + controls().motion * 0.04f) * dt;
  if (rumblePhase_ > 1.0f) rumblePhase_ -= 1.0f;
  const float rumbleMod = 0.75f + 0.25f * std::sin(rumblePhase_ * 2.0f * kPi);
  float rumble = rumbleLowPass_.process(noiseThunder_.brown());
  rumble *= (0.20f + controls().intensity * 0.30f + controls().accent * 0.12f) * rumbleMod;

  boltTimer_ -= dt;
  if (boltTimer_ <= 0.0f && boltEnvelope_ < 0.08f) {
    boltEnvelope_ = 0.85f + controls().intensity * 0.15f;
    crackEnvelope_ = 0.92f + controls().detail * 0.12f + controls().accent * 0.08f;
    boltDecayRate_ = 0.9980f + nextRandom01() * 0.0016f;
    rollDelaySeconds_ = randomRange(0.018f, 0.085f) + (1.0f - controls().motion) * 0.025f;
    const float boltRateHz =
        0.08f + controls().intensity * 0.20f + controls().accent * 0.04f + controls().motion * 0.05f;
    boltTimer_ = sampleEventInterval(boltRateHz, 1.0f);
  }
  boltEnvelope_ *= boltDecayRate_;
  crackEnvelope_ *= 0.9865f - controls().detail * 0.0025f;
  rollDelaySeconds_ = std::max(0.0f, rollDelaySeconds_ - dt);

  const float brightPhase = crackEnvelope_ * crackEnvelope_;
  float crack = crackBandPass_.process(noiseThunderBolt_.white());
  crack *= brightPhase * brightPhase * (0.50f + controls().detail * 0.30f + controls().accent * 0.38f);
  float crackAir = rainHighPass_.process(noiseThunderBolt_.white());
  crackAir *= crackEnvelope_ * (0.02f + controls().detail * 0.04f + controls().accent * 0.10f);

  const float bodyGate = rollDelaySeconds_ <= 0.0f ? 1.0f : 0.0f;
  const float bodyPhase = bodyGate * boltEnvelope_ * (1.0f - std::min(0.55f, brightPhase * 0.35f));
  float boltRumble = rumbleLowPass_.process(noiseThunderBolt_.brown());
  boltRumble *= bodyPhase * (0.4f + controls().intensity * 0.3f);

  float rain = rainHighPass_.process(noiseMid_.pink());
  rain *= (0.10f + controls().intensity * 0.16f + controls().detail * 0.10f)
      * (0.85f + mediumLfo_.triangle() * 0.15f);

  dropTimer_ -= dt;
  if (dropTimer_ <= 0.0f) {
    dropEnvelope_ = std::max(
        dropEnvelope_,
        (0.16f + controls().intensity * 0.20f + controls().accent * 0.16f) * randomRange(0.85f, 1.18f));
    const float dropRateHz = 6.0f + controls().intensity * 14.0f + controls().accent * 2.0f;
    dropTimer_ = sampleEventInterval(dropRateHz, 0.01f);
  }
  dropEnvelope_ *= 0.9945f - controls().motion * 0.001f;
  float drop = dropBandPass_.process(noiseDrop_.white());
  drop *= dropEnvelope_ * (0.25f + controls().detail * 0.20f);

  float wind = noiseMid_.filtered(0.05f + controls().intensity * 0.10f, 0.15f + controls().motion * 0.1f);
  wind *= (0.06f + controls().intensity * 0.10f + controls().motion * 0.06f) * (0.8f + slowMod * 0.4f);

  return std::tanh((subBass + rumble + crack + crackAir + boltRumble + rain + drop + wind) * 0.72f);
}

}  // namespace jvn::audiofx::detail
