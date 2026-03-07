#pragma once

#include <array>
#include <cstdint>
#include <random>
#include <string>

namespace jvn::audiofx {

class BiquadFilter {
public:
  enum class Type { LowPass, HighPass, BandPass };

  BiquadFilter();

  void setCoefficients(Type type, float frequency, float q, float sampleRate);
  float process(float input);
  void reset();

private:
  float b0_ = 0.0f;
  float b1_ = 0.0f;
  float b2_ = 0.0f;
  float a1_ = 0.0f;
  float a2_ = 0.0f;
  float x1_ = 0.0f;
  float x2_ = 0.0f;
  float y1_ = 0.0f;
  float y2_ = 0.0f;
};

class Lfo {
public:
  explicit Lfo(float rate = 1.0f, float phase = 0.0f, uint32_t seed = 0x13579BDFu);

  void setRate(float rate);
  void setSampleRate(float sampleRate);
  float sine();
  float triangle();
  float smoothRandom();
  void advance();

private:
  float rate_;
  float phase_;
  float sampleRate_ = 44100.0f;
  float smoothValue_ = 0.0f;
  float targetValue_ = 0.0f;
  std::mt19937 rng_;
};

class NoiseGenerator {
public:
  explicit NoiseGenerator(uint32_t seed = 0x2468ACE0u);

  float white();
  float pink();
  float brown();
  float filtered(float cutoff, float resonance);
  void reset();

private:
  std::mt19937 rng_;
  std::uniform_real_distribution<float> dist_{-1.0f, 1.0f};
  std::array<float, 16> pinkRows_{};
  int pinkIndex_ = 0;
  float pinkRunningSum_ = 0.0f;
  float brownValue_ = 0.0f;
  float filterState_ = 0.0f;
};

class GustGenerator {
public:
  explicit GustGenerator(float sampleRate = 44100.0f, uint32_t seed = 0xA5A55A5Au);

  void setSampleRate(float sampleRate);
  void trigger(float intensity = 1.0f);
  float generate();
  void update();
  bool isActive() const noexcept;

private:
  float sampleRate_;
  bool active_ = false;
  float phase_ = 0.0f;
  float duration_ = 0.0f;
  float intensity_ = 0.0f;
  float attackTime_ = 0.0f;
  float decayTime_ = 0.0f;
  std::mt19937 rng_;
};

class LoomAmbienceRenderer {
public:
  explicit LoomAmbienceRenderer(int sampleRate);

  void configure(
      const std::string& preset,
      float intensity,
      float volume,
      float detail,
      float motion,
      float spread,
      float accent,
      bool loop);
  int render(uint8_t* pcm, int frames);
  void stop();
  void setVolume(float volume);
  bool finished() const noexcept;

private:
  enum class Preset { Wind, Rain, Ocean, Thunder, Fireplace, NightInsects };

  static float clamp01(float value);
  static Preset presetFromToken(const std::string& token);
  float nextRandom01();
  float randomRange(float minValue, float maxValue);
  float sampleEventInterval(float rateHz, float minimumSeconds);
  void updateFilters();
  void maybeTriggerWindGust(float dt);
  float synthesizeWindSample();
  float synthesizeRainSample();
  float synthesizeOceanSample();
  float synthesizeThunderSample();
  float synthesizeFireplaceSample();
  float synthesizeNightInsectsSample();
  float nextMonoSample();

  int sampleRate_;
  Preset preset_ = Preset::Wind;
  float intensity_ = 0.65f;
  float volume_ = 0.45f;
  float detail_ = 0.5f;
  float motion_ = 0.5f;
  float spread_ = 0.5f;
  float accent_ = 0.5f;
  bool loop_ = true;
  bool finished_ = false;
  float elapsedSeconds_ = 0.0f;
  float gustTimer_ = 0.0f;
  float dropletEnvelope_ = 0.0f;
  float rainDropTimer_ = 0.0f;
  float thunderRumblePhase_ = 0.0f;
  float thunderCrackEnvelope_ = 0.0f;
  float crackleEnvelope_ = 0.0f;
  float chirpPhase_ = 0.0f;
  float chirpEnvelope_ = 0.0f;
  float windWhistlePhase_ = 0.0f;

