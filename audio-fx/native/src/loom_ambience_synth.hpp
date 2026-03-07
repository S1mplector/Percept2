#pragma once

#include <array>
#include <cstdint>
#include <random>
#include <string>

namespace jvn::audiofx::detail {

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
  void reset();
  float sine();
  float triangle();
  float smoothRandom();
  void advance();

private:
  float rate_;
  float phase_;
  float initialPhase_;
  float sampleRate_ = 44100.0f;
  float smoothValue_ = 0.0f;
  float targetValue_ = 0.0f;
  uint32_t seed_;
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
  uint32_t seed_;
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
  void reset();
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
  uint32_t seed_;
  std::mt19937 rng_;
};

class DcBlocker {
public:
  explicit DcBlocker(float cutoffHz = 18.0f, float sampleRate = 44100.0f);

  void setSampleRate(float sampleRate);
  void reset();
  float process(float input);

private:
  float cutoffHz_;
  float sampleRate_;
  float coefficient_ = 0.995f;
  float x1_ = 0.0f;
  float y1_ = 0.0f;
};

struct RenderControls {
  float intensity = 0.65f;
  float volume = 0.45f;
  float detail = 0.5f;
  float motion = 0.5f;
  float spread = 0.5f;
  float accent = 0.5f;
  bool loop = true;
};

enum class AmbiencePreset { Wind, Rain, Ocean, Thunder, Fireplace, NightInsects };

struct SharedLfoBank {
  Lfo slow{0.15f, 0.0f, 0xDEADBEEFu};
  Lfo medium{0.6f, 0.25f, 0xC0FFEE11u};
  Lfo fast{2.5f, 0.5f, 0xFACE1234u};
  Lfo pan{0.11f, 0.1f, 0x76543210u};

  void setSampleRate(float sampleRate);
  void reset();
};

struct SharedNoiseBank {
  NoiseGenerator low{0x11111111u};
  NoiseGenerator mid{0x22222222u};
  NoiseGenerator high{0x33333333u};
  NoiseGenerator gust{0x44444444u};
  NoiseGenerator drop{0x55555555u};
  NoiseGenerator thunder{0x66666666u};
  NoiseGenerator fire{0x77777777u};
  NoiseGenerator insect{0x88888888u};
  NoiseGenerator oceanFoam{0x99999999u};
  NoiseGenerator thunderBolt{0xAAAAAAAAu};
  NoiseGenerator fireDetail{0xBBBBBBBBu};
  NoiseGenerator insect2{0xCCCCCCCCu};

  void reset();
};

struct WindState {
  float gustTimer = 0.0f;
  float whistlePhase = 0.0f;
  float whistleOvertonePhase = 0.0f;
  GustGenerator gust{44100.0f, 0xABCDEF01u};
  BiquadFilter lowPassLow;
  BiquadFilter lowPassMid;
  BiquadFilter highPassMid;
  BiquadFilter highPassHigh;
  BiquadFilter lowPassHigh;
  BiquadFilter gustFilter;

  void setSampleRate(float sampleRate);
  void reset(float initialGustTimer);
};

struct RainState {
  float dropletEnvelope = 0.0f;
  float impactEnvelope = 0.0f;
  float impactPhase = 0.0f;
  float impactFrequency = 1200.0f;
  float dropTimer = 0.0f;
  BiquadFilter bedLowPass;
  BiquadFilter bedHighPass;
  BiquadFilter hissHighPass;
  BiquadFilter dropBandPass;
  BiquadFilter impactBandPass;

  void reset(float initialDropTimer);
};

struct OceanState {
  float crashEnvelope = 0.0f;
  float backwashEnvelope = 0.0f;
  float crashTimer = 0.0f;
  BiquadFilter swellLowPass;
  BiquadFilter washBandPass;
  BiquadFilter foamHighPass;
  BiquadFilter undertowLowPass;
  BiquadFilter crashBandPass;
  BiquadFilter sprayHighPass;
  BiquadFilter backwashBandPass;

  void reset(float initialCrashTimer);
};

struct ThunderState {
  float rumblePhase = 0.0f;
  float crackEnvelope = 0.0f;
  float boltEnvelope = 0.0f;
  float boltTimer = 0.0f;
  float boltDecayRate = 0.9990f;
  float rollDelaySeconds = 0.0f;
  float dropEnvelope = 0.0f;
  float dropTimer = 0.0f;
  BiquadFilter rumbleLowPass;
  BiquadFilter crackBandPass;
  BiquadFilter rainHighPass;
  BiquadFilter subBassLowPass;
  BiquadFilter dropBandPass;

  void reset(float initialBoltTimer, float initialDropTimer);
};

struct MasterState {
  DcBlocker dcBlocker{18.0f, 44100.0f};
  float limiterEnvelope = 0.0f;
  float limiterGain = 1.0f;

  void setSampleRate(float sampleRate);
  void reset();
  float process(float input);
};

struct FireplaceState {
  float crackleEnvelope = 0.0f;
  float popEnvelope = 0.0f;
  float snapEnvelope = 0.0f;
  float emberPhase = 0.0f;
  float crackleTimer = 0.0f;
  float popTimer = 0.0f;
  float snapTimer = 0.0f;
  BiquadFilter crackleBandPass;
  BiquadFilter baseLowPass;
  BiquadFilter hissHighPass;
  BiquadFilter snapBandPass;
  BiquadFilter emberLowPass;

  void reset(float initialCrackleTimer, float initialPopTimer, float initialSnapTimer);
};

struct NightInsectsState {
  float chirpPhase = 0.0f;
  float chirpEnvelope = 0.0f;
  float cricket2Phase = 0.0f;
  float cricket2Envelope = 0.0f;
  float cricket3Phase = 0.0f;
  float frogEnvelope = 0.0f;
  float frogTimer = 0.0f;
  BiquadFilter chirpBandPass;
  BiquadFilter bedLowPass;
  BiquadFilter detailHighPass;
  BiquadFilter cricket2BandPass;
  BiquadFilter frogBandPass;

  void reset(float initialFrogTimer);
};

class LoomAmbienceSynthCore {
public:
  explicit LoomAmbienceSynthCore(int sampleRate);

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
  static float clamp01(float value);
  static AmbiencePreset presetFromToken(const std::string& token);

  float nextRandom01();
  float randomRange(float minValue, float maxValue);
  float sampleEventInterval(float rateHz, float minimumSeconds);
  void resetDynamicState();
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
  AmbiencePreset preset_ = AmbiencePreset::Wind;
  RenderControls controls_{};
  bool finished_ = false;
  float elapsedSeconds_ = 0.0f;

  std::mt19937 eventRng_{0x10293847u};
  std::uniform_real_distribution<float> eventDist_{0.0f, 1.0f};

  SharedLfoBank lfos_{};
  SharedNoiseBank noise_{};
  WindState wind_{};
  RainState rain_{};
  OceanState ocean_{};
  ThunderState thunder_{};
  FireplaceState fireplace_{};
  NightInsectsState insects_{};
  MasterState master_{};
};

}  // namespace jvn::audiofx::detail
