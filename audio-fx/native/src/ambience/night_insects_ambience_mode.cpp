#include "ambience/night_insects_ambience_mode.hpp"

namespace jvn::audiofx::detail {

NightInsectsAmbienceMode::NightInsectsAmbienceMode(int sampleRate)
    : BaseAmbienceMode(sampleRate, 0x60293847u) {
  const float sr = static_cast<float>(sampleRate);
  slowLfo_.setSampleRate(sr);
  mediumLfo_.setSampleRate(sr);
  fastLfo_.setSampleRate(sr);
}

void NightInsectsAmbienceMode::configure(const RenderControls& controls) {
  setControls(controls);
  chirpPhase_ = 0.0f;
  chirpEnvelope_ = 0.0f;
  cricket2Phase_ = 0.0f;
  cricket2Envelope_ = 0.0f;
  cricket3Phase_ = 0.0f;
  frogEnvelope_ = 0.0f;
  frogTimer_ = randomRange(2.0f, 4.5f);
  slowLfo_.reset();
  mediumLfo_.reset();
  fastLfo_.reset();
  noiseInsect_.reset();
  noiseInsect2_.reset();
  noiseMid_.reset();
  noiseHigh_.reset();
  chirpBandPass_.reset();
  bedLowPass_.reset();
  detailHighPass_.reset();
  cricket2BandPass_.reset();
  frogBandPass_.reset();
  updateFilters();
}

void NightInsectsAmbienceMode::updateFilters() {
  const float sr = static_cast<float>(sampleRate());
  chirpBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      3200.0f + controls().detail * 2800.0f + controls().accent * 800.0f,
      3.0f + controls().accent * 2.0f,
      sr);
  bedLowPass_.setCoefficients(
      BiquadFilter::Type::LowPass,
      280.0f + controls().intensity * 120.0f,
      0.7f,
      sr);
  detailHighPass_.setCoefficients(
      BiquadFilter::Type::HighPass,
      1600.0f + controls().detail * 1400.0f,
      0.65f,
      sr);
  cricket2BandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      5200.0f + controls().detail * 1800.0f + controls().accent * 600.0f,
      3.5f + controls().accent * 1.5f,
      sr);
  frogBandPass_.setCoefficients(
      BiquadFilter::Type::BandPass,
      320.0f + controls().accent * 180.0f,
      2.0f + controls().accent * 1.0f,
      sr);
}

float NightInsectsAmbienceMode::sample(float /*elapsedSeconds*/) {
  const float dt = 1.0f / sampleRate();
  slowLfo_.advance();
  mediumLfo_.advance();
  fastLfo_.advance();
  const float slowMod = slowLfo_.sine() * 0.5f + 0.5f;
  const float chorusMod = 0.55f + 0.45f * slowMod * (0.6f + controls().motion * 0.8f);

  float bed = bedLowPass_.process(noiseInsect_.brown());
  bed *= (0.10f + controls().intensity * 0.14f) * (0.85f + slowMod * 0.15f);

  chirpPhase_ += (3.0f + controls().accent * 4.5f + controls().motion * 2.5f) * dt;
  if (chirpPhase_ > 1.0f) chirpPhase_ -= 1.0f;
  const float burstPhase = std::fmod(chirpPhase_ * 3.0f, 1.0f);
  const float chirpPulse = burstPhase < 0.6f
      ? std::max(0.0f, std::sin(burstPhase / 0.6f * kPi))
      : 0.0f;
  const float chirpGate = std::pow(chirpPulse, 2.0f + controls().accent * 2.2f);

  const float chirp1Trigger = 0.003f + controls().intensity * 0.006f + controls().accent * 0.005f;
  if (noiseInsect_.white() > (1.0f - chirp1Trigger) && chirpEnvelope_ < 0.1f) {
    chirpEnvelope_ = 0.28f + controls().intensity * 0.35f + controls().accent * 0.25f;
  }
  chirpEnvelope_ *= 0.9960f - controls().motion * 0.0008f;
  float chirp1 = chirpBandPass_.process(noiseInsect_.white());
  chirp1 *= chirpGate * chirpEnvelope_
      * (0.35f + controls().detail * 0.40f + controls().accent * 0.14f)
      * chorusMod;

  cricket2Phase_ += (4.5f + controls().accent * 3.0f + controls().motion * 1.8f) * dt;
  if (cricket2Phase_ > 1.0f) cricket2Phase_ -= 1.0f;
  const float chirp2Pulse = std::max(0.0f, std::sin(cricket2Phase_ * 2.0f * kPi));
  const float chirp2Gate = std::pow(chirp2Pulse, 3.0f + controls().accent * 1.8f);

  const float chirp2Trigger = 0.002f + controls().intensity * 0.005f + controls().accent * 0.003f;
  if (noiseInsect2_.white() > (1.0f - chirp2Trigger) && cricket2Envelope_ < 0.1f) {
    cricket2Envelope_ = 0.20f + controls().intensity * 0.28f + controls().accent * 0.18f;
  }
  cricket2Envelope_ *= 0.9955f - controls().motion * 0.0007f;
  float chirp2 = cricket2BandPass_.process(noiseInsect2_.white());
  chirp2 *= chirp2Gate * cricket2Envelope_
      * (0.22f + controls().detail * 0.30f + controls().accent * 0.10f)
      * chorusMod;

  cricket3Phase_ += (6.0f + controls().motion * 3.0f) * dt;
  if (cricket3Phase_ > 1.0f) cricket3Phase_ -= 1.0f;
  const float cicadaMod = 0.5f + 0.5f * std::sin(cricket3Phase_ * 2.0f * kPi);
  const float cicadaPresence = std::max(0.0f, controls().accent - 0.25f) / 0.75f;
  float cicada = detailHighPass_.process(noiseMid_.pink());
  cicada *= cicadaPresence * cicadaMod * (0.06f + controls().intensity * 0.10f + controls().detail * 0.06f);

  frogTimer_ -= dt;
  if (frogTimer_ <= 0.0f && frogEnvelope_ < 0.08f) {
    frogEnvelope_ =
        (0.22f + controls().intensity * 0.20f + controls().accent * 0.18f) * randomRange(0.90f, 1.15f);
    const float frogRateHz = 0.04f + controls().intensity * 0.10f + controls().accent * 0.12f;
    frogTimer_ = sampleEventInterval(frogRateHz, 0.9f);
  }
  frogEnvelope_ *= 0.9985f - controls().motion * 0.0003f;
  float frog = frogBandPass_.process(noiseInsect2_.white());
  frog *= frogEnvelope_ * (0.15f + controls().accent * 0.20f);

  float rustle = detailHighPass_.process(noiseHigh_.pink());
  rustle *= (0.03f + controls().detail * 0.06f + controls().intensity * 0.03f)
      * (0.7f + mediumLfo_.triangle() * 0.3f);

  return std::tanh((bed + chirp1 + chirp2 + cicada + frog + rustle) * 0.82f);
}

}  // namespace jvn::audiofx::detail