  // Ocean enhanced state
  float oceanCrashEnvelope_ = 0.0f;
  float oceanCrashTimer_ = 0.0f;
  // Thunder enhanced state
  float thunderBoltEnvelope_ = 0.0f;
  float thunderBoltTimer_ = 0.0f;
  float thunderBoltDecayRate_ = 0.9990f;
  float thunderDropEnvelope_ = 0.0f;
  float thunderDropTimer_ = 0.0f;
  // Fireplace enhanced state
  float fireSnapEnvelope_ = 0.0f;
  float firePopEnvelope_ = 0.0f;
  float fireEmberPhase_ = 0.0f;
  float fireCrackleTimer_ = 0.0f;
  float firePopTimer_ = 0.0f;
  float fireSnapTimer_ = 0.0f;
  // Night insects enhanced state
  float cricket2Phase_ = 0.0f;
  float cricket2Envelope_ = 0.0f;
  float cricket3Phase_ = 0.0f;
  float frogEnvelope_ = 0.0f;
  float frogTimer_ = 0.0f;

  std::mt19937 eventRng_{0x10293847u};
  std::uniform_real_distribution<float> eventDist_{0.0f, 1.0f};

  NoiseGenerator noiseLow_{0x11111111u};
  NoiseGenerator noiseMid_{0x22222222u};
  NoiseGenerator noiseHigh_{0x33333333u};
  NoiseGenerator noiseGust_{0x44444444u};
  NoiseGenerator noiseDrop_{0x55555555u};

  BiquadFilter lowPassLow_;
  BiquadFilter lowPassMid_;
  BiquadFilter highPassMid_;
  BiquadFilter highPassHigh_;
  BiquadFilter lowPassHigh_;
  BiquadFilter gustFilter_;

  BiquadFilter rainBedLowPass_;
  BiquadFilter rainBedHighPass_;
  BiquadFilter rainHissHighPass_;
  BiquadFilter rainDropBandPass_;

  BiquadFilter oceanSwellLowPass_;
  BiquadFilter oceanWashBandPass_;
  BiquadFilter oceanFoamHighPass_;

  BiquadFilter thunderRumbleLowPass_;
  BiquadFilter thunderCrackBandPass_;
  BiquadFilter thunderRainHighPass_;

  BiquadFilter fireCrackleBandPass_;
  BiquadFilter fireBaseLowPass_;
  BiquadFilter fireHissHighPass_;

  BiquadFilter insectChirpBandPass_;
  BiquadFilter insectBedLowPass_;
  BiquadFilter insectDetailHighPass_;

  // Ocean enhanced filters
  BiquadFilter oceanUndertowLowPass_;
  BiquadFilter oceanCrashBandPass_;
  BiquadFilter oceanSprayHighPass_;
  // Thunder enhanced filters
  BiquadFilter thunderSubBassLowPass_;
  BiquadFilter thunderDropBandPass_;
  // Fireplace enhanced filters
  BiquadFilter fireSnapBandPass_;
  BiquadFilter fireEmberLowPass_;
  // Night insects enhanced filters
  BiquadFilter cricket2BandPass_;
  BiquadFilter frogBandPass_;

  Lfo slowLfo_{0.15f, 0.0f, 0xDEADBEEFu};
  Lfo mediumLfo_{0.6f, 0.25f, 0xC0FFEE11u};
  Lfo fastLfo_{2.5f, 0.5f, 0xFACE1234u};
  Lfo panLfo_{0.11f, 0.1f, 0x76543210u};
  NoiseGenerator noiseThunder_{0x66666666u};
  NoiseGenerator noiseFire_{0x77777777u};
  NoiseGenerator noiseInsect_{0x88888888u};
  NoiseGenerator noiseOceanFoam_{0x99999999u};
  NoiseGenerator noiseThunderBolt_{0xAAAAAAAAu};
  NoiseGenerator noiseFireDetail_{0xBBBBBBBBu};
  NoiseGenerator noiseInsect2_{0xCCCCCCCCu};

  GustGenerator gust_{44100.0f, 0xABCDEF01u};
};

}  // namespace jvn::audiofx
