#include "ambience/ambience_filters.hpp"
#include "ambience/ambience_mastering.hpp"
#include "ambience/ambience_modulation.hpp"
#include "ambience/rain_ambience_mode.hpp"
#include "ambience/ambience_noise.hpp"
#include "ambience/ambience_mode.hpp"

#include <cmath>
#include <iostream>
#include <string>
#include <vector>

namespace {

using namespace jvn::audiofx::detail;

int gFailures = 0;

void expect(bool condition, const std::string& message) {
  if (!condition) {
    std::cerr << "FAILED: " << message << '\n';
    ++gFailures;
  }
}

class ProbeMode final : public BaseAmbienceMode {
public:
  ProbeMode() : BaseAmbienceMode(44100, 0x7A6B5C4Du) {}

  void configure(const RenderControls& controls) override { setControls(controls); }
  float sample(float) override { return 0.0f; }

  using BaseAmbienceMode::sampleEventInterval;
};

double mean(const std::vector<float>& values) {
  double sum = 0.0;
  for (float value : values) sum += value;
  return sum / static_cast<double>(values.size());
}

double rms(const std::vector<float>& values) {
  double energy = 0.0;
  for (float value : values) energy += static_cast<double>(value) * static_cast<double>(value);
  return std::sqrt(energy / static_cast<double>(values.size()));
}

double highFrequencyEnergy(const std::vector<float>& values) {
  double energy = 0.0;
  for (size_t i = 1; i < values.size(); ++i) {
    const double diff = static_cast<double>(values[i]) - static_cast<double>(values[i - 1]);
    energy += diff * diff;
  }
  return std::sqrt(energy / static_cast<double>(values.size() - 1));
}

double shortLagCoherence(const std::vector<float>& values, int maxLag) {
  double energy = 0.0;
  for (float value : values) {
    energy += static_cast<double>(value) * static_cast<double>(value);
  }
  double total = 0.0;
  for (int lag = 1; lag <= maxLag; ++lag) {
    double corr = 0.0;
    for (size_t i = static_cast<size_t>(lag); i < values.size(); ++i) {
      corr += static_cast<double>(values[i]) * static_cast<double>(values[i - lag]);
    }
    total += std::abs(corr) / std::max(1.0e-9, energy);
  }
  return total / std::max(1, maxLag);
}

double lowBandShare(const std::vector<float>& values, double cutoffHz) {
  const double dt = 1.0 / 44100.0;
  const double alpha = dt / ((1.0 / (2.0 * kPi * cutoffHz)) + dt);
  double lowPass = 0.0;
  double low = 0.0;
  double high = 0.0;
  for (float value : values) {
    lowPass += alpha * (static_cast<double>(value) - lowPass);
    const double highPass = static_cast<double>(value) - lowPass;
    low += lowPass * lowPass;
    high += highPass * highPass;
  }
  return low / std::max(1.0e-9, low + high);
}

void testBiquadLowPassImpulseStability() {
  BiquadFilter filter;
  filter.setCoefficients(BiquadFilter::Type::LowPass, 1200.0f, 0.707f, 44100.0f);
  float maxAbs = 0.0f;
  float tail = 0.0f;
  for (int i = 0; i < 8192; ++i) {
    const float sample = filter.process(i == 0 ? 1.0f : 0.0f);
    expect(std::isfinite(sample), "low-pass impulse output must stay finite");
    maxAbs = std::max(maxAbs, std::abs(sample));
    if (i > 7000) tail += std::abs(sample);
  }
  expect(maxAbs < 1.1f, "low-pass impulse response should stay bounded");
  expect(tail < 0.002f, "low-pass impulse response should decay close to zero");
}

void testBandPassRejectsDc() {
  BiquadFilter filter;
  filter.setCoefficients(BiquadFilter::Type::BandPass, 1400.0f, 1.4f, 44100.0f);
  std::vector<float> tail;
  tail.reserve(4096);
  for (int i = 0; i < 8192; ++i) {
    const float sample = filter.process(1.0f);
    if (i >= 4096) tail.push_back(sample);
  }
  expect(rms(tail) < 1.0e-3, "band-pass output should reject steady-state DC");
}

void testModalResonatorProducesFiniteDecay() {
  ModalResonator resonator;
  resonator.setMode(920.0f, 0.12f, 44100.0f);
  resonator.excite(1.0f);
  float maxAbs = 0.0f;
  double earlyEnergy = 0.0;
  double lateEnergy = 0.0;
  for (int i = 0; i < 8192; ++i) {
    const float sample = resonator.process();
    expect(std::isfinite(sample), "modal resonator output must stay finite");
    maxAbs = std::max(maxAbs, std::abs(sample));
    if (i < 2048) {
      earlyEnergy += static_cast<double>(sample) * static_cast<double>(sample);
    } else if (i > 6144) {
      lateEnergy += static_cast<double>(sample) * static_cast<double>(sample);
    }
  }
  expect(maxAbs < 2.0f, "modal resonator should stay bounded after excitation");
  expect(earlyEnergy > lateEnergy * 4.0, "modal resonator should decay over time");
}

void testLfoSmoothRandomIsDeterministicAndContinuous() {
  Lfo first(0.45f, 0.1f, 0xBADC0DEu);
  Lfo second(0.45f, 0.1f, 0xBADC0DEu);
  first.setSampleRate(44100.0f);
  second.setSampleRate(44100.0f);
  first.reset();
  second.reset();
  float maxStep = 0.0f;
  float previous = first.smoothRandom();
  float otherPrevious = second.smoothRandom();
  for (int i = 0; i < 8192; ++i) {
    const float a = first.smoothRandom();
    const float b = second.smoothRandom();
    expect(std::abs(a - b) < 1.0e-6f, "smooth random LFO must be deterministic after reset");
    maxStep = std::max(maxStep, std::abs(a - previous));
    previous = a;
    otherPrevious = b;
    first.advance();
    second.advance();
  }
  (void) otherPrevious;
  expect(maxStep < 0.08f, "smooth random LFO should remain continuous between adjacent samples");
}

void testNoiseColorOrdering() {
  NoiseGenerator whiteNoise(0x1111u);
  NoiseGenerator pinkNoise(0x1111u);
  NoiseGenerator brownNoise(0x1111u);
  std::vector<float> white;
  std::vector<float> pink;
  std::vector<float> brown;
  white.reserve(65536);
  pink.reserve(65536);
  brown.reserve(65536);
  for (int i = 0; i < 65536; ++i) {
    white.push_back(whiteNoise.white());
    pink.push_back(pinkNoise.pink());
    brown.push_back(brownNoise.brown());
  }
  const double whiteHf = highFrequencyEnergy(white);
  const double pinkHf = highFrequencyEnergy(pink);
  const double brownHf = highFrequencyEnergy(brown);
  expect(whiteHf > pinkHf, "pink noise should have less high-frequency energy than white noise");
  expect(pinkHf > brownHf, "brown noise should have less high-frequency energy than pink noise");
}

void testFilteredNoiseStaysBounded() {
  NoiseGenerator noise(0x2222u);
  float maxAbs = 0.0f;
  for (int i = 0; i < 32768; ++i) {
    const float sample = noise.filtered(0.08f, 0.75f);
    expect(std::isfinite(sample), "filtered noise must stay finite");
    maxAbs = std::max(maxAbs, std::abs(sample));
  }
  expect(maxAbs < 1.6f, "filtered noise should stay bounded under resonant settings");
}

void testGustGeneratorTerminates() {
  GustGenerator gust(44100.0f, 0x3333u);
  gust.reset();
  gust.trigger(0.9f);
  float maxValue = 0.0f;
  for (int i = 0; i < 44100 * 8; ++i) {
    maxValue = std::max(maxValue, gust.generate());
    gust.update();
    if (!gust.isActive()) break;
  }
  expect(maxValue > 0.2f, "gust generator should produce an audible envelope");
  expect(!gust.isActive(), "gust generator should terminate after its decay");
}

void testDcBlockerRemovesOffset() {
  DcBlocker blocker(18.0f, 44100.0f);
  std::vector<float> tail;
  tail.reserve(4096);
  for (int i = 0; i < 16384; ++i) {
    const float sample = blocker.process(0.35f);
    if (i >= 12288) tail.push_back(sample);
  }
  expect(std::abs(mean(tail)) < 1.0e-3, "DC blocker should remove steady offset");
}

void testMasterLimiterBoundsHotSignal() {
  MasterState master;
  master.reset();
  float maxAbs = 0.0f;
  for (int i = 0; i < 32768; ++i) {
    const float hot = (i & 1) == 0 ? 3.0f : -3.0f;
    const float sample = master.process(hot);
    maxAbs = std::max(maxAbs, std::abs(sample));
  }
  expect(maxAbs <= 1.0f + 1.0e-6f, "master stage should clamp output to the audio range");
}

void testPoissonIntervalsRespectMinimum() {
  ProbeMode probe;
  probe.configure(RenderControls{});
  double sum = 0.0;
  const float minimum = 0.05f;
  for (int i = 0; i < 4096; ++i) {
    const float interval = probe.sampleEventInterval(4.5f, minimum);
    expect(interval >= minimum, "sampled event intervals must respect the configured minimum");
    sum += interval;
  }
  const double meanInterval = sum / 4096.0;
  expect(meanInterval > minimum + 0.18, "sampled event intervals should follow a positive exponential tail");
}

void testRainModeKeepsLowMidBodyAndTemporalCoherence() {
  RainAmbienceMode rain(44100);
  RenderControls controls{};
  controls.intensity = 0.82f;
  controls.volume = 0.56f;
  controls.detail = 0.68f;
  controls.motion = 0.52f;
  controls.spread = 0.48f;
  controls.accent = 0.72f;
  controls.loop = true;
  rain.configure(controls);

  std::vector<float> samples;
  samples.reserve(176400);
  for (int i = 0; i < 176400; ++i) {
    samples.push_back(rain.sample(static_cast<float>(i) / 44100.0f));
  }

  std::vector<float> white(samples.size(), 0.0f);
  std::mt19937 rng(0x4A564E52u);
  std::uniform_real_distribution<float> dist(-1.0f, 1.0f);
  for (float& sample : white) {
    sample = dist(rng);
  }

  expect(lowBandShare(samples, 900.0) > 0.24, "rain should keep low-mid roof/gutter body");
  expect(shortLagCoherence(samples, 12) > shortLagCoherence(white, 12) * 6.0,
      "rain should be materially more coherent than white noise");
}

}  // namespace

int main() {
  testBiquadLowPassImpulseStability();
  testBandPassRejectsDc();
  testModalResonatorProducesFiniteDecay();
  testLfoSmoothRandomIsDeterministicAndContinuous();
  testNoiseColorOrdering();
  testFilteredNoiseStaysBounded();
  testGustGeneratorTerminates();
  testDcBlockerRemovesOffset();
  testMasterLimiterBoundsHotSignal();
  testPoissonIntervalsRespectMinimum();
  testRainModeKeepsLowMidBodyAndTemporalCoherence();
  if (gFailures != 0) {
    std::cerr << gFailures << " native ambience math test(s) failed\n";
    return 1;
  }
  std::cout << "Native ambience math tests passed\n";
  return 0;
}
